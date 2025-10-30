package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Stores AI-configurable reminder configuration.
 */
@Component(id = "reminder-config")
public class ReminderConfigEntity extends KeyValueEntity<ReminderConfigEntity.ReminderConfig> {

    private static final int DEFAULT_INTERVAL_SECONDS = 86400; // 24 hours

    public record ReminderConfig(int intervalSeconds) {}

    public Effect<ReminderConfig> setInterval(int seconds) {
        ReminderConfig newConfig = new ReminderConfig(seconds);
        return effects().updateState(newConfig).thenReply(newConfig);
    }

    public Effect<Integer> getInterval() {
        ReminderConfig config = currentState();
        if (config == null) {
            return effects().reply(DEFAULT_INTERVAL_SECONDS);
        }
        return effects().reply(config.intervalSeconds());
    }
}
