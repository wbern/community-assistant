package community.application;

import com.example.Bootstrap;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.Email;
import community.domain.EmailTags;
import community.domain.MockSheetSyncService;
import community.domain.SheetRow;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Unit tests for GoogleSheetConsumer.
 * Updated: Consumer writes to buffer, which is periodically flushed to sheets.
 */
public class GoogleSheetConsumerTest extends TestKitSupport {

    // Access the singleton mock from Bootstrap
    private MockSheetSyncService mockSheetService;

    @Override
    protected TestKit.Settings testKitSettings() {
        mockSheetService = Bootstrap.getMockSheetService();
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    @BeforeEach
    public void clearState() {
        // Clear mock service and buffer before each test
        mockSheetService.clear();
        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
    }

    @Test
    public void shouldWriteEmailToBufferWhenEmailReceivedEventPublished() {
        // Updated: Consumer now writes to buffer instead of directly to sheets

        // GIVEN: An EmailReceived event
        Email email = Email.create(
            "msg-test-001",
            "test@community.com",
            "Test Subject",
            "Test Body"
        );
        EmailEntity.Event.EmailReceived event = new EmailEntity.Event.EmailReceived(email);

        // WHEN: Event is published to EmailEntity stream
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(event, "msg-test-001");

        // THEN: Event should be written to buffer (not immediately to sheets)
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();

                assertEquals(1, bufferSize, "Buffer should contain 1 row");
            });

