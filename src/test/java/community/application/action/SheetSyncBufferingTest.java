package community.application.action;

import community.infrastructure.config.ServiceConfiguration;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.model.Email;
import community.infrastructure.mock.MockSheetSyncService;
import community.application.entity.EmailEntity;
import community.application.entity.SheetSyncBufferEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Test for buffer-based batching of Google Sheets sync.
 * RED PHASE: Testing that events accumulate in buffer and are flushed as a batch.
 */
public class SheetSyncBufferingTest extends TestKitSupport {

    private MockSheetSyncService mockSheetService;

    @Override
    protected TestKit.Settings testKitSettings() {
        mockSheetService = ServiceConfiguration.getMockSheetService();
        mockSheetService.clear();

        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(community.application.entity.EmailEntity.class);
    }

    @Test
    public void shouldAccumulateEventsInBufferAndFlushAsBatch() {
        // RED: Testing that multiple events are batched into ONE sync call
        // This reduces Google Sheets API calls from N to 1 per flush

        // GIVEN: 3 EmailReceived events published
        Email email1 = Email.create("batch-msg-001", "batch1@test.com", "Subject 1", "Body 1");
        Email email2 = Email.create("batch-msg-002", "batch2@test.com", "Subject 2", "Body 2");
        Email email3 = Email.create("batch-msg-003", "batch3@test.com", "Subject 3", "Body 3");

        EmailEntity.Event.EmailReceived event1 = new EmailEntity.Event.EmailReceived(email1);
        EmailEntity.Event.EmailReceived event2 = new EmailEntity.Event.EmailReceived(email2);
        EmailEntity.Event.EmailReceived event3 = new EmailEntity.Event.EmailReceived(email3);

        // WHEN: Events are published to the stream
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class).publish(event1, "batch-msg-001");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class).publish(event2, "batch-msg-002");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class).publish(event3, "batch-msg-003");

        // THEN: Events should NOT be immediately synced (they're buffered)
        // Wait a bit to ensure consumer has processed
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify nothing synced yet (batch count should be 0)
        assertEquals(0, mockSheetService.getBatchCallCount(),
            "Events should be buffered, not immediately synced");

        // WHEN: We manually trigger flush by reading and syncing the buffer
        // (In production, this would be done by SheetSyncFlushAction periodically)
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();

                assertEquals(3, bufferSize, "Buffer should contain 3 rows");
            });

        // Manually flush buffer to sheets (simulating what TimedAction does)
        var rows = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();

        mockSheetService.batchUpsertRows(rows);

        // THEN: Should result in exactly ONE batch call with 3 rows
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertEquals(1, mockSheetService.getBatchCallCount(),
                    "Expected exactly 1 batch call after flush");

                assertEquals(3, mockSheetService.getLastBatchSize(),
                    "Expected batch to contain 3 rows");

                // Verify all rows are present in sheet
                assertNotNull(mockSheetService.getRow("batch-msg-001"));
                assertNotNull(mockSheetService.getRow("batch-msg-002"));
                assertNotNull(mockSheetService.getRow("batch-msg-003"));
            });
    }
}
