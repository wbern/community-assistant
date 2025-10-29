package community.application.entity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Tracks the single active inquiry that board members should address.
 * Only one inquiry can be active at a time.
 * GREEN phase: Minimal implementation to get/set active inquiry.
 */
@Component(id = "active-inquiry")
public class ActiveInquiryEntity extends KeyValueEntity<ActiveInquiryEntity.ActiveInquiry> {

    public record ActiveInquiry(String emailId) {}

    public Effect<String> setActiveInquiry(String emailId) {
        ActiveInquiry inquiry = new ActiveInquiry(emailId);
        return effects().updateState(inquiry).thenReply(emailId);
    }

    public ReadOnlyEffect<String> getActiveInquiryEmailId() {
        ActiveInquiry inquiry = currentState();
        if (inquiry == null) {
            return effects().error("No active inquiry");
        }
        return effects().reply(inquiry.emailId());
    }
}
