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
package com.qaprosoft.zafira.services.services.application.scm;

import com.qaprosoft.zafira.models.dto.scm.Organization;
import com.qaprosoft.zafira.models.dto.scm.Repository;
import com.qaprosoft.zafira.services.exceptions.ServiceException;

import java.io.IOException;
import java.util.List;

public interface IScmService {

    String getClientId();

    List<Organization> getOrganizations(String accessToken) throws IOException, ServiceException;

    List<Repository> getRepositories(String accessToken, String organizationName) throws IOException, ServiceException;

    Repository getRepository(String accessToken, String organizationName, String repositoryName);

    String getLoginName(String accessToken);

}
