package community.application.action;

import akka.javasdk.testkit.TestKitSupport;
import community.application.entity.ActiveInquiryEntity;
import community.application.entity.ReminderConfigEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing TimedAction execution logging.
 */
public class TimedActionLoggerTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger timedActionLogger;

    @BeforeEach
    public void setupLogCapture() {
        timedActionLogger = (Logger) LoggerFactory.getLogger(TimedActionLogger.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        timedActionLogger.addAppender(logAppender);
    }

    @AfterEach
    public void teardownLogCapture() {
        timedActionLogger.detachAppender(logAppender);
    }

    @Test
    public void red_shouldLogWhenReminderActionExecutes() {
        // RED: When ReminderAction.sendReminderForActiveInquiry() executes,
        // there should be structured logging of the timed action execution

        // Setup: Create an active inquiry and configure reminder interval
        String emailId = "test-email-reminder-001";
        
        componentClient.forKeyValueEntity("active-inquiry")
            .method(ActiveInquiryEntity::setActiveInquiry)
            .invoke(emailId);

        componentClient.forKeyValueEntity("reminder-config")
            .method(ReminderConfigEntity::setInterval)
            .invoke(2); // 2 seconds for testing

        // Act: Trigger ReminderAction execution directly via timer
        timerScheduler.createSingleTimer(
            "test-reminder-timer",
            Duration.ofMillis(100), // Short delay for test
            componentClient.forTimedAction()
                .method(ReminderAction::sendReminderForActiveInquiry)
                .deferred()
        );

        // Assert: A logging mechanism should have logged the TimedAction execution
        // We expect a log entry containing: actionType=reminder-action, actionMethod=sendReminderForActiveInquiry
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = logAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("TimedAction executed") 
                          && event.getFormattedMessage().contains("actionType=reminder-action")
                          && event.getFormattedMessage().contains("actionMethod=sendReminderForActiveInquiry"));
            assertTrue(found, "Expected TimedAction execution log not found in: " + logEvents);
        });
    }
}