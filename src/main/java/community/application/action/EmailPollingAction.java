package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import community.application.entity.EmailPollingConfigEntity;
import community.application.workflow.EmailProcessingWorkflow;

import java.time.Duration;

/**
 * GREEN phase: TimedAction that periodically polls for new emails.
 */
@Component(id = "email-polling-action")
public class EmailPollingAction extends TimedAction {

    /** Timer name for the email polling operation */
    private static final String TIMER_NAME = "email-polling-timer";
    
    /** Default config entity ID */
    private static final String CONFIG_ENTITY_ID = "polling-config";

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public EmailPollingAction(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    /**
     * Poll for new emails by triggering EmailProcessingWorkflow.
     * Reschedules itself for the next poll.
     */
    public Effect pollForEmails() {
        TimedActionLogger.logExecution("email-polling-action", "pollForEmails");
        
        // Trigger email processing workflow
        EmailProcessingWorkflow.ProcessResult result = componentClient
            .forWorkflow("email-processor")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());
        
        // Log result if emails were processed
        if (result.emailsProcessed() > 0) {
            TimedActionLogger.logExecution("email-polling-action", "pollForEmails", 
                                          "Processed " + result.emailsProcessed() + " emails");
        }

        // Schedule next poll
        scheduleNextPoll();

        return effects().done();
    }

    /**
     * Schedule the next email poll using configured interval.
     */
    public Effect scheduleNextPoll() {
        TimedActionLogger.logExecution("email-polling-action", "scheduleNextPoll");
        
        // Get polling interval from config entity
        Integer intervalSeconds = componentClient.forKeyValueEntity(CONFIG_ENTITY_ID)
            .method(EmailPollingConfigEntity::getInterval)
            .invoke();
        
        Duration pollInterval = Duration.ofSeconds(intervalSeconds);
        
        try {
            timerScheduler.createSingleTimer(
                TIMER_NAME,
                pollInterval,
                componentClient
                    .forTimedAction()
                    .method(EmailPollingAction::pollForEmails)
                    .deferred()
            );
        } catch (RuntimeException e) {
            // Handle timer scheduler shutdown during test teardown
            if (e.getCause() instanceof akka.pattern.AskTimeoutException) {
                TimedActionLogger.logExecution("email-polling-action", "scheduleNextPoll", 
                                              "Timer scheduler terminated - skipping reschedule");
            } else {
                // Re-throw unexpected errors
                throw e;
            }
        }
        
        return effects().done();
    }
}