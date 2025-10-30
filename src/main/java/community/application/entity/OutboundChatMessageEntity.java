package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

/**
 * Tracks outbound chat messages sent by the assistant.
 * GREEN phase: Minimal stub to make test compile.
 */
@Component(id = "outbound-chat-message")
public class OutboundChatMessageEntity extends EventSourcedEntity<OutboundChatMessageEntity.State, OutboundChatMessageEntity.Event> {

    public record State(ChatMessage message) {}

    public record ChatMessage(String messageId, String text) {
        public String text() { return text; }
    }

    public sealed interface Event permits MessageSent {}

    @TypeName("message-sent")
    public record MessageSent(ChatMessage message) implements Event {}

    @Override
    public State emptyState() {
        return new State(null);
    }

    @Override
    public State applyEvent(Event event) {
        return switch(event) {
            case MessageSent sent -> new State(sent.message());
        };
    }

    public Effect<String> sendMessage(String messageText) {
        String messageId = commandContext().entityId();
        ChatMessage message = new ChatMessage(messageId, messageText);
        return effects()
            .persist(new MessageSent(message))
            .thenReply(__ -> messageId);
    }

    public ReadOnlyEffect<ChatMessage> getMessage() {
        State state = currentState();
        if (state == null || state.message() == null) {
            return effects().error("No message found");
        }
        return effects().reply(state.message());
    }
}

