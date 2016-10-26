package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.FormatUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Created by aakilomar on 8/24/15.
 */
@Component
public class MessageAssemblingManager implements MessageAssemblingService {

    private Logger log = LoggerFactory.getLogger(MessageAssemblingManager.class);

    @Autowired
    @Qualifier("servicesMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    // major todo : consolidate & externalize all the date formatters
    private static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

    @Override
    public String createEventInfoMessage(User user, Event event) {
        String messageKey = EventType.VOTE.equals(event.getEventType()) ? "sms.vote.send.new" :
                event.isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), getUserLocale(user));
    }

    @Override
    public String createEventChangedMessage(User user, Event event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.change";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.change";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createEventCancelledMessage(User user, Event event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.cancel";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.cancel";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createEventResponseMessage(User user, Event event, EventRSVPResponse rsvpResponse) {
        Meeting meeting = (Meeting) event;
        String messageKey;
        String salutation = (( meeting.getParent()).hasName()) ? ((Group) meeting.getParent()).getGroupName() : "Grassroot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = sdf.format(meeting.getEventDateTimeAtSAST());
        String[] field = {salutation, user.nameToDisplay(), meeting.getName(), dateString, meeting.getEventLocation()};
        if (rsvpResponse.equals(EventRSVPResponse.YES)) {
            messageKey = "sms.mtg.attendance.yes";
        } else if (rsvpResponse.equals(EventRSVPResponse.NO)) {
            messageKey = "sms.mtg.attendance.no";
        } else {
            messageKey = "sms.mtg.attendance.maybe";
        }
        return messageSourceAccessor.getMessage(messageKey, field, getUserLocale(user));
    }

    @Override
    public String createTodoReminderMessage(User user, Todo todo) {
        Locale locale = getUserLocale(user);
        String[] args = populateTodoFields(todo);
        final String msgKey = todo.getActionByDate().isAfter(Instant.now()) ? "sms.todo.reminder" : "sms.todo.reminder.past";
        return messageSourceAccessor.getMessage(msgKey, args, locale);
    }

    @Override
    public String createTodoRecordedNotificationMessage(User target, Todo todo) {
        Locale locale = getUserLocale(target);
        String[] args = populateTodoFields(todo);
        String messageKey = todo.isAllGroupMembersAssigned() ? "sms.todo.new.notassigned" :
                (todo.getAssignedMembers().size()) == 1 ? "sms.todo.new.assigned.one" : "sms.todo.new.assigned.many";
        return messageSourceAccessor.getMessage(messageKey, args, locale);
    }

    @Override
    public String createTodoUpdateNotificationMessage(User target, Todo todo) {
        Locale locale = getUserLocale(target);
        String[] args = populateTodoFields(todo);
        String messageKey = todo.isAllGroupMembersAssigned() ? "sms.todo.update.notassigned" :
                (todo.getAssignedMembers().size()) == 1 ? "sms.todo.update.assigned.one" : "sms.todo.update.assigned.many";
        return messageSourceAccessor.getMessage(messageKey, args, locale);

    }

    @Override
    public String createVoteResultsMessage(User user, Vote event, double yes, double no, double abstain, double noReply) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.vote.send.results";
        String[] args = populateEventFields(event, yes, no, abstain, noReply);
        return messageSourceAccessor.getMessage(messageKey, args, locale);
    }

    @Override
    public String createScheduledEventReminderMessage(User destination, Event event) {
        Locale locale = getUserLocale(destination);
        String messageKey = "sms.mtg.send.reminder";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.reminder";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createMeetingRsvpTotalMessage(User user, Meeting meeting, ResponseTotalsDTO responses) {
        log.info("Constructing meeting total message ... with response object totals={}", responses.toString());
        String[] fields = new String[]{meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(shortDateFormatter),
                String.valueOf(responses.getYes()),
                String.valueOf(responses.getNo()),
                String.valueOf(responses.getNumberNoRSVP()),
                String.valueOf(responses.getNumberOfUsers())};
        return messageSourceAccessor.getMessage("sms.meeting.rsvp.totals", fields, getUserLocale(user));
    }

    @Override
    public String createMeetingThankYourMessage(User target, Meeting meeting) {
        // sms.meeting.thankyou = {0}: Thank you for attending the meeting about {1} on {2}. Dial *134*1994# to create and join groups or call meetings.
        MeetingContainer parent = meeting.getParent();
        String prefix = (parent.getJpaEntityType().equals(JpaEntityType.GROUP) && ((Group) parent).hasName()) ?
                ((Group) parent).getGroupName() : "Grassroot";
        String[] fields = new String[]{prefix, meeting.getName(), meeting.getEventDateTimeAtSAST().format(shortDateFormatter)};
        return messageSourceAccessor.getMessage("sms.meeting.thankyou", fields, getUserLocale(target));
    }

    @Override
    public String createWelcomeMessage(String messageId, User user) {
        String[] welcomeFields = {
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber()
        };
        return messageSourceAccessor.getMessage(messageId, welcomeFields, getUserLocale(user.getLanguageCode()));
    }

    @Override
    public String createMeetingAttendanceConfirmationMessage(User organiser, User member, EventLog eventLog) {
        final Meeting meeting = ((Meeting) eventLog.getEvent());
        String[] fields = new String[]{
                meeting.getAncestorGroup().getName(),
                member.getDisplayName(),
                meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(shortDateFormatter),
                meeting.getEventLocation()
        };
        return messageSourceAccessor.getMessage("sms.meeting.attendance." + eventLog.getResponse().toString().toLowerCase(),
                fields, getUserLocale(organiser));
    }

    @Override
    public String createSafetyEventMessage(User respondent, User requestor, Address address, boolean reminder) {

        String[] fields;
        String message;
        if (address != null) {
            fields = new String[]{requestor.nameToDisplay(), address.getHouseNumber(), address.getStreetName(), address.getTown()};
            message = (reminder) ? messageSourceAccessor.getMessage("sms.safety.reminder", fields, getUserLocale(requestor)) :
                    messageSourceAccessor.getMessage("sms.safety.new", fields, getUserLocale(requestor));
        } else {
            fields = new String[]{requestor.nameToDisplay()};
            message = (reminder) ? messageSourceAccessor.getMessage("sms.safety.reminder.nolocation", fields, getUserLocale(requestor)) :
                    messageSourceAccessor.getMessage("sms.safety.new.nolocation", fields, getUserLocale(requestor));
        }
        return message;
    }

    @Override
    public String createFalseSafetyEventActivationMessage(User requestor, long count) {
        return messageSourceAccessor.getMessage("sms.safety.false", new String[]{
                String.valueOf(count)}, getUserLocale(requestor));
    }

    @Override
    public String createSafetyEventReportMessage(User user, User respondent, SafetyEvent safetyEvent, boolean respondedTo) {
        String fields[];
        String message;
        if (respondedTo) {
            fields = new String[]{respondent.nameToDisplay(), safetyEvent.getActivatedBy().getDisplayName()};
            message = (!safetyEvent.isFalseAlarm()) ? messageSourceAccessor.getMessage("sms.safety.valid", fields, getUserLocale(user)) :
                    messageSourceAccessor.getMessage("sms.safety.invalid", fields, getUserLocale(user));
        } else {
            //todo trigger this message after an hour if the safetyevent was not responded to
            fields = new String[]{safetyEvent.getActivatedBy().nameToDisplay()};
            message = messageSourceAccessor.getMessage("sms.safety.noresponse", fields, getUserLocale(user));
        }
        return message;
    }

    @Override
    public String createBarringMessage(User requestor) {
        return messageSourceAccessor.getMessage("sms.safety.barred", "", getUserLocale(requestor));
    }

    @Override
    public String createGroupJoinRequestMessage(User user, GroupJoinRequest request) {
        String[] fields = {request.getRequestor().getDisplayName(), request.getGroup().getGroupName()};
        return messageSourceAccessor.getMessage("sms.group.join.request", fields, getUserLocale(user));
    }

    @Override
    public String createGroupJoinReminderMessage(User user, GroupJoinRequest request) {
        String[] fields = {request.getRequestor().getDisplayName(), request.getGroup().getGroupName()};
        return messageSourceAccessor.getMessage("sms.group.join.request.reminder", fields, getUserLocale(user));
    }

    @Override
    public String createGroupJoinResultMessage(GroupJoinRequest request, boolean approved) {
        final String[] fields = { request.getGroup().getName() };
        return approved ? messageSourceAccessor.getMessage("sms.group.join.approved", fields, getUserLocale(request.getRequestor()))
                : messageSourceAccessor.getMessage("sms.group.join.denied", fields, getUserLocale(request.getRequestor()));
    }


    @Override
    public String createReplyFailureMessage(User user) {
        return messageSourceAccessor.getMessage("sms.reply.failure", "", getUserLocale(user));

    }

    @Override
    public String createGroupJoinCodeUseMessage(User user, String groupName, int numberJoined, List<String> namesJoined) {
        String message;
        String[] fields;
        if (namesJoined == null) {
            fields = new String[]{groupName, String.valueOf(numberJoined)};
            message = messageSourceAccessor.getMessage("sms.group.join.number", fields, getUserLocale(user));
        } else {
            String numbers = String.join(", ", namesJoined);
            fields = new String[]{groupName, numbers};
            message = messageSourceAccessor.getMessage("sms.group.join.named", fields, getUserLocale(user));
        }
        return message;
    }


    public Locale getUserLocale(User user) {
        return getUserLocale(user.getLanguageCode());
    }

    private Locale getUserLocale(String languageCode) {
        if (languageCode == null || languageCode.trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(languageCode);
        }

    }

    public String[] populateEventFields(Event event) {
        return populateEventFields(event, 0D, 0D, 0D, 0D);
    }

    public String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply) {
        String salutation = ((event.getParent()).hasName()) ? event.getParent().getName() : "Grassroot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = sdf.format(event.getEventDateTimeAtSAST());

        String location = null;
        if (event instanceof Meeting) {
            Meeting meeting = (Meeting) event;
            location = meeting.getEventLocation();
        }

        String subject = event.getName();
        subject = (subject.contains("&")) ? subject.replace("&", "and") : subject;
        String[] eventVariables = new String[]{
                salutation,
                event.getCreatedByUser().nameToDisplay(),
                subject,
                location,
                dateString,
                FormatUtil.formatDoubleToString(yes),
                FormatUtil.formatDoubleToString(no),
                FormatUtil.formatDoubleToString(abstain),
                FormatUtil.formatDoubleToString(noReply)
        };

        return eventVariables;

    }

    @Override
    public String createAndroidLinkSms(User user) {
        return messageSourceAccessor.getMessage("sms.link.android", getUserLocale(user));

    }

    @Override
    public String createAccountBillingNotification(AccountBillingRecord record) {
        return "Hello you need to pay us money thanks";
    }

    @Override
    public String createAccountStatementSubject(AccountBillingRecord record) {
        return "Grassroot Account Statement";
    }

    @Override
    public String createAccountStatementEmail(AccountBillingRecord record) {

        // okay, really need to set a "billed user" field
        final String salutation = String.format("Dear %s,\n\n", "Person Paying Us");

        final String body = String.format("Your monthly subscription fee for Grassroot of R%d " +
                "is due for payment. We will automatically charge your payment method on file on %s. " +
                "To upgrade your account, or change any of your settings, please visit " +
                "http://app.grassroot.org.za/account/settings. As always, we hope we were useful.\n\n",
                record.getAmountToPay(), LocalDate.now().plusDays(1).toString());

        final String closing = "Regards,\nGrassroot";

        return salutation + body + closing;
    }

    private String[] populateTodoFields(Todo todo) {
        Group group = todo.getAncestorGroup();
        String salutation = (group.hasName()) ? group.getGroupName() : "Grassroot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE, d MMM");
        String dateString = sdf.format(todo.getActionByDateAtSAST());
        String assignment = (todo.getAssignedMembers().size() == 1) ?
                todo.getAssignedMembers().iterator().next().getDisplayName() : String.valueOf(todo.getAssignedMembers().size());

        String[] variables = new String[]{
                salutation,
                todo.getMessage(),
                dateString,
                assignment
        };
        return variables;
    }
}
