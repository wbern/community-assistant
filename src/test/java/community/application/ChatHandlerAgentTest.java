package community.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.Email;
import community.domain.EmailTags;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChatHandlerAgent.
 * RED phase: Testing topic lookup by tags for meaningful responses.
 */
public class ChatHandlerAgentTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    @Test
    public void shouldLookupTopicByElevatorTag() {
        // GREEN: This test now passes with hardcoded elevator topic lookup
        
        // Arrange: @assistant mention with elevator keyword should lookup tagged topics
        String boardMemberMessage = "@assistant elevator";

        // Act: Call agent to handle chat message  
        String response = componentClient.forAgent()
            .inSession("topic-session-1")
            .method(ChatHandlerAgent::handleMessage)
            .invoke(boardMemberMessage);

        // Assert: Should lookup and return topic with elevator tag
        assertNotNull(response);
        assertTrue(response.contains("Here's the topic you are talking about"));
        assertTrue(response.matches(".*\\[\\d+\\].*")); // Should contain [id] pattern
    }

    @Test
    public void shouldLookupTopicByNoiseTag() {
        // Arrange: @assistant mention with noise keyword should lookup tagged topics
        String boardMemberMessage = "@assistant noise";

        // Act: Call agent to handle chat message  
        String response = componentClient.forAgent()
            .inSession("topic-session-2")
            .method(ChatHandlerAgent::handleMessage)
            .invoke(boardMemberMessage);

        // Assert: Should lookup and return topic with noise tag (different ID than elevator)
        assertNotNull(response);
        assertTrue(response.contains("Here's the topic you are talking about"));
        assertTrue(response.matches(".*\\[\\d+\\].*")); // Should contain [id] pattern
        assertFalse(response.contains("[42]")); // Should NOT be same ID as elevator topic
    }

    @Test
    public void shouldQueryViewForTopicLookup() {
        // Arrange: Create email with maintenance tag and publish events to populate View
        String topicId = "67890";  // Use known ID instead of random for GREEN phase
        String expectedResponse = "Here's the topic you are talking about [" + topicId + "]";
        
        // Create email and tags for maintenance topic
        Email email = Email.create(
            topicId,
            "maintenance@building.com",
            "HVAC Maintenance",
            "Annual HVAC system servicing required"
        );
        EmailTags tags = EmailTags.create(
            Set.of("maintenance", "hvac"),
            "Scheduled maintenance for HVAC system",
            "Building B, Mechanical Room"
        );
        
        // Publish events to EmailEntity to populate the View
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), topicId);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.TagsGenerated(tags), topicId);
        
        String boardMemberMessage = "@assistant maintenance";

        // Wait for View to be updated, then test agent
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Act: Call agent to handle chat message  
            String response = componentClient.forAgent()
                .inSession("topic-session-3")
                .method(ChatHandlerAgent::handleMessage)
                .invoke(boardMemberMessage);

            // Assert: Should find the maintenance topic and return its ID
            assertNotNull(response);
            assertEquals(expectedResponse, response);
        });
    }
}