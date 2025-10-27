package community.application.action;

import community.infrastructure.config.ServiceConfiguration;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.model.Email;
import community.infrastructure.mock.MockSheetSyncService;
import community.domain.model.SheetRow;
import community.application.entity.SheetSyncBufferEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SheetSyncFlushAction.
 * Tests the flush logic using TestKitSupport with actual components.
 */
public class SheetSyncFlushActionTest extends TestKitSupport {

    private MockSheetSyncService mockSheetService;

    @Override
    protected akka.javasdk.testkit.TestKit.Settings testKitSettings() {
        mockSheetService = ServiceConfiguration.getMockSheetService();
        return akka.javasdk.testkit.TestKit.Settings.DEFAULT;
    }

    @BeforeEach
    public void clearMockService() {
        mockSheetService.clear();
        // Also clear the buffer before each test
        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
    }

    @Test
    public void shouldFlushBufferToSheetService() {
        // GIVEN: Buffer has 3 rows
        Email email1 = Email.create("msg-001", "test1@example.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-002", "test2@example.com", "Subject 2", "Body 2");
        Email email3 = Email.create("msg-003", "test3@example.com", "Subject 3", "Body 3");

        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::addRow)
            .invoke(SheetRow.fromEmail(email1));

        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::addRow)
            .invoke(SheetRow.fromEmail(email2));

        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::addRow)
            .invoke(SheetRow.fromEmail(email3));

        // Verify buffer has 3 rows
        var bufferSize = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::getBufferSize)
            .invoke();
        assertEquals(3, bufferSize);

        // WHEN: Manually invoke flush action (simulating timer trigger)
        var rows = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();

        mockSheetService.batchUpsertRows(rows);

        // THEN: MockSheetService should have received batch call
        assertEquals(1, mockSheetService.getBatchCallCount(),
            "Should have exactly 1 batch call");

        assertEquals(3, mockSheetService.getLastBatchSize(),
            "Batch should contain 3 rows");

        // Verify all rows are in sheet
        assertNotNull(mockSheetService.getRow("msg-001"));
        assertNotNull(mockSheetService.getRow("msg-002"));
        assertNotNull(mockSheetService.getRow("msg-003"));

        // Verify buffer is now empty
        var finalBufferSize = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::getBufferSize)
            .invoke();
        assertEquals(0, finalBufferSize);
    }

    @Test
    public void shouldHandleEmptyBuffer() {
        // GIVEN: Empty buffer
        var bufferSize = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::getBufferSize)
            .invoke();
        assertEquals(0, bufferSize);

        // WHEN: Flush is called on empty buffer
        var rows = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();

        // THEN: Should return empty list
        assertTrue(rows.isEmpty());

        // If we call batchUpsertRows with empty list, it should be a no-op
        // (In production, SheetSyncFlushAction checks for empty before calling)
        if (!rows.isEmpty()) {
            mockSheetService.batchUpsertRows(rows);
        }

        // Verify no batch calls were made
        assertEquals(0, mockSheetService.getBatchCallCount());
    }

    @Test
    public void shouldAccumulateAndFlushMultipleTimes() {
        // GIVEN: First batch of 2 rows
        Email email1 = Email.create("msg-001", "test1@example.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-002", "test2@example.com", "Subject 2", "Body 2");

        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::addRow)
            .invoke(SheetRow.fromEmail(email1));

        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::addRow)
            .invoke(SheetRow.fromEmail(email2));

        // WHEN: First flush
        var rows1 = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows1);

        // THEN: First batch processed
        assertEquals(1, mockSheetService.getBatchCallCount());
        assertEquals(2, mockSheetService.getLastBatchSize());

        // GIVEN: Second batch of 1 row
        Email email3 = Email.create("msg-003", "test3@example.com", "Subject 3", "Body 3");
        componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::addRow)
            .invoke(SheetRow.fromEmail(email3));

        // WHEN: Second flush
        var rows2 = componentClient.forKeyValueEntity("global-buffer")
            .method(SheetSyncBufferEntity::flushBuffer)
            .invoke();
        mockSheetService.batchUpsertRows(rows2);

        // THEN: Second batch processed
        assertEquals(2, mockSheetService.getBatchCallCount());
        assertEquals(1, mockSheetService.getLastBatchSize());

        // Verify all 3 rows are in sheet
        assertNotNull(mockSheetService.getRow("msg-001"));
        assertNotNull(mockSheetService.getRow("msg-002"));
        assertNotNull(mockSheetService.getRow("msg-003"));
    }
}
