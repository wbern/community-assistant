package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Stores AI-configurable reminder configuration.
 * GREEN phase: Minimal implementation to store interval in seconds.
 */
@Component(id = "reminder-config")
public class ReminderConfigEntity extends KeyValueEntity<ReminderConfigEntity.ReminderConfig> {

    public record ReminderConfig(int intervalSeconds) {}

    public Effect<ReminderConfig> setInterval(int seconds) {
        ReminderConfig newConfig = new ReminderConfig(seconds);
        return effects().updateState(newConfig).thenReply(newConfig);
    }

    public Effect<Integer> getInterval() {
        ReminderConfig config = currentState();
        if (config == null) {
            return effects().reply(86400);  // Default: 86400 seconds (24 hours)
        }
        return effects().reply(config.intervalSeconds());
    }
}
