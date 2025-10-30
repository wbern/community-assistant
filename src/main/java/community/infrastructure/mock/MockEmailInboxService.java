package community.infrastructure.mock;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import community.domain.port.EmailInboxService;
import community.domain.model.Email;
import community.infrastructure.gmail.ExternalServiceLogger;

/**
 * Mock implementation of EmailInboxService for testing.
 * Returns predefined test data without external dependencies.
 * Supports customizable message ID prefix for test isolation.
 * Caches emails by cursor to ensure same cursor returns same emails (for skip tests).
 */
public class MockEmailInboxService implements EmailInboxService {

    private final String basePrefix;
    private final java.util.concurrent.atomic.AtomicInteger callCounter;
    private final java.util.Map<Instant, List<Email>> emailCache;

    /**
     * Default constructor with standard ID prefix.
     * Used by Bootstrap for production mock.
     */
    public MockEmailInboxService() {
        this("msg-elevator");
    }

    /**
     * Constructor with custom ID prefix for test isolation.
     * Caches emails by cursor: same cursor returns same emails, different cursor returns unique emails.
     */
    public MockEmailInboxService(String basePrefix) {
        this.basePrefix = basePrefix;
        this.callCounter = new java.util.concurrent.atomic.AtomicInteger(0);
        this.emailCache = new java.util.concurrent.ConcurrentHashMap<>();
    }

    @Override
    public List<Email> fetchUnprocessedEmails() {
        // Generate unique ID prefix for this call to ensure test isolation
        int callId = callCounter.incrementAndGet();
        String uniquePrefix = basePrefix + "-c" + callId;

        // Return multiple emails from the SAME sender to test messageId-based entity storage
        Email email1 = Email.create(
            uniquePrefix + "-001",
            "resident@community.com",
            "Broken elevator",
            "The elevator in building A has been broken for 3 days"
        );

        Email email2 = Email.create(
            uniquePrefix + "-002",
            "resident@community.com",
            "Elevator still broken",
            "Following up - the elevator is still not fixed"
        );

        return List.of(email1, email2);
    }

    public List<Email> fetchEmailsSince(Instant since) {
        // Log external service call
        ExternalServiceLogger.logServiceCall("gmail", "fetchEmailsSince", "success");
        
        // Cache emails by cursor: same cursor returns same emails (for skip tests),
        // different cursor generates unique emails (for test isolation)
        List<Email> allEmails = emailCache.computeIfAbsent(since, cursor -> {
            // Generate unique ID prefix for this cursor
            int callId = callCounter.incrementAndGet();
            String uniquePrefix = basePrefix + "-c" + callId;

            // Create emails with specific timestamps
            Email email1 = Email.create(
                uniquePrefix + "-001",
                "resident@community.com",
                "Broken elevator",
                "The elevator in building A has been broken for 3 days",
                Instant.parse("2025-10-20T09:00:00Z")
            );

            Email email2 = Email.create(
                uniquePrefix + "-002",
                "resident@community.com",
                "Elevator still broken",
                "Following up - the elevator is still not fixed",
                Instant.parse("2025-10-20T11:00:00Z")
            );

            return List.of(email1, email2);
        });

        // Filter emails by cursor (return emails at or after 'since')
        // Using >= instead of > because entity-level idempotency handles duplicates
        return allEmails.stream()
            .filter(email -> !email.getReceivedAt().isBefore(since))
            .collect(Collectors.toList());
    }
}
