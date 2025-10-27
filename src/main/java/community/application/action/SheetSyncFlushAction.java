package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import community.domain.model.SheetRow;
import community.domain.port.SheetSyncService;
import community.application.entity.SheetSyncBufferEntity;

import java.time.Duration;
import java.util.List;

/**
 * TimedAction that periodically flushes the SheetSyncBuffer to Google Sheets.
 * This reduces API calls by batching multiple events into a single flush operation.
 *
 * Runs every 10 seconds to flush accumulated events.
 */
@Component(id = "sheet-sync-flush-action")
public class SheetSyncFlushAction extends TimedAction {

    /** Entity ID for the singleton buffer that accumulates sheet rows */
    private static final String BUFFER_ENTITY_ID = "global-buffer";

    /** Timer name for the periodic flush operation */
    private static final String TIMER_NAME = "sheet-sync-flush-timer";

    /** Interval between flush operations - balances latency vs API efficiency */
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(10);

    private final ComponentClient componentClient;
    private final SheetSyncService sheetService;
    private final TimerScheduler timerScheduler;

    public SheetSyncFlushAction(ComponentClient componentClient, SheetSyncService sheetService, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.sheetService = sheetService;
        this.timerScheduler = timerScheduler;
    }

    /**
     * Flush the buffer to Google Sheets.
     * Called periodically by the timer. Reschedules itself for the next flush.
     */
    public Effect flushToSheets() {
        // Get and flush buffer from KVE
        List<SheetRow> rows = componentClient
            .forKeyValueEntity(BUFFER_ENTITY_ID)
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();

        // If buffer has rows, batch upsert to Google Sheets
        if (!rows.isEmpty()) {
            sheetService.batchUpsertRows(rows);
        }

        // Reschedule the next flush
        scheduleNextFlush();

        return effects().done();
    }

    /**
     * Schedule the next flush to run after FLUSH_INTERVAL.
     * Can be called manually to bootstrap the timer or for testing.
     */
    public Effect scheduleNextFlush() {
        timerScheduler.createSingleTimer(
            TIMER_NAME,
            FLUSH_INTERVAL,
            componentClient
                .forTimedAction()
                .method(SheetSyncFlushAction::flushToSheets)
                .deferred()
        );
        return effects().done();
    }
}
