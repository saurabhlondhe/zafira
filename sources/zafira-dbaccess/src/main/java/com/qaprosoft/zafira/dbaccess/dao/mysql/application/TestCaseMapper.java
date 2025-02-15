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
package com.qaprosoft.zafira.dbaccess.dao.mysql.application;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.qaprosoft.zafira.dbaccess.dao.mysql.application.search.TestCaseSearchCriteria;
import com.qaprosoft.zafira.models.db.TestCase;

public interface TestCaseMapper {
    void createTestCase(TestCase testCase);

    TestCase getTestCaseById(long id);

    List<TestCase> getTestCasesByUsername(String username);

    TestCase getOwnedTestCase(@Param("userId") Long userId, @Param("testClass") String testClass, @Param("testMethod") String testMethod);

    void updateTestCase(TestCase testCase);

    void deleteTestCaseById(long id);

    void deleteTestCase(TestCase testCase);

    List<TestCase> searchTestCases(TestCaseSearchCriteria sc);

    Integer getTestCasesSearchCount(TestCaseSearchCriteria sc);
}
