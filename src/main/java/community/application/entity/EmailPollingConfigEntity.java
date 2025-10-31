package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import community.application.action.KeyValueEntityLogger;

/**
 * GREEN phase: Stores configurable email polling intervals.
 */
@Component(id = "email-polling-config")
public class EmailPollingConfigEntity extends KeyValueEntity<EmailPollingConfigEntity.PollingConfig> {

    private static final int DEFAULT_INTERVAL_SECONDS = 300; // 5 minutes

    public record PollingConfig(int intervalSeconds) {}

    public Effect<PollingConfig> setInterval(int seconds) {
        String entityId = commandContext().entityId();
        KeyValueEntityLogger.logStateChange("email-polling-config", entityId, "setInterval");
        
        PollingConfig newConfig = new PollingConfig(seconds);
        return effects().updateState(newConfig).thenReply(newConfig);
    }

    public Effect<Integer> getInterval() {
        PollingConfig config = currentState();
        if (config == null) {
            return effects().reply(DEFAULT_INTERVAL_SECONDS);
        }
        return effects().reply(config.intervalSeconds());
    }
}