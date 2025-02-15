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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qaprosoft.zafira.dbaccess.dao.mysql.application.WorkItemMapper;
import com.qaprosoft.zafira.models.db.WorkItem;
import com.qaprosoft.zafira.models.db.WorkItem.Type;

@Service
public class WorkItemService {

    @Autowired
    private WorkItemMapper workItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createWorkItem(WorkItem workItem) {
        workItemMapper.createWorkItem(workItem);
    }

    @Transactional(readOnly = true)
    public WorkItem getWorkItemById(long id) {
        return workItemMapper.getWorkItemById(id);
    }

    @Transactional(readOnly = true)
    public WorkItem getWorkItemByJiraIdAndType(String jiraId, Type type) {
        return workItemMapper.getWorkItemByJiraIdAndType(jiraId, type);
    }

    @Transactional(readOnly = true)
    public WorkItem getWorkItemByTestCaseIdAndHashCode(long testCaseId, int hashCode) {
        return workItemMapper.getWorkItemByTestCaseIdAndHashCode(testCaseId, hashCode);
    }

    @Transactional(readOnly = true)
    public List<WorkItem> getWorkItemsByTestCaseIdAndType(long testCaseId, Type type) {
        return workItemMapper.getWorkItemsByTestCaseIdAndType(testCaseId, type);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkItem updateWorkItem(WorkItem workItem) {
        workItemMapper.updateWorkItem(workItem);
        return workItem;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKnownIssuesByTestId(long testId) {
        workItemMapper.deleteKnownIssuesByTestId(testId);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkItem createOrGetWorkItem(WorkItem newWorkItem) {
        WorkItem workItem = getWorkItemByJiraIdAndType(newWorkItem.getJiraId(), newWorkItem.getType());
        if (workItem == null) {
            createWorkItem(newWorkItem);
            return newWorkItem;
        } else {
            return workItem;
        }
    }
}
