package community.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import community.domain.Email;

/**
 * EventSourced Entity for safe email persistence.
 * Uses event sourcing to ensure emails are never lost.
 */
@ComponentId("email")
public class EmailEntity extends EventSourcedEntity<EmailEntity.State, EmailEntity.Event> {

    public record State() {}

    public sealed interface Event {
        record EmailReceived(Email email) implements Event {}
    }

    @Override
    public State applyEvent(Event event) {
        return switch (event) {
            case Event.EmailReceived ignored -> new State();
        };
    }

    public Effect<String> receiveEmail(Email email) {
        Event.EmailReceived emailReceived = new Event.EmailReceived(email);
        return effects()
            .persist(emailReceived)
            .thenReply(newState -> "Email received");
    }
}
