package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import community.domain.model.SheetRow;
import community.application.entity.EmailEntity;
import community.application.entity.SheetSyncBufferEntity;

/**
 * Consumer that listens to EmailEntity events and writes them to a buffer.
 * Events are buffered in SheetSyncBufferEntity and flushed periodically by SheetSyncFlushAction.
 *
 * This batching approach avoids Google Sheets API rate limiting during event replay.
 */
@Component(id = "google-sheet-consumer")
@Consume.FromEventSourcedEntity(EmailEntity.class)
public class GoogleSheetConsumer extends Consumer {

    private static final String BUFFER_ENTITY_ID = "global-buffer";

    private final ComponentClient componentClient;

    public GoogleSheetConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect onEvent(EmailEntity.Event event) {
        // Get the entity ID (which is the messageId)
        String messageId = messageContext().eventSubject().get();

        // Convert event to SheetRow if applicable
        return switch (event) {
            case EmailEntity.Event.EmailReceived emailReceived -> {
                Email email = emailReceived.email();
                SheetRow row = SheetRow.fromEmail(email);

                // Add row to buffer (will be flushed periodically by TimedAction)
                componentClient
                    .forKeyValueEntity(BUFFER_ENTITY_ID)
                    .method(SheetSyncBufferEntity::addRow)
                    .invoke(row);

                yield effects().done();
            }
            case EmailEntity.Event.TagsGenerated tagsGenerated -> {
                EmailTags tags = tagsGenerated.tags();
                SheetRow row = SheetRow.onlyTags(messageId, tags);

                // Add row to buffer (will be flushed periodically by TimedAction)
                componentClient
                    .forKeyValueEntity(BUFFER_ENTITY_ID)
                    .method(SheetSyncBufferEntity::addRow)
                    .invoke(row);

                yield effects().done();
            }
            case EmailEntity.Event.InquiryAddressed addressed -> {
                // Inquiry addressed - no sheet sync needed for this event
                yield effects().done();
            }
        };
    }
}
