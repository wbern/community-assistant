package community.application;

import com.example.Bootstrap;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.Email;
import community.domain.MockSheetSyncService;
import community.domain.SheetRow;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke test for buffer-based Google Sheets sync.
 *
 * RED PHASE: Verifies that an event flows from EmailEntity → Consumer → Buffer → TimedAction → Sheet
 * using the REAL timer (periodic flush every 10 seconds).
 *
 * This test publishes a single event and waits for the real SheetSyncFlushAction timer
 * to flush it to Google Sheets.
 */
public class EndToEndSheetSyncIntegrationTest extends TestKitSupport {

    private MockSheetSyncService mockSheetService;

    @Override
    protected TestKit.Settings testKitSettings() {
        mockSheetService = Bootstrap.getMockSheetService();
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    @BeforeEach
    public void clearState() {
        // Clear mock service and buffer before test
        mockSheetService.clear();
        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
    }

    @Test
    public void shouldSyncEventToSheetViaRealTimerFlush() {
        // RED PHASE: This test will FAIL because the TimedAction timer is not started automatically
        // The SheetSyncFlushAction needs to be bootstrapped to start its periodic execution

        // GIVEN: A single email event
        Email email = Email.create(
            "smoke-test-001",
            "smoketest@community.com",
            "End-to-End Test",
            "Testing the full sync pipeline"
        );
        EmailEntity.Event.EmailReceived event = new EmailEntity.Event.EmailReceived(email);

        // WHEN: Event is published (should flow to Consumer → Buffer)
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(event, "smoke-test-001");

        // THEN: Event should accumulate in buffer first
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();
                assertEquals(1, bufferSize, "Event should be in buffer");
            });

        // AND: After waiting for timer (10s flush interval), event should be in Google Sheets
        // This is where the test will FAIL - timer doesn't auto-start
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)  // 10s flush + 5s buffer
            .pollInterval(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SheetRow row = mockSheetService.getRow("smoke-test-001");
                assertNotNull(row, "Event should be synced to sheet after timer flush");
                assertEquals("smoketest@community.com", row.from());
                assertEquals("End-to-End Test", row.subject());
                assertEquals("Testing the full sync pipeline", row.body());

                // Verify it was a batch call (not manual flush)
                assertTrue(mockSheetService.getBatchCallCount() >= 1,
                    "Should have at least 1 batch call from timer");
            });
    }
}
