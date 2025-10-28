package community.application.entity;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmailEntity using Akka TestKit.
 * RED phase: Testing EventSourced Entity for safe email persistence.
 */
public class EmailEntityTest {

    @Test
    public void shouldReceiveAndStoreEmailAsEvent() {
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        Email email = Email.create(
            "msg-123",
            "resident@community.com",
            "Broken elevator",
            "The elevator is broken"
        );

        EventSourcedResult<String> result = testKit.method(EmailEntity::receiveEmail)
            .invoke(email);

        assertTrue(result.isReply());
        assertEquals("Email received", result.getReply());

        // Verify event was emitted
        assertEquals(1, result.getAllEvents().size());
        assertTrue(result.getAllEvents().get(0) instanceof EmailEntity.Event.EmailReceived);

        EmailEntity.Event.EmailReceived event =
            (EmailEntity.Event.EmailReceived) result.getAllEvents().get(0);
        assertEquals("resident@community.com", event.email().getFrom());
    }

    @Test
    public void shouldStoreEmailInStateAfterReceiving() {
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        Email email = Email.create(
            "msg-456",
            "resident@community.com",
            "Broken elevator",
            "The elevator is broken"
        );

        testKit.method(EmailEntity::receiveEmail).invoke(email);

        // Verify state now contains the email
        EmailEntity.State state = testKit.getState();
        assertNotNull(state.email());
        assertEquals("resident@community.com", state.email().getFrom());
        assertEquals("Broken elevator", state.email().getSubject());
    }

    @Test
    public void shouldStoreTagsAfterReceivingEmail() {
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        // First receive an email
        Email email = Email.create(
            "msg-789",
            "resident@community.com",
            "Broken elevator",
            "The elevator is broken"
        );
        testKit.method(EmailEntity::receiveEmail).invoke(email);

        // Then add tags
        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance", "elevator"),
            "Elevator broken in Building A",
            "Building A"
        );
        EventSourcedResult<String> result = testKit.method(EmailEntity::addTags).invoke(tags);

        // Verify tags event was emitted
        assertTrue(result.isReply());
        assertEquals(1, result.getAllEvents().size());
        assertTrue(result.getAllEvents().get(0) instanceof EmailEntity.Event.TagsGenerated);

