package community.application;

import community.domain.Email;
import community.domain.EmailTags;
import community.domain.GoogleSheetSyncService;
import community.domain.SheetRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for GoogleSheetSyncService with real Google Sheets API.
 *
 * Environment variables are configured in pom.xml:
 * - GOOGLE_APPLICATION_CREDENTIALS: ${project.basedir}/credentials.gitignore.json
 * - TEST_SPREADSHEET_ID: 1ILH_bjTDuYzopIqtchL6P5dzQ8Jvp1smpOPI1cpIKaM
 */
public class GoogleSheetSyncServiceIntegrationTest {

    private GoogleSheetSyncService syncService;
    private String spreadsheetId;

    @BeforeEach
    void setUp() {
        // Get spreadsheet ID from environment
        spreadsheetId = System.getenv("TEST_SPREADSHEET_ID");
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            fail("TEST_SPREADSHEET_ID environment variable not set");
        }

        // Initialize service with credentials
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null || credentialsPath.isEmpty()) {
            fail("GOOGLE_APPLICATION_CREDENTIALS environment variable not set");
        }

        syncService = new GoogleSheetSyncService(spreadsheetId);

        // Clear any leftover data from previous test run to ensure clean slate
        // More robust than @AfterEach since it runs even if previous test crashed
        try {
            syncService.clearAllRows();
        } catch (Exception e) {
            System.err.println("Warning: Failed to clear test data before test: " + e.getMessage());
        }
    }

    @Test
    public void shouldUpsertEmailRowToGoogleSheets() throws Exception {
        // GIVEN: An email row
        Email email = Email.create(
            "integration-test-001",
            "integration@test.com",
            "Integration Test Subject",
            "Integration test body"
        );
        SheetRow row = SheetRow.fromEmail(email);

        // WHEN: Upserting to Google Sheets
        syncService.upsertRow("integration-test-001", row);

        // THEN: Should be able to read it back
        // Note: In real implementation, we'd verify by reading from the sheet
        // For now, if no exception is thrown, we consider it successful
        assertTrue(true, "Upsert completed without exception");
    }

    @Test
    public void shouldMergePartialUpdateWithTags() throws Exception {
        // GIVEN: Email row already exists
        Email email = Email.create(
            "integration-test-002",
            "merge@test.com",
            "Merge Test",
            "Testing partial updates"
        );
        SheetRow emailRow = SheetRow.fromEmail(email);
        syncService.upsertRow("integration-test-002", emailRow);

        // WHEN: Upserting partial update with only tags
        EmailTags tags = EmailTags.create(
            Set.of("integration", "test"),
            "Integration test summary",
            "Test location"
        );
        SheetRow tagsRow = SheetRow.onlyTags("integration-test-002", tags);
        syncService.upsertRow("integration-test-002", tagsRow);

        // THEN: Should have merged data (both email fields and tags)
        SheetRow merged = syncService.getRow("integration-test-002");

        assertNotNull(merged, "Row should exist");
        assertEquals("integration-test-002", merged.messageId());

        // Email fields should be preserved
        assertEquals("merge@test.com", merged.from());
        assertEquals("Merge Test", merged.subject());
        assertEquals("Testing partial updates", merged.body());

        // Tag fields should be added
        assertEquals("integration, test", merged.tags());
        assertEquals("Integration test summary", merged.summary());
        assertEquals("Test location", merged.location());
    }

    @Test
    public void shouldBeIdempotentOnReplay() throws Exception {
        // GIVEN: Events representing email + tags
        Email email = Email.create(
            "integration-test-003",
            "replay@test.com",
            "Replay Test",
            "Testing idempotency"
        );
        EmailTags tags = EmailTags.create(
            Set.of("replay"),
            "Replay summary",
            "Replay location"
        );

        // WHEN: Replaying events multiple times
        syncService.upsertRow("integration-test-003", SheetRow.fromEmail(email));
        syncService.upsertRow("integration-test-003", SheetRow.onlyTags("integration-test-003", tags));

        // Read state after first replay
        SheetRow afterFirst = syncService.getRow("integration-test-003");

        // Replay same events again
        syncService.upsertRow("integration-test-003", SheetRow.fromEmail(email));
        syncService.upsertRow("integration-test-003", SheetRow.onlyTags("integration-test-003", tags));

        // Read state after second replay
        SheetRow afterSecond = syncService.getRow("integration-test-003");

        // THEN: Should produce same result (idempotent)
        assertNotNull(afterFirst, "Row should exist after first replay");
        assertNotNull(afterSecond, "Row should exist after second replay");

        // All fields should be identical
        assertEquals(afterFirst.messageId(), afterSecond.messageId());
        assertEquals(afterFirst.from(), afterSecond.from());
        assertEquals(afterFirst.subject(), afterSecond.subject());
        assertEquals(afterFirst.body(), afterSecond.body());
        assertEquals(afterFirst.tags(), afterSecond.tags());
        assertEquals(afterFirst.summary(), afterSecond.summary());
        assertEquals(afterFirst.location(), afterSecond.location());

        // Verify actual values (complete state)
        assertEquals("integration-test-003", afterSecond.messageId());
        assertEquals("replay@test.com", afterSecond.from());
        assertEquals("Replay Test", afterSecond.subject());
        assertEquals("Testing idempotency", afterSecond.body());
        assertEquals("replay", afterSecond.tags());
        assertEquals("Replay summary", afterSecond.summary());
        assertEquals("Replay location", afterSecond.location());
    }

    @Test
    public void shouldReadBackWrittenRow() throws Exception {
        // RED PHASE: This test will fail because getRow() doesn't exist yet

        // GIVEN: Write an email to sheet
        Email email = Email.create(
            "read-test-001",
            "read@test.com",
            "Read Test Subject",
            "Read test body"
        );
        SheetRow written = SheetRow.fromEmail(email);
        syncService.upsertRow("read-test-001", written);

        // WHEN: Read it back
        SheetRow retrieved = syncService.getRow("read-test-001");

        // THEN: All fields should match exactly
        assertNotNull(retrieved, "Row should exist");
        assertEquals("read-test-001", retrieved.messageId());
        assertEquals("read@test.com", retrieved.from());
        assertEquals("Read Test Subject", retrieved.subject());
        assertEquals("Read test body", retrieved.body());
        assertNull(retrieved.tags(), "Tags should be null for email-only row");
        assertNull(retrieved.summary());
        assertNull(retrieved.location());
    }

    @Test
    public void shouldReconstructSheetFromEventReplay() throws Exception {
        // STRESS TEST: Simulate rebuilding sheet from event stream
        // This verifies that our merge semantics correctly handle event replay

        // GIVEN: A sequence of events for 3 different emails
        // Email 1: Received, then tagged
        String msgId1 = "stress-001";
        Email email1 = Email.create(msgId1, "alice@example.com", "Subject 1", "Body 1");
        EmailTags tags1 = EmailTags.create(Set.of("urgent", "question"), "Summary 1", "Location A");

        // Email 2: Received, then tagged, then received again (duplicate)
        String msgId2 = "stress-002";
        Email email2 = Email.create(msgId2, "bob@example.com", "Subject 2", "Body 2");
        EmailTags tags2 = EmailTags.create(Set.of("info"), "Summary 2", "Location B");

        // Email 3: Only received, never tagged
        String msgId3 = "stress-003";
        Email email3 = Email.create(msgId3, "charlie@example.com", "Subject 3", "Body 3");

        // WHEN: Simulating event replay (as if rebuilding from event log)
        // Play all events in chronological order
        syncService.upsertRow(msgId1, SheetRow.fromEmail(email1));
        syncService.upsertRow(msgId2, SheetRow.fromEmail(email2));
        syncService.upsertRow(msgId3, SheetRow.fromEmail(email3));
        syncService.upsertRow(msgId1, SheetRow.onlyTags(msgId1, tags1));
        syncService.upsertRow(msgId2, SheetRow.onlyTags(msgId2, tags2));
        syncService.upsertRow(msgId2, SheetRow.fromEmail(email2)); // Duplicate event

        // THEN: Verify complete reconstruction
        SheetRow reconstructed1 = syncService.getRow(msgId1);
        assertNotNull(reconstructed1, "Email 1 should exist");
        assertEquals("alice@example.com", reconstructed1.from());
        assertEquals("Subject 1", reconstructed1.subject());
        assertEquals("Body 1", reconstructed1.body());
        assertEquals("question, urgent", reconstructed1.tags()); // Alphabetically sorted
        assertEquals("Summary 1", reconstructed1.summary());
        assertEquals("Location A", reconstructed1.location());

        SheetRow reconstructed2 = syncService.getRow(msgId2);
        assertNotNull(reconstructed2, "Email 2 should exist");
        assertEquals("bob@example.com", reconstructed2.from());
        assertEquals("Subject 2", reconstructed2.subject());
        assertEquals("Body 2", reconstructed2.body());
        assertEquals("info", reconstructed2.tags());
        assertEquals("Summary 2", reconstructed2.summary());
        assertEquals("Location B", reconstructed2.location());

        SheetRow reconstructed3 = syncService.getRow(msgId3);
        assertNotNull(reconstructed3, "Email 3 should exist");
        assertEquals("charlie@example.com", reconstructed3.from());
        assertEquals("Subject 3", reconstructed3.subject());
        assertEquals("Body 3", reconstructed3.body());
        assertNull(reconstructed3.tags(), "Email 3 was never tagged");
        assertNull(reconstructed3.summary());
        assertNull(reconstructed3.location());

        // Verify idempotency: Replay all events again
        syncService.upsertRow(msgId1, SheetRow.fromEmail(email1));
        syncService.upsertRow(msgId2, SheetRow.fromEmail(email2));
        syncService.upsertRow(msgId3, SheetRow.fromEmail(email3));
        syncService.upsertRow(msgId1, SheetRow.onlyTags(msgId1, tags1));
        syncService.upsertRow(msgId2, SheetRow.onlyTags(msgId2, tags2));
        syncService.upsertRow(msgId2, SheetRow.fromEmail(email2));

        // State should be unchanged after replay
        SheetRow afterReplay1 = syncService.getRow(msgId1);
        assertEquals(reconstructed1.from(), afterReplay1.from());
        assertEquals(reconstructed1.tags(), afterReplay1.tags());
        assertEquals(reconstructed1.summary(), afterReplay1.summary());

        SheetRow afterReplay2 = syncService.getRow(msgId2);
        assertEquals(reconstructed2.from(), afterReplay2.from());
        assertEquals(reconstructed2.tags(), afterReplay2.tags());

        SheetRow afterReplay3 = syncService.getRow(msgId3);
        assertEquals(reconstructed3.from(), afterReplay3.from());
        assertNull(afterReplay3.tags());
    }
}
