package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import community.application.entity.OutboundChatMessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event logger for OutboundChatMessageEntity events.
 * GREEN phase: Minimal implementation to make test pass.
 */
@Component(id = "outbound-chat-message-logger-consumer")
@Consume.FromEventSourcedEntity(OutboundChatMessageEntity.class)
public class OutboundChatMessageLoggerConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(OutboundChatMessageLoggerConsumer.class);

    public Effect onEvent(OutboundChatMessageEntity.Event event) {
        String entityId = messageContext().eventSubject().orElse("unknown");
        
        logger.info("Event persisted: entityType=outbound-chat-message, entityId={}, eventType={}, event={}", 
                   entityId, event.getClass().getSimpleName(), event);
        
        return effects().done();
    }
}