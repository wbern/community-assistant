package community.application;

import com.example.Bootstrap;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.Email;
import community.domain.EmailTags;
import community.domain.MockSheetSyncService;
import community.domain.SheetRow;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Unit tests for GoogleSheetConsumer.
 * RED phase: Testing Consumer listens to EmailEntity events and syncs to sheets.
 */
public class GoogleSheetConsumerTest extends TestKitSupport {

    // Access the singleton mock from Bootstrap
    private MockSheetSyncService mockSheetService;

    @Override
    protected TestKit.Settings testKitSettings() {
        // Clear the mock before each test
        mockSheetService = Bootstrap.getMockSheetService();
        mockSheetService.clear();

        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    @Test
    public void shouldSyncEmailToSheetWhenEmailReceivedEventPublished() {
        // GIVEN: An EmailReceived event
        Email email = Email.create(
            "msg-test-001",
            "test@community.com",
            "Test Subject",
            "Test Body"
        );
        EmailEntity.Event.EmailReceived event = new EmailEntity.Event.EmailReceived(email);

        // WHEN: Event is published to EmailEntity stream (simulating entity emitting event)
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(event, "msg-test-001");  // messageId as entity ID

        // THEN: Consumer should sync to sheet (eventually)
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SheetRow row = mockSheetService.getRow("msg-test-001");
                assertNotNull(row, "Email should be synced to sheet");
                assertEquals("msg-test-001", row.messageId());
                assertEquals("test@community.com", row.from());
                assertEquals("Test Subject", row.subject());
                assertEquals("Test Body", row.body());
                assertNull(row.tags(), "Tags should be null for EmailReceived event");
            });
    }

    @Test
    public void shouldUpdateRowWhenTagsGeneratedEventPublished() {
        // GIVEN: Email already exists in sheet
        Email email = Email.create(
            "msg-test-002",
            "test2@community.com",
            "Another Test",
            "Another Body"
        );
        EmailEntity.Event.EmailReceived emailEvent = new EmailEntity.Event.EmailReceived(email);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(emailEvent, "msg-test-002");

        // Wait for initial sync
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(mockSheetService.getRow("msg-test-002")));

        // WHEN: TagsGenerated event is published
        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance"),
            "Urgent maintenance needed",
            "Building B"
        );
        EmailEntity.Event.TagsGenerated tagsEvent = new EmailEntity.Event.TagsGenerated(tags);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(tagsEvent, "msg-test-002");

        // THEN: Row should be updated with tags
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SheetRow row = mockSheetService.getRow("msg-test-002");
                assertNotNull(row);
                assertEquals("maintenance, urgent", row.tags());  // Sorted alphabetically
                assertEquals("Urgent maintenance needed", row.summary());
                assertEquals("Building B", row.location());
            });
    }

    @Test
    public void shouldBeIdempotentWhenReplayingEvents() {
        // Test that replaying events produces the same result
        // This is critical for event sourcing - sheet must be rebuildable from events

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

        // Wait for consumer to process
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SheetRow row = mockSheetService.getRow("msg-replay");
                assertNotNull(row);
                assertNotNull(row.tags(), "Tags should be set after both events");
            });

        SheetRow firstResult = mockSheetService.getRow("msg-replay");

        // WHEN: Replay the same events (simulating event replay for rebuilding sheet)
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(emailReceived, "msg-replay");
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(tagsGenerated, "msg-replay");

        // THEN: Result should be identical (idempotent)
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SheetRow row = mockSheetService.getRow("msg-replay");
                assertEquals(firstResult, row, "Replaying events should produce identical result");
                // Verify all fields match
                assertEquals("replay@test.com", row.from());
                assertEquals("Replay Test", row.subject());
                assertEquals("Testing idempotency", row.body());
                assertEquals("replay, test", row.tags());
                assertEquals("Replay summary", row.summary());
                assertEquals("Replay location", row.location());
            });
    }
}
