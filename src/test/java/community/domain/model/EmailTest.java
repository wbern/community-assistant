package community.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Unit tests for the Email domain model.
 *
 * This is our first test following the Red-Green-Refactor cycle.
 * We're testing the core Email entity for the community board assistant.
 */
public class EmailTest {

    @Test
    public void shouldCreateEmailFromIncomingMessage() {
        // GIVEN
        String messageId = "550e8400-e29b-41d4-a716-446655440000";
        String from = "resident@community.com";
        String subject = "Broken elevator";
        String body = "The elevator in building A is not working";

        // WHEN
        Email email = Email.create(messageId, from, subject, body);

        // THEN
        assertNotNull(email);
        assertEquals(messageId, email.getMessageId());
        assertEquals(from, email.getFrom());
        assertEquals(subject, email.getSubject());
        assertEquals(body, email.getBody());
        assertEquals(Email.Status.UNPROCESSED, email.getStatus());
    }

    @Test
    public void shouldRejectNullMessageId() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create(null, "from@example.com", "Subject", "Body");
        });
    }

    @Test
    public void shouldRejectEmptyMessageId() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("", "from@example.com", "Subject", "Body");
        });
    }

    @Test
    public void shouldRejectNullFrom() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("msg-123", null, "Subject", "Body");
        });
    }

    @Test
    public void shouldRejectEmptyFrom() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("msg-123", "", "Subject", "Body");
        });
    }

    @Test
    public void shouldRejectNullSubject() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("msg-123", "from@example.com", null, "Body");
        });
    }

    @Test
    public void shouldRejectNullBody() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("msg-123", "from@example.com", "Subject", null);
        });
    }

    @Test
    public void shouldAllowEmptySubjectAndBody() {
        // GIVEN
        String messageId = "msg-123";
        String from = "resident@community.com";

        // WHEN
        Email email = Email.create(messageId, from, "", "");

        // THEN
        assertNotNull(email);
        assertEquals("", email.getSubject());
        assertEquals("", email.getBody());
    }

    @Test
    public void shouldStoreReceivedAtTimestamp() {
        // RED PHASE: Test for receivedAt timestamp field needed for cursor-based email sync
        // GIVEN
        String messageId = "msg-cursor-001";
        String from = "resident@community.com";
        String subject = "Test Subject";
        String body = "Test Body";
        Instant receivedAt = Instant.parse("2025-10-20T10:15:30Z");

        // WHEN
        Email email = Email.create(messageId, from, subject, body, receivedAt);

        // THEN
        assertNotNull(email);
        assertEquals(receivedAt, email.getReceivedAt());
    }
}
