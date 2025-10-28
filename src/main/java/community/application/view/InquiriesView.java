package community.application.view;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import community.application.entity.EmailEntity;
import community.domain.model.Email;

/**
 * View for projecting emails as inquiries for board member discussion.
 * Minimal implementation for GREEN phase.
 */
@Component(id = "inquiries-view")
public class InquiriesView extends View {

    public record Inquiry(
        String emailId,
        String inquiryText
    ) {}

    @Consume.FromEventSourcedEntity(EmailEntity.class)
    public static class InquiriesTableUpdater extends TableUpdater<Inquiry> {

        public Effect<Inquiry> onEvent(EmailEntity.Event event) {
            String emailId = updateContext().eventSubject().orElse("");

            return switch(event) {
                case EmailEntity.Event.EmailReceived received -> {
                    Email email = received.email();
                    String inquiryText = formatAsInquiry(email);

                    Inquiry inquiry = new Inquiry(emailId, inquiryText);
                    yield effects().updateRow(inquiry);
                }
                case EmailEntity.Event.TagsGenerated tags -> {
                    yield effects().ignore();
                }
                case EmailEntity.Event.InquiryAddressed addressed -> {
                    yield effects().ignore();
                }
            };
        }

        private String formatAsInquiry(Email email) {
            // Format inquiry with subject and first 50 characters of email body (plain text)
            String body = email.getBody();
            String bodyPreview = body.substring(0, Math.min(50, body.length()));
            String subject = email.getSubject();

            return String.format("%s: %s", subject, bodyPreview);
        }
    }

    @Query("SELECT * FROM inquiries WHERE emailId = :emailId")
    public QueryEffect<Inquiry> getInquiryByEmailId(String emailId) {
        return queryResult();
    }
}
