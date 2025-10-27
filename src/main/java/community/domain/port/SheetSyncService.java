package community.domain.port;

import community.domain.model.SheetRow;

import java.util.List;

/**
 * Service interface for syncing data to Google Sheets.
 * Implementations can be real (Google Sheets API) or mock (for testing).
 */
public interface SheetSyncService {

    /**
     * Upsert a row in the sheet using messageId as the unique identifier.
     * If a row with this messageId exists, it will be updated.
     * If no row exists, a new one will be created.
     *
     * Partial Update Semantics:
     * - If a SheetRow field is null or empty string, the existing value is preserved
     * - If a SheetRow field has a non-empty value, it updates/overwrites the existing value
     *
     * This allows events with partial data (e.g., TagsGenerated with only tag fields)
     * to update specific columns without overwriting the complete row.
     *
     * This method must be idempotent - calling it multiple times with the
     * same data should have the same effect as calling it once.
     *
     * @param messageId Unique identifier for the row (email message ID)
     * @param row The row data to sync (may contain partial data)
     */
    void upsertRow(String messageId, SheetRow row);

    /**
     * Batch upsert multiple rows in a single operation.
     * This is more efficient than calling upsertRow() multiple times,
     * as it reduces the number of API calls to Google Sheets.
     *
     * Each row contains its own messageId and follows the same
     * partial update semantics as upsertRow().
     *
     * This method must be idempotent - calling it multiple times with the
     * same data should have the same effect as calling it once.
     *
     * @param rows List of rows to upsert (each row contains its messageId)
     */
    void batchUpsertRows(List<SheetRow> rows);

    /**
     * Retrieve a row from the sheet by messageId.
     *
     * @param messageId Unique identifier for the row (email message ID)
     * @return The row data, or null if not found
     */
    SheetRow getRow(String messageId);

    /**
     * Delete a row from the sheet by messageId.
     * Used for test cleanup and data management.
     *
     * @param messageId Unique identifier for the row to delete
     */
    void deleteRow(String messageId);

    /**
     * Clear all data rows from the sheet (preserves header row).
     * Used for test cleanup to ensure complete test isolation.
     */
    void clearAllRows();
}
