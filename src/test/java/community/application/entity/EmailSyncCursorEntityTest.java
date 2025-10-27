package community.application.entity;

import akka.javasdk.testkit.EventSourcedTestKit;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class EmailSyncCursorEntityTest {

    @Test
    public void shouldInitializeWithDefaultCursor() {
        // RED PHASE: Test that cursor entity starts with epoch (1970-01-01) if never updated
        // GIVEN
        var testKit = KeyValueEntityTestKit.of(EmailSyncCursorEntity::new);

        // WHEN
        var result = testKit.method(EmailSyncCursorEntity::getCursor).invoke();

        // THEN
        assertNotNull(result);
        assertEquals(Instant.EPOCH, result.getReply());
    }

    @Test
    public void shouldUpdateCursor() {
        // RED PHASE: Test that cursor can be updated to a specific timestamp
        // GIVEN
        var testKit = KeyValueEntityTestKit.of(EmailSyncCursorEntity::new);
        Instant newCursor = Instant.parse("2025-10-20T10:15:30Z");

        // WHEN
        testKit.method(EmailSyncCursorEntity::updateCursor).invoke(newCursor);

        // THEN
        var result = testKit.method(EmailSyncCursorEntity::getCursor).invoke();
        assertEquals(newCursor, result.getReply());
    }

    @Test
    public void shouldRetainLastUpdatedCursor() {
        // RED PHASE: Test that multiple updates preserve the latest cursor
        // GIVEN
        var testKit = KeyValueEntityTestKit.of(EmailSyncCursorEntity::new);
        Instant firstCursor = Instant.parse("2025-10-20T10:00:00Z");
        Instant secondCursor = Instant.parse("2025-10-20T11:00:00Z");

        // WHEN
        testKit.method(EmailSyncCursorEntity::updateCursor).invoke(firstCursor);
        testKit.method(EmailSyncCursorEntity::updateCursor).invoke(secondCursor);

        // THEN
        var result = testKit.method(EmailSyncCursorEntity::getCursor).invoke();
        assertEquals(secondCursor, result.getReply());
    }
}
