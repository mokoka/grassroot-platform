package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.time.Instant;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * Created by Siyanda Mzam on 2016/03/16.
 */

public class GroupRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    GroupRestController groupRestController;

    String path = "/api/group/";
    List<Group> groups = new ArrayList<>();
    Set<Group> groupSet = new HashSet<>();
    MembershipInfo membershipInfo = new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getUsername());
    Set<MembershipInfo> membersToAdd = Sets.newHashSet();
    Event event = meetingEvent;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(groupRestController).build();
    }

    @Test
    public void createGroupShouldWork() throws Exception {

        settingUpDummyData(group, groups, membershipInfo, membersToAdd);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.create(sessionTestUser.getUid(), testGroupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP, testEventDescription)).thenReturn(group);
        mockMvc.perform(post(path + "create/{phoneNumber}/{code}", testUserPhone, testUserCode).param("groupName", testGroupName).param("description", testEventDescription)).andExpect(status().isCreated());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupBrokerMock).create(sessionTestUser.getUid(), testGroupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP, meetingEvent.getDescription());
    }

    @Test
    public void getUserGroupsShouldWork() throws Exception {

        sessionTestUser.setId(2L);
        GroupLog groupLog = new GroupLog(group.getId(), sessionTestUser.getId(), GroupLogType.GROUP_ADDED, sessionTestUser.getId());
        groupLog.setCreatedDateTime(Date.from(Instant.now()));
        groupLog.setCreatedDateTime(DateTimeUtil.addHoursFromNow(1));
        group.addMember(sessionTestUser, "ROLE_GROUP_ORGANIZER");
        groupSet.add(group);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.getMostRecentEvent(group)).thenReturn(event);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(groupSet);
        when(groupLogServiceMock.load(groupLog.getId())).thenReturn(groupLog);
        mockMvc.perform(get(path + "list/{phoneNumber}/{code}", testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventManagementServiceMock).getMostRecentEvent(group);
        verify(permissionBrokerMock).getActiveGroups(sessionTestUser, null);
        verify(groupLogServiceMock).load(groupLog.getId());
    }

    @Test
    public void searchForGroupsShouldWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.findGroupFromJoinCode(testSearchTerm)).thenReturn(group);
        mockMvc.perform(get(path + "search").param("searchTerm", testSearchTerm)).andExpect(status().is2xxSuccessful());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock).findGroupFromJoinCode(testSearchTerm);
    }

    @Test
    public void searchRequestToJoinGroup() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupJoinRequestServiceMock.open(sessionTestUser.getUid(), group.getUid())).thenReturn(group.getUid());
        mockMvc.perform(post(path + "join/request/{phoneNumber}/{code}", testUserPhone, testUserCode).param("uid", group.getUid())).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupJoinRequestServiceMock).open(sessionTestUser.getUid(), group.getUid());
    }

    @Test
    public void gettingAGroupMemberShouldWork() throws Exception {

        List<User> userList = new ArrayList<>();
        Page<User> userPage = new PageImpl<>(userList, new PageRequest(0, 5), 1);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(userManagementServiceMock.getGroupMembers(group, 0, 5)).thenReturn(userPage);
        mockMvc.perform(get(path + "/members/{id}/{phoneNumber}/{code}", group.getUid(), testUserPhone, testUserCode).param("page", String.valueOf(1)).param("size", String.valueOf(5))).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupBrokerMock).load(group.getUid());
        verify(userManagementServiceMock).getGroupMembers(group, 0, 5);
    }
    private void settingUpDummyData(Group group, List<Group> groups, MembershipInfo membershipInfo, Set<MembershipInfo> membersToAdd) {

        membersToAdd.add(membershipInfo);
        group.addMember(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER);
        groups.add(group);
        group.setDescription(testEventDescription);
    }
}