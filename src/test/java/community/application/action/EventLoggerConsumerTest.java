package community.application.action;

import akka.javasdk.testkit.TestKitSupport;
import community.application.entity.EmailEntity;
import community.application.entity.OutboundChatMessageEntity;
import community.domain.model.Email;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing centralized event logging via EventLoggerConsumer.
 */
public class EventLoggerConsumerTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> emailLogAppender;
    private ListAppender<ILoggingEvent> chatLogAppender;
    private Logger emailLoggerConsumer;
    private Logger chatLoggerConsumer;

    @BeforeEach
    public void setupLogCapture() {
        // Setup log capture for EmailEntity events
        emailLoggerConsumer = (Logger) LoggerFactory.getLogger(EventLoggerConsumer.class);
        emailLogAppender = new ListAppender<>();
        emailLogAppender.start();
        emailLoggerConsumer.addAppender(emailLogAppender);
        
        // Setup log capture for OutboundChatMessageEntity events
        chatLoggerConsumer = (Logger) LoggerFactory.getLogger(OutboundChatMessageLoggerConsumer.class);
        chatLogAppender = new ListAppender<>();
        chatLogAppender.start();
        chatLoggerConsumer.addAppender(chatLogAppender);
    }

    @AfterEach
    public void teardownLogCapture() {
        emailLoggerConsumer.detachAppender(emailLogAppender);
        chatLoggerConsumer.detachAppender(chatLogAppender);
    }

    @Test
    public void red_shouldLogEventWhenEmailEntityPersistsEvent() {
        // RED: When EmailEntity persists an EmailReceived event,
        // EventLoggerConsumer should log the event with entity context

        String emailId = "test-email-log-001";
        Email testEmail = Email.create(
            emailId,
            "test@example.com",
            "Test Subject",
            "Test body content"
        );

        // Persist event in EmailEntity - this should trigger EventLoggerConsumer
        componentClient.forEventSourcedEntity(emailId)
            .method(EmailEntity::receiveEmail)
            .invoke(testEmail);

        // Assert: EventLoggerConsumer should have logged the EmailReceived event
        // We expect a log entry containing: entityType=email, entityId=test-email-log-001, eventType=EmailReceived
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = emailLogAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Event persisted") 
                          && event.getFormattedMessage().contains("entityType=email")
                          && event.getFormattedMessage().contains("entityId=" + emailId)
                          && event.getFormattedMessage().contains("eventType=EmailReceived"));
            assertTrue(found, "Expected EmailEntity event log not found in: " + logEvents);
        });
    }

    @Test
    public void red_shouldLogEventWhenOutboundChatMessageEntityPersistsEvent() {
        // RED: When OutboundChatMessageEntity persists a MessageSent event,
        // EventLoggerConsumer should log the event with entity context

        String messageId = "test-chat-msg-001";
        OutboundChatMessageEntity.ChatMessage chatMessage = 
            new OutboundChatMessageEntity.ChatMessage(messageId, "Test chat message content");

        // Persist event in OutboundChatMessageEntity - this should trigger EventLoggerConsumer
        componentClient.forEventSourcedEntity(messageId)
            .method(OutboundChatMessageEntity::sendMessage)
            .invoke(chatMessage.text());

        // Assert: EventLoggerConsumer should have logged the MessageSent event
        // We expect a log entry containing: entityType=outbound-chat-message, entityId=test-chat-msg-001, eventType=MessageSent
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = chatLogAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Event persisted") 
                          && event.getFormattedMessage().contains("entityType=outbound-chat-message")
                          && event.getFormattedMessage().contains("entityId=" + messageId)
                          && event.getFormattedMessage().contains("eventType=MessageSent"));
            assertTrue(found, "Expected OutboundChatMessageEntity event log not found in: " + logEvents);
        });
    }

}