package community.infrastructure.sheets;

import akka.javasdk.testkit.TestKitSupport;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import community.domain.model.SheetRow;
import community.infrastructure.mock.MockSheetSyncService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing external service logging for Google Sheets API operations.
 */
public class GoogleSheetSyncServiceLoggingTest extends TestKitSupport {

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
    public void red_shouldLogGoogleSheetsApiCallsWithQuotaAndRateLimit() {
        // RED: When MockSheetSyncService makes API calls to Google Sheets,
        // there should be structured logging of the external service interactions
        // including API operations, quota usage, and rate limiting info
        
        // Use MockSheetSyncService since we can't easily test real Google Sheets API in unit tests
        var mockService = new MockSheetSyncService();
        
        // Act: Call Google Sheets API operation that should trigger external service logging
        var testRow = new SheetRow("test-msg-001", "test@example.com", "Test Subject", "Test body", "tag1,tag2", "Test summary", "Building A");
        mockService.upsertRow("test-msg-001", testRow);
        
        // Assert: External service logging should capture Google Sheets API interaction
        // We expect log entries containing: service=sheets, operation=upsertRow, status=success
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = logAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("External service called") 
                          && event.getFormattedMessage().contains("service=sheets")
                          && event.getFormattedMessage().contains("operation=upsertRow")
                          && event.getFormattedMessage().contains("status=success"));
            assertTrue(found, "Expected Google Sheets API external service log not found in: " + logEvents);
        });
    }
}