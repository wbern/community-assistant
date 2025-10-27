package community.api;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.application.entity.EmailEntity;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChatEndpoint.
 * RED phase: Testing HTTP endpoint that sends messages to ChatHandlerAgent.
 */
public class ChatEndpointTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String response) {}

    @Test
    public void shouldSendChatMessageViaHttpEndpoint() {
        // Arrange: Create email with elevator tag and publish events to populate View
        String topicId = "42";
        Email email = Email.create(
            topicId,
            "admin@building.com",
            "Elevator Issue",
            "Elevator broken in Building A"
        );
        EmailTags tags = EmailTags.create(
            Set.of("elevator", "urgent"),
            "Elevator malfunction requires immediate attention",
            "Building A, Elevator"
        );
        
        // Publish events to EmailEntity to populate the View
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), topicId);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.TagsGenerated(tags), topicId);

        ChatRequest request = new ChatRequest("@assistant elevator");

        // Wait for View to be updated, then test endpoint
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Act: Send chat message via HTTP endpoint
            var response = httpClient.POST("/chat/message")
                .withRequestBody(request)
                .responseBodyAs(ChatResponse.class)
                .invoke();

            // Assert: Should get response from ChatHandlerAgent with topic ID
            assertNotNull(response);
            ChatResponse chatResponse = response.body();
            assertNotNull(chatResponse.response());
            assertTrue(chatResponse.response().contains("Here's the topic you are talking about"));
            assertTrue(chatResponse.response().contains("[42]"));
        });
    }
}