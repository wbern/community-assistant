package community.domain;

import java.util.List;

/**
 * Mock implementation of EmailInboxService for testing.
 * Returns predefined test data without external dependencies.
 * Uses fixed message IDs for predictable testing.
 */
public class MockEmailInboxService implements EmailInboxService {

    @Override
    public List<Email> fetchUnprocessedEmails() {
        // Return multiple emails from the SAME sender to test messageId-based entity storage
        Email email1 = Email.create(
            "msg-elevator-001",
            "resident@community.com",
            "Broken elevator",
            "The elevator in building A has been broken for 3 days"
        );

        Email email2 = Email.create(
            "msg-elevator-002",
            "resident@community.com",
            "Elevator still broken",
            "Following up - the elevator is still not fixed"
        );

        return List.of(email1, email2);
    }
}
