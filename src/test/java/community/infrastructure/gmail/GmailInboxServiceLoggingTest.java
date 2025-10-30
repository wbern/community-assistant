package community.infrastructure.gmail;

import akka.javasdk.testkit.TestKitSupport;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import community.infrastructure.mock.MockEmailInboxService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing external service logging for Gmail API operations.
 */
public class GmailInboxServiceLoggingTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    public void setupLogCapture() {
        // Setup log capture for external service operations
        logger = (Logger) LoggerFactory.getLogger("community.infrastructure.gmail.ExternalServiceLogger");
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    public void teardownLogCapture() {
        logger.detachAppender(logAppender);
    }

    @Test
    public void red_shouldLogGmailApiCallsWithAuthenticationAndRateLimit() {
        // RED: When GmailInboxService makes API calls to Gmail,
        // there should be structured logging of the external service interactions
        // including authentication events, API call success/failure, and rate limiting
        
        // Use MockEmailInboxService since we can't easily test real Gmail API in unit tests
        var mockService = new MockEmailInboxService();
        
        // Act: Call Gmail API operation that should trigger external service logging
        var emails = mockService.fetchEmailsSince(Instant.now().minusSeconds(3600));
        
        // Assert: External service logging should capture Gmail API interaction
        // We expect log entries containing: service=gmail, operation=fetchEmailsSince, status=success
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = logAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("External service called") 
                          && event.getFormattedMessage().contains("service=gmail")
                          && event.getFormattedMessage().contains("operation=fetchEmailsSince")
                          && event.getFormattedMessage().contains("status=success"));
            assertTrue(found, "Expected Gmail API external service log not found in: " + logEvents);
        });
    }
}