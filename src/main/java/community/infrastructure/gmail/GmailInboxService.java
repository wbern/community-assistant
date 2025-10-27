package community.infrastructure.gmail;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import community.domain.port.EmailInboxService;
import community.domain.model.Email;

/**
 * Gmail implementation of EmailInboxService.
 * Reads emails from Gmail inbox using Gmail API with service account + domain-wide delegation.
 */
public class GmailInboxService implements EmailInboxService {

    private static final List<String> GMAIL_SCOPES = List.of(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/gmail.send",
        "https://www.googleapis.com/auth/gmail.modify"
    );

    private final Gmail gmailService;
    private final String userEmail;

    /**
     * Initialize Gmail service with default credentials from environment.
     * Expects GOOGLE_APPLICATION_CREDENTIALS environment variable to be set.
     *
     * @param userEmail The email address to impersonate (e.g., board@yourdomain.com)
     * @throws IOException If credentials cannot be loaded
     * @throws GeneralSecurityException If HTTP transport initialization fails
     */
    public GmailInboxService(String userEmail) throws IOException, GeneralSecurityException {
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null || credentialsPath.isEmpty()) {
            throw new IllegalStateException("GOOGLE_APPLICATION_CREDENTIALS environment variable not set");
        }

        this.userEmail = userEmail;
        this.gmailService = createGmailService(credentialsPath, userEmail);
    }

    /**
     * Initialize Gmail service with explicit credentials path.
     *
     * @param credentialsPath Path to service account credentials JSON file
     * @param userEmail The email address to impersonate (e.g., board@yourdomain.com)
     * @throws IOException If credentials cannot be loaded
     * @throws GeneralSecurityException If HTTP transport initialization fails
     */
    public GmailInboxService(String credentialsPath, String userEmail)
            throws IOException, GeneralSecurityException {
        this.userEmail = userEmail;
        this.gmailService = createGmailService(credentialsPath, userEmail);
    }

    /**
     * Create Gmail service with credentials from file.
     */
    private Gmail createGmailService(String credentialsPath, String userEmail)
            throws IOException, GeneralSecurityException {

        // Load service account credentials with domain-wide delegation
        GoogleCredentials credentials = ServiceAccountCredentials
            .fromStream(new FileInputStream(credentialsPath))
            .createScoped(GMAIL_SCOPES)
            .createDelegated(userEmail); // Impersonate the user

        return new Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials)
        )
        .setApplicationName("Community Assistant")
        .build();
    }

    @Override
    public List<Email> fetchUnprocessedEmails() {
        // For Gmail, we'll use fetchEmailsSince with a reasonable default (last 30 days)
        Instant thirtyDaysAgo = Instant.now().minus(java.time.Duration.ofDays(30));
        return fetchEmailsSince(thirtyDaysAgo);
    }

    @Override
    public List<Email> fetchEmailsSince(Instant since) {
        try {
            // Build query: after timestamp + in inbox
            String query = "after:" + since.getEpochSecond();

            // List message IDs matching query
            ListMessagesResponse response = gmailService.users().messages()
                .list(userEmail)
                .setQ(query)
                .setMaxResults(100L) // Limit to 100 most recent
                .execute();

            if (response.getMessages() == null || response.getMessages().isEmpty()) {
                return List.of();
            }

            // Fetch full message details for each ID
            List<Email> emails = response.getMessages().stream()
                .map(msgRef -> {
                    try {
                        return fetchMessageDetails(msgRef.getId());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to fetch message: " + msgRef.getId(), e);
                    }
                })
                .filter(Objects::nonNull)
                .filter(email -> email.getReceivedAt().isAfter(since)) // Filter by timestamp
                .sorted(Comparator.comparing(Email::getReceivedAt)) // Chronological order
                .collect(Collectors.toList());

            return emails;

        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch emails from Gmail", e);
        }
    }

    /**
     * Fetch full message details from Gmail.
     */
    private Email fetchMessageDetails(String messageId) throws IOException {
        Message message = gmailService.users().messages()
            .get(userEmail, messageId)
            .setFormat("full")
            .execute();

        // Extract headers
        String from = getHeader(message, "From");
        String subject = getHeader(message, "Subject");
        String date = getHeader(message, "Date");

        // Parse received timestamp (Gmail internal date in milliseconds)
        Long internalDate = message.getInternalDate();
        Instant receivedAt = internalDate != null
            ? Instant.ofEpochMilli(internalDate)
            : Instant.now();

        // Extract body text
        String body = extractBody(message);

        // Validate required Gmail API data - fail fast instead of creating fake data
        if (from == null || from.isEmpty()) {
            throw new IllegalStateException("Gmail API returned message without From header: " + message.getId());
        }
        if (subject == null) {
            throw new IllegalStateException("Gmail API returned message without Subject header: " + message.getId());
        }
        if (body == null) {
            throw new IllegalStateException("Gmail API returned message without body: " + message.getId());
        }

        return Email.create(message.getId(), from, subject, body, receivedAt);
    }

    /**
     * Get header value from message.
     */
    private String getHeader(Message message, String headerName) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return null;
        }

        return message.getPayload().getHeaders().stream()
            .filter(header -> headerName.equalsIgnoreCase(header.getName()))
            .findFirst()
            .map(MessagePartHeader::getValue)
            .orElse(null);
    }

    /**
     * Extract body text from message payload.
     * Handles both simple text/plain and multipart messages.
     */
    private String extractBody(Message message) {
        if (message.getPayload() == null) {
            return "";
        }

        // Simple text/plain message
        if (message.getPayload().getBody() != null &&
            message.getPayload().getBody().getData() != null) {
            return decodeBase64(message.getPayload().getBody().getData());
        }

        // Multipart message - find text/plain part
        if (message.getPayload().getParts() != null) {
            return message.getPayload().getParts().stream()
                .filter(part -> "text/plain".equals(part.getMimeType()))
                .filter(part -> part.getBody() != null && part.getBody().getData() != null)
                .findFirst()
                .map(part -> decodeBase64(part.getBody().getData()))
                .orElse("");
        }

        return "";
    }

    /**
     * Decode Base64URL encoded string (Gmail uses URL-safe Base64).
     */
    private String decodeBase64(String base64Data) {
        if (base64Data == null) {
            return "";
        }
        byte[] decodedBytes = Base64.getUrlDecoder().decode(base64Data);
        return new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
