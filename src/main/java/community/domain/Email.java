package community.domain;

import java.time.Instant;

/**
 * Domain model representing an email received by the community board.
 * Minimal implementation following TDD GREEN phase.
 */
public class Email {

    public enum Status {
        UNPROCESSED
    }

    private final String messageId;
    private final String from;
    private final String subject;
    private final String body;
    private final Status status;
    private final Instant receivedAt;

    private Email(String messageId, String from, String subject, String body, Status status, Instant receivedAt) {
        this.messageId = messageId;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public static Email create(String messageId, String from, String subject, String body) {
        // Use current time as default for backward compatibility
        return create(messageId, from, subject, body, Instant.now());
    }

    public static Email create(String messageId, String from, String subject, String body, Instant receivedAt) {
        if (messageId == null) {
            throw new IllegalArgumentException();
        }
        if (messageId.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (from == null) {
            throw new IllegalArgumentException();
        }
        if (from.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (subject == null) {
            throw new IllegalArgumentException();
        }
        if (body == null) {
            throw new IllegalArgumentException();
        }

        return new Email(messageId, from, subject, body, Status.UNPROCESSED, receivedAt);
    }

    public String getMessageId() {
        return messageId;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
