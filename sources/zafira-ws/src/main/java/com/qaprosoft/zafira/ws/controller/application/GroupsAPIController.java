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
package com.qaprosoft.zafira.ws.controller.application;

import com.qaprosoft.zafira.models.db.Group;
import com.qaprosoft.zafira.services.exceptions.EntityNotExistsException;
import com.qaprosoft.zafira.services.exceptions.ForbiddenOperationException;
import com.qaprosoft.zafira.services.services.application.GroupService;
import com.qaprosoft.zafira.ws.controller.AbstractController;
import com.qaprosoft.zafira.ws.swagger.annotations.ResponseStatusDetails;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api("Groups API")
@CrossOrigin
@RequestMapping(path = "api/groups", produces = MediaType.APPLICATION_JSON_VALUE)
@RestController
public class GroupsAPIController extends AbstractController {

    @Autowired
    private GroupService groupService;

    @ResponseStatusDetails
    @ApiOperation(value = "Create group", nickname = "createGroup", httpMethod = "POST", response = Group.class)
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasPermission('MODIFY_USER_GROUPS')")
    @PostMapping()
    public Group createGroup(@RequestBody Group group) {
        return groupService.createGroup(group);
    }

    @ResponseStatusDetails
    @ApiOperation(value = "Add permissions to group", nickname = "addPermissionsToGroup", httpMethod = "POST", response = Group.class)
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasPermission('MODIFY_USER_GROUPS')")
    @PostMapping("/permissions")
    public Group addPermissionsToGroup(@RequestBody Group group) {
        return groupService.addPermissionsToGroup(group);
    }

    @ResponseStatusDetails
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @ApiOperation(value = "Get group", nickname = "getGroup", httpMethod = "GET", response = Group.class)
    @PreAuthorize("hasPermission('MODIFY_USER_GROUPS')")
    @GetMapping("/{id}")
    public Group getGroup(@PathVariable("id") long id) {
        return groupService.getGroupById(id);
    }

    @ResponseStatusDetails
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @ApiOperation(value = "Get all groups", nickname = "getAllGroups", httpMethod = "GET", response = List.class)
    @GetMapping("/all")
    @PreAuthorize("hasPermission('MODIFY_USER_GROUPS') or #isPublic")
    public List<Group> getAllGroups(@RequestParam(name = "public", required = false) boolean isPublic) {
        return groupService.getAllGroups(isPublic);
    }

    @ResponseStatusDetails
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @ApiOperation(value = "Get groups count", nickname = "getGroupsCount", httpMethod = "GET", response = Integer.class)
    @PreAuthorize("hasPermission('MODIFY_USER_GROUPS')")
    @GetMapping("/count")
    public Integer getGroupsCount() {
        return groupService.getGroupsCount();
    }

    @ResponseStatusDetails
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @ApiOperation(value = "Get roles", nickname = "getRoles", httpMethod = "GET", response = List.class)
    @PreAuthorize("hasPermission('MODIFY_USER_GROUPS')")
    @GetMapping("/roles")
    public List<Group.Role> getRoles() {
        return GroupService.getRoles();
    }

    @ResponseStatusDetails
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @ApiOperation(value = "Update group", nickname = "updateGroup", httpMethod = "PUT", response = Group.class)
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasPermission('MODIFY_USER_GROUPS')")
    @PutMapping()
    public Group updateGroup(@RequestBody Group group) {
        return groupService.updateGroup(group);
    }

    @ResponseStatusDetails
    @ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", paramType = "header") })
    @ApiOperation(value = "Delete group", nickname = "deleteGroup", httpMethod = "DELETE")
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasPermission('MODIFY_USER_GROUPS')")
    @DeleteMapping("/{id}")
    public void deleteGroup(@PathVariable("id") long id) {
        Group group = groupService.getGroupById(id);
        if (group == null) {
            throw new EntityNotExistsException(Group.class, false);
        }
        if (group.getUsers().size() > 0) {
            throw new ForbiddenOperationException("It's necessary to clear the group initially.", true);
        }
        groupService.deleteGroup(id);
    }

}
