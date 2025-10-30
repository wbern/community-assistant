package community.application.action;

import akka.javasdk.testkit.TestKitSupport;
import community.application.entity.ReminderConfigEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing KeyValueEntity state change logging.
 */
public class KeyValueEntityLoggerTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger reminderConfigLogger;

    @BeforeEach
    public void setupLogCapture() {
        // Capture logs from the centralized KeyValueEntityLogger
        reminderConfigLogger = (Logger) LoggerFactory.getLogger(KeyValueEntityLogger.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        reminderConfigLogger.addAppender(logAppender);
    }

    @AfterEach
    public void teardownLogCapture() {
        reminderConfigLogger.detachAppender(logAppender);
    }

    @Test
    public void red_shouldLogWhenReminderConfigEntityStateChanges() {
        // RED: When ReminderConfigEntity state changes,
        // there should be structured logging of the state change operation
        
        String entityId = "test-reminder-config-001";
        int intervalValue = 30; // 30 seconds
        
        // Act: Update ReminderConfigEntity state
        var result = componentClient.forKeyValueEntity(entityId)
            .method(ReminderConfigEntity::setInterval)
            .invoke(intervalValue);
        
        // Assert: KeyValueEntity state change should be logged
        // We expect a log entry containing: entityType=reminder-config, entityId=test-reminder-config-001, operation=setInterval
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = logAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("KeyValueEntity state changed") 
                          && event.getFormattedMessage().contains("entityType=reminder-config")
                          && event.getFormattedMessage().contains("entityId=" + entityId)
                          && event.getFormattedMessage().contains("operation=setInterval"));
            assertTrue(found, "Expected KeyValueEntity state change log not found in: " + logEvents);
        });
    }
}