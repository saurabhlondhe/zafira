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
 ******************************************************************************/
package com.qaprosoft.zafira.services.services.application;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qaprosoft.zafira.dbaccess.dao.mysql.application.FilterMapper;
import com.qaprosoft.zafira.models.db.Filter;
import com.qaprosoft.zafira.models.dto.filter.FilterType;
import com.qaprosoft.zafira.models.dto.filter.StoredSubject;
import com.qaprosoft.zafira.models.dto.filter.Subject;
import com.qaprosoft.zafira.services.exceptions.ServiceException;
import com.qaprosoft.zafira.services.util.FreemarkerUtil;

@Service
public class FilterService {

    @Autowired
    private FilterMapper filterMapper;

    private StoredSubject storedSubject;

    @Autowired
    private FreemarkerUtil freemarkerUtil;

    public FilterService() {
        this.storedSubject = new StoredSubject();
    }

    public enum Template {
        TEST_RUN_TEMPLATE("/filter.ftl");

        private final String path;

        Template(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Filter createFilter(Filter filter) {
        filterMapper.createFilter(filter);
        return filter;
    }

    @Transactional(readOnly = true)
    public Filter getFilterById(long id) {
        return filterMapper.getFilterById(id);
    }

    @Transactional(readOnly = true)
    public Filter getFilterByName(String name) {
        return filterMapper.getFilterByName(name);
    }

    @Transactional(readOnly = true)
    public List<Filter> getAllPublicFilters(Long userId) {
        return filterMapper.getAllPublicFilters(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Filter updateFilter(Filter filter, boolean isAdmin) {
        Filter dbFilter = getFilterById(filter.getId());
        if (dbFilter == null) {
            throw new ServiceException("No filters found by id: " + filter.getId());
        }
        if (!filter.getName().equals(dbFilter.getName()) && getFilterByName(filter.getName()) != null) {
            throw new ServiceException("Filter with name '" + filter.getName() + "' already exists");
        }
        dbFilter.setName(filter.getName());
        dbFilter.setDescription(filter.getDescription());
        dbFilter.setSubject(filter.getSubject());
        dbFilter.setPublicAccess(isAdmin && filter.isPublicAccess());
        filterMapper.updateFilter(dbFilter);
        return dbFilter;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFilterById(long id) {
        filterMapper.deleteFilterById(id);
    }

    public Subject getStoredSubject(Subject.Name name) {
        return storedSubject.getSubjectByName(name);
    }

    public String getTemplate(FilterType filter, Template template) {
        return freemarkerUtil.getFreeMarkerTemplateContent(template.getPath(), filter);
    }
}
