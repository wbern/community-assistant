package community.application.entity;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReminderConfigEntity.
 * RED phase: Testing AI-configurable reminder interval storage in seconds.
 */
public class ReminderConfigEntityTest {

    @Test
    public void shouldStoreReminderInterval() {
        // Arrange
        var testKit = KeyValueEntityTestKit.of("config-1", ReminderConfigEntity::new);

        // Act - set interval to 120 seconds (2 minutes)
        testKit.method(ReminderConfigEntity::setInterval).invoke(120);

        // Assert
        ReminderConfigEntity.ReminderConfig state = testKit.getState();
        assertEquals(120, state.intervalSeconds());
    }

    @Test
    public void shouldReturnDefaultIntervalWhenNotSet() {
        // Arrange
        var testKit = KeyValueEntityTestKit.of("config-2", ReminderConfigEntity::new);

        // Act - don't set any interval, just get it
        var result = testKit.method(ReminderConfigEntity::getInterval).invoke();
        int interval = result.getReply();

        // Assert - should return default of 86400 seconds (24 hours)
        assertEquals(86400, interval);
    }

    @Test
    public void shouldStoreTenSecondInterval() {
        // Arrange
        var testKit = KeyValueEntityTestKit.of("config-3", ReminderConfigEntity::new);

        // Act - set interval to 10 seconds
        testKit.method(ReminderConfigEntity::setInterval).invoke(10);

        // Assert
        ReminderConfigEntity.ReminderConfig state = testKit.getState();
        assertEquals(10, state.intervalSeconds());
    }
}
