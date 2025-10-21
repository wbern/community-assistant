package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import community.domain.SheetRow;
import community.domain.SheetSyncBufferState;

import java.util.List;

/**
 * Key-Value Entity that buffers SheetRows before they are flushed to Google Sheets.
 * This enables batching to reduce API calls and avoid rate limiting.
 *
 * Entity ID: "global-buffer" (singleton buffer for all events)
 */
@Component(id = "sheet-sync-buffer")
public class SheetSyncBufferEntity extends KeyValueEntity<SheetSyncBufferState> {

    @Override
    public SheetSyncBufferState emptyState() {
        return SheetSyncBufferState.empty();
    }

    /**
     * Add a row to the buffer.
     */
    public Effect<String> addRow(SheetRow row) {
        SheetSyncBufferState newState = currentState().addRow(row);
        return effects()
            .updateState(newState)
            .thenReply("Row added, buffer size: " + newState.size());
    }

    /**
     * Flush the buffer and return all rows.
     * Clears the buffer after returning the rows.
     */
    public Effect<List<SheetRow>> flushBuffer() {
        List<SheetRow> rows = currentState().rows();
        return effects()
            .updateState(SheetSyncBufferState.empty())
            .thenReply(rows);
    }

    /**
     * Get the current buffer size (for monitoring/testing).
     */
    public Effect<Integer> getBufferSize() {
        return effects().reply(currentState().size());
    }
}
