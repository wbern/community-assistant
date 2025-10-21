package community.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MockEmailInboxServiceTest {

    @Test
    public void shouldFetchEmailsSinceGivenCursor() {
        // RED PHASE: Test that fetchEmailsSince only returns emails after cursor timestamp
        // GIVEN
        MockEmailInboxService service = new MockEmailInboxService();
        Instant cursor = Instant.parse("2025-10-20T10:00:00Z");

        // WHEN
        List<Email> emails = service.fetchEmailsSince(cursor);

        // THEN
        assertNotNull(emails);
        // All returned emails should have receivedAt > cursor
        for (Email email : emails) {
            assertTrue(email.getReceivedAt().isAfter(cursor),
                "Email " + email.getMessageId() + " received at " + email.getReceivedAt()
                + " should be after cursor " + cursor);
        }
    }

    @Test
    public void shouldReturnEmptyListWhenNoEmailsAfterCursor() {
        // RED PHASE: Test that fetchEmailsSince returns empty list when cursor is after all emails
        // GIVEN
        MockEmailInboxService service = new MockEmailInboxService();
        Instant futureDate = Instant.parse("2099-12-31T23:59:59Z");

        // WHEN
        List<Email> emails = service.fetchEmailsSince(futureDate);

        // THEN
        assertNotNull(emails);
        assertTrue(emails.isEmpty(), "Should return empty list when cursor is after all emails");
    }

    @Test
    public void shouldReturnAllEmailsWhenCursorIsEpoch() {
        // RED PHASE: Test that fetchEmailsSince with EPOCH cursor returns all emails
        // GIVEN
        MockEmailInboxService service = new MockEmailInboxService();
        Instant epoch = Instant.EPOCH;

        // WHEN
        List<Email> emails = service.fetchEmailsSince(epoch);

        // THEN
        assertNotNull(emails);
        assertFalse(emails.isEmpty(), "Should return emails when cursor is EPOCH");
        // All emails should be after EPOCH (1970)
        for (Email email : emails) {
            assertTrue(email.getReceivedAt().isAfter(epoch));
        }
    }
}
