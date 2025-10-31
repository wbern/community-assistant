package community.infrastructure.config;

import akka.javasdk.testkit.TestKitSupport;
import community.application.action.TimedActionLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing ServiceConfiguration bootstrap integration for EmailPollingAction.
 */
public class ServiceConfigurationBootstrapTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    public void setupLogCapture() {
        // Setup log capture for TimedAction logging
        logger = (Logger) LoggerFactory.getLogger(TimedActionLogger.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    public void teardownLogCapture() {
        logger.detachAppender(logAppender);
    }

    @Test
    public void shouldDisableEmailPollingInTestEnvironment() {
        // ServiceConfiguration should NOT bootstrap EmailPollingAction in test environment
        // Email polling is disabled in test environments (enabled = false in test application.conf)
        // This test validates that email polling is properly disabled during tests
        
        // Wait a moment for any potential bootstrap activity to complete
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Assert: EmailPollingAction should NOT be scheduled in test environment
        // We should NOT see any log entries for email-polling-action schedule activity
        List<ILoggingEvent> logEvents = logAppender.list;
        boolean found = logEvents.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("TimedAction executed") 
                      && event.getFormattedMessage().contains("actionType=email-polling-action")
                      && event.getFormattedMessage().contains("actionMethod=scheduleNextPoll"));
        assertTrue(!found, "EmailPollingAction should be disabled in test environment, but found scheduling logs: " + logEvents);
    }
}