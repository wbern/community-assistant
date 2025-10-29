package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import community.application.entity.EmailEntity;
import community.application.entity.ActiveInquiryEntity;

/**
 * Sets the active inquiry when an email is received.
 * GREEN phase: Minimal implementation to make test pass.
 */
@Component(id = "active-inquiry-consumer")
@Consume.FromEventSourcedEntity(EmailEntity.class)
public class ActiveInquiryConsumer extends Consumer {

    private final ComponentClient componentClient;

    public ActiveInquiryConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect onEvent(EmailEntity.Event event) {
        return switch(event) {
            case EmailEntity.Event.EmailReceived received -> {
                String emailId = messageContext().eventSubject().orElse("");

                // Set this email as the active inquiry (singleton entity)
                componentClient.forKeyValueEntity("active-inquiry")
                    .method(ActiveInquiryEntity::setActiveInquiry)
                    .invoke(emailId);

                yield effects().done();
            }
            case EmailEntity.Event.TagsGenerated tags -> effects().ignore();
            case EmailEntity.Event.InquiryAddressed addressed -> effects().ignore();
        };
    }
}
