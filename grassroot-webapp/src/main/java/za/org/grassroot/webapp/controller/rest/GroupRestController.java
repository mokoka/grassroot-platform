package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.webapp.model.rest.GroupDTO;
import za.org.grassroot.webapp.model.rest.UserDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/group")
public class GroupRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    UserRepository userRepository;

    @RequestMapping(value = "/add/{userid}/{phonenumbers}", method = RequestMethod.POST)
    public GroupDTO add(@PathVariable("userid") Long userid,@PathVariable("phonenumbers") String phoneNumbers) {
        return new GroupDTO(groupManagementService.createNewGroup(userid,PhoneNumberUtil.splitPhoneNumbers(phoneNumbers, " ").get("valid")));
    }

    @RequestMapping(value = "/add/subgroup/{userId}/{groupId}/{subGroupName}",
            method = RequestMethod.POST)
    public GroupDTO addSubGroup(@PathVariable("userId") Long userId,
                                @PathVariable("groupId") Long groupId,
                                @PathVariable("subGroupName") String subGroupName) {
        return new GroupDTO(groupManagementService.createSubGroup(userId, groupId, subGroupName));
    }



    @RequestMapping(value = "/add/usertogroup/{userId}/{groupId}",
            method = RequestMethod.POST)
    public GroupDTO addUserToGroup(@PathVariable("userId") Long userId,
                                @PathVariable("groupId") Long groupId) {
        return new GroupDTO(groupManagementService.addGroupMember(groupId, userId));
    }

    @RequestMapping(value = "/list/usersingroup/{groupId}",
            method = RequestMethod.GET)
    public List<UserDTO> listUsersInGroup(@PathVariable("groupId") Long groupId) {
        List<UserDTO> list =  new ArrayList<UserDTO>();
        for (User user : groupManagementService.getGroupById(groupId).getGroupMembers()) {
            list.add(new UserDTO(user));
        }
        return list;
    }

    @RequestMapping(value = "/list/usersingroupandsubgroups/{groupId}",
            method = RequestMethod.GET)
    public List<UserDTO> listUsersInGroupAndSubGroups(@PathVariable("groupId") Long groupId) {
        List<UserDTO> list =  new ArrayList<UserDTO>();
        for (User user : groupManagementService.getAllUsersInGroupAndSubGroups(groupId)) {
            list.add(new UserDTO(user));
        }
        return list;
    }

}
