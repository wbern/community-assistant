package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import community.domain.SheetRow;

import java.util.List;

/**
 * View for retrieving all topics/tickets using the same data structure as Google Sheets.
 * Returns SheetRow records with all fields.
 */
@Component(id = "topics-view")
public class TopicsView extends View {

    public record TopicsList(List<SheetRow> topics) {}

    // Required TableUpdater for View validation
    @Consume.FromEventSourcedEntity(EmailEntity.class)
    public static class TopicsTableUpdater extends TableUpdater<SheetRow> {
        
        public Effect<SheetRow> onEvent(EmailEntity.Event event) {
            String entityId = updateContext().eventSubject().orElse("");
            
            return switch(event) {
                case EmailEntity.Event.EmailReceived received -> {
                    // Create initial row from email only, providing defaults for missing fields
                    SheetRow row = new SheetRow(
                        received.email().getMessageId(),
                        received.email().getFrom(),
                        received.email().getSubject(),
                        received.email().getBody(),
                        "", // Empty tags initially
                        "", // Empty summary initially  
                        ""  // Empty location initially
                    );
                    yield effects().updateRow(row);
                }
                case EmailEntity.Event.TagsGenerated tagsEvent -> {
                    // Get current row and update with tags, preserving email data
                    SheetRow currentRow = rowState();
                    if (currentRow != null && currentRow.messageId().equals(entityId)) {
                        // Merge tags into existing row
                        SheetRow updatedRow = new SheetRow(
                            currentRow.messageId(),
                            currentRow.from(),
                            currentRow.subject(), 
                            currentRow.body(),
                            String.join(", ", tagsEvent.tags().tags()),
                            tagsEvent.tags().summary() != null ? tagsEvent.tags().summary() : "",
                            tagsEvent.tags().location() != null ? tagsEvent.tags().location() : ""
                        );
                        yield effects().updateRow(updatedRow);
                    } else {
                        // Create row with tags only if no email exists yet
                        SheetRow row = new SheetRow(
                            entityId,
                            "", // Empty email fields
                            "",
                            "",
                            String.join(", ", tagsEvent.tags().tags()),
                            tagsEvent.tags().summary() != null ? tagsEvent.tags().summary() : "",
                            tagsEvent.tags().location() != null ? tagsEvent.tags().location() : ""
                        );
                        yield effects().updateRow(row);
                    }
                }
            };
        }
    }

    @Query("SELECT * AS topics FROM all_topics")
    public QueryEffect<TopicsList> getAllTopics() {
        // Stub data for GREEN phase - will be replaced with real View projections later
        // Views execute queries against the database, not return hardcoded data
        return queryResult();
    }
}