package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import community.application.entity.EmailEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized event logger for all EventSourcedEntity events.
 * GREEN phase: Minimal implementation to make test pass.
 */
@Component(id = "event-logger-consumer")
@Consume.FromEventSourcedEntity(EmailEntity.class)
public class EventLoggerConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(EventLoggerConsumer.class);

    public Effect onEvent(EmailEntity.Event event) {
        String entityId = messageContext().eventSubject().orElse("unknown");
        
        logger.info("Event persisted: entityType=email, entityId={}, eventType={}, event={}", 
                   entityId, event.getClass().getSimpleName(), event);
        
        return effects().done();
    }
}