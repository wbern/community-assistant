package community.application;

import community.domain.Email;
import community.domain.GmailInboxService;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for GmailInboxService.
 * Tests real Gmail API integration for reading emails.
 *
 * Prerequisites:
 * - GOOGLE_APPLICATION_CREDENTIALS: ${project.basedir}/credentials.gitignore.json
 * - GMAIL_USER_EMAIL: email address to impersonate (e.g., board@yourdomain.com)
 * - Gmail API enabled in Google Cloud Console
 * - Domain-wide delegation configured for service account
 *
 * RED phase: This test will fail until GmailInboxService is implemented.
 */
public class GmailInboxServiceIntegrationTest {

    private GmailInboxService gmailService;

    @BeforeAll
    static void loadEnv() {
        // Load .env file if it exists
        try {
            Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            // Ignore - environment variables may already be set
        }
    }

    @BeforeEach
    void setUp() {
        // Initialize service with credentials (check both env and system properties)
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null) {
            credentialsPath = System.getProperty("GOOGLE_APPLICATION_CREDENTIALS");
        }
        if (credentialsPath == null || credentialsPath.isEmpty()) {
            fail("GOOGLE_APPLICATION_CREDENTIALS not set (check .env file or environment)");
        }

        String userEmail = System.getenv("GMAIL_USER_EMAIL");
        if (userEmail == null) {
            userEmail = System.getProperty("GMAIL_USER_EMAIL");
        }
        if (userEmail == null || userEmail.isEmpty()) {
            fail("GMAIL_USER_EMAIL not set (check .env file or environment)");
        }

        try {
            gmailService = new GmailInboxService(credentialsPath, userEmail);
        } catch (Exception e) {
            fail("Failed to initialize GmailInboxService: " + e.getMessage(), e);
        }
    }

    @Test
    void shouldFetchEmailsFromRealGmail() {
        // GIVEN: A cursor timestamp (fetch emails from last 7 days)
        Instant sevenDaysAgo = Instant.now().minus(java.time.Duration.ofDays(7));

        // WHEN: Fetching emails since cursor
        List<Email> emails = gmailService.fetchEmailsSince(sevenDaysAgo);

        // THEN: Should return list (may be empty if no emails in last 7 days)
        assertNotNull(emails, "Email list should not be null");

        // If emails exist, verify structure
        if (!emails.isEmpty()) {
            Email first = emails.get(0);
            assertNotNull(first.getMessageId(), "Message ID should not be null");
            assertNotNull(first.getFrom(), "From address should not be null");
            assertNotNull(first.getSubject(), "Subject should not be null");
            assertNotNull(first.getBody(), "Body should not be null");
            assertNotNull(first.getReceivedAt(), "Received timestamp should not be null");

            System.out.println("✅ Fetched " + emails.size() + " emails from Gmail");
            System.out.println("First email: " + first.getSubject() + " from " + first.getFrom());
        } else {
            System.out.println("ℹ️  No emails found in last 7 days");
        }
    }

    @Test
    void shouldRespectCursorTimestamp() {
        // GIVEN: A cursor in the future (should return no emails)
        Instant tomorrow = Instant.now().plus(java.time.Duration.ofDays(1));

        // WHEN: Fetching emails since tomorrow
        List<Email> emails = gmailService.fetchEmailsSince(tomorrow);

        // THEN: Should return empty list (no emails in the future)
        assertNotNull(emails);
        assertTrue(emails.isEmpty(), "Should not return emails from the future");
    }

    @Test
    void shouldReturnEmailsInChronologicalOrder() {
        // GIVEN: A cursor timestamp
        Instant thirtyDaysAgo = Instant.now().minus(java.time.Duration.ofDays(30));

        // WHEN: Fetching emails
        List<Email> emails = gmailService.fetchEmailsSince(thirtyDaysAgo);

        // THEN: Emails should be in chronological order (oldest first)
        if (emails.size() >= 2) {
            for (int i = 0; i < emails.size() - 1; i++) {
                Instant current = emails.get(i).getReceivedAt();
                Instant next = emails.get(i + 1).getReceivedAt();
                assertTrue(
                    current.isBefore(next) || current.equals(next),
                    "Emails should be in chronological order: " +
                    "email[" + i + "]=" + current + " should be <= email[" + (i+1) + "]=" + next
                );
            }
            System.out.println("✅ " + emails.size() + " emails in correct chronological order");
        }
    }
}
