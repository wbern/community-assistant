package community.domain;

import java.util.List;

/**
 * Mock implementation of EmailInboxService for testing.
 * Returns predefined test data without external dependencies.
 */
public class MockEmailInboxService implements EmailInboxService {

    @Override
    public List<Email> fetchUnprocessedEmails() {
        return List.of();
    }
}
