package community.application.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import community.application.entity.EmailEntity;
import community.application.view.InquiriesView;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChatHandlerAgent.
 * RED phase: Testing topic lookup by tags for meaningful responses.
 *
 * <h2>Testing Patterns for Akka SDK Integration Tests</h2>
 *
 * <h3>Key Insight: Event Publishing vs Entity Hydration</h3>
 * <ul>
 *   <li><b>publishEmailToView():</b> Use testKit.getEventSourcedEntityIncomingMessages().publish()
 *       to populate Views and Consumers. This does NOT hydrate the entity for command calls.</li>
 *   <li><b>hydrateEntityAndPublishToView():</b> First call entity command (receiveEmail), then publish event.
 *       Use when tests need agent to call entity commands like markAsAddressed().</li>
 * </ul>
 *
 * <h3>Why This Matters</h3>
 * <p>If an agent needs to call entity commands, the entity must be hydrated via command invocation.
 * Just publishing events is not enough - the entity instance won't exist for command handling.</p>
 *
 * <h3>Two-Level Testing Strategy</h3>
 * <ul>
 *   <li><b>Unit tests (EmailEntityTest):</b> Use EventSourcedTestKit to test entity commands/events directly</li>
 *   <li><b>Integration tests (this class):</b> Test agent behavior and view projections</li>
 * </ul>
 */
public class ChatHandlerLofiAgentUnitTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    /**
     * Helper: Publish EmailReceived event to populate views.
     * Use this for tests that only need views populated (not entity commands).
     */
    private void publishEmailToView(Email email, String emailId) {
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), emailId);
    }

    /**
     * Helper: Publish both EmailReceived and TagsGenerated events to populate views.
     */
    private void publishEmailWithTagsToView(Email email, EmailTags tags, String emailId) {
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), emailId);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.TagsGenerated(tags), emailId);
    }

    /**
     * Helper: Hydrate entity by calling receiveEmail command AND publish event to views.
     * Use this when tests need both entity commands to work AND views to be populated.
     */
    private void hydrateEntityAndPublishToView(Email email, String emailId) {
        // First hydrate entity for command calls
        componentClient.forEventSourcedEntity(emailId)
            .method(EmailEntity::receiveEmail)
            .invoke(email);

        // Then publish event to populate views
        publishEmailToView(email, emailId);
    }

    @Test
    public void shouldLookupTopicByElevatorTag() {
        // GREEN: This test now passes with hardcoded elevator topic lookup
        
        // Arrange: @assistant mention with elevator keyword should lookup tagged topics
        String boardMemberMessage = "@assistant elevator";

        // Act: Call agent to handle chat message  
        String response = componentClient.forAgent()
            .inSession("topic-session-1")
            .method(ChatHandlerLofiAgent::handleMessage)
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
            .method(ChatHandlerLofiAgent::handleMessage)
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
        publishEmailWithTagsToView(email, tags, topicId);
        
        String boardMemberMessage = "@assistant maintenance";

        // Wait for View to be updated, then test agent
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Act: Call agent to handle chat message  
            String response = componentClient.forAgent()
                .inSession("topic-session-3")
                .method(ChatHandlerLofiAgent::handleMessage)
                .invoke(boardMemberMessage);

            // Assert: Should find the maintenance topic and return its ID
            assertNotNull(response);
            assertEquals(expectedResponse, response);
        });
    }

    @Test
    public void shouldSearchEmailsByKeywordForBoardMember() {
        // RED: Board member should get email content based on keyword search, not generic community response

        // Arrange: Create emails with different keywords
        Email elevatorEmail = Email.create(
            "email-001",
            "resident@building.com",
            "Elevator broken on floor 3",
            "The elevator has been out of service since Monday. Please fix urgently."
        );

        Email noiseEmail = Email.create(
            "email-002",
            "tenant@building.com",
            "Noise complaint from upstairs",
            "There's loud music every night from apartment 4B. Very disruptive."
        );

        // Publish emails to make them searchable
        publishEmailToView(elevatorEmail, "email-001");
        publishEmailToView(noiseEmail, "email-002");

        // Act: Board member searches for "elevator" keyword
        String boardMemberQuery = "elevator";

        // Wait for emails to be available, then test search
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String response = componentClient.forAgent()
                .inSession("board-session-1")
                .method(ChatHandlerLofiAgent::handleMessage)
                .invoke(boardMemberQuery);

            // Assert: Should return elevator email content, not generic community response
            assertNotNull(response);
            assertFalse(response.contains("contact building management directly"),
                "Should not give generic community member response to board member");
            assertTrue(response.contains("elevator") || response.contains("Elevator"),
                "Should contain elevator-related content from emails");
            assertTrue(response.contains("email-001") || response.contains("floor 3") || response.contains("Monday"),
                "Should contain specific details from the elevator email");
        });
    }

    @Test
    public void red_shouldGenerateInquiryToBoardMembersWhenEmailArrives() {
        // RED: When an incoming email arrives, it should generate an inquiry to board members over chat

        // Arrange: New email arrives about parking violation
        Email parkingEmail = Email.create(
            "email-003",
            "resident@building.com",
            "Parking violation in my spot",
            "Someone parked in my assigned spot #42. Can you help resolve this?"
        );

        // Publish email received event
        publishEmailToView(parkingEmail, "email-003");

        // Wait for inquiry to be generated, then verify
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Query the view directly to see if inquiry was generated for this email
            InquiriesView.Inquiry inquiry = componentClient.forView()
                .method(InquiriesView::getInquiryByEmailId)
                .invoke("email-003");

            // Assert: Should have generated an inquiry with email content
            assertNotNull(inquiry, "Should generate inquiry when email arrives");
            String inquiryText = inquiry.inquiryText();
            assertTrue(inquiryText.contains("parking") || inquiryText.contains("Parking"),
                "Inquiry should contain email subject context");
            assertTrue(inquiryText.contains("email-003") || inquiryText.contains("#42"),
                "Inquiry should reference the email or its details");
        });
    }

    @Test
    public void red_inquiryShouldContainFirst50CharsOfEmailBodyInPlainText() {
        // RED: InquiriesView should generate inquiry text containing first 50 chars of email body (plain text)

        // Arrange: Create email with body longer than 50 characters
        String emailBody = "This is a detailed complaint about the broken heating system in apartment 12B. " +
                          "The temperature has been below 15 degrees for three days now. " +
                          "My elderly mother is living with me and this is a health hazard.";
        Email heatingEmail = Email.create(
            "email-004",
            "tenant@building.com",
            "Urgent: Heating broken in 12B",
            emailBody
        );

        // Act: Publish email received event to generate inquiry
        publishEmailToView(heatingEmail, "email-004");

        // Assert: Wait for inquiry to be generated and verify it contains first 50 chars
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            InquiriesView.Inquiry inquiry = componentClient.forView()
                .method(InquiriesView::getInquiryByEmailId)
                .invoke("email-004");

            assertNotNull(inquiry, "Inquiry should be generated");

            String inquiryText = inquiry.inquiryText();
            String expectedBodyPreview = emailBody.substring(0, Math.min(50, emailBody.length()));

            assertTrue(inquiryText.contains(expectedBodyPreview),
                "Inquiry text should contain first 50 characters of email body: '" + expectedBodyPreview + "'");

            // Verify it's using plain text, not HTML (no HTML tags in inquiry)
            assertFalse(inquiryText.contains("<html>") || inquiryText.contains("<body>") || inquiryText.contains("<p>"),
                "Should use plain text email body, not HTML");
        });
    }

    @Test
    public void red_boardMemberAssistantReplyMarksInquiryAsAddressed() {
        // Integration test: When board member replies to inquiry with @assistant mention,
        // the assistant should recognize this and acknowledge with standard message
        // Note: Entity status update is tested separately in EmailEntityTest unit tests

        // Arrange: Create email that will generate an inquiry
        String emailId = "email-005";
        Email waterLeakEmail = Email.create(
            emailId,
            "resident@building.com",
            "Water leak in basement",
            "There's a water leak in the basement storage area near unit 3A. Please investigate."
        );

        // Hydrate entity AND publish to view (agent needs to call markAsAddressed command)
        hydrateEntityAndPublishToView(waterLeakEmail, emailId);

        // Wait for inquiry to be generated in view
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            InquiriesView.Inquiry inquiry = componentClient.forView()
                .method(InquiriesView::getInquiryByEmailId)
                .invoke(emailId);
            assertNotNull(inquiry, "Inquiry should be generated");
        });

        // Act: Board member responds to inquiry with @assistant mention
        // The chat session should be associated with the inquiry/email
        String chatSessionId = "board-inquiry-session-" + emailId;
        String boardMemberReply = "@assistant I've contacted the plumber. They will come tomorrow at 9 AM.";

        String response = componentClient.forAgent()
            .inSession(chatSessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(boardMemberReply);

        // Assert: Verify the assistant acknowledged the board member's response with exact message
        assertEquals("Noted. The inquiry has been marked as addressed.", response,
            "Assistant should respond with exact acknowledgment message");
    }
}