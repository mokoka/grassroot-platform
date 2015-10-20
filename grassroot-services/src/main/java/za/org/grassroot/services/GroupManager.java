package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.NewGroupMember;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.util.TokenGeneratorService;

import javax.jws.soap.SOAPBinding;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * @author luke on 2015/08/14.
 *
 */

@Service
@Transactional
public class GroupManager implements GroupManagementService {

    private final static Logger log = LoggerFactory.getLogger(GroupManager.class);

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserManagementService userManager;

    @Autowired
    TokenGeneratorService tokenGeneratorService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;

    /**
     * Have not yet created methods analogous to those in UserManager, as not sure if necessary
     * For the moment, using this to expose some basic group services for the application interfaces
     */

    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public List<Group> getGroupsFromUser(User sessionUser) {
        // todo: add pagination
        return sessionUser.getGroupsPartOf();
    }

    @Override
    public boolean isUserInGroup(Group group, User user) {
        // at some point may want to make this more efficient than getter method
        return group.getGroupMembers().contains(user);
    }

    @Override
    public Group saveGroup(Group groupToSave) {
        return groupRepository.save(groupToSave);
    }

    @Override
    public void deleteGroup(Group groupToDelete) {
        /*
        there are issues with cascading if we delete the group before removing all users, hence doing it this way
        which will be quite slow, but this function should almost never be used, so not a major issue, for now, and
        rather safe than sorry on deletion.
         */

        List<User> members = new ArrayList<>(groupToDelete.getGroupMembers());
        log.info("We are now going to delete a group ... first, we unsubscribe " + members.size() + " members");

        for (User user : members) {
            groupToDelete = removeGroupMember(groupToDelete, user);
        }

        log.info("Group members removed ... " + groupToDelete.getGroupMembers().size() + " members left. Proceeding to delete");

        groupRepository.delete(groupToDelete);
    }


    @Override
    public Group addGroupMember(Long currentGroupId, Long newMemberId) {
        return addGroupMember(getGroupById(currentGroupId), userManager.getUserById(newMemberId));
    }

    @Override
    public Group removeGroupMember(Group group, User user) {
        // todo: error handling
        group.getGroupMembers().remove(user);
        return saveGroup(group);
    }

    @Override
    public Group addRemoveGroupMembers(Group group, List<User> revisedUserList) {

        List<User> originalUsers = new ArrayList<>(group.getGroupMembers());

        log.info("These are the original users: " + originalUsers);

        // for some reason, the list remove function isn't working on these users, hence have to do this hard way

        for (User user : originalUsers) {
            if (!revisedUserList.contains(user)) {
                log.info("Removing a member: " + user);
                removeGroupMember(group, user);
            }
        }

        for (User user : revisedUserList) {
            if (!originalUsers.contains(user)) {
                log.info("Adding a member: " + user);
                addGroupMember(group, user);
            }
        }

        return groupRepository.save(group);
    }

    @Override
    public Group createNewGroup(User creatingUser, String groupName) {
        Group group = new Group(groupName, creatingUser);
        return groupRepository.save(group);
    }

