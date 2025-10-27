package community.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * State for SheetSyncBufferEntity (KVE).
 * Holds a buffer of SheetRows waiting to be flushed to Google Sheets.
 */
public record SheetSyncBufferState(List<SheetRow> rows) {

    /**
     * Create an empty buffer.
     */
    public static SheetSyncBufferState empty() {
        return new SheetSyncBufferState(new ArrayList<>());
    }

    /**
     * Add a row to the buffer.
     */
    public SheetSyncBufferState addRow(SheetRow row) {
        List<SheetRow> updatedRows = new ArrayList<>(rows);
        updatedRows.add(row);
        return new SheetSyncBufferState(updatedRows);
    }

    /**
     * Clear the buffer (used after flush).
     */
    public SheetSyncBufferState clear() {
        return new SheetSyncBufferState(new ArrayList<>());
    }

    /**
     * Get the number of rows in the buffer.
     */
    public int size() {
        return rows.size();
    }

    /**
     * Check if buffer is empty.
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
