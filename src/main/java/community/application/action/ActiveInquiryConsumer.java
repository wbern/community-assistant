package community.application.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.timer.TimerScheduler;
import community.application.entity.EmailEntity;
import community.application.entity.ActiveInquiryEntity;
import community.application.entity.ReminderConfigEntity;

import java.time.Duration;

/**
 * Sets the active inquiry when an email is received and schedules reminder timer.
 */
@Component(id = "active-inquiry-consumer")
@Consume.FromEventSourcedEntity(EmailEntity.class)
public class ActiveInquiryConsumer extends Consumer {

    private static final String REMINDER_TIMER_PREFIX = "reminder-";

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public ActiveInquiryConsumer(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    public Effect onEvent(EmailEntity.Event event) {
        return switch(event) {
            case EmailEntity.Event.EmailReceived received -> {
                String emailId = messageContext().eventSubject().orElse("");

                // Set this email as the active inquiry (singleton entity)
                componentClient.forKeyValueEntity("active-inquiry")
                    .method(ActiveInquiryEntity::setActiveInquiry)
                    .invoke(emailId);

                // Get reminder interval from config
                int intervalSeconds = componentClient.forKeyValueEntity("reminder-config")
                    .method(ReminderConfigEntity::getInterval)
                    .invoke();

                // Schedule reminder timer to fire after the configured interval
                timerScheduler.createSingleTimer(
                    REMINDER_TIMER_PREFIX + emailId,
                    Duration.ofSeconds(intervalSeconds),
                    componentClient.forTimedAction()
                        .method(ReminderAction::sendReminderForActiveInquiry)
                        .deferred()
                );

                yield effects().done();
            }
            case EmailEntity.Event.TagsGenerated tags -> effects().ignore();
            case EmailEntity.Event.InquiryAddressed addressed -> effects().ignore();
        };
    }
}
