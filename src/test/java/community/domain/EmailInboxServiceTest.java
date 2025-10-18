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
}
