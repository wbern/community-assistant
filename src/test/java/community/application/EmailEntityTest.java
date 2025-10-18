package community.application;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import community.domain.Email;
import org.junit.jupiter.api.Test;

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
}
