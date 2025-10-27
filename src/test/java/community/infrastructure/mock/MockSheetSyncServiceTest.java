package community.infrastructure.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import community.domain.model.SheetRow;
import community.domain.model.EmailTags;
import community.domain.model.Email;

/**
 * Tests for MockSheetSyncService.
 * Verifies the mock correctly stores and retrieves rows.
 */
public class MockSheetSyncServiceTest {

    private MockSheetSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new MockSheetSyncService();
    }

    @Test
    public void shouldStoreAndRetrieveRow() {
        // GIVEN: A sheet row
        Email email = Email.create("msg-1", "test@example.com", "Subject", "Body");
        SheetRow row = SheetRow.fromEmail(email);

        // WHEN: Upserting the row
        syncService.upsertRow("msg-1", row);

        // THEN: Should be able to retrieve it
        SheetRow retrieved = syncService.getRow("msg-1");
        assertNotNull(retrieved);
        assertEquals("msg-1", retrieved.messageId());
        assertEquals("test@example.com", retrieved.from());
    }

    @Test
    public void shouldUpdateExistingRow() {
        // GIVEN: A row already exists
        Email email1 = Email.create("msg-2", "test@example.com", "Original", "Original body");
        SheetRow row1 = SheetRow.fromEmail(email1);
        syncService.upsertRow("msg-2", row1);

        // WHEN: Upserting with the same messageId but different data
        Email email2 = Email.create("msg-2", "test@example.com", "Updated", "Updated body");
        SheetRow row2 = SheetRow.fromEmail(email2);
        syncService.upsertRow("msg-2", row2);

        // THEN: Should have only one row with updated data
        assertEquals(1, syncService.getRowCount());
        SheetRow retrieved = syncService.getRow("msg-2");
        assertEquals("Updated", retrieved.subject());
        assertEquals("Updated body", retrieved.body());
    }

    @Test
    public void shouldStoreMultipleRows() {
        // GIVEN: Multiple different rows
        Email email1 = Email.create("msg-3", "user1@example.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-4", "user2@example.com", "Subject 2", "Body 2");

        // WHEN: Upserting multiple rows
        syncService.upsertRow("msg-3", SheetRow.fromEmail(email1));
        syncService.upsertRow("msg-4", SheetRow.fromEmail(email2));

        // THEN: Should store both rows
        assertEquals(2, syncService.getRowCount());
        assertNotNull(syncService.getRow("msg-3"));
        assertNotNull(syncService.getRow("msg-4"));
    }

    @Test
    public void shouldMergePartialUpdate() {
        // GIVEN: Email row already exists
        Email email = Email.create("msg-5", "user@example.com", "Subject", "Body");
        syncService.upsertRow("msg-5", SheetRow.fromEmail(email));

        // WHEN: Upserting with only tags (using onlyTags factory)
        EmailTags tags = EmailTags.create(
            Set.of("urgent", "test"),
            "Test summary",
            "Test location"
        );
        SheetRow tagsRow = SheetRow.onlyTags("msg-5", tags);
        syncService.upsertRow("msg-5", tagsRow);

        // THEN: Should preserve email data and add tags
        SheetRow result = syncService.getRow("msg-5");
        assertEquals("user@example.com", result.from(), "From should be preserved");
        assertEquals("Subject", result.subject(), "Subject should be preserved");
        assertEquals("Body", result.body(), "Body should be preserved");
        assertEquals("test, urgent", result.tags(), "Tags should be added");
        assertEquals("Test summary", result.summary());
        assertEquals("Test location", result.location());
    }
}
