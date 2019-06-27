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
package com.qaprosoft.zafira.client;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.MediaType;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.qaprosoft.zafira.models.dto.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.internal.SdkBufferedInputStream;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.qaprosoft.zafira.config.CIConfig;
import com.qaprosoft.zafira.config.GensonProvider;
import com.qaprosoft.zafira.models.db.Status;
import com.qaprosoft.zafira.models.db.TestRun.Initiator;
import com.qaprosoft.zafira.models.dto.auth.AccessTokenType;
import com.qaprosoft.zafira.models.dto.auth.AuthTokenType;
import com.qaprosoft.zafira.models.dto.auth.CredentialsType;
import com.qaprosoft.zafira.models.dto.auth.RefreshTokenType;
import com.qaprosoft.zafira.models.dto.auth.TenantType;
import com.qaprosoft.zafira.models.dto.aws.SessionCredentials;
import com.qaprosoft.zafira.models.dto.user.UserType;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class ZafiraClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZafiraClient.class);

    public static final String DEFAULT_USER = "anonymous";
    public static final String DEFAULT_PROJECT = "UNKNOWN";

    private static final Integer CONNECT_TIMEOUT = 60000;
    private static final Integer READ_TIMEOUT = 60000;

    private static final String STATUS_PATH = "/api/status";
    private static final String PROFILE_PATH = "/api/users/profile";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String ACCESS_PATH = "/api/auth/access";
    private static final String REFRESH_TOKEN_PATH = "/api/auth/refresh";
    private static final String USERS_PATH = "/api/users";
    private static final String JOBS_PATH = "/api/jobs";
    private static final String TESTS_PATH = "/api/tests";
    private static final String TEST_FINISH_PATH = "/api/tests/%d/finish";
    private static final String TEST_BY_ID_PATH = "/api/tests/%d";
    private static final String TEST_WORK_ITEMS_PATH = "/api/tests/%d/workitems";
    private static final String TEST_ARTIFACTS_PATH = "/api/tests/%d/artifacts";
    private static final String TEST_SUITES_PATH = "/api/tests/suites";
    private static final String TEST_CASES_PATH = "/api/tests/cases";
    private static final String TEST_CASES_BATCH_PATH = "/api/tests/cases/batch";
    private static final String TEST_RUNS_PATH = "/api/tests/runs";
    private static final String TEST_RUNS_FINISH_PATH = "/api/tests/runs/%d/finish";
    private static final String TEST_RUNS_RESULTS_PATH = "/api/tests/runs/%d/results";
    private static final String TEST_RUNS_ABORT_PATH = "/api/tests/runs/abort?id=%d";
    private static final String TEST_RUN_BY_ID_PATH = "/api/tests/runs/%d";
    private static final String SETTINGS_TOOL_PATH = "/api/settings/tool/%s";
    private static final String AMAZON_SESSION_CREDENTIALS_PATH = "/api/settings/amazon/creds";
    private static final String GOOGLE_SESSION_CREDENTIALS_PATH = "/api/settings/google/creds";
    private static final String TENANT_TYPE_PATH = "/api/auth/tenant";
    private static final String PROJECTS_PATH = "/api/projects/%s";

    private String serviceURL;
    private Client client;
    private String authToken;
    private String project = DEFAULT_PROJECT;

    private CompletableFuture<AmazonS3> amazonClient;
    private CompletableFuture<Sheets> sheets;
    private CompletableFuture<TenantType> tenantType;

    private SessionCredentials amazonS3SessionCredentials;

    public ZafiraClient(String serviceURL) {
        this.serviceURL = serviceURL;
        this.client = Client.create(new DefaultClientConfig(GensonProvider.class));
        this.client.setConnectTimeout(CONNECT_TIMEOUT);
        this.client.setReadTimeout(READ_TIMEOUT);
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isAvailable() {
        boolean isAvailable = false;
        try {
            WebResource webResource = client.resource(serviceURL + STATUS_PATH);
            ClientResponse clientRS = webResource.get(ClientResponse.class);
            if (clientRS.getStatus() == 200) {
                isAvailable = true;
            }

        } catch (Exception e) {
            LOGGER.error("Unable to send ping", e);
        }
        return isAvailable;
    }

    public synchronized Response<UserType> getUserProfile() {
        Response<UserType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + PROFILE_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(UserType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to authorize user", e);
        }
        return response;
    }

    public synchronized Response<UserType> getUserProfile(String username) {
        Response<UserType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + PROFILE_PATH + "?username=" + username);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(UserType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to authorize user", e);
        }
        return response;
    }

    public synchronized Response<AuthTokenType> login(String username, String password) {
        Response<AuthTokenType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + LOGIN_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, new CredentialsType(username, password));
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(AuthTokenType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to login", e);
        }
        return response;
    }

    public synchronized Response<AccessTokenType> generateAccessToken() {
        Response<AccessTokenType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + ACCESS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(AccessTokenType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to generate access token", e);
        }
        return response;
    }

    public synchronized Response<UserType> createUser(UserType user) {
        Response<UserType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + USERS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, user);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(UserType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create user", e);
        }
        return response;
    }

    public synchronized Response<AuthTokenType> refreshToken(String token) {
        Response<AuthTokenType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + REFRESH_TOKEN_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, new RefreshTokenType(token));
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(AuthTokenType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create user", e);
        }
        return response;
    }

    public synchronized Response<JobType> createJob(JobType job) {
        Response<JobType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + JOBS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, job);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(JobType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create job", e);
        }
        return response;
    }

    public synchronized Response<TestSuiteType> createTestSuite(TestSuiteType testSuite) {
        Response<TestSuiteType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TEST_SUITES_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, testSuite);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestSuiteType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create test suite", e);
        }
        return response;
    }

    public Response<TestRunType> startTestRun(TestRunType testRun) {
        Response<TestRunType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TEST_RUNS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, testRun);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestRunType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to start test run", e);
        }
        return response;
    }

    public Response<TestRunType> updateTestRun(TestRunType testRun) {
        Response<TestRunType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TEST_RUNS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, testRun);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestRunType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to update test run", e);
        }
        return response;
    }

    public Response<TestRunType> finishTestRun(long id) {
        Response<TestRunType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_RUNS_FINISH_PATH, id));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestRunType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to finish test run", e);
        }
        return response;
    }

    public Response<TestRunType> getTestRun(long id) {
        Response<TestRunType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_RUN_BY_ID_PATH, id));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestRunType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to find test run by id", e);
        }
        return response;
    }

    public Response<TestRunType> getTestRunByCiRunId(String ciRunId) {
        Response<TestRunType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TEST_RUNS_PATH);
            ClientResponse clientRS = initHeaders(webResource.queryParam("ciRunId", ciRunId).type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestRunType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable find test run by CI run id", e);
        }
        return response;
    }

    public Response<TestType> startTest(TestType test) {
        Response<TestType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TESTS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, test);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to start test", e);
        }
        return response;
    }

    public Response<TestType> finishTest(TestType test) {
        Response<TestType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_FINISH_PATH, test.getId()));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, test);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to finish test", e);
        }
        return response;
    }

    public void deleteTest(long id) {
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_BY_ID_PATH, id));
            webResource.delete(ClientResponse.class);

        } catch (Exception e) {
            LOGGER.error("Unable to finish test", e);
        }
    }

    public Response<TestType> createTestWorkItems(long testId, List<String> workItems) {
        Response<TestType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_WORK_ITEMS_PATH, testId));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, workItems);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create test work items", e);
        }
        return response;
    }

    /**
     * Attaches test artifact like logs or demo URLs.
     * 
     * @param artifact - test artifact
     */
    public void addTestArtifact(TestArtifactType artifact) {
        Response<TestArtifactType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_ARTIFACTS_PATH, artifact.getTestId()));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, artifact);
            response.setStatus(clientRS.getStatus());
        } catch (Exception e) {
            LOGGER.error("Unable to add test artifact", e);
        }
    }

    public synchronized Response<TestCaseType> createTestCase(TestCaseType testCase) {
        Response<TestCaseType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TEST_CASES_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, testCase);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestCaseType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create test case", e);
        }
        return response;
    }

    public Response<TestCaseType[]> createTestCases(TestCaseType[] testCases) {
        Response<TestCaseType[]> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TEST_CASES_BATCH_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, testCases);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestCaseType[].class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to create test cases", e);
        }
        return response;
    }

    public Response<TestType[]> getTestRunResults(long id) {
        Response<TestType[]> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_RUNS_RESULTS_PATH, id));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TestType[].class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to find test run results", e);
        }
        return response;
    }

    public class Response<T> {
        private int status;
        private T object;

        public Response(int status, T object) {
            this.status = status;
            this.object = object;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public T getObject() {
            return object;
        }

        public void setObject(T object) {
            this.object = object;
        }
    }

    private WebResource.Builder initHeaders(WebResource.Builder builder) {
        if (!StringUtils.isEmpty(authToken)) {
            builder.header("Authorization", authToken);
        }
        if (!StringUtils.isEmpty(project)) {
            builder.header("Project", project);
        }
        return builder;
    }

    public String getProject() {
        return project;
    }

    /**
     * Initializes project context, sets default project if none found in DB.
     * 
     * @param project name
     * @return instance of {@link ZafiraClient}
     */
    public ZafiraClient initProject(String project) {
        if (!StringUtils.isEmpty(project)) {
            Response<ProjectType> rs = getProjectByName(project);
            if (rs.getStatus() == 200) {
                this.project = rs.getObject().getName();
            }
        }
        return this;
    }

    /**
     * Registers user in Zafira, it may be a new one or existing returned by service.
     * 
     * @param userName - in general LDAP user name
     * @param email - user email
     * @param firstName - user first name
     * @param lastName - user last name
     * @return registered user
     */
    public UserType registerUser(String userName, String email, String firstName, String lastName) {
        if (StringUtils.isEmpty(userName) || userName.equals("$BUILD_USER_ID")) {
            userName = DEFAULT_USER;
        }
        userName = userName.toLowerCase();

        String userDetails = "userName: %s, email: %s, firstName: %s, lastName: %s";
        LOGGER.debug("User details for registration:" + String.format(userDetails, userName, email, firstName, lastName));

        UserType user = new UserType(userName, email, firstName, lastName);
        Response<UserType> response = createUser(user);
        user = response.getObject();

        if (user == null) {
            throw new RuntimeException("Unable to register user '" + userName + "' for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered user details:"
                    + String.format(userDetails, user.getUsername(), user.getEmail(), user.getFirstName(), user.getLastName()));
        }
        return user;
    }

    /**
     * Registers test case in Zafira, it may be a new one or existing returned by service.
     * 
     * @param suiteId - test suite id
     * @param primaryOwnerId - primary owner user id
     * @param secondaryOwnerId - secondary owner user id
     * @param testClass - test class name
     * @param testMethod - test method name
     * @return registered test case
     */
    public TestCaseType registerTestCase(Long suiteId, Long primaryOwnerId, Long secondaryOwnerId, String testClass, String testMethod) {
        TestCaseType testCase = new TestCaseType(testClass, testMethod, "", suiteId, primaryOwnerId, secondaryOwnerId);
        String testCaseDetails = "testClass: %s, testMethod: %s, info: %s, testSuiteId: %d, primaryOwnerId: %d, secondaryOwnerId: %d";
        LOGGER.debug("Test Case details for registration:"
                + String.format(testCaseDetails, testClass, testMethod, "", suiteId, primaryOwnerId, secondaryOwnerId));
        Response<TestCaseType> response = createTestCase(testCase);
        testCase = response.getObject();
        if (testCase == null) {
            throw new RuntimeException("Unable to register test case '"
                    + String.format(testCaseDetails, testClass, testMethod, "", suiteId, primaryOwnerId) + "' for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered test case details:"
                    + String.format(testCaseDetails, testClass, testMethod, "", suiteId, primaryOwnerId, secondaryOwnerId));
        }
        return testCase;
    }

    /**
     * Registers test work items.
     * 
     * @param testId - test id
     * @param workItems - test work items
     * @return test for which we registers work items
     */
    public TestType registerWorkItems(Long testId, List<String> workItems) {
        TestType test = null;
        if (workItems != null && workItems.size() > 0) {
            Response<TestType> response = createTestWorkItems(testId, workItems);
            test = response.getObject();
        }
        return test;
    }

    /**
     * Registers test suite in Zafira, it may be a new one or existing returned by service.
     * 
     * @param suiteName - test suite name
     * @param fileName - TestNG xml file name
     * @param userId - suite owner user id
     * @return created test suite
     */
    public TestSuiteType registerTestSuite(String suiteName, String fileName, Long userId) {
        TestSuiteType testSuite = new TestSuiteType(suiteName, fileName, userId);
        String testSuiteDetails = "suiteName: %s, fileName: %s, userId: %s";
        LOGGER.debug("Test Suite details for registration:" + String.format(testSuiteDetails, suiteName, fileName, userId));

        Response<TestSuiteType> response = createTestSuite(testSuite);
        testSuite = response.getObject();

        if (testSuite == null) {
            throw new RuntimeException("Unable to register test suite '" + suiteName + "' for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered test suite details:"
                    + String.format(testSuiteDetails, testSuite.getName(), testSuite.getFileName(), testSuite.getUserId()));
        }
        return testSuite;
    }

    /**
     * Registers job in Zafira, it may be a new one or existing returned by service.
     * 
     * @param jobUrl - CI job URL
     * @param userId - job owner user id
     * @return created job
     */
    public JobType registerJob(String jobUrl, Long userId) {
        // JobsService uses the same logics in createOrUpdateJobByURL method
        jobUrl = jobUrl.replaceAll("/$", "");
        String jobName = StringUtils.substringAfterLast(jobUrl, "/");
        String jenkinsHost = StringUtils.EMPTY;
        if (jobUrl.contains("/view/")) {
            jenkinsHost = jobUrl.split("/view/")[0];
        } else if (jobUrl.contains("/job/")) {
            jenkinsHost = jobUrl.split("/job/")[0];
        }

        String jobDetails = "jobName: %s, jenkinsHost: %s, userId: %s";
        LOGGER.debug("Job details for registration:" + String.format(jobDetails, jobName, jenkinsHost, userId));

        JobType job = new JobType(jobName, jobUrl, jenkinsHost, userId);
        Response<JobType> response = createJob(job);
        job = response.getObject();

        if (job == null) {
            throw new RuntimeException("Unable to register job for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered job details:" + String.format(jobDetails, job.getName(), job.getJenkinsHost(), job.getUserId()));
        }

        return job;
    }

    /**
     * Registers new test run triggered by human.
     * 
     * @param testSuiteId - test suited id
     * @param userId - user id
     * @param configXML - test config XML
     * @param jobId - job id
     * @param ciConfig - ci config
     * @param startedBy - user id who started the suite
     * @param workItem - test work item
     * @return created test run
     */
    public TestRunType registerTestRunByHUMAN(Long testSuiteId, Long userId, String configXML, Long jobId, CIConfig ciConfig, Initiator startedBy,
            String workItem) {
        TestRunType testRun = new TestRunType(ciConfig.getCiRunId(), testSuiteId, userId, ciConfig.getGitUrl(), ciConfig.getGitBranch(),
                ciConfig.getGitCommit(), configXML, jobId, ciConfig.getCiBuild(), startedBy, workItem);
        String testRunDetails = "testSuiteId: %s, userId: %s, scmURL: %s, scmBranch: %s, scmCommit: %s, jobId: %s, buildNumber: %s, startedBy: %s, workItem";
        LOGGER.debug("Test Run details for registration:" + String.format(testRunDetails, testSuiteId, userId, ciConfig.getGitUrl(),
                ciConfig.getGitBranch(), ciConfig.getGitCommit(), jobId, ciConfig.getCiBuild(), startedBy, workItem));

        Response<TestRunType> response = startTestRun(testRun);
        testRun = response.getObject();
        if (testRun == null) {
            throw new RuntimeException("Unable to register test run '" + String.format(testRunDetails, testSuiteId, userId,
                    ciConfig.getGitUrl(), ciConfig.getGitBranch(), ciConfig.getGitCommit(), jobId, ciConfig.getCiBuild(), startedBy, workItem)
                    + "' for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered test run details:"
                    + String.format(testRunDetails, testSuiteId, userId, ciConfig.getGitUrl(), ciConfig.getGitBranch(), ciConfig.getGitCommit(),
                            jobId, ciConfig.getCiBuild(), startedBy, workItem));
        }
        return testRun;
    }

    /**
     * Registers new test run triggered by scheduler.
     * 
     * @param testSuiteId - test suited id
     * @param configXML - test config XML
     * @param jobId - job id
     * @param ciConfig - ci config
     * @param startedBy - user id who started the suite
     * @param workItem - test work item
     * @return created test run
     */
    public TestRunType registerTestRunBySCHEDULER(Long testSuiteId, String configXML, Long jobId, CIConfig ciConfig, Initiator startedBy,
            String workItem) {
        TestRunType testRun = new TestRunType(ciConfig.getCiRunId(), testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(),
                ciConfig.getGitCommit(), configXML, jobId, ciConfig.getCiBuild(), startedBy, workItem);
        String testRunDetails = "testSuiteId: %s, scmURL: %s, scmBranch: %s, scmCommit: %s, jobId: %s, buildNumber: %s, startedBy: %s, workItem";
        LOGGER.debug("Test Run details for registration:" + String.format(testRunDetails, testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(),
                ciConfig.getGitCommit(), jobId, ciConfig.getCiBuild(), startedBy, workItem));

        Response<TestRunType> response = startTestRun(testRun);
        testRun = response.getObject();
        if (testRun == null) {
            throw new RuntimeException("Unable to register test run '"
                    + String.format(testRunDetails, testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(), ciConfig.getGitCommit(), jobId,
                            ciConfig.getCiBuild(), startedBy, workItem)
                    + "' for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered test run details:" + String.format(testRunDetails, testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(),
                    ciConfig.getGitCommit(), jobId, ciConfig.getCiBuild(), startedBy, workItem));
        }
        return testRun;
    }

    /**
     * Registers new test run triggered by upstream job.
     * 
     * @param testSuiteId - test suited id
     * @param configXML - test config XML
     * @param jobId - job id
     * @param parentJobId - parent job id
     * @param ciConfig - ci config
     * @param startedBy - user id who started the suite
     * @param workItem - test work item
     * @return created test run
     */
    public TestRunType registerTestRunUPSTREAM_JOB(Long testSuiteId, String configXML, Long jobId, Long parentJobId, CIConfig ciConfig,
            Initiator startedBy, String workItem) {
        TestRunType testRun = new TestRunType(ciConfig.getCiRunId(), testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(),
                ciConfig.getGitCommit(), configXML, jobId, parentJobId, ciConfig.getCiParentBuild(),
                ciConfig.getCiBuild(), startedBy, workItem);
        String testRunDetails = "testSuiteId: %s, scmURL: %s, scmBranch: %s, scmCommit: %s, jobId: %s, parentJobId: %s, parentBuildNumber: %s, buildNumber: %s, startedBy: %s, workItem";
        LOGGER.debug("Test Run details for registration:"
                + String.format(testRunDetails, testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(), ciConfig.getGitCommit(), jobId,
                        parentJobId, ciConfig.getCiParentBuild(), ciConfig.getCiBuild(), startedBy, workItem));

        Response<TestRunType> response = startTestRun(testRun);
        testRun = response.getObject();
        if (testRun == null) {
            throw new RuntimeException("Unable to register test run '"
                    + String.format(testRunDetails, testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(), ciConfig.getGitCommit(), jobId,
                            parentJobId, ciConfig.getCiParentBuild(), ciConfig.getCiBuild(), startedBy, workItem)
                    + "' for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered test run details:" + String.format(testRunDetails, testSuiteId, ciConfig.getGitUrl(), ciConfig.getGitBranch(),
                    ciConfig.getGitCommit(), jobId, parentJobId, ciConfig.getCiParentBuild(), ciConfig.getCiBuild(), startedBy, workItem));
        }
        return testRun;
    }

    /**
     * Finalizes test run calculating test results.
     * 
     * @param testRun - test run object
     * @return updated test run
     */
    public TestRunType registerTestRunResults(TestRunType testRun) {
        updateTestRun(testRun);
        Response<TestRunType> response = finishTestRun(testRun.getId());
        return response.getObject();
    }

    /**
     * Registers test run in Zafira.
     * 
     * @param name - test name
     * @param group - test group
     * @param status - test status
     * @param testArgs - test args
     * @param testRunId - test run id
     * @param testCaseId - test case id
     * @param retry - retry count
     * @param dependsOnMethods - list of dependent tests
     * @param configXML - config XML
     * @return registered test
     */
    public TestType registerTestStart(String name, String group, Status status, String testArgs, Long testRunId, Long testCaseId, int retry,
            String configXML, String[] dependsOnMethods, String ciTestId, Set<TagType> tags) {
        // TODO: remove "Set<TagType> tags" param later
        Long startTime = new Date().getTime();

        String testDetails = "name: %s, status: %s, testArgs: %s, testRunId: %s, testCaseId: %s, startTime: %s, retry: %d";

        TestType test = new TestType(name, status, testArgs, testRunId, testCaseId, startTime, null, retry, configXML);
        LOGGER.debug("Test details for startup registration:"
                + String.format(testDetails, name, status, testArgs, testRunId, testCaseId, startTime, retry));

        test.setCiTestId(ciTestId);
        test.setTestGroup(group);
        if (tags != null) {
            test.setTags(tags);
        }
        if (dependsOnMethods != null) {
            StringBuilder sb = new StringBuilder();
            for (String method : dependsOnMethods) {
                sb.append(StringUtils.substringAfterLast(method, ".")).append(StringUtils.SPACE);
            }
            test.setDependsOnMethods(sb.toString());
        }

        Response<TestType> response = startTest(test);
        test = response.getObject();
        if (test == null) {
            throw new RuntimeException(
                    "Unable to register test '" + String.format(testDetails, name, status, testArgs, testRunId, testCaseId, startTime, retry)
                            + "' startup for zafira service: " + serviceURL);
        } else {
            LOGGER.debug(
                    "Registered test startup details:" + String.format(testDetails, name, status, testArgs, testRunId, testCaseId, startTime, retry));
        }
        return test;
    }

    /**
     * Registers test re-run in Zafira.
     * 
     * @param test - test object
     * @return registered test
     */
    public TestType registerTestRestart(TestType test) {
        String testName = test.getName();
        Response<TestType> response = startTest(test);
        test = response.getObject();
        if (test == null) {
            throw new RuntimeException("Unable to register test '" + testName + "' restart for zafira service: " + serviceURL);
        } else {
            LOGGER.debug("Registered test restart details:'" + testName + "'; startTime: " + new Date(test.getStartTime()));
        }
        return test;
    }

    /**
     * Aborts test run.
     * 
     * @param id of test run
     * @return status
     */
    public boolean abortTestRun(long id) {
        boolean aborted = false;
        try {
            WebResource webResource = client.resource(serviceURL + String.format(TEST_RUNS_ABORT_PATH, id));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            aborted = clientRS.getStatus() == 200;
        } catch (Exception e) {
            LOGGER.error("Unable to find test run by id", e);
        }
        return aborted;
    }

    @SuppressWarnings({ "unchecked" })
    public synchronized Response<List<HashMap<String, String>>> getToolSettings(String tool, boolean decrypt) {
        Response<List<HashMap<String, String>>> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(SETTINGS_TOOL_PATH, tool) + "?decrypt=" + decrypt);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(List.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to authorize user", e);
        }
        return response;
    }

    /**
     * Uploads file to Amazon S3 used integration data from server
     * 
     * @param file - any file to upload
     * @param expiresIn - in seconds to generate presigned URL
     * @param keyPrefix - bucket folder name where file will be stored
     * @return url of the file in string format
     * @throws Exception throws when there are any issues with a Amazon S3 connection
     */
    public String uploadFile(File file, Integer expiresIn, String keyPrefix) throws Exception {
        String filePath = null;
        if (getAmazonClient() != null && getTenantType() != null && !StringUtils.isBlank(getTenantType().getTenant())) {
            String fileName = RandomStringUtils.randomAlphanumeric(20) + "." + FilenameUtils.getExtension(file.getName());
            String relativeKey = keyPrefix + fileName;
            String key = getTenantType().getTenant() + relativeKey;

            try (SdkBufferedInputStream stream = new SdkBufferedInputStream(new FileInputStream(file), (int) (file.length() + 100))) {
                String type = Mimetypes.getInstance().getMimetype(file.getName());

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(type);
                metadata.setContentLength(file.length());

                PutObjectRequest putRequest = new PutObjectRequest(this.amazonS3SessionCredentials.getBucket(), key, stream, metadata);
                getAmazonClient().putObject(putRequest);
                CannedAccessControlList controlList = getTenantType().isUseArtifactsProxy() ? CannedAccessControlList.Private
                        : CannedAccessControlList.PublicRead;
                getAmazonClient().setObjectAcl(this.amazonS3SessionCredentials.getBucket(), key, controlList);

                filePath = getTenantType().isUseArtifactsProxy() ? getServiceURL() + relativeKey : getFilePath(key);

            } catch (Exception e) {
                LOGGER.error("Can't save file to Amazon S3", e);
            }
        } else {
            throw new Exception("Can't save file to Amazon S3. Verify your credentials or bucket name");
        }

        return filePath;
    }

    public String getServiceURL() {
        return getTenantType().getServiceUrl();
    }

    private String getFilePath(String key) {
        return getAmazonClient().getUrl(this.amazonS3SessionCredentials.getBucket(), key).toString();
    }

    /**
     * Registers Amazon S3 client
     */
    void initAmazonS3Client() {
        this.amazonClient = CompletableFuture.supplyAsync(() -> {
            this.amazonS3SessionCredentials = getAmazonSessionCredentials().getObject();
            AmazonS3 client = null;
            if (this.amazonS3SessionCredentials != null) {
                try {
                    client = AmazonS3ClientBuilder.standard()
                            .withCredentials(
                                    new AWSStaticCredentialsProvider(new BasicSessionCredentials(this.amazonS3SessionCredentials.getAccessKeyId(),
                                            this.amazonS3SessionCredentials.getSecretAccessKey(), this.amazonS3SessionCredentials.getSessionToken())))
                            .withRegion(Regions.fromName(this.amazonS3SessionCredentials.getRegion())).build();
                    if (!client.doesBucketExistV2(this.amazonS3SessionCredentials.getBucket())) {
                        throw new Exception(
                                String.format("Amazon S3 bucket with name '%s' doesn't exist.", this.amazonS3SessionCredentials.getBucket()));
                    }
                } catch (Exception e) {
                    LOGGER.error("Amazon integration is invalid. Verify your credentials or region.", e);
                }
            }
            return client;
        });
    }

    /**
     * Gets Amazon S3 temporary credentials
     * 
     * @return Amazon S3 temporary credentials
     */
    private Response<SessionCredentials> getAmazonSessionCredentials() {
        Response<SessionCredentials> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + AMAZON_SESSION_CREDENTIALS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200 && clientRS.hasEntity()) {
                response.setObject(clientRS.getEntity(SessionCredentials.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to get AWS session credentials", e);
        }
        return response;
    }

    public Optional<Sheets> getSpreadsheetService() {
        if (!isAvailable())
            LOGGER.error("Spreadsheet`s operations are unavailable until connection with Zafira is established!");
        return Optional.ofNullable(getSheets());
    }

    void initGoogleClient() {
        this.sheets = CompletableFuture.supplyAsync(() -> {
            Sheets sheets = null;
            String accessToken = getGoogleSessionCredentials().getObject();
            if (accessToken != null) {
                try {
                    GoogleCredential googleCredential = new GoogleCredential().setAccessToken(accessToken);
                    sheets = new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), googleCredential)
                            .setApplicationName(UUID.randomUUID().toString())
                            .build();
                } catch (Exception e) {
                    LOGGER.error("Google integration is invalid", e);
                }
            }
            return sheets;
        });
    }

    /**
     * Gets Google temporary credentials
     * 
     * @return Google temporary credentials
     */
    private Response<String> getGoogleSessionCredentials() {
        Response<String> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + GOOGLE_SESSION_CREDENTIALS_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.TEXT_PLAIN))
                    .accept(MediaType.TEXT_PLAIN).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200 && clientRS.hasEntity()) {
                response.setObject(clientRS.getEntity(String.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to get Google session credentials", e);
        }
        return response;
    }

    void initTenant() {
        this.tenantType = CompletableFuture.supplyAsync(() -> getTenant().getObject());
    }

    private Response<TenantType> getTenant() {
        Response<TenantType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + TENANT_TYPE_PATH);
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(TenantType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to get tenant", e);
        }
        return response;
    }

    /**
     * Returns user by username or anonymous if not found.
     * 
     * @param username to find user
     * @return user from DB
     */
    public synchronized UserType getUserOrAnonymousIfNotFound(String username) {
        Response<UserType> response = getUserProfile(username);
        if (response.getStatus() != 200) {
            response = getUserProfile(DEFAULT_USER);
        }
        return response.getObject();
    }

    /**
     * Gets project by name
     * 
     * @param name of the project
     * @return project
     */
    public Response<ProjectType> getProjectByName(String name) {
        Response<ProjectType> response = new Response<>(0, null);
        try {
            WebResource webResource = client.resource(serviceURL + String.format(PROJECTS_PATH, name));
            ClientResponse clientRS = initHeaders(webResource.type(MediaType.APPLICATION_JSON))
                    .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
            response.setStatus(clientRS.getStatus());
            if (clientRS.getStatus() == 200) {
                response.setObject(clientRS.getEntity(ProjectType.class));
            }

        } catch (Exception e) {
            LOGGER.error("Unable to get project by name", e);
        }
        return response;
    }

    private AmazonS3 getAmazonClient() {
        return getAsync(this.amazonClient);
    }

    private Sheets getSheets() {
        return getAsync(this.sheets);
    }

    public TenantType getTenantType() {
        return getAsync(this.tenantType);
    }

    private <I> I getAsync(CompletableFuture<I> async) {
        I result = null;
        if (async != null) {
            try {
                result = async.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return result;
    }

    void onInit() {
        initAmazonS3Client();
        initGoogleClient();
        initTenant();
    }

}
