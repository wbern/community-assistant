package community.application.integration;

import community.domain.model.Email;
import community.domain.model.EmailTags;
import community.infrastructure.sheets.GoogleSheetSyncService;
import community.domain.model.SheetRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
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
    public void shouldMaintainCompleteFieldsAfterBatchDuplicateProcessing() throws Exception {
        // RED PHASE: This test will fail because batch processing 
        // may leave blank fields when handling duplicates in the same batch

        // GIVEN: Complete email data that will be processed multiple times
        String messageId = "batch-complete-001";
        Email email = Email.create(
            messageId,
            "batch@test.com", 
            "Batch Complete Subject",
            "Batch complete body with all details"
        );
        EmailTags tags = EmailTags.create(
            Set.of("batch", "complete"),
            "Batch complete summary",
            "Meeting Room B"
        );

        // WHEN: Batch processing with duplicates (simulating real e2e workflow)
        List<SheetRow> batchRows = Arrays.asList(
            SheetRow.fromEmail(email),
            SheetRow.onlyTags(messageId, tags),
            SheetRow.fromEmail(email),     // Duplicate email in same batch
            SheetRow.onlyTags(messageId, tags)  // Duplicate tags in same batch
        );
        
        syncService.batchUpsertRows(batchRows);

        // THEN: Reading back should show ALL fields properly filled (no blanks)
        SheetRow result = syncService.getRow(messageId);
        assertNotNull(result, "Row should exist");
        
        // All email fields must be populated (not null/empty)
        assertEquals(messageId, result.messageId());
        assertNotNull(result.from(), "From field should not be null after batch processing");
        assertFalse(result.from().isEmpty(), "From field should not be empty after batch processing");
        assertEquals("batch@test.com", result.from());
        
        assertNotNull(result.subject(), "Subject field should not be null after batch processing");
        assertFalse(result.subject().isEmpty(), "Subject field should not be empty after batch processing");
        assertEquals("Batch Complete Subject", result.subject());
        
        assertNotNull(result.body(), "Body field should not be null after batch processing");
        assertFalse(result.body().isEmpty(), "Body field should not be empty after batch processing");
        assertEquals("Batch complete body with all details", result.body());
        
        // All tag fields must be populated (not null/empty)
        assertNotNull(result.tags(), "Tags field should not be null after batch processing");
        assertFalse(result.tags().isEmpty(), "Tags field should not be empty after batch processing");
        assertEquals("batch, complete", result.tags());
        
        assertNotNull(result.summary(), "Summary field should not be null after batch processing");
        assertFalse(result.summary().isEmpty(), "Summary field should not be empty after batch processing");
        assertEquals("Batch complete summary", result.summary());
        
        assertNotNull(result.location(), "Location field should not be null after batch processing");
        assertFalse(result.location().isEmpty(), "Location field should not be empty after batch processing");
        assertEquals("Meeting Room B", result.location());
    }

    @Test
    public void shouldBatchUpsertRowsEfficiently() throws Exception {
        // RED PHASE: This test will fail because current implementation
        // makes N individual API calls instead of 1 batch call

        // GIVEN: 10 email rows to batch upsert
        List<SheetRow> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String msgId = "batch-test-" + String.format("%03d", i);
            Email email = Email.create(
                msgId,
                "batch" + i + "@test.com",
                "Batch Test Subject " + i,
                "Batch test body " + i
            );
            rows.add(SheetRow.fromEmail(email));
        }

        // WHEN: Batch upserting all rows
        long startTime = System.currentTimeMillis();
        syncService.batchUpsertRows(rows);
        long batchDuration = System.currentTimeMillis() - startTime;

        System.out.println("⏱️  Batch upsert (10 rows): " + batchDuration + "ms");

        // THEN: Verify all rows were written
        for (int i = 1; i <= 10; i++) {
            String msgId = "batch-test-" + String.format("%03d", i);
            SheetRow retrieved = syncService.getRow(msgId);
            assertNotNull(retrieved, "Row " + msgId + " should exist");
            assertEquals("batch" + i + "@test.com", retrieved.from());
        }

        // PERFORMANCE CHECK: Batch should be significantly faster than individual calls
        // Current implementation makes N individual calls, so this will FAIL
        // After optimization, batch should complete in < 2 seconds for 10 rows
        assertTrue(batchDuration < 2000,
            "Batch upsert should complete in < 2s (was " + batchDuration + "ms). " +
            "This proves we're using true batch API, not individual calls.");
    }
}
