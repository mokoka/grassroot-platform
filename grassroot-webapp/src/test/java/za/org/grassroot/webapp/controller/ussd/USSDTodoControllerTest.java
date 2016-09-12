package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.GroupPage;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.convertDateStringToLocalDateTime;
import static za.org.grassroot.core.util.DateTimeUtil.reformatDateInput;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/12/18.
 */
public class USSDTodoControllerTest extends USSDAbstractUnitTest {

    // private static final Logger log = LoggerFactory.getLogger(USSDTodoControllerTest.class);

    public static final String assignUserID = "assignUserUid";

    private static final String testUserPhone = "0601110001";
    private static final String phoneParam = "msisdn";
    private static final String todoUidParam = "todoUid";
    private static final String dummyUserInput = "blah blah blah blah";
    private static final String groupMenu = "group",
            subjectMenu = "subject",
            dueDateMenu = "due_date",
            confirmMenu = "confirm",
            send = "send";
    private static final String listEntriesMenu = "list",
            viewEntryMenu = "view",
            viewEntryDates = "view_dates",
            viewAssignment = "view_assigned",
            setCompleteMenu = "set_complete",
            completingUser = "choose_completor",
            pickCompletor = "pick_completor",
            completedDate = "date_completed",
            confirmCompleteDate = "confirm_date";

    private static final String path = "/ussd/todo/";

    @InjectMocks
    private USSDToDoController ussdLogBookController;

