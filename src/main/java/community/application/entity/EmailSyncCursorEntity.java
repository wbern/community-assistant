package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import community.application.action.KeyValueEntityLogger;

import java.time.Instant;

@Component(id = "email-sync-cursor")
public class EmailSyncCursorEntity extends KeyValueEntity<Instant> {

    @Override
    public Instant emptyState() {
        return Instant.EPOCH;
    }

    public Effect<Instant> getCursor() {
        return effects().reply(currentState());
    }

    public Effect<Instant> updateCursor(Instant newCursor) {
        String entityId = commandContext().entityId();
        KeyValueEntityLogger.logStateChange("email-sync-cursor", entityId, "updateCursor");
        
        return effects().updateState(newCursor).thenReply(newCursor);
    }
}
