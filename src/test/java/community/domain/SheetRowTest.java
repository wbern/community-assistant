package community.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Unit tests for SheetRow domain model.
 * RED phase: Testing row representation for Google Sheets sync.
 */
public class SheetRowTest {

    @Test
    public void shouldCreateSheetRowFromEmail() {
        // GIVEN: An email without tags
        Email email = Email.create(
            "msg-123",
            "resident@community.com",
            "Broken elevator",
            "The elevator in building A is not working"
        );

        // WHEN: Creating a sheet row from email only
        SheetRow row = SheetRow.fromEmail(email);

        // THEN: Row should contain email data
        assertNotNull(row);
        assertEquals("msg-123", row.messageId());
        assertEquals("resident@community.com", row.from());
        assertEquals("Broken elevator", row.subject());
        assertEquals("The elevator in building A is not working", row.body());
        assertNull(row.tags(), "Tags should be null when not provided");
        assertNull(row.summary(), "Summary should be null when not provided");
        assertNull(row.location(), "Location should be null when not provided");
    }

    @Test
    public void shouldCreateSheetRowFromEmailWithTags() {
        // GIVEN: An email and tags
        Email email = Email.create(
            "msg-456",
            "tenant@community.com",
            "Noise complaint",
            "Loud music every night"
        );

        EmailTags tags = EmailTags.create(
            Set.of("urgent", "noise", "complaint"),
            "Noise complaint from apartment 3B",
            "Building C, Apartment 3B"
        );

        // WHEN: Creating a sheet row with both email and tags
        SheetRow row = SheetRow.fromEmailAndTags(email, tags);

        // THEN: Row should contain both email and tag data
        assertNotNull(row);
        assertEquals("msg-456", row.messageId());
        assertEquals("tenant@community.com", row.from());
        assertEquals("Noise complaint", row.subject());
        assertEquals("Loud music every night", row.body());
        assertEquals("complaint, noise, urgent", row.tags());  // Tags are sorted alphabetically
        assertEquals("Noise complaint from apartment 3B", row.summary());
        assertEquals("Building C, Apartment 3B", row.location());
    }

    @Test
    public void shouldHandleNullTagsInFromEmailAndTags() {
        // GIVEN: Email with null tags
        Email email = Email.create(
            "msg-789",
            "admin@community.com",
            "Meeting reminder",
            "Board meeting next week"
        );

        // WHEN: Creating row with null tags
        SheetRow row = SheetRow.fromEmailAndTags(email, null);

        // THEN: Should handle gracefully
        assertNotNull(row);
        assertEquals("msg-789", row.messageId());
        assertNull(row.tags());
        assertNull(row.summary());
        assertNull(row.location());
    }
}
