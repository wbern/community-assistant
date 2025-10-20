package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import community.domain.Email;
import community.domain.EmailTags;
import community.domain.SheetRow;
import community.domain.SheetSyncService;

/**
 * Consumer that listens to EmailEntity events and syncs data to Google Sheets.
 * Events are the source of truth - the sheet is a materialized view that can be rebuilt.
 */
@Component(id = "google-sheet-consumer")
@Consume.FromEventSourcedEntity(EmailEntity.class)
public class GoogleSheetConsumer extends Consumer {

    private final SheetSyncService sheetService;

    public GoogleSheetConsumer(SheetSyncService sheetService) {
        this.sheetService = sheetService;
    }

    public Effect onEvent(EmailEntity.Event event) {
        // Get the entity ID (which is the messageId)
        String messageId = messageContext().eventSubject().get();

        return switch (event) {
            case EmailEntity.Event.EmailReceived emailReceived -> {
                // Sync email to sheet (without tags initially)
                Email email = emailReceived.email();
                SheetRow row = SheetRow.fromEmail(email);
                sheetService.upsertRow(messageId, row);
                yield effects().done();
            }

            case EmailEntity.Event.TagsGenerated tagsGenerated -> {
                // Update the row with tags (partial update - email fields will be preserved)
                EmailTags tags = tagsGenerated.tags();
                SheetRow row = SheetRow.onlyTags(messageId, tags);
                sheetService.upsertRow(messageId, row);
                yield effects().done();
            }
        };
    }
}