        // WHEN: Buffer is flushed
        var rows = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows);

        // THEN: Row should be in sheets with correct data
        SheetRow row = mockSheetService.getRow("msg-test-001");
        assertNotNull(row, "Email should be synced to sheet after flush");
        assertEquals("msg-test-001", row.messageId());
        assertEquals("test@community.com", row.from());
        assertEquals("Test Subject", row.subject());
        assertEquals("Test Body", row.body());
        assertNull(row.tags(), "Tags should be null for EmailReceived event");
    }

    @Test
    public void shouldUpdateRowWhenTagsGeneratedEventPublished() {
        // Updated: Both events go to buffer, then flushed together

        // GIVEN: Email event
        Email email = Email.create(
            "msg-test-002",
            "test2@community.com",
            "Another Test",
            "Another Body"
        );
        EmailEntity.Event.EmailReceived emailEvent = new EmailEntity.Event.EmailReceived(email);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(emailEvent, "msg-test-002");

        // Wait for buffer to contain email
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();
                assertTrue(bufferSize >= 1, "Buffer should contain at least 1 row");
            });

        // Flush first event
        var rows1 = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows1);

        // Verify email is in sheet
        assertNotNull(mockSheetService.getRow("msg-test-002"));

        // WHEN: TagsGenerated event is published
        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance"),
            "Urgent maintenance needed",
            "Building B"
        );
        EmailEntity.Event.TagsGenerated tagsEvent = new EmailEntity.Event.TagsGenerated(tags);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(tagsEvent, "msg-test-002");

        // Wait for tags to be in buffer
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();
                assertEquals(1, bufferSize, "Buffer should contain tags update");
            });

        // Flush tags event
        var rows2 = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows2);

        // THEN: Row should be updated with tags
        SheetRow row = mockSheetService.getRow("msg-test-002");
        assertNotNull(row);
        assertEquals("maintenance, urgent", row.tags());  // Sorted alphabetically
        assertEquals("Urgent maintenance needed", row.summary());
        assertEquals("Building B", row.location());
    }

    @Test
    public void shouldBeIdempotentWhenReplayingEvents() {
        // Updated: Events accumulate in buffer, flush produces same result

        // GIVEN: Email and tags events
        Email email = Email.create(
            "msg-replay",
            "replay@test.com",
            "Replay Test",
            "Testing idempotency"
        );
        EmailEntity.Event.EmailReceived emailReceived = new EmailEntity.Event.EmailReceived(email);

        EmailTags tags = EmailTags.create(
            Set.of("replay", "test"),
            "Replay summary",
            "Replay location"
        );
        EmailEntity.Event.TagsGenerated tagsGenerated = new EmailEntity.Event.TagsGenerated(tags);

        // WHEN: First playthrough - publish both events
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(emailReceived, "msg-replay");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(tagsGenerated, "msg-replay");

        // Wait for buffer to contain both events
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();
                assertEquals(2, bufferSize, "Buffer should contain 2 events");
            });

        // Flush first time
        var rows1 = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows1);

        SheetRow firstResult = mockSheetService.getRow("msg-replay");
        assertNotNull(firstResult);
        assertNotNull(firstResult.tags(), "Tags should be set after both events");

        // WHEN: Replay the same events (simulating event replay for rebuilding sheet)
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(emailReceived, "msg-replay");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(tagsGenerated, "msg-replay");

        // Wait for buffer to contain replayed events
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();
                assertEquals(2, bufferSize, "Buffer should contain 2 replayed events");
            });

        // Flush second time
        var rows2 = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows2);

        // THEN: Result should be identical (idempotent)
        SheetRow row = mockSheetService.getRow("msg-replay");
        assertEquals(firstResult, row, "Replaying events should produce identical result");
        // Verify all fields match
        assertEquals("replay@test.com", row.from());
        assertEquals("Replay Test", row.subject());
        assertEquals("Testing idempotency", row.body());
        assertEquals("replay, test", row.tags());
        assertEquals("Replay summary", row.summary());
        assertEquals("Replay location", row.location());
    }

    @Test
    public void shouldAccumulateEventsInBufferBeforeFlushing() {
        // Updated test for buffer-based batching architecture
        // Consumer writes to buffer, TimedAction flushes periodically

        // GIVEN: 3 EmailReceived events published rapidly
        Email email1 = Email.create("msg-batch-001", "batch1@test.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-batch-002", "batch2@test.com", "Subject 2", "Body 2");
        Email email3 = Email.create("msg-batch-003", "batch3@test.com", "Subject 3", "Body 3");

        EmailEntity.Event.EmailReceived event1 = new EmailEntity.Event.EmailReceived(email1);
        EmailEntity.Event.EmailReceived event2 = new EmailEntity.Event.EmailReceived(email2);
        EmailEntity.Event.EmailReceived event3 = new EmailEntity.Event.EmailReceived(email3);

        // WHEN: Events are published to the stream (Consumer writes to buffer)
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class).publish(event1, "msg-batch-001");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class).publish(event2, "msg-batch-002");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class).publish(event3, "msg-batch-003");

        // THEN: Events should accumulate in buffer (NOT immediately synced to sheets)
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                // Verify buffer contains 3 rows
                Integer bufferSize = componentClient
                    .forKeyValueEntity("global-buffer")
                    .method(SheetSyncBufferEntity::getBufferSize)
                    .invoke();

                assertEquals(3, bufferSize, "Buffer should contain 3 rows");

                // Verify NO batch calls made yet (events are buffered, not synced)
                assertEquals(0, mockSheetService.getBatchCallCount(),
                    "No sheets sync should occur yet - events are buffered");
            });

        // WHEN: Buffer is manually flushed (simulating TimedAction)
        var rows = componentClient
            .forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();

        mockSheetService.batchUpsertRows(rows);

        // THEN: Should result in ONE batch call with 3 rows
        assertEquals(1, mockSheetService.getBatchCallCount(),
            "Expected exactly 1 batch call after flush");

        assertEquals(3, mockSheetService.getLastBatchSize(),
            "Expected batch size of 3");

        // Verify all rows are present in the sheet
        assertNotNull(mockSheetService.getRow("msg-batch-001"));
        assertNotNull(mockSheetService.getRow("msg-batch-002"));
        assertNotNull(mockSheetService.getRow("msg-batch-003"));
    }
}
