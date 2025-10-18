package community.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmailInboxService interface contract.
 * RED phase: Testing the interface definition exists.
 */
public class EmailInboxServiceTest {

    @Test
    public void shouldFetchUnprocessedEmails() {
        EmailInboxService service = new MockEmailInboxService();

        List<Email> emails = service.fetchUnprocessedEmails();

        assertNotNull(emails);
    }

    @Test
    public void shouldReturnTestEmailsFromMockService() {
        MockEmailInboxService mockService = new MockEmailInboxService();

        List<Email> emails = mockService.fetchUnprocessedEmails();

        assertFalse(emails.isEmpty());
        assertEquals(2, emails.size());
        assertEquals("resident@community.com", emails.get(0).getFrom());
        assertEquals("Broken elevator", emails.get(0).getSubject());
    }
}
