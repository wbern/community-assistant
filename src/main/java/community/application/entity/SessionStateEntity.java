package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Session state storage for chat agents.
 * Stores temporary state between user interactions within a session.
 */
@Component(id = "session-state")
public class SessionStateEntity extends KeyValueEntity<SessionStateEntity.State> {

    public record State(PendingEmailRequest pendingEmailRequest) {
        public static State empty() {
            return new State(null);
        }
        
        public State withPendingEmailRequest(PendingEmailRequest request) {
            return new State(request);
        }
        
        public State clearPendingEmailRequest() {
            return new State(null);
        }
        
        public boolean hasPendingEmailRequest() {
            return pendingEmailRequest != null;
        }
    }
    
    public record PendingEmailRequest(String recipientEmail, String activeInquiryId) {}
    
    public record StorePendingEmailCmd(String recipientEmail, String activeInquiryId) {}
    public record ClearPendingEmailCmd() {}
    public record GetStateCmd() {}

    @Override
    public State emptyState() {
        return State.empty();
    }

    public Effect<String> storePendingEmail(StorePendingEmailCmd cmd) {
        PendingEmailRequest request = new PendingEmailRequest(cmd.recipientEmail(), cmd.activeInquiryId());
        State newState = currentState().withPendingEmailRequest(request);
        return effects().updateState(newState).thenReply("stored");
    }
    
    public Effect<State> getState(GetStateCmd cmd) {
        return effects().reply(currentState());
    }
    
    public Effect<String> clearPendingEmail(ClearPendingEmailCmd cmd) {
        State clearedState = currentState().clearPendingEmailRequest();
        return effects().updateState(clearedState).thenReply("cleared");
    }
}