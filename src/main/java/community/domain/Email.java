package community.domain;

/**
 * Domain model representing an email received by the community board.
 * Minimal implementation following TDD GREEN phase.
 */
public class Email {

    public enum Status {
        UNPROCESSED
    }

    private final String from;
    private final String subject;
    private final String body;
    private final Status status;

    private Email(String from, String subject, String body, Status status) {
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.status = status;
    }

    public static Email create(String from, String subject, String body) {
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

        return new Email(from, subject, body, Status.UNPROCESSED);
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
}
