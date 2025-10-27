package community.application.entity;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import community.domain.model.Email;
import community.domain.model.SheetRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for SheetSyncBufferEntity (Key-Value Entity).
 * Tests buffer accumulation, flushing, and size tracking.
 */
public class SheetSyncBufferEntityTest {

    @Test
    public void shouldAddRowToBuffer() {
        // GIVEN: Empty buffer
        var testKit = KeyValueEntityTestKit.of(SheetSyncBufferEntity::new);

        // WHEN: Adding a row
        Email email = Email.create("msg-001", "test@example.com", "Subject", "Body");
        SheetRow row = SheetRow.fromEmail(email);

        var result = testKit.method(SheetSyncBufferEntity::addRow).invoke(row);

        // THEN: Should reply successfully
        assertTrue(result.isReply());
        assertTrue(result.getReply().contains("Row added"));

        // AND: Buffer should contain 1 row
        assertEquals(1, testKit.getState().size());
        assertEquals("msg-001", testKit.getState().rows().get(0).messageId());
    }

    @Test
    public void shouldAccumulateMultipleRows() {
        // GIVEN: Empty buffer
        var testKit = KeyValueEntityTestKit.of(SheetSyncBufferEntity::new);

        // WHEN: Adding 3 rows
        Email email1 = Email.create("msg-001", "test1@example.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-002", "test2@example.com", "Subject 2", "Body 2");
        Email email3 = Email.create("msg-003", "test3@example.com", "Subject 3", "Body 3");

        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email1));
        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email2));
        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email3));

        // THEN: Buffer should contain 3 rows
        assertEquals(3, testKit.getState().size());
    }

    @Test
    public void shouldFlushBufferAndClear() {
        // GIVEN: Buffer with 3 rows
        var testKit = KeyValueEntityTestKit.of(SheetSyncBufferEntity::new);

        Email email1 = Email.create("msg-001", "test1@example.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-002", "test2@example.com", "Subject 2", "Body 2");
        Email email3 = Email.create("msg-003", "test3@example.com", "Subject 3", "Body 3");

        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email1));
        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email2));
        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email3));

        // WHEN: Flushing the buffer
        var result = testKit.method(SheetSyncBufferEntity::flushBuffer).invoke();

        // THEN: Should return all 3 rows
        assertTrue(result.isReply());
        List<SheetRow> rows = result.getReply();
        assertEquals(3, rows.size());
        assertEquals("msg-001", rows.get(0).messageId());
        assertEquals("msg-002", rows.get(1).messageId());
        assertEquals("msg-003", rows.get(2).messageId());

        // AND: Buffer should be empty after flush
        assertEquals(0, testKit.getState().size());
        assertTrue(testKit.getState().isEmpty());
    }

    @Test
    public void shouldReturnCorrectBufferSize() {
        // GIVEN: Buffer with 2 rows
        var testKit = KeyValueEntityTestKit.of(SheetSyncBufferEntity::new);

        Email email1 = Email.create("msg-001", "test1@example.com", "Subject 1", "Body 1");
        Email email2 = Email.create("msg-002", "test2@example.com", "Subject 2", "Body 2");

        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email1));
        testKit.method(SheetSyncBufferEntity::addRow).invoke(SheetRow.fromEmail(email2));

        // WHEN: Querying buffer size
        var result = testKit.method(SheetSyncBufferEntity::getBufferSize).invoke();

        // THEN: Should return 2
        assertTrue(result.isReply());
        assertEquals(2, result.getReply());
    }

    @Test
    public void shouldHandleEmptyBufferFlush() {
        // GIVEN: Empty buffer
        var testKit = KeyValueEntityTestKit.of(SheetSyncBufferEntity::new);

        // WHEN: Flushing empty buffer
        var result = testKit.method(SheetSyncBufferEntity::flushBuffer).invoke();

        // THEN: Should return empty list
        assertTrue(result.isReply());
        List<SheetRow> rows = result.getReply();
        assertTrue(rows.isEmpty());

        // AND: Buffer should remain empty
        assertEquals(0, testKit.getState().size());
    }
}
