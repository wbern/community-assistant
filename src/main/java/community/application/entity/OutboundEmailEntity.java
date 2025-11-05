package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

/**
 * EventSourced Entity for outbound email lifecycle tracking.
 * Tracks email sending lifecycle: Draft → Approve → Send → Delivery Confirmation
 */
@Component(id = "outbound-email")
public class OutboundEmailEntity extends EventSourcedEntity<OutboundEmailEntity.State, OutboundEmailEntity.Event> {

    public record State(String originalCaseId, String recipientEmail, String subject, String body) {}

    public record CreateDraftCommand(String originalCaseId, String recipientEmail, String subject, String body) {}

    public sealed interface Event {
        @TypeName("draft-created")
        record DraftCreated(String originalCaseId, String recipientEmail, String subject, String body) implements Event {}
    }

    @Override
    public State emptyState() {
        return new State(null, null, null, null);
    }

    @Override
    public State applyEvent(Event event) {
        return switch (event) {
            case Event.DraftCreated e -> new State(e.originalCaseId(), e.recipientEmail(), e.subject(), e.body());
        };
    }

    public Effect<String> createDraft(CreateDraftCommand command) {
        return effects()
            .persist(new Event.DraftCreated(command.originalCaseId(), command.recipientEmail(), command.subject(), command.body()))
            .thenReply(__ -> "Draft created");
    }

    public ReadOnlyEffect<State> getDraft() {
        return effects().reply(currentState());
    }
}