/*******************************************************************************
 * Copyright 2013-2019 Qaprosoft (http://www.qaprosoft.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.zafira.services.services.application;

import static com.qaprosoft.zafira.models.db.Setting.SettingType.JIRA_URL;
import static com.qaprosoft.zafira.models.db.Status.*;
import static com.qaprosoft.zafira.services.util.DateFormatter.actualizeSearchCriteriaDate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.qaprosoft.zafira.models.db.*;
import com.qaprosoft.zafira.services.util.URLResolver;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDateTime;
import org.joda.time.Seconds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.qaprosoft.zafira.dbaccess.dao.mysql.application.TestRunMapper;
import com.qaprosoft.zafira.dbaccess.dao.mysql.application.search.JobSearchCriteria;
import com.qaprosoft.zafira.dbaccess.dao.mysql.application.search.SearchResult;
import com.qaprosoft.zafira.dbaccess.dao.mysql.application.search.TestRunSearchCriteria;
import com.qaprosoft.zafira.models.db.config.Argument;
import com.qaprosoft.zafira.models.db.config.Configuration;
import com.qaprosoft.zafira.models.dto.QueueTestRunParamsType;
import com.qaprosoft.zafira.models.dto.TestRunStatistics;
import com.qaprosoft.zafira.services.exceptions.IntegrationException;
import com.qaprosoft.zafira.services.exceptions.InvalidTestRunException;
import com.qaprosoft.zafira.services.exceptions.ServiceException;
import com.qaprosoft.zafira.services.exceptions.TestRunNotFoundException;
import com.qaprosoft.zafira.services.services.application.cache.StatisticsService;
import com.qaprosoft.zafira.services.services.application.emails.TestRunResultsEmail;
import com.qaprosoft.zafira.services.util.FreemarkerUtil;

@Service
public class TestRunService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRunService.class);
    public static final String DEFAULT_PROJECT = "UNKNOWN";

    public enum FailureCause {
        UNRECOGNIZED_FAILURE("UNRECOGNIZED FAILURE"),
        COMPILATION_FAILURE("COMPILATION FAILURE"),
        TIMED_OUT("TIMED OUT"),
        BUILD_FAILURE("BUILD FAILURE"),
        ABORTED("ABORTED");

        private String cause;

        public String getCause() {
            return this.cause;
        }

        FailureCause(String cause) {
            this.cause = cause;
        }
    }

    @Autowired
    private URLResolver urlResolver;

    @Autowired
    private TestRunMapper testRunMapper;

    @Autowired
    private TestService testService;

    @Autowired
    private FreemarkerUtil freemarkerUtil;

    @Autowired
    private TestConfigService testConfigService;

    @Autowired
    private WorkItemService workItemService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private JobsService jobsService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private StatisticsService statisticsService;

    private static final LoadingCache<Long, Lock> updateLocks = CacheBuilder.newBuilder()
            .maximumSize(100000)
            .expireAfterWrite(150, TimeUnit.MILLISECONDS)
            .build(
                    new CacheLoader<Long, Lock>() {
                        public Lock load(Long key) {
                            return new ReentrantLock();
                        }
                    });

    @Transactional(rollbackFor = Exception.class)
    public void createTestRun(TestRun testRun) {
        testRunMapper.createTestRun(testRun);
    }

    @Transactional(readOnly = true)
    public TestRun getTestRunById(long id) {
        return testRunMapper.getTestRunById(id);
    }

    @Transactional(readOnly = true)
    public TestRun getNotNullTestRunById(long id) {
        TestRun testRun = getTestRunById(id);
        if (testRun == null) {
            throw new TestRunNotFoundException();
        }
        return testRun;
    }

    @Transactional(readOnly = true)
    public SearchResult<TestRun> searchTestRuns(TestRunSearchCriteria sc) {
        actualizeSearchCriteriaDate(sc);
        SearchResult<TestRun> results = new SearchResult<>();
        results.setPage(sc.getPage());
        results.setPageSize(sc.getPageSize());
        results.setSortOrder(sc.getSortOrder());
        List<TestRun> testRuns = testRunMapper.searchTestRuns(sc);
        for (TestRun testRun : testRuns) {
            if (!StringUtils.isEmpty(testRun.getConfigXML())) {
                for (Argument arg : testConfigService.readConfigArgs(testRun.getConfigXML())) {
                    if (!StringUtils.isEmpty(arg.getValue())) {
                        if ("browser_version".equals(arg.getKey()) && !arg.getValue().equals("*") && !arg.getValue().equals("")
                                && arg.getValue() != null) {
                            testRun.setPlatform(testRun.getPlatform() + " " + arg.getValue());
                        }
                    }
                }
            }
        }
        results.setResults(testRuns);
        results.setTotalResults(testRunMapper.getTestRunsSearchCount(sc));
        return results;
    }

    @Transactional(readOnly = true)
    public TestRun getTestRunByCiRunId(String ciRunId) {
        return !StringUtils.isEmpty(ciRunId) ? testRunMapper.getTestRunByCiRunId(ciRunId) : null;
    }

    @Transactional(readOnly = true)
    public TestRun getTestRunByIdFull(long id) {
        return testRunMapper.getTestRunByIdFull(id);
    }

    @Transactional(readOnly = true)
    public TestRun getTestRunByIdFull(String id) {
        return id.matches("\\d+") ? testRunMapper.getTestRunByIdFull(Long.valueOf(id)) : getTestRunByCiRunIdFull(id);
    }

    @Transactional(readOnly = true)
    public TestRun getTestRunByCiRunIdFull(String ciRunId) {
        return testRunMapper.getTestRunByCiRunIdFull(ciRunId);
    }

    @Transactional(readOnly = true)
    public List<TestRun> getTestRunsByUpstreamJobIdAndUpstreamJobBuildNumber(Long jobId, Integer buildNumber) {
        return testRunMapper.getTestRunsByUpstreamJobIdAndUpstreamJobBuildNumber(jobId, buildNumber);
    }

    @Transactional(readOnly = true)
    public Map<Long, TestRun> getLatestJobTestRuns(String env, List<Long> jobIds) {
        Map<Long, TestRun> jobTestRuns = new HashMap<>();
        for (TestRun tr : testRunMapper.getLatestJobTestRuns(env, jobIds)) {
            jobTestRuns.put(tr.getJob().getId(), tr);
        }
        return jobTestRuns;
    }

    @Transactional(readOnly = true)
    public TestRun getLatestJobTestRunByBranchAndJobURL(String branch, String jobURL) {
        return testRunMapper.getLatestJobTestRunByBranch(branch, jobsService.getJobByJobURL(jobURL).getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public TestRun queueTestRun(QueueTestRunParamsType queueTestRunParams, User user) {
        TestRun testRun;
        // Check if testRun with provided ci_run_id exists in DB (mostly for queued and aborted without execution)
        TestRun existingRun = getTestRunByCiRunId(queueTestRunParams.getCiRunId());
        if (existingRun == null || Status.QUEUED.equals(existingRun.getStatus())) {
            testRun = getLatestJobTestRunByBranchAndJobURL(queueTestRunParams.getBranch(),
                    queueTestRunParams.getJobUrl());
            if (testRun != null) {
                Long latestTestRunId = testRun.getId();
                if (!StringUtils.isEmpty(queueTestRunParams.getCiParentUrl())) {
                    Job job = jobsService.createOrUpdateJobByURL(queueTestRunParams.getCiParentUrl(), user);
                    testRun.setUpstreamJob(job);
                }
                if (!StringUtils.isEmpty(queueTestRunParams.getCiParentBuild())) {
                    testRun.setUpstreamJobBuildNumber(Integer.valueOf(queueTestRunParams.getCiParentBuild()));
                }
                if (!StringUtils.isEmpty(queueTestRunParams.getProject())) {
                    Project project = projectService.getProjectByName(queueTestRunParams.getProject());
                    if (project == null) {
                        project = projectService.getProjectByName(DEFAULT_PROJECT);
                    }
                    testRun.setProject(project);
                }
                testRun.setEnv(queueTestRunParams.getEnv());
                testRun.setCiRunId(queueTestRunParams.getCiRunId());
                testRun.setElapsed(null);
                testRun.setPlatform(null);
                testRun.setConfigXML(null);
                testRun.setConfig(null);
                testRun.setComments(null);
                testRun.setAppVersion(null);
                testRun.setReviewed(false);
                testRun.setKnownIssue(false);
                testRun.setBlocker(false);

                // make sure to reset below3 fields for existing run as well
                testRun.setStatus(Status.QUEUED);
                testRun.setStartedAt(Calendar.getInstance().getTime());
                testRun.setBuildNumber(Integer.valueOf(queueTestRunParams.getBuildNumber()));
                createTestRun(testRun);
                List<Test> tests = testService.getTestsByTestRunId(latestTestRunId);
                TestRun queuedTestRun = getTestRunByCiRunId(queueTestRunParams.getCiRunId());
                for (Test test : tests) {
                    if (test.getStatus() != Status.QUEUED) {
                        test.setId(null);
                        test.setTestRunId(queuedTestRun.getId());
                        test.setStatus(Status.QUEUED);
                        test.setMessage(null);
                        test.setKnownIssue(false);
                        test.setBlocker(false);
                        test.setDependsOnMethods(null);
                        test.setTestConfig(null);
                        test.setNeedRerun(true);
                        test.setCiTestId(null);
                        test.setTags(null);
                        testService.createTest(test);
                    }
                }
            }
        } else {
            testRun = existingRun;
            testRun.setStatus(Status.QUEUED);
            testRun.setStartedAt(Calendar.getInstance().getTime());
            testRun.setBuildNumber(Integer.valueOf(queueTestRunParams.getBuildNumber()));
            updateTestRun(testRun);
        }
        return testRun;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestRun updateTestRun(TestRun testRun) {
        testRunMapper.updateTestRun(testRun);
        return testRun;
    }

    @CacheEvict(value = "environments", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestRunById(Long id) {
        testRunMapper.deleteTestRunById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public TestRun startTestRun(TestRun testRun) {
        if (!StringUtils.isEmpty(testRun.getCiRunId())) {
            TestRun existingTestRun = testRunMapper.getTestRunByCiRunId(testRun.getCiRunId());
            if (existingTestRun != null) {
                existingTestRun.setBuildNumber(testRun.getBuildNumber());
                existingTestRun.setConfigXML(testRun.getConfigXML());
                existingTestRun.setTestSuite(testRun.getTestSuite());
                testRun = existingTestRun;
                // TODO: investigate if startedBy should be also copied
            }
            LOGGER.info("Looking for test run with CI ID: " + testRun.getCiRunId());
            LOGGER.info("Test run found: " + (existingTestRun != null));
        } else {
            testRun.setCiRunId(UUID.randomUUID().toString());
            LOGGER.info("Generating new test run CI ID: " + testRun.getCiRunId());
        }

        initTestRunWithXml(testRun);

        // Initialize starting time
        testRun.setStartedAt(Calendar.getInstance().getTime());
        testRun.setReviewed(false);
        testRun.setEta(testRunMapper.getTestRunEtaByTestSuiteId(testRun.getTestSuite().getId()));

        // New test run
        if (testRun.getId() == null || testRun.getId() == 0) {
            switch (testRun.getStartedBy()) {
            case HUMAN:
                if (testRun.getUser() == null) {
                    throw new InvalidTestRunException("Specify userName if started by HUMAN!");
                }
                break;
            case SCHEDULER:
                testRun.setUpstreamJobBuildNumber(null);
                testRun.setUpstreamJob(null);
                testRun.setUser(null);
                break;
            case UPSTREAM_JOB:
                if (testRun.getUpstreamJob() == null || testRun.getUpstreamJobBuildNumber() == null) {
                    throw new InvalidTestRunException("Specify upstreamJobId and upstreaBuildNumber if started by UPSTREAM_JOB!");
                }
                break;
            }

            if (testRun.getWorkItem() != null && !StringUtils.isEmpty(testRun.getWorkItem().getJiraId())) {
                testRun.setWorkItem(workItemService.createOrGetWorkItem(testRun.getWorkItem()));
            }
            testRun.setStatus(IN_PROGRESS);
            createTestRun(testRun);
        }
        // Existing test run
        else {
            testRun.setStatus(IN_PROGRESS);
            updateTestRun(testRun);
        }
        return testRun;
    }

    public void initTestRunWithXml(TestRun testRun) {
        if (!StringUtils.isEmpty(testRun.getConfigXML())) {
            TestConfig config = testConfigService.createTestConfigForTestRun(testRun.getConfigXML());
            testRun.setConfig(config);
            testRun.setEnv(config.getEnv());
            testRun.setAppVersion(config.getAppVersion());
            if (!StringUtils.isEmpty(config.getBrowser()) && !config.getBrowser().equals("*")) {
                testRun.setPlatform(config.getBrowser());
            } else {
                testRun.setPlatform(config.getPlatform());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TestRun abortTestRun(TestRun testRun, String abortCause) {
        if (testRun != null) {
            List<Test> tests = testService.getTestsByTestRunId(testRun.getId());
            if (IN_PROGRESS.equals(testRun.getStatus()) || QUEUED.equals(testRun.getStatus()) && isBuildFailure(abortCause)) {
                for (Test test : tests) {
                    if (IN_PROGRESS.equals(test.getStatus()) || QUEUED.equals(test.getStatus())) {
                        testService.abortTest(test, abortCause);
                    }
                }
            }
            testRun = addComment(testRun.getId(), abortCause);
            testRun.setStatus(Status.ABORTED);
            updateTestRun(testRun);
            calculateTestRunResult(testRun.getId(), true);
        }
        return testRun;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestRun markAsReviewed(Long id, String comment) {
        TestRun tr = addComment(id, comment);
        if (!"undefined failure".equalsIgnoreCase(comment)) {
            tr.setReviewed(true);
        }
        tr = updateTestRun(tr);
        TestRunStatistics.Action action = tr.isReviewed() ? TestRunStatistics.Action.MARK_AS_REVIEWED : TestRunStatistics.Action.MARK_AS_NOT_REVIEWED;
        updateStatistics(tr.getId(), action);
        return tr;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<TestRun> getTestRunsForSmartRerun(JobSearchCriteria sc) {
        return testRunMapper.getTestRunsForSmartRerun(sc);
    }

    @Transactional(rollbackFor = Exception.class)
    public TestRun calculateTestRunResult(long id, boolean finishTestRun) {
        TestRun testRun = getNotNullTestRunById(id);

        List<Test> tests = testService.getTestsByTestRunId(testRun.getId());

        // Aborted testruns don't need status recalculation (already recalculated on abort end-point)
        if (!ABORTED.equals(testRun.getStatus())) {
            // Do not update test run status if tests are running and one clicks mark as passed or mark as known issue
            // (https://github.com/qaprosoft/zafira/issues/34)
            if ((finishTestRun || !IN_PROGRESS.equals(testRun.getStatus()))) {
                for (Test test : tests) {
                    if (IN_PROGRESS.equals(test.getStatus())) {
                        testService.skipTest(test);
                    }
                }
                testRun.setStatus(tests.size() > 0 ? PASSED : SKIPPED);
                testRun.setKnownIssue(false);
                testRun.setBlocker(false);
                for (Test test : tests) {
                    if (test.isKnownIssue()) {
                        testRun.setKnownIssue(true);
                    }
                    if (test.isBlocker()) {
                        testRun.setBlocker(true);
                    }
                    if (Arrays.asList(FAILED, SKIPPED).contains(test.getStatus())
                            && (!test.isKnownIssue() || (test.isKnownIssue() && test.isBlocker()))) {
                        testRun.setStatus(FAILED);
                        break;
                    }
                }
            }
        }
        if (finishTestRun && testRun.getStartedAt() != null) {
            LocalDateTime startedAt = new LocalDateTime(testRun.getStartedAt());
            LocalDateTime finishedAt = new LocalDateTime(Calendar.getInstance().getTime());
            Integer elapsed = Seconds.secondsBetween(startedAt, finishedAt).getSeconds();
            // according to https://github.com/qaprosoft/zafira/issues/748
            if (testRun.getElapsed() != null) {
                testRun.setElapsed(testRun.getElapsed() + elapsed);
            } else {
                testRun.setElapsed(elapsed);
            }
        }

        updateTestRun(testRun);
        testService.updateTestRerunFlags(testRun, tests);
        return testRun;
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, Test>> createCompareMatrix(List<Long> testRunIds) {
        Map<Long, Map<String, Test>> testNamesWithTests = new HashMap<>();
        Set<String> testNames = new HashSet<>();
        for (Long id : testRunIds) {
            List<Test> tests = testService.getTestsByTestRunId(id);
            testNamesWithTests.put(id, new HashMap<>());
            for (Test test : tests) {
                testNames.add(test.getName());
                testNamesWithTests.get(id).put(test.getName(), test);
            }
        }
        for (Long testRunId : testRunIds) {
            for (String testName : testNames) {
                testNamesWithTests.get(testRunId).putIfAbsent(testName, null);
            }
        }
        return testNamesWithTests;
    }

    @Transactional(readOnly = true)
    public String sendTestRunResultsEmail(final String testRunId, boolean showOnlyFailures, boolean showStacktrace, final String... recipients)
            throws JAXBException {
        TestRun testRun = getTestRunByIdFull(testRunId);
        if (testRun == null) {
            throw new TestRunNotFoundException("No test runs found by ID: " + testRunId);
        }
        List<Test> tests = testService.getTestsByTestRunId(testRunId);
        return sendTestRunResultsNotification(testRun, tests, showOnlyFailures, showStacktrace, recipients);
    }

    public String sendTestRunResultsNotification(final TestRun testRun, final List<Test> tests, boolean showOnlyFailures, boolean showStacktrace,
            final String... recipients) throws JAXBException {
        Configuration configuration = readConfiguration(testRun.getConfigXML());
        // Forward from API to Web
        configuration.getArg().add(new Argument("zafira_service_url", urlResolver.buildWebURL()));
        for (Test test : tests) {
            test.setArtifacts(new TreeSet<>(test.getArtifacts()));
        }
        TestRunResultsEmail email = new TestRunResultsEmail(configuration, testRun, tests);
        email.setJiraURL(settingsService.getSettingByType(JIRA_URL));
        email.setShowOnlyFailures(showOnlyFailures);
        email.setShowStacktrace(showStacktrace);
        email.setSuccessRate(calculateSuccessRate(testRun));
        String emailContent = null;
        try {
            emailContent = emailService.sendEmail(email, recipients);
        } catch (IntegrationException e) {
            LOGGER.error("Unable to send results email " + e);
        }
        return emailContent;
    }

    @Transactional(readOnly = true)
    public String exportTestRunHTML(final String id) throws JAXBException {
        TestRun testRun = getTestRunByIdFull(id);
        if (testRun == null) {
            throw new TestRunNotFoundException("No test runs found by ID: " + id);
        }
        Configuration configuration = readConfiguration(testRun.getConfigXML());
        configuration.getArg().add(new Argument("zafira_service_url", urlResolver.buildWebURL()));

        List<Test> tests = testService.getTestsByTestRunId(id);

        TestRunResultsEmail email = new TestRunResultsEmail(configuration, testRun, tests);
        email.setJiraURL(settingsService.getSettingByType(JIRA_URL));
        email.setSuccessRate(calculateSuccessRate(testRun));
        return freemarkerUtil.getFreeMarkerTemplateContent(email.getType().getTemplateName(), email);
    }

    public Configuration readConfiguration(String xml) throws JAXBException {
        Configuration configuration = new Configuration();
        if (!StringUtils.isEmpty(xml)) {
            ByteArrayInputStream xmlBA = new ByteArrayInputStream(xml.getBytes());
            configuration = (Configuration) JAXBContext.newInstance(Configuration.class).createUnmarshaller().unmarshal(xmlBA);
            IOUtils.closeQuietly(xmlBA);
        }
        return configuration;
    }

    public static int calculateSuccessRate(TestRun testRun) {
        int total = testRun.getPassed() + testRun.getFailed() + testRun.getSkipped();
        double rate = (double) testRun.getPassed() / (double) total;
        return total > 0 ? (new BigDecimal(rate).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(100))).intValue() : 0;
    }

    public boolean isBuildFailure(String comments) {
        boolean failure = false;
        if (StringUtils.isNotEmpty(comments)) {
            if (comments.contains(FailureCause.BUILD_FAILURE.getCause()) ||
                    comments.contains(FailureCause.COMPILATION_FAILURE.getCause()) ||
                    comments.contains(FailureCause.UNRECOGNIZED_FAILURE.getCause())) {
                failure = true;
            }
        }
        return failure;
    }

    @Transactional
    public TestRun addComment(long id, String comment) {
        TestRun testRun = getTestRunById(id);
        if (testRun == null) {
            throw new ServiceException("No test run found by ID: " + id);
        }
        testRun.setComments(comment);
        updateTestRun(testRun);
        return testRunMapper.getTestRunByIdFull(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "environments", key = "T(com.qaprosoft.zafira.dbaccess.utils.TenancyContext).tenantName + ':' + #result", condition = "#result != null && #result.size() != 0")
    public List<String> getEnvironments() {
        return testRunMapper.getEnvironments();
    }

    @Transactional(readOnly = true)
    public List<String> getPlatforms() {
        return testRunMapper.getPlatforms();
    }

    /**
     * Method contains a container needed to do a work safe with Test run statistics cache thread
     * 
     * @param testRunId - test run id
     * @param statisticsFunction - action with cached values
     * @return testRunStatistics with value incremented
     */
    private TestRunStatistics updateStatisticsSafe(Long testRunId, Function<TestRunStatistics, TestRunStatistics> statisticsFunction) {
        TestRunStatistics testRunStatistics = null;
        Lock lock = null;
        try {
            lock = updateLocks.get(testRunId);
            lock.lock();
            testRunStatistics = statisticsService.getTestRunStatistic(testRunId);
            testRunStatistics = statisticsFunction.apply(testRunStatistics);
            testRunStatistics = statisticsService.setTestRunStatistic(testRunStatistics);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
        return testRunStatistics;
    }

    /**
     * Increment value to statistics
     * 
     * @param testRunStatistics - cached test run statistic
     * @param status - test status
     * @param increment - integer (to increment - positive number, to decrement - negative number)
     * @return testRunStatistics with value incremented
     */
    private TestRunStatistics updateStatistics(TestRunStatistics testRunStatistics, Status status, int increment) {
        switch (status) {
        case PASSED:
            testRunStatistics.setPassed(testRunStatistics.getPassed() + increment);
            break;
        case FAILED:
            testRunStatistics.setFailed(testRunStatistics.getFailed() + increment);
            break;
        case SKIPPED:
            testRunStatistics.setSkipped(testRunStatistics.getSkipped() + increment);
            break;
        case ABORTED:
            testRunStatistics.setAborted(testRunStatistics.getAborted() + increment);
            break;
        case IN_PROGRESS:
            testRunStatistics.setInProgress(testRunStatistics.getInProgress() + increment);
            break;
        default:
            break;
        }
        return testRunStatistics;
    }

    /**
     * Update statistic by {@link com.qaprosoft.zafira.models.dto.TestRunStatistics.Action}
     * 
     * @param testRunId - test run id
     * @return new statistics
     */
    public TestRunStatistics updateStatistics(Long testRunId, TestRunStatistics.Action action) {
        return updateStatisticsSafe(testRunId, testRunStatistics -> {
            switch (action) {
            case MARK_AS_KNOWN_ISSUE:
                testRunStatistics.setFailedAsKnown(testRunStatistics.getFailedAsKnown() + 1);
                break;
            case REMOVE_KNOWN_ISSUE:
                testRunStatistics.setFailedAsKnown(testRunStatistics.getFailedAsKnown() - 1);
                break;
            case MARK_AS_BLOCKER:
                testRunStatistics.setFailedAsBlocker(testRunStatistics.getFailedAsBlocker() + 1);
                break;
            case REMOVE_BLOCKER:
                testRunStatistics.setFailedAsBlocker(testRunStatistics.getFailedAsBlocker() - 1);
                break;
            case MARK_AS_REVIEWED:
                testRunStatistics.setReviewed(true);
                break;
            case MARK_AS_NOT_REVIEWED:
                testRunStatistics.setReviewed(false);
                break;
            default:
                break;
            }
            return testRunStatistics;
        });
    }

    /**
     * Calculate new statistic by {@link com.qaprosoft.zafira.models.db.TestRun getStatus}
     * 
     * @param testRunId - test run id
     * @param status - new status
     * @return new statistics
     */
    public TestRunStatistics updateStatistics(Long testRunId, Status status, boolean isRerun) {
        int increment = isRerun ? -1 : 1;
        TestRunStatistics trs = updateStatisticsSafe(testRunId,
                testRunStatistics -> !status.equals(IN_PROGRESS) && (isRerun || testRunStatistics.getInProgress() > 0)
                        ? updateStatistics(testRunStatistics, IN_PROGRESS, -increment)
                        : testRunStatistics);
        if (trs != null && trs.getQueued() > 0 && (status.equals(IN_PROGRESS) || status.equals(ABORTED))) {
            updateStatisticsSafe(testRunId, testRunStatistics -> {
                testRunStatistics.setQueued(testRunStatistics.getQueued() - 1);
                return testRunStatistics;
            });
        }
        return updateStatisticsSafe(testRunId, testRunStatistics -> updateStatistics(testRunStatistics, status, increment));
    }

    /**
     * Calculate new statistic by {@link com.qaprosoft.zafira.models.db.TestRun getStatus}
     * 
     * @param testRunId - test run id
     * @param newStatus - new test status
     * @param currentStatus - current test status
     * @return new statistics
     */
    public TestRunStatistics updateStatistics(Long testRunId, Status newStatus, Status currentStatus) {
        return updateStatisticsSafe(testRunId, testRunStatistics -> updateStatistics(
                updateStatistics(testRunStatistics, currentStatus, -1), newStatus, 1));
    }

    public TestRunStatistics updateStatistics(Long testRunId, Status status) {
        return updateStatistics(testRunId, status, false);
    }
}
