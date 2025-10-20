package community.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock implementation of SheetSyncService for testing.
 * Stores rows in memory instead of syncing to real Google Sheets.
 */
public class MockSheetSyncService implements SheetSyncService {

    private final Map<String, SheetRow> rows = new HashMap<>();

    @Override
    public void upsertRow(String messageId, SheetRow row) {
        // Smart merge: if row already exists, merge fields
        // Semantics: null or empty = "keep existing", non-empty = "update to new"
        SheetRow existing = rows.get(messageId);
        if (existing != null) {
            // Merge: if new value is null/empty, keep existing; otherwise use new
            String mergedFrom = (row.from() == null || row.from().isEmpty()) ? existing.from() : row.from();
            String mergedSubject = (row.subject() == null || row.subject().isEmpty()) ? existing.subject() : row.subject();
            String mergedBody = (row.body() == null || row.body().isEmpty()) ? existing.body() : row.body();
            String mergedTags = (row.tags() == null || row.tags().isEmpty()) ? existing.tags() : row.tags();
            String mergedSummary = (row.summary() == null || row.summary().isEmpty()) ? existing.summary() : row.summary();
            String mergedLocation = (row.location() == null || row.location().isEmpty()) ? existing.location() : row.location();

            SheetRow merged = new SheetRow(
                messageId,  // Always use the messageId parameter
                mergedFrom,
                mergedSubject,
                mergedBody,
                mergedTags,
                mergedSummary,
                mergedLocation
            );
            rows.put(messageId, merged);
        } else {
            rows.put(messageId, row);
        }
    }

    /**
     * Get a row by messageId (for test verification).
     */
    public SheetRow getRow(String messageId) {
        return rows.get(messageId);
    }

    /**
     * Get the total number of rows stored (for test verification).
     */
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Clear all rows (for test cleanup).
     */
    public void clear() {
        rows.clear();
    }
}
