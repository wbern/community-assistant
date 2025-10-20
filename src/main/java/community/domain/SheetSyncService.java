package community.domain;

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
}
