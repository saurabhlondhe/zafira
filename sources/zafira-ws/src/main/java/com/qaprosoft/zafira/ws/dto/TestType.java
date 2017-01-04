package com.qaprosoft.zafira.ws.dto;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.qaprosoft.zafira.dbaccess.model.Status;

@JsonInclude(Include.NON_NULL)
public class TestType extends AbstractType
{
	@NotNull
	private String name;
	private Status status;
	private String testArgs;
	@NotNull
	private Long testRunId;
	@NotNull
	private Long testCaseId;
	private String testGroup;
	private String message;
	private Date startTime;
	private Date finishTime;
	private String demoURL;
	private String logURL;
	private List<String> workItems;
	private int retry;
	private String configXML;
	private Map<String, Long> testMetrics;
	private boolean knownIssue;

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public String getTestArgs()
	{
		return testArgs;
	}

	public void setTestArgs(String testArgs)
	{
		this.testArgs = testArgs;
	}

	public Long getTestRunId()
	{
		return testRunId;
	}

	public void setTestRunId(Long testRunId)
	{
		this.testRunId = testRunId;
	}

	public Long getTestCaseId()
	{
		return testCaseId;
	}

	public void setTestCaseId(Long testCaseId)
	{
		this.testCaseId = testCaseId;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(String message)
	{
		this.message = message;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public void setStartTime(Date startTime)
	{
		this.startTime = startTime;
	}

	public Date getFinishTime()
	{
		return finishTime;
	}

	public void setFinishTime(Date finishTime)
	{
		this.finishTime = finishTime;
	}

	public String getDemoURL()
	{
		return demoURL;
	}

	public void setDemoURL(String demoURL)
	{
		this.demoURL = demoURL;
	}

	public String getLogURL()
	{
		return logURL;
	}

	public void setLogURL(String logURL)
	{
		this.logURL = logURL;
	}

	public List<String> getWorkItems()
	{
		return workItems;
	}

	public void setWorkItems(List<String> workItems)
	{
		this.workItems = workItems;
	}

	public int getRetry()
	{
		return retry;
	}

	public void setRetry(int retry)
	{
		this.retry = retry;
	}

	public String getConfigXML()
	{
		return configXML;
	}

	public void setConfigXML(String configXML)
	{
		this.configXML = configXML;
	}

	public Map<String, Long> getTestMetrics()
	{
		return testMetrics;
	}

	public void setTestMetrics(Map<String, Long> testMetrics)
	{
		this.testMetrics = testMetrics;
	}

	public boolean isKnownIssue()
	{
		return knownIssue;
	}

	public void setKnownIssue(boolean knownIssue)
	{
		this.knownIssue = knownIssue;
	}

	public String getTestGroup()
	{
		return testGroup;
	}

	public void setTestGroup(String testGroup)
	{
		this.testGroup = testGroup;
	}
}
