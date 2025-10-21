package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import community.domain.Email;
import community.domain.EmailTags;

/**
 * EventSourced Entity for safe email persistence.
 * Uses event sourcing to ensure emails are never lost.
 */
@Component(id = "email")
public class EmailEntity extends EventSourcedEntity<EmailEntity.State, EmailEntity.Event> {

    public record State(Email email, EmailTags tags) {}

    public sealed interface Event {
        @TypeName("email-received")
        record EmailReceived(Email email) implements Event {}

        @TypeName("tags-generated")
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
        // Idempotency check: if email already received, don't persist duplicate event
        if (currentState().email() != null) {
            if (currentState().email().getMessageId().equals(email.getMessageId())) {
                // Same email already processed - idempotent response
                return effects().reply("Email already received");
            } else {
                // Different messageId for same entity - should not happen
                return effects().error(
                    "Entity " + email.getMessageId() + " already contains different email: " +
                    currentState().email().getMessageId()
                );
            }
        }

        // First time receiving this email - persist event
        Event.EmailReceived emailReceived = new Event.EmailReceived(email);
        return effects()
            .persist(emailReceived)
            .thenReply(newState -> "Email received");
    }

    public ReadOnlyEffect<Email> getEmail() {
        // Return empty Optional-style: null if not yet received
        if (currentState().email() == null) {
            // Entity not yet initialized - return error to signal "not found"
            return effects().error("Email not yet received");
        }
        return effects().reply(currentState().email());
    }

    public Effect<String> addTags(EmailTags tags) {
        // Idempotency check: if tags already exist, don't persist duplicate event
        if (currentState().tags() != null) {
            // Tags already added - idempotent response
            return effects().reply("Tags already added");
        }

        // First time adding tags - persist event
        Event.TagsGenerated tagsGenerated = new Event.TagsGenerated(tags);
        return effects()
            .persist(tagsGenerated)
            .thenReply(newState -> "Tags added");
    }

    public ReadOnlyEffect<EmailTags> getTags() {
        // Return empty Optional-style: null if not yet added
        if (currentState().tags() == null) {
            // Tags not yet generated - return error to signal "not found"
            return effects().error("Tags not yet generated");
        }
        return effects().reply(currentState().tags());
    }

    /**
     * Check if this email has been fully processed (has both email and tags).
     * Used by workflow for idempotency checks.
     */
    public ReadOnlyEffect<Boolean> isFullyProcessed() {
        boolean hasEmail = currentState().email() != null;
        boolean hasTags = currentState().tags() != null;
        return effects().reply(hasEmail && hasTags);
    }
}
