package community.domain;

import java.util.List;

/**
 * Mock implementation of EmailInboxService for testing.
 * Returns predefined test data without external dependencies.
 */
public class MockEmailInboxService implements EmailInboxService {

    @Override
    public List<Email> fetchUnprocessedEmails() {
        Email email1 = Email.create(
            "resident@community.com",
            "Broken elevator",
            "The elevator in building A has been broken for 3 days"
        );

        Email email2 = Email.create(
            "tenant@community.com",
            "Noise complaint",
            "Loud music from apartment 3B every night"
        );

        return List.of(email1, email2);
    }
}
