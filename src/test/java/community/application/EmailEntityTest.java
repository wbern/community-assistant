package community.application;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import community.domain.Email;
import community.domain.EmailTags;
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

        EventSourcedResult<String> result = testKit.call(e -> e.receiveEmail(email));

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

        testKit.call(e -> e.receiveEmail(email));

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
        testKit.call(e -> e.receiveEmail(email));

        // Then add tags
        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance", "elevator"),
            "Elevator broken in Building A",
            "Building A"
        );
        EventSourcedResult<String> result = testKit.call(e -> e.addTags(tags));

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
        testKit.call(e -> e.receiveEmail(email));

        EmailTags tags = EmailTags.create(
            Set.of("urgent", "maintenance"),
            "Urgent maintenance issue",
            null
        );
        testKit.call(e -> e.addTags(tags));

        // Query tags
        var retrievedTags = testKit.call(e -> e.getTags()).getReply();

        assertNotNull(retrievedTags);
        assertEquals(2, retrievedTags.tags().size());
        assertTrue(retrievedTags.tags().contains("urgent"));
        assertTrue(retrievedTags.tags().contains("maintenance"));
    }
}
