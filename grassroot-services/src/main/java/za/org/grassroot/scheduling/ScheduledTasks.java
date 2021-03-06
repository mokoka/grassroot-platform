package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.integration.GroupChatService;
import za.org.grassroot.integration.MessageSendingService;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.mqtt.MqttSubscriptionService;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.specifications.TodoSpecifications;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TodoBroker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 10/5/15.
 * todo : clean up dependency mess in here
 */
@Component
public class ScheduledTasks {

    private Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private SafetyEventBroker safetyEventBroker;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired(required = false)
    private GroupChatService groupChatService;

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private MessageSendingService messageSendingService;

    @Autowired(required = false)
    private MqttSubscriptionService mqttSubscriptionService;

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendReminders() {
        List<Event> events = eventRepository.findEventsForReminders(Instant.now());
        logger.info("Sending scheduled reminders for {} event(s)", events.size());

        for (Event event : events) {
            try {
                eventBroker.sendScheduledReminder(event.getUid());
            } catch (Exception e) {
                logger.error("Error while sending scheduled reminder of event " + event + ": " + e.getMessage(), e);
            }
        }

        logger.info("Sending scheduled reminders...done");
    }

    @Scheduled(fixedRate = 300000)
    public void sendSafetyReminders() {
        List<SafetyEvent> safetyEvents = safetyEventRepository.findSafetyEvents(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());
        logger.info("Sending out safety reminders");
        if (safetyEvents != null) {
            safetyEvents.forEach(e -> safetyEventBroker.sendReminders(e.getUid()));
        }
    }

    @Scheduled(fixedRate = 60000) //runs every 1 minute
    public void sendUnsentVoteResults() {
        List<Vote> votes = voteRepository.findUnsentVoteResults(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());
        if (!votes.isEmpty()) {
            logger.info("Sending vote results for {} unsent votes...", votes.size());
        }

        for (Vote vote : votes) {
            try {
                eventBroker.sendVoteResults(vote.getUid());
            } catch (Exception e) {
                logger.error("Error while sending vote results for vote " + vote + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 16 * * *") // runs at 4pm (=6pm SAST) every day
    public void sendMeetingThankYous() {
        LocalDate yesterday = LocalDate.now().minus(1, ChronoUnit.DAYS);
        Instant start = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MIN), getSAST());
        Instant end = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MAX), getSAST());

        List<Meeting> meetings = meetingRepository.findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(start, end);
        logger.info("Sending out meeting thank you for {} meetings", meetings.size());

        for (Meeting meeting : meetings) {
            try {
                eventBroker.sendMeetingAcknowledgements(meeting.getUid());
            } catch (Exception ex) {
                logger.error("Error while sending acknowledgments for meeting " + meeting + ": " + ex.getMessage(), ex);
            }
        }
    }

    @Scheduled(fixedRate = 3600000) // runs every hour
    public void sendMeetingRSVPsToDate() {
        // since the scheduled job runs once an hour, check for meetings created two days ago, in an hour interval
        Instant start = Instant.now().minus(48, ChronoUnit.HOURS);
		Instant end = start.minus(1, ChronoUnit.HOURS);
        List<Meeting> meetings = meetingRepository.meetingsForResponseTotals(Instant.now(), start, end);

		logger.info("Sending out RSVP totals for {} meetings", meetings.size());
        for (Meeting meeting : meetings) {
            try {
                eventBroker.sendMeetingRSVPsToDate(meeting.getUid());
            } catch (Exception e) {
                logger.error("Error while sending responses for meeting " + meeting + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendTodoReminders() {
        List<Todo> todos = todoRepository.findAll(Specifications.where(TodoSpecifications.notCancelled())
                .and(TodoSpecifications.remindersLeftToSend())
                .and(TodoSpecifications.reminderTimeBefore(Instant.now()))
                .and(TodoSpecifications.completionConfirmsBelow(COMPLETION_PERCENTAGE_BOUNDARY, false))
                .and(TodoSpecifications.todoNotConfirmedByCreator()));

        logger.info("Sending scheduled reminders for {} todos, after using threshold of {}", todos.size(), COMPLETION_PERCENTAGE_BOUNDARY);
        for (Todo todo : todos) {
            try {
                todoBroker.sendScheduledReminder(todo.getUid());
            } catch (Throwable th) {
                logger.error("Error while sending reminder for todo " + todo + ": " + th.getMessage(), th);
            }
        }
    }

    @Scheduled(fixedRate = 300000)
    public void reactivateMutedUsers() throws Exception {
        if (groupChatService != null) {
            List<GroupChatSettings> groupChatSettingses = groupChatService.loadUsersToBeUnmuted();
            for (GroupChatSettings messengerSetting : groupChatSettingses) {
                String userUid = messengerSetting.getUser().getUid();
                String groupUid = messengerSetting.getGroup().getUid();
                try {
                    groupChatService.updateActivityStatus(userUid, groupUid, true, false);
                } catch (GroupChatSettingNotFoundException e) {
                    logger.error("Error while trying unmute user with " + userUid);
                }
            }
        }
    }

    @Scheduled(cron = "0 0 1 * * *") //runs at 1 am everyday
    public void subscribeServerToGroupTopics(){
        if (mqttSubscriptionService != null) {
            mqttSubscriptionService.subscribeServerToAllGroupTopics();
        }
    }


    @Scheduled(cron = "0 0 15 * * *") // runs at 3pm (= 5pm SAST) every day
    public void sendGroupJoinNotifications() { groupBroker.notifyOrganizersOfJoinCodeUse(Instant.now().minus(1, ChronoUnit.DAYS),
                                                                                         Instant.now());}
    @Scheduled(fixedRate = 300000) // runs every five minutes
    public void gcmKeepAlive(){
        messageSendingService.sendPollingMessage();
    }


}
