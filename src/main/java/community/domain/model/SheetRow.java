package community.domain.model;

import java.util.stream.Collectors;

/**
 * Domain model representing a row in Google Sheets.
 * Created from Email and EmailTags for syncing to spreadsheet.
 */
public record SheetRow(
    String messageId,
    String from,
    String subject,
    String body,
    String tags,
    String summary,
    String location
) {
    /**
     * Create a sheet row from an email only (without tags).
     */
    public static SheetRow fromEmail(Email email) {
        return new SheetRow(
            email.getMessageId(),
            email.getFrom(),
            email.getSubject(),
            email.getBody(),
            null,  // No tags yet
            null,  // No summary yet
            null   // No location yet
        );
    }

    /**
     * Create a sheet row from email and tags combined.
     */
    public static SheetRow fromEmailAndTags(Email email, EmailTags tags) {
        if (tags == null) {
            return fromEmail(email);
        }

        // Convert Set<String> tags to comma-separated string
        String tagsString = tags.tags().stream()
            .sorted()  // Sort for consistency
            .collect(Collectors.joining(", "));

        return new SheetRow(
            email.getMessageId(),
            email.getFrom(),
            email.getSubject(),
            email.getBody(),
            tagsString,
            tags.summary(),
            tags.location()
        );
    }

    /**
     * Create a sheet row with only tags (for partial updates).
     * Email fields will be null, allowing the service to preserve existing values.
     */
    public static SheetRow onlyTags(String messageId, EmailTags tags) {
        // Convert Set<String> tags to comma-separated string
        String tagsString = tags.tags().stream()
            .sorted()  // Sort for consistency
            .collect(Collectors.joining(", "));

        return new SheetRow(
            messageId,
            null,  // Will be preserved by merge
            null,  // Will be preserved by merge
            null,  // Will be preserved by merge
            tagsString,
            tags.summary(),
            tags.location()
        );
    }
}
