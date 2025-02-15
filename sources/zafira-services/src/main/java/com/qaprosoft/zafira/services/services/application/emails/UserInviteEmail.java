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
package com.qaprosoft.zafira.services.services.application.emails;

public class UserInviteEmail extends AbstractEmail {

    private static final String SUBJECT = "Join the workspace";

    private final String token;
    private final String zafiraLogoURL;
    private final String workspaceURL;

    public UserInviteEmail(String token, String zafiraLogoURL, String workspaceURL) {
        super(SUBJECT, EmailType.USER_INVITE, zafiraLogoURL, workspaceURL);
        this.token = token;
        this.zafiraLogoURL = zafiraLogoURL;
        this.workspaceURL = workspaceURL;
    }

    public String getToken() {
        return token;
    }

    public String getZafiraLogoURL() {
        return zafiraLogoURL;
    }

    public String getWorkspaceURL() {
        return workspaceURL;
    }

}