    @Override
    public Group addGroupMember(Group currentGroup, User newMember) {

        // todo: just make sure this works as planned, if user has persisted in interim (e.g., maybe call repo?).
        if (currentGroup.getGroupMembers().contains(newMember)) {
            return currentGroup;
        } else {
            currentGroup.addMember(newMember);
            jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(),new NewGroupMember(currentGroup,newMember));
            return groupRepository.save(currentGroup);
        }
    }

    @Override
    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers) {
        return createNewGroup(userManager.getUserById(creatingUserId), phoneNumbers);
    }

    @Override
    public Group createNewGroup(User creatingUser, List<String> phoneNumbers) {

        // todo: consider some way to check if group "exists", needs a solid "equals" logic
        // todo: defaulting to using Lists as Collection type for many-many, but that's an amateur decision ...

        Group groupToCreate = new Group();

        groupToCreate.setCreatedByUser(creatingUser);
        groupToCreate.setGroupName(""); // column not-null, so use blank string as default

        List<User> groupMembers = userManager.getUsersFromNumbers(phoneNumbers);
        groupMembers.add(creatingUser);
        groupToCreate.setGroupMembers(groupMembers);

        return groupRepository.save(groupToCreate);

    }

    @Override
    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers) {

        Group groupToExpand = loadGroup(groupId);
        List<User> groupNewMembers = userManager.getUsersFromNumbers(phoneNumbers);

        for (User newMember : groupNewMembers) {
            groupToExpand.addMember(newMember);
            jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(),new NewGroupMember(groupToExpand,newMember));
        }

        return groupRepository.save(groupToExpand);

    }

    /*
    Methods to implement finding last group and prompting to rename if unnamed. May make this a little more complex
    in future (e.g., check not just last created group, but any unnamed created groups above X users, or so on). Hence
    a little duplication / redundancy for now.
     */
    @Override
    public Group getLastCreatedGroup(User creatingUser) {
        return groupRepository.findFirstByCreatedByUserOrderByIdDesc(creatingUser);
    }

    @Override
    public boolean needsToRenameGroup(User sessionUser) {
        Group lastCreatedGroup = getLastCreatedGroup(sessionUser);
        return (lastCreatedGroup != null && !lastCreatedGroup.hasName());
    }

    @Override
    public Long groupToRename(User sessionUser) {
        return getLastCreatedGroup(sessionUser).getId();
    }

    @Override
    public Group renameGroup(Group group, String newGroupName) {
        // only bother if the name has changed (in some instances, web app may call this without actual name change)
        if (!group.getGroupName().equals(newGroupName)) {
            group.setGroupName(newGroupName);
            return groupRepository.save(group);
        } else {
            return group;
        }
    }

    @Override
    public List<Group> getCreatedGroups(User creatingUser) {
        return groupRepository.findByCreatedByUser(creatingUser);
    }

    @Override
    public List<Group> getGroupsPartOf(User sessionUser) {
        return groupRepository.findByGroupMembers(sessionUser);
    }

    @Override
    public List<Group> getPaginatedGroups(User sessionUser, int pageNumber, int pageSize) {
        Page<Group> pageOfGroups = groupRepository.findByGroupMembers(sessionUser, new PageRequest(pageNumber, pageSize));
        return pageOfGroups.getContent();
    }

    /*
    We use this for the web home page, so we can show a structured set of groups. Another way to do this might be via
    a query in the repository, but the most obvious, findByUserAndParentNull, won't quite work if a user is a member
     of a subgroup but not of the parent (i.e., the group is seniormost for that user but not within that tree
     todo: figure out how to do this via query, or the below implementation may kill us when the group lists are large
     */

    @Override
    public List<Group> getTopLevelGroups(User user) {

        List<Group> groupsPartOf = getGroupsPartOf(user);
        List<Group> topLevelGroups = new ArrayList<>();

        for (Group group : groupsPartOf) {
            if (group.getParent() == null || !isUserInGroup(group.getParent(), user)) {
                topLevelGroups.add(group);
            }
        }

        return topLevelGroups;
    }

    @Override
    public List<Group> getSubGroups(Group group) {
        return groupRepository.findByParent(group);
    }

    @Override
    public Group getGroupById(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public Group getGroupByToken(String groupToken) {
        return groupRepository.findByGroupTokenCode(groupToken);
    }

    @Override
    public Group generateGroupToken(Group group, Integer daysValid) {
        // todo: checks for whether the code already exists, and/or existing validity of group

        log.info("Generating a new group token, for group: " + group.getId());

        Integer daysMillis = 24 * 60 * 60 * 1000;
        Timestamp expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + daysValid * daysMillis);

        group.setGroupTokenCode(generateCodeString());
        group.setTokenExpiryDateTime(expiryDateTime);

        log.info("Group code generated: " + group.getGroupTokenCode());

        group = groupRepository.save(group);

        log.info("Group code after save: " + group.getGroupTokenCode());

        return group;
    }

    @Override
    public Group generateGroupToken(Long groupId, Integer daysValid) {
        return generateGroupToken(loadGroup(groupId), daysValid);
    }

    @Override
    public Group extendGroupToken(Group group, Integer daysExtension) {
        Integer daysMillis = 24 * 60 * 60 * 1000; // need to put this somewhere else so not copying & pasting
        Timestamp newExpiryDateTime = new Timestamp(group.getTokenExpiryDateTime().getTime() + daysExtension * daysMillis);
        group.setTokenExpiryDateTime(newExpiryDateTime);
        return groupRepository.save(group);
    }

    @Override
    public Group invalidateGroupToken(Group group) {
        group.setTokenExpiryDateTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
        group.setGroupTokenCode(null); // alternately, set it to ""
        return groupRepository.save(group);
    }

    @Override
    public Group invalidateGroupToken(Long groupId) {
        return invalidateGroupToken(loadGroup(groupId));
    }

    @Override
    public boolean groupHasValidToken(Group group) {

        boolean codeExists = group.getGroupTokenCode() != null && group.getGroupTokenCode().trim() != "";
        boolean codeValid = group.getTokenExpiryDateTime() != null &&
                group.getTokenExpiryDateTime().after(new Timestamp(Calendar.getInstance().getTimeInMillis()));

        return codeExists && codeValid;

    }

    @Override
    public boolean tokenExists(String groupToken) {
        // separating this from getGroupByToken because in time we will want to hone its performance, a lot
        // todo: find a way to make this very, very fast--in some use cases, will be triggered by 10k+ users within seconds
        log.info("Looking for this token ... " + groupToken);
        return (groupRepository.findByGroupTokenCode(groupToken) != null);
    }

    private String generateCodeString() {
        // todo: implement a unique code generating algorithm that actually makes sense
        //return String.valueOf(1000 + new Random().nextInt(9999));
        return String.valueOf(tokenGeneratorService.getNextToken());
    }

    /*
    returns the new sub-group
     */
    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName) {
        return createSubGroup(userManager.getUserById(createdByUserId), loadGroup(groupId),subGroupName);
    }

    public Group createSubGroup(User createdByUser, Group group, String subGroupName) {
        return groupRepository.save(new Group(subGroupName,createdByUser,group));
    }

    @Override
    public List<User> getAllUsersInGroupAndSubGroups(Long groupId) {
        return getAllUsersInGroupAndSubGroups(loadGroup(groupId));
    }

    @Override
    public List<User> getAllUsersInGroupAndSubGroups(Group group) {
        List<User> userList = new ArrayList<User>();
        recursiveUserAdd(group,userList);
        return userList;
    }

    @Override
    public List<Group> getAllParentGroups(Group group) {
        List<Group> parentGroups = new ArrayList<Group>();
        recursiveParentGroups(group,parentGroups);
        return parentGroups;
    }

    @Override
    public boolean hasParent(Group group) {
        return group.getParent() != null;
    }

    @Override
    public Group getParent(Group group) {
        // trivial method, but seems safer to do a trivial services call than have this sort of thing sitting in view
        return group.getParent();
    }

    @Override
    public Group linkSubGroup(Group child, Group parent) {
        // todo: error checking, for one more barrier against infintite loops
        child.setParent(parent);
        return groupRepository.save(child);
    }

    /*
    The method checks whether the
     */
    @Override
    public boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup) {
        for (Group g : getAllParentGroups(possibleParentGroup)) {
            // if this returns true, then the group being passed as child is already in the parent chain of the desired
            // parent, which will create an infinite loop, hence prevent it
            if (g.getId() == possibleChildGroup.getId()) return true;
        }
        return false;
    }

    @Override
    public Group setGroupDefaultReminderMinutes(Group group, Integer minutes) {
        group.setReminderMinutes(minutes);
        return groupRepository.save(group);
    }

    @Override
    public Group setGroupDefaultReminderMinutes(Long groupId, Integer minutes) {
        return setGroupDefaultReminderMinutes(loadGroup(groupId), minutes);
    }

    @Override
    public Group setGroupDefaultLanguage(Group group, String locale) {

        /*
         Note: intentionally not doing this for all subgroups too -- a sufficiently delicate operation to require user
         executing it to go through each subgroup, if they want to do that. Also note basic logic -- if user has language
         which is non-default, we do not change theirs. Again, might lead to some extra work if some members are part
         of two groups, and then have to manually over-ride. But that is better than alternatives, which is either to
         over-ride user preference once set (= angry user), or have one more dummy field.
         Also todo: make this something recurring, so, once set, applies to new members joining the group
          */

        List<User> groupMembers = group.getGroupMembers();

        for (User user : groupMembers) {
            if (!user.isHasInitiatedSession()) {
                log.info("User hasn't set their own language, so adjusting it to: " + locale + " for this user: " + user.nameToDisplay());
                userManager.setUserLanguage(user, locale);
            }
        }

        return saveGroup(group);

    }

    @Override
    public Integer getGroupSize(Group group, boolean includeSubGroups) {
        // as with a few above, a little trivial as implemented here, but may change in future, so rather here than code in webapp

        log.info("Getting group member size");
        if (!includeSubGroups) {
            log.info("Getting group size, for group: " + group);
            return group.getGroupMembers().size();
        } else {
            log.info("Getting group size, including sub-groups, for group:" + group);
            return getAllUsersInGroupAndSubGroups(group).size();
        }

    }

    @Override
    public boolean canUserDeleteGroup(User user, Group group) {
        // todo: Integrate with permission checking -- for now, just checking if group created by user in last 48 hours
        // todo: the time checking would be so much easier if we use Joda or Java 8 DateTime ...
        boolean createdByUser = (group.getCreatedByUser() == user);
        Timestamp thresholdTime = new Timestamp(Calendar.getInstance().getTimeInMillis() - (48 * 60 * 60 * 1000));
        boolean groupCreatedSinceThreshold = (group.getCreatedDateTime().after(thresholdTime));
        return (createdByUser && groupCreatedSinceThreshold);
    }

    private void recursiveUserAdd(Group parentGroup, List<User> userList ) {

        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
            recursiveUserAdd(childGroup,userList);
        }

        // add all the users at this level
        userList.addAll(parentGroup.getGroupMembers());

    }

    private void recursiveParentGroups(Group childGroup, List<Group> parentGroups) {
        if (childGroup.getParent() != null && childGroup.getParent().getId() != 0) {
            recursiveParentGroups(childGroup.getParent(),parentGroups);
        }
        // add the current group as there are no more parents
        // todo aakil this adds the group even if it had no parents??? is this a problem, rethink
        parentGroups.add(childGroup);
    }

}
