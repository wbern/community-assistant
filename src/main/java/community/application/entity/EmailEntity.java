package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import community.domain.model.Email;
import community.domain.model.EmailTags;

/**
 * EventSourced Entity for safe email persistence.
 * Uses event sourcing to ensure emails are never lost.
 */
@Component(id = "email")
public class EmailEntity extends EventSourcedEntity<EmailEntity.State, EmailEntity.Event> {

    public record State(Email email, EmailTags tags) {
        public boolean hasEmail() { return email != null; }
        public boolean hasTags() { return tags != null; }
        public boolean isFullyProcessed() { return hasEmail() && hasTags(); }
        
        public State withEmail(Email email) {
            return new State(email, this.tags);
        }
        
        public State withTags(EmailTags tags) {
            return new State(this.email, tags);
        }
    }

    public sealed interface Event {
        @TypeName("email-received")
        record EmailReceived(Email email) implements Event {}

        @TypeName("tags-generated")
        record TagsGenerated(EmailTags tags) implements Event {}

        @TypeName("inquiry-addressed")
        record InquiryAddressed() implements Event {}
    }

    @Override
    public State emptyState() {
        return new State(null, null);
    }

    @Override
    public State applyEvent(Event event) {
        return switch (event) {
            case Event.EmailReceived e -> currentState().withEmail(e.email());
            case Event.TagsGenerated e -> currentState().withTags(e.tags());
            case Event.InquiryAddressed e -> currentState().withEmail(currentState().email().markAsAddressed());
        };
    }

    public Effect<String> receiveEmail(Email email) {
        if (currentState().hasEmail()) {
            // Idempotent: already received
            return effects().reply("Email already received");
        }
        
        return effects()
            .persist(new Event.EmailReceived(email))
            .thenReply(__ -> "Email received");
    }

    public ReadOnlyEffect<Email> getEmail() {
        return currentState().hasEmail()
            ? effects().reply(currentState().email())
            : effects().error("Email not yet received");
    }

    public Effect<String> addTags(EmailTags tags) {
        if (currentState().hasTags()) {
            // Idempotent: tags already added
            return effects().reply("Tags already added");
        }
        
        return effects()
            .persist(new Event.TagsGenerated(tags))
            .thenReply(__ -> "Tags added");
    }

    public ReadOnlyEffect<EmailTags> getTags() {
        return currentState().hasTags()
            ? effects().reply(currentState().tags())
            : effects().error("Tags not yet generated");
    }

    public ReadOnlyEffect<Boolean> isFullyProcessed() {
        return effects().reply(currentState().isFullyProcessed());
    }

    public Effect<String> markAsAddressed() {
        if (!currentState().hasEmail()) {
            return effects().error("Email not yet received");
        }

        if (currentState().email().getStatus() == Email.Status.ADDRESSED) {
            // Idempotent: already marked as addressed
            return effects().reply("Already marked as addressed");
        }

        return effects()
            .persist(new Event.InquiryAddressed())
            .thenReply(__ -> "Marked as addressed");
    }
}