        // Verify tags are in state
        EmailEntity.State state = testKit.getState();
        assertNotNull(state.tags());
        assertEquals(3, state.tags().tags().size());
        assertTrue(state.tags().tags().contains("urgent"));
        assertEquals("Building A", state.tags().location());
    }

    @Test
    public void shouldRetrieveTagsFromEntity() {
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        // Receive email and add tags
        Email email = Email.create(
            "msg-101",
            "resident@community.com",
            "Broken elevator",
            "The elevator is broken"
        );
        testKit.method(EmailEntity::receiveEmail).invoke(email);

        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance"),
            "Urgent maintenance issue",
            null
        );
        testKit.method(EmailEntity::addTags).invoke(tags);

        // Query tags
        var retrievedTags = testKit.method(EmailEntity::getTags).invoke().getReply();

        assertNotNull(retrievedTags);
        assertEquals(2, retrievedTags.tags().size());
        assertTrue(retrievedTags.tags().contains("urgent"));
        assertTrue(retrievedTags.tags().contains("maintenance"));
    }

    @Test
    public void shouldBeIdempotentWhenReceivingSameEmailTwice() {
        // RED: Test that receiving the same email twice doesn't create duplicate events
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        Email email = Email.create(
            "msg-duplicate-test",
            "resident@community.com",
            "Broken elevator",
            "The elevator is broken"
        );

        // First receive
        EventSourcedResult<String> result1 = testKit.method(EmailEntity::receiveEmail)
            .invoke(email);
        assertTrue(result1.isReply());
        assertEquals(1, result1.getAllEvents().size(), "First call should emit 1 event");

        // Second receive (same email) - should be idempotent
        EventSourcedResult<String> result2 = testKit.method(EmailEntity::receiveEmail)
            .invoke(email);
        assertTrue(result2.isReply());
        assertEquals(0, result2.getAllEvents().size(),
            "Second call should not emit event (idempotent)");

        // State should still have the email
        EmailEntity.State state = testKit.getState();
        assertNotNull(state.email());
        assertEquals("msg-duplicate-test", state.email().getMessageId());
    }

    @Test
    public void shouldBeIdempotentWhenAddingSameTagsTwice() {
        // RED: Test that adding tags twice doesn't create duplicate TagsGenerated events
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        // Receive email first
        Email email = Email.create(
            "msg-tags-idempotent",
            "resident@community.com",
            "Broken elevator",
            "The elevator is broken"
        );
        testKit.method(EmailEntity::receiveEmail).invoke(email);

        // Add tags first time
        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance"),
            "Urgent issue",
            "Building A"
        );
        EventSourcedResult<String> result1 = testKit.method(EmailEntity::addTags).invoke(tags);
        assertTrue(result1.isReply());
        assertEquals(1, result1.getAllEvents().size(), "First addTags should emit 1 event");

        // Add same tags again - should be idempotent
        EventSourcedResult<String> result2 = testKit.method(EmailEntity::addTags).invoke(tags);
        assertTrue(result2.isReply());
        assertEquals(0, result2.getAllEvents().size(),
            "Second addTags should not emit event (idempotent)");

        // State should still have the tags
        EmailEntity.State state = testKit.getState();
        assertNotNull(state.tags());
        assertEquals(2, state.tags().tags().size());
        assertTrue(state.tags().tags().contains("urgent"));
    }

    @Test
    public void shouldMarkEmailAsAddressed() {
        // Unit test for markAsAddressed command
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        // Receive email first
        Email email = Email.create(
            "email-005",
            "resident@building.com",
            "Water leak in basement",
            "There's a water leak in the basement storage area near unit 3A. Please investigate."
        );
        testKit.method(EmailEntity::receiveEmail).invoke(email);

        // Mark email as addressed
        EventSourcedResult<String> result = testKit.method(EmailEntity::markAsAddressed).invoke();

        // Verify response
        assertTrue(result.isReply());
        assertEquals("Marked as addressed", result.getReply());

        // Verify event was emitted
        assertEquals(1, result.getAllEvents().size());
        assertTrue(result.getAllEvents().get(0) instanceof EmailEntity.Event.InquiryAddressed);

        // Verify state was updated
        assertEquals(Email.Status.ADDRESSED, testKit.getState().email().getStatus());
    }

    @Test
    public void shouldBeIdempotentWhenMarkingAsAddressedTwice() {
        // Test that marking as addressed twice is idempotent
        EventSourcedTestKit<EmailEntity.State, EmailEntity.Event, EmailEntity> testKit =
            EventSourcedTestKit.of(EmailEntity::new);

        // Receive email first
        Email email = Email.create(
            "email-idempotent-addressed",
            "resident@building.com",
            "Test subject",
            "Test body"
        );
        testKit.method(EmailEntity::receiveEmail).invoke(email);

        // First mark as addressed
        EventSourcedResult<String> result1 = testKit.method(EmailEntity::markAsAddressed).invoke();
        assertTrue(result1.isReply());
        assertEquals(1, result1.getAllEvents().size(), "First call should emit 1 event");

        // Second mark as addressed - should be idempotent
        EventSourcedResult<String> result2 = testKit.method(EmailEntity::markAsAddressed).invoke();
        assertTrue(result2.isReply());
        assertEquals(0, result2.getAllEvents().size(),
            "Second call should not emit event (idempotent)");
        assertEquals("Already marked as addressed", result2.getReply());

        // State should still be ADDRESSED
        assertEquals(Email.Status.ADDRESSED, testKit.getState().email().getStatus());
    }
}