    private User testUser;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdLogBookController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdLogBookController);
        testUser = new User(testUserPhone);
    }

    @Test
    public void groupSelectMenuShouldWorkWithGroup() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                new Group("tg2", testUser),
                new Group("tg3", testUser));
        for (Group testGroup : testGroups) {
            testGroup.addMember(testUser);
        }
        GroupPage pageOfGroups = GroupPage.createFromGroups(testGroups, 0, 3);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(true);
        when(permissionBrokerMock.getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3)).thenReturn(pageOfGroups);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(2)).getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void groupSelectMenuShouldWorkWithNoGroup() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(false);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void listEntriesMenuShouldWork() throws Exception {

        String message = "some message about meeting some other people to" +
                " discuss something important about the community";
        Instant now = Instant.now();
        Group testGroup = new Group("somegroup", testUser);
        List<Todo> testTodos = Arrays.asList(
                new Todo(testUser, testGroup, message, now),
                new Todo(testUser, testGroup, message, now),
                new Todo(testUser, testGroup, message, now));

        Page<Todo> dummyPage = new PageImpl<>(testTodos);
        String urlToSave = logViewExistingUrl(listEntriesMenu, testGroup.getUid(), true, 0);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoBrokerMock.retrieveGroupTodos(testUser.getUid(), testGroup.getUid(), true, 0, 3)).thenReturn(dummyPage);
        mockMvc.perform(get(path + listEntriesMenu).param(phoneParam, testUserPhone).param("groupUid", testGroup.getUid())
                .param("done", String.valueOf(true))).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoBrokerMock, times(1)).retrieveGroupTodos(testUser.getUid(), testGroup.getUid(), true, 0, 3);
        verifyNoMoreInteractions(todoBrokerMock);
    }

    @Test
    public void askForSubjectShouldWork() throws Exception {
        Group dummyGroup = new Group("", testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, dummyGroup);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(todoRequestBrokerMock.create(testUser.getUid(), dummyGroup.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, saveToDoMenu("subject", dummyLogBook.getUid()));
        verifyNoMoreInteractions(cacheUtilManagerMock);
        verify(todoRequestBrokerMock, times(1)).create(testUser.getUid(), dummyGroup.getUid());
        verifyNoMoreInteractions(todoRequestBrokerMock);
    }

    @Test
    public void askForDueDateShouldWorkAfterInterruption() throws Exception {

        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);
        String urlToSave = saveToDoMenu("due_date", dummyLogBook.getUid(), dummyUserInput);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).
                thenReturn(testUser);

        mockMvc.perform(get(path + dueDateMenu).param(todoUidParam, dummyLogBook.getUid()).param(phoneParam, testUserPhone)
                .param("prior_input", dummyUserInput).param("interrupted", String.valueOf(true))
                .param("revising", String.valueOf(false)).param("request", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), dummyUserInput);
        verifyNoMoreInteractions(todoRequestBrokerMock);

    }

    /* Since we are no longer doing user assignment through USSD, this is redundant

    @Test
    public void askForAssignmentWorksAfterInterruption() throws Exception {

        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);

        String priorInput = "20/1";

        String urlToSave = USSDUrlUtil.saveToDoMenu(assignMenu, dummyLogBook.getUid(), priorInput);
        String formattedDateString = reformatDateInput(priorInput).trim();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + assignMenu).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone).param("prior_input", priorInput).param("interrupted", String.valueOf(true)).param("request", "1").param("revising", String.valueOf(false)))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(todoRequestBrokerMock, times(1)).updateDueDate(testUser.getUid(), dummyLogBook.getUid(),
                                                                 parsePreformattedDate(reformatDateInput(priorInput).trim(), hour, minutes));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }*/

    /* As above, commenting out -- possibly to delete later
    @Test
    public void searchUserWorksAfterInterruption() throws Exception {

        Todo dummyLogBook = Todo.makeEmpty();
        String urlToSave = USSDUrlUtil.saveToDoMenu(searchUserMenu, dummyLogBook.getUid());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + searchUserMenu).param(logBookIdParam, String.valueOf(dummyId))
                .param(phoneParam, testUserPhone).param("prior_input", "1").param("interrupted", String.valueOf(true))
                .param("request", "1"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
    }
    */

    /* As above, leaving this out
    @Test
    public void askForAssignedUsersWorksWithNumberAfterInterruption() throws Exception {

        Todo dummyLogBook = Todo.makeEmpty();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setParent(new Group("somegroup", testUser));
        String urlToSave = USSDUrlUtil.saveToDoMenu(pickUserMenu, dummyLogBook.getUid(), testUserPhone);
        List<User> dummyPossibleUsers = Arrays.asList(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        when(userManagementServiceMock.searchByGroupAndNameNumber(dummyLogBook.getThisOrAncestorGroup().getUid(), testUserPhone))
                .thenReturn(dummyPossibleUsers);
        mockMvc.perform(get(path + pickUserMenu).param(logBookIdParam,
                String.valueOf(dummyId)).param("prior_input", testUserPhone).param(phoneParam, testUserPhone)
                .param("interrupted", String.valueOf(true)).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).load(dummyId);
        verify(userManagementServiceMock, times(1)).searchByGroupAndNameNumber(dummyLogBook.getThisOrAncestorGroup().getUid(), testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }*/

    @Test
    public void confirmLogEntryBookWorksWhenAssignedToUser() throws Exception {

        Group testGroup = new Group("testGroup", testUser);
        testGroup.addMember(testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, testGroup);

        testUser.setDisplayName("Paballo");

        dummyLogBook.setMessage(dummyUserInput);
        dummyLogBook.setActionByDate(Instant.now());

        String urlToSave = saveToDoMenu(confirmMenu, dummyLogBook.getUid(), subjectMenu, "revised message", true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + confirmMenu).param(todoUidParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone).param("request", "revised message").param("prior_menu", subjectMenu))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verify(todoRequestBrokerMock, times(2)).load(dummyLogBook.getUid());
        verify(todoRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), "revised message");
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoRequestBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void dateProcessingShouldWork() throws Exception {

        Group testGroup = new Group("test testGroup", testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, testGroup);

        LocalDateTime correctDueDate = LocalDateTime.of(testYear.getValue(), testDay.getMonthValue(), testDay.getDayOfMonth(), 13, 0);
        List<String> bloomVariations = Arrays.asList("%02d-%02d", "%02d %02d", "%02d/%02d", "%d-%d", "%d %d", "%d/%d",
                "%02d-%02d-%d", "%02d %02d %d", "%02d/%02d/%d", "%d-%d-%d", "%d/%d/%d");

	    for (String format : bloomVariations) {

	        String date = String.format(format, testDay.getDayOfMonth(), testDay.getMonthValue(), testYear.getValue());
		    String urlToSave = USSDUrlUtil.saveToDoMenu(confirmMenu, dummyLogBook.getUid(), dueDateMenu, date, true);
	        String formattedDateString = reformatDateInput(date).trim();

            dummyLogBook.setActionByDate(convertDateStringToLocalDateTime(formattedDateString, 13, 0).toInstant(ZoneOffset.UTC));
            when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
            when(todoRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

            mockMvc.perform(get(path + confirmMenu).param(phoneParam, testUserPhone).param(todoUidParam, dummyLogBook.getUid())
                    .param("prior_input", date).param("prior_menu", "due_date").param("request", "1"))
                    .andExpect(status().isOk());
        }

        verify(userManagementServiceMock, times(bloomVariations.size())).findByInputNumber(eq(testUserPhone), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(bloomVariations.size())).load(dummyLogBook.getUid());
        verify(todoRequestBrokerMock, times(bloomVariations.size())).updateDueDate(testUser.getUid(), dummyLogBook.getUid(),
                                                                                      correctDueDate);
        verifyNoMoreInteractions(todoRequestBrokerMock);

    }

    @Test
    public void finishLogBookShouldWork() throws Exception {

        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + send).param(todoUidParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(1)).finish(dummyLogBook.getUid());
        verifyNoMoreInteractions(todoRequestBrokerMock);
    }

    @Test
    public void viewEntryMenuWorks() throws Exception {
        Group testGroup = new Group("test testGroup", testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "Some logbook subject", Instant.now());
        dummyTodo.getAncestorGroup().addMember(testUser);
        dummyTodo.addCompletionConfirmation(testUser, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveToDoMenu(viewEntryMenu, dummyTodo.getUid()))).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + viewEntryMenu).param(todoUidParam, dummyTodo.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveToDoMenu(viewEntryMenu, dummyTodo.getUid()));
        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);
    }

    @Test
    public void viewLogBookDatesWorksWhenActionInComplete() throws Exception {

        Group testGroup = new Group("tg1", testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "test logbook", Instant.now().plus(7, ChronoUnit.DAYS));
        dummyTodo.getAncestorGroup().addMember(testUser);
        dummyTodo.addCompletionConfirmation(testUser, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + viewEntryDates).param(todoUidParam, dummyTodo.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());

        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void viewLogBookDatesWorksWhenActionCompleted() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));
        dummyTodo.getAncestorGroup().addMember(testUser);
        dummyTodo.addCompletionConfirmation(testUser, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + viewEntryDates).param(todoUidParam, dummyTodo.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void viewLogBookAssignmentWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyTodo.addCompletionConfirmation(testUser, Instant.now());
        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + viewAssignment).param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void logBookCompleteMenuWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyTodo.addCompletionConfirmation(testUser, Instant.now());
        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));

        String urlToSave = saveToDoMenu(setCompleteMenu, dummyTodo.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + setCompleteMenu).param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void selectCompletingUserPromptWorks() throws Exception {

        Todo dummyTodo = Todo.makeEmpty();
        String urlToSave = saveToDoMenu(completingUser, dummyTodo.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + completingUser).param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone,
                saveToDoMenu(completingUser, dummyTodo.getUid()));
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void pickCompletorWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));

        List<User> testPossibleUsers = Arrays.asList(testUser);
        String urlToSave = saveToDoMenu(pickCompletor, dummyTodo.getUid(), testUserPhone);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.searchByGroupAndNameNumber(dummyTodo.getAncestorGroup().getUid(), testUserPhone))
                .thenReturn(testPossibleUsers);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + pickCompletor).param(phoneParam, testUserPhone).param(todoUidParam, dummyTodo.getUid())
                                .param("request", testUserPhone)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone,
                saveToDoMenu(pickCompletor, dummyTodo.getUid(), testUserPhone));
        verify(userManagementServiceMock, times(2)).searchByGroupAndNameNumber(dummyTodo.getAncestorGroup().getUid(), testUserPhone);
        verify(todoBrokerMock, times(2)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void completedDatePromptWorks() throws Exception {

        String logBookUid = Todo.makeEmpty().getUid();
        String urlToSave = saveToDoMenu(completedDate, logBookUid);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + completedDate).param(phoneParam, testUserPhone).param(todoUidParam, logBookUid))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void confirmCompletedDateWorks() throws Exception {

        String logBookUid = Todo.makeEmpty().getUid();

        String priorInput = "20 01";
        String urlToSave = saveToDoMenu(confirmCompleteDate, logBookUid, priorInput);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + confirmCompleteDate).param(phoneParam, testUserPhone).param(todoUidParam, logBookUid).
                param(userInputParam, priorInput)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userInputParam, "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void setLogBookEntryCompleteWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, "test logbook", Instant.now().plus(1, ChronoUnit.DAYS));

        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));
        dummyTodo.addCompletionConfirmation(testUser, Instant.now());
        String completed_date = "20/11";
        LocalDateTime correctDateTime = LocalDateTime.of(2016, 11, 20, 13, 0);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + setCompleteMenu + "-do").param(phoneParam, testUserPhone).
                param(todoUidParam, dummyTodo.getUid()).param("completed_date", completed_date).
                param(assignUserID, testUser.getUid())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(todoBrokerMock, times(1)).confirmCompletion(testUser.getUid(), dummyTodo.getUid(), correctDateTime);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }

}