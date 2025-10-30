package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import community.application.entity.ActiveInquiryEntity;
import community.application.entity.OutboundChatMessageEntity;

/**
 * TimedAction that sends reminder messages for active inquiries.
 */
@Component(id = "reminder-action")
public class ReminderAction extends TimedAction {

    public static final String REMINDER_MESSAGE_PREFIX = "reminder-";

    private final ComponentClient componentClient;

    public ReminderAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect sendReminderForActiveInquiry() {
        // Get the active inquiry email ID
        String emailId = componentClient.forKeyValueEntity("active-inquiry")
            .method(ActiveInquiryEntity::getActiveInquiryEmailId)
            .invoke();

        // Send reminder message via OutboundChatMessageEntity
        String messageId = REMINDER_MESSAGE_PREFIX + emailId;
        String reminderText = "Reminder: Please address the inquiry for email " + emailId;

        componentClient.forEventSourcedEntity(messageId)
            .method(OutboundChatMessageEntity::sendMessage)
            .invoke(reminderText);

        return effects().done();
    }
}
