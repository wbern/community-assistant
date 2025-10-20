package community.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import community.domain.Email;
import community.domain.EmailTags;

/**
 * EventSourced Entity for safe email persistence.
 * Uses event sourcing to ensure emails are never lost.
 */
@ComponentId("email")
public class EmailEntity extends EventSourcedEntity<EmailEntity.State, EmailEntity.Event> {

    public record State(Email email, EmailTags tags) {}

    public sealed interface Event {
        record EmailReceived(Email email) implements Event {}
        record TagsGenerated(EmailTags tags) implements Event {}
    }

    @Override
    public State emptyState() {
        return new State(null, null);
    }

    @Override
    public State applyEvent(Event event) {
        return switch (event) {
            case Event.EmailReceived emailReceived ->
                new State(emailReceived.email(), currentState().tags());
            case Event.TagsGenerated tagsGenerated ->
                new State(currentState().email(), tagsGenerated.tags());
        };
    }

    public Effect<String> receiveEmail(Email email) {
        Event.EmailReceived emailReceived = new Event.EmailReceived(email);
        return effects()
            .persist(emailReceived)
            .thenReply(newState -> "Email received");
    }

    public ReadOnlyEffect<Email> getEmail() {
        return effects().reply(currentState().email());
    }

    public Effect<String> addTags(EmailTags tags) {
        Event.TagsGenerated tagsGenerated = new Event.TagsGenerated(tags);
        return effects()
            .persist(tagsGenerated)
            .thenReply(newState -> "Tags added");
    }

    public ReadOnlyEffect<EmailTags> getTags() {
        return effects().reply(currentState().tags());
    }
}
