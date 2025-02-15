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
package com.qaprosoft.zafira.models.dto.auth;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class TenantType implements Serializable {
    private static final long serialVersionUID = 8220711984153406216L;

    private String tenant;
    private String serviceUrl;
    private boolean multitenant;
    private boolean useArtifactsProxy;

    public TenantType() {
    }

    public TenantType(String tenant) {
        this.tenant = tenant;
    }

    public TenantType(String tenant, String serviceUrl, Boolean useArtifactsProxy) {
        this.tenant = tenant;
        this.serviceUrl = serviceUrl;
        this.useArtifactsProxy = useArtifactsProxy;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public boolean isMultitenant() {
        return multitenant;
    }

    public void setMultitenant(boolean multitenant) {
        this.multitenant = multitenant;
    }

    public boolean isUseArtifactsProxy() {
        return useArtifactsProxy;
    }

    public void setUseArtifactsProxy(boolean useArtifactsProxy) {
        this.useArtifactsProxy = useArtifactsProxy;
    }
}
