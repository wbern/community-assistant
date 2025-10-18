package community.domain;

import org.junit.jupiter.api.Test;
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
        String from = "resident@community.com";
        String subject = "Broken elevator";
        String body = "The elevator in building A is not working";

        // WHEN
        Email email = Email.create(from, subject, body);

        // THEN
        assertNotNull(email);
        assertEquals(from, email.getFrom());
        assertEquals(subject, email.getSubject());
        assertEquals(body, email.getBody());
        assertEquals(Email.Status.UNPROCESSED, email.getStatus());
    }

    @Test
    public void shouldRejectNullFrom() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create(null, "Subject", "Body");
        });
    }

    @Test
    public void shouldRejectEmptyFrom() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("", "Subject", "Body");
        });
    }

    @Test
    public void shouldRejectNullSubject() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("from@example.com", null, "Body");
        });
    }

    @Test
    public void shouldRejectNullBody() {
        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            Email.create("from@example.com", "Subject", null);
        });
    }

    @Test
    public void shouldAllowEmptySubjectAndBody() {
        // GIVEN
        String from = "resident@community.com";

        // WHEN
        Email email = Email.create(from, "", "");

        // THEN
        assertNotNull(email);
        assertEquals("", email.getSubject());
        assertEquals("", email.getBody());
    }
}
