package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.FormatUtil;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 8/24/15.
 */
@Component
public class MessageAssemblingManager implements MessageAssemblingService {

    private Logger log = LoggerFactory.getLogger(MessageAssemblingManager.class);

    @Autowired
    @Qualifier("servicesMessageSource")
    MessageSource messageSource;

    @Autowired
    @Qualifier("servicesMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    private static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

    /*
    Although it says meeting using the same functions for voting!
     */
    @Override
    public String createMeetingNotificationMessage(User user, EventDTO event) {
        //TODO fix the locale resolver in config
        Locale locale = getUserLocale(user);
        String messageKey = "";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.new";
        } else {
            messageKey = event.isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";

        }
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }



    @Override
    public String createLogBookReminderMessage(User user, Group group, LogBook logBook) {
        Locale locale = getUserLocale(user);
        return messageSourceAccessor.getMessage("sms.logbook.reminder", populateLogBookFields(user, group, logBook), locale);
    }



    @Override
    public String createNewLogBookNotificationMessage(User user, Group group, LogBook logBook, boolean assigned) {
        Locale locale = getUserLocale(user);
        if (assigned) {
            return messageSourceAccessor.getMessage("sms.logbook.new.assigned", populateLogBookFields(user, group, logBook), locale);
        } else {
            return messageSourceAccessor.getMessage("sms.logbook.new.notassigned", populateLogBookFields(user, group, logBook), locale);
        }
    }


    @Override
    public String createChangeMeetingNotificationMessage(User user, EventDTO event) {
        // TODO think if there's a simple way to work out which variable has changed and only send that
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.change";
        if (event.getEventType() == EventType.VOTE) messageKey = "sms.vote.send.change";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }



    @Override
    public String createCancelMeetingNotificationMessage(User user, EventDTO event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.cancel";
        if (event.getEventType() == EventType.VOTE) messageKey = "sms.vote.send.cancel";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }



    @Override
    public String createMeetingReminderMessage(User user, EventDTO event) {
        Locale locale = getUserLocale(user);
        String messageKey =  "sms.mtg.send.reminder";
        if (event.getEventType() == EventType.VOTE) messageKey = "sms.vote.send.reminder";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }



    @Override
    public String createVoteResultsMessage(User user, EventDTO event, double yes, double no, double abstain, double noReply) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.vote.send.results";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event, yes, no, abstain, noReply), locale);
    }



    @Override
    public String createMeetingReminderMessage(String locale, User user, EventDTO event) {
        log.info("Creating meeting reminder in this locale ..." + locale);
        return messageSourceAccessor.getMessage("sms.mtg.send.reminder", populateFields(user, event), locale);
    }



    @Override
    public String createMeetingRsvpTotalMessage(User user, EventDTO meeting, ResponseTotalsDTO responses) {
        log.info("Constructing meeting total message ... with response object totals={}", responses.toString());
        String[] fields = new String[] { meeting.getName(),
                meeting.getEventStartDateAtSAST().format(shortDateFormatter),
                String.valueOf(responses.getYes()),
                String.valueOf(responses.getNo()),
                String.valueOf(responses.getNumberNoRSVP()),
                String.valueOf(responses.getNumberOfUsers()) };
        return messageSourceAccessor.getMessage("sms.meeting.rsvp.totals", fields, getUserLocale(user));
    }

    @Override
    public String createWelcomeMessage(String messageId, UserDTO userDTO) {
        return messageSourceAccessor.getMessage(messageId, populateWelcomeFields(userDTO), getUserLocale(userDTO.getLanguageCode()));
    }

    @Override
    public String createMeetingThankYouMessage(User user, EventDTO event) {
        // sms.meeting.thankyou = {0}: Thank you for attending the meeting about {1} on {2}. Dial *134*1994# to create and join groups or call meetings.
        String prefix = (event.getParent().getJpaEntityType().equals(JpaEntityType.GROUP) &&
                ((Group) event.getParent()).hasName()) ? ((Group) event.getParent()).getGroupName() : "Grassroot";
        String[] fields = new String[] {  prefix, event.getName(), event.getEventStartDateAtSAST().format(shortDateFormatter) };
        return messageSourceAccessor.getMessage("sms.meeting.thankyou", fields, getUserLocale(user));
    }


    @Override
    public String createReplyFailureMessage(User user) {
        return messageSourceAccessor.getMessage("sms.reply.failure", "",getUserLocale(user));

    }

    @Override
    public String createGroupJoinCodeUseMessage(User user, String groupName, int numberJoined, List<String> namesJoined) {
        String message;
        String[] fields;
        if (namesJoined == null) {
            fields = new String[] { groupName, String.valueOf(numberJoined) };
            message = messageSourceAccessor.getMessage("sms.group.join.number", fields, getUserLocale(user));
        } else {
            String numbers = String.join(", ", namesJoined);
            fields = new String[] { groupName, numbers };
            message = messageSourceAccessor.getMessage("sms.group.join.named", fields, getUserLocale(user));
        }
        return message;
    }


    private Locale getUserLocale(User user) {
        return getUserLocale(user.getLanguageCode());
    }

    private Locale getUserLocale(String languageCode) {
        if (languageCode == null || languageCode.trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(languageCode);
        }

    }

    private String[] populateFields(User user, EventDTO event) {
        return populateFields(user, event, 0D, 0D, 0D, 0D);
    }

    private String[] populateFields(User user, EventDTO event, double yes, double no, double abstain, double noReply) {

        // todo: switch this to new name (may want a "hasName"/"getName" method defined on UidIdentifiable?
        String salutation = (((Group) event.getParent()).hasName()) ? ((Group) event.getParent()).getGroupName() : "Grassroot";
        log.info("populateFields...user..." + user.getPhoneNumber() + "...event..." + event.getId() + "...version..." + event.getVersion());
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = "no date specified";
        if (event.getEventStartDateTime() != null) {
            dateString = sdf.format(event.getEventStartDateTime().atZone(getSAST()));
        }
        String[] eventVariables = new String[]{
                salutation,
                event.getCreatedByUser().nameToDisplay(),
                event.getName(),
                event.getEventLocation(),
                dateString,
                FormatUtil.formatDoubleToString(yes),
                FormatUtil.formatDoubleToString(no),
                FormatUtil.formatDoubleToString(abstain),
                FormatUtil.formatDoubleToString(noReply)
        };

        return eventVariables;

    }

    private String[] populateLogBookFields(User user, Group group, LogBook logBook) {
        String salutation = (group.hasName()) ? group.getGroupName() : "GrassRoot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = (logBook.getActionByDate() == null) ? "no date specified" :
                sdf.format(logBook.getActionByDateAtSAST());

        String[] variables = new String[]{
                salutation,
                logBook.getMessage(),
                dateString,
                user.getDisplayName()
        };
        return variables;
    }

    private String[] populateWelcomeFields(UserDTO userDTO) {
        return new String[] {
                userDTO.getDisplayName(),
                userDTO.getFirstName(),
                userDTO.getLastName(),
                userDTO.getPhoneNumber()
        };
    }

    private String getLocaleProperty(String channel, String field){
        StringBuilder sb = new StringBuilder();
        return sb.append(channel).append(field).toString();
    }
}