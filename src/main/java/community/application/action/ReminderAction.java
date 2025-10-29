package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;

/**
 * TimedAction that sends reminder messages for active inquiries.
 * GREEN phase: Minimal stub to make test compile.
 */
@Component(id = "reminder-action")
public class ReminderAction extends TimedAction {

    private final ComponentClient componentClient;

    public ReminderAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect sendReminderForActiveInquiry() {
        return effects().error("Not yet implemented");
    }
}
