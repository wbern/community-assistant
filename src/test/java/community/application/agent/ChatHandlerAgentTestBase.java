package community.application.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import community.application.entity.EmailEntity;
import community.application.entity.ActiveInquiryEntity;
import community.application.entity.OutboundChatMessageEntity;
import community.application.view.InquiriesView;
import community.application.action.ReminderAction;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for ChatHandler agent tests.
 *
 * <p>Defines the behavioral contract that both LofiAgent and AIAgent must fulfill:
 * <ul>
 *   <li>Topic lookup by tags</li>
 *   <li>Question mark detection for inquiry vs question disambiguation</li>
 *   <li>Email keyword search</li>
 *   <li>Inquiry generation and addressing</li>
 * </ul>
 *
 * <p>Subclasses specify which agent implementation to test and how to configure it.
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
 */
public abstract class ChatHandlerAgentTestBase extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class);
    }

    /**
     * Subclasses must implement to specify which agent to test.
     */
    protected abstract String invokeAgentAndGetResponse(String sessionId, String message);

    /**
     * Helper: Publish EmailReceived event to populate views.
     * Use this for tests that only need views populated (not entity commands).
     */
    protected void publishEmailToView(Email email, String emailId) {
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), emailId);
    }

    /**
     * Helper: Publish both EmailReceived and TagsGenerated events to populate views.
     */
    protected void publishEmailWithTagsToView(Email email, EmailTags tags, String emailId) {
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), emailId);
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.TagsGenerated(tags), emailId);
    }

    /**
     * Helper: Hydrate entity by calling receiveEmail command AND publish event to views.
     * Use this when tests need both entity commands to work AND views to be populated.
     */
    protected void hydrateEntityAndPublishToView(Email email, String emailId) {
        // First hydrate entity for command calls
        componentClient.forEventSourcedEntity(emailId)
            .method(EmailEntity::receiveEmail)
            .invoke(email);

        // Then publish event to populate views
        publishEmailToView(email, emailId);
    }

    @Test
    public void shouldLookupTopicByElevatorTag() {
        // Arrange: Create email with elevator tag and publish to populate View
        String topicId = "42";  // Use known ID for consistency

        Email elevatorEmail = Email.create(
            topicId,
            "maintenance@building.com",
            "Elevator Maintenance Schedule",
            "Monthly elevator inspection and maintenance"
        );
        EmailTags tags = EmailTags.create(
            Set.of("elevator", "maintenance"),
            "Elevator maintenance topic",
            "Building A, Elevator Bank"
        );

        // Publish events to populate TopicsView
        publishEmailWithTagsToView(elevatorEmail, tags, topicId);

        String boardMemberMessage = "@assistant elevator?";

        // Wait for View to be updated, then test agent
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            // Act: Call agent to handle chat message
            String response = invokeAgentAndGetResponse("topic-session-1", boardMemberMessage);

            // Assert: Should lookup and return topic with elevator tag
            assertNotNull(response);
            assertTrue(response.contains("Here's the topic you are talking about") ||
                       response.contains("elevator") || response.contains("Elevator"),
                "Should mention elevator topic. Got: " + response);
            assertTrue(response.matches(".*\\[\\d+\\].*") || response.contains("42"),
                "Should contain topic ID. Got: " + response);
        });
    }

    @Test
    public void shouldLookupTopicByNoiseTag() {
        // Arrange: Create email with noise tag and publish to populate View
        String topicId = "15";  // Use known ID, different from elevator

        Email noiseEmail = Email.create(
            topicId,
            "complaints@building.com",
            "Noise Complaint Procedures",
            "Guidelines for handling noise complaints from residents"
        );
        EmailTags tags = EmailTags.create(
            Set.of("noise", "complaints"),
            "Noise complaint procedures",
            "Building Rules, Section 3"
        );

        // Publish events to populate TopicsView
        publishEmailWithTagsToView(noiseEmail, tags, topicId);

        String boardMemberMessage = "@assistant noise?";

        // Wait for View to be updated, then test agent
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            // Act: Call agent to handle chat message
            String response = invokeAgentAndGetResponse("topic-session-2", boardMemberMessage);

            // Assert: Should lookup and return topic with noise tag (different ID than elevator)
            assertNotNull(response);
            assertTrue(response.contains("Here's the topic you are talking about") ||
                       response.contains("noise") || response.contains("Noise"),
                "Should mention noise topic. Got: " + response);
            assertTrue(response.matches(".*\\[\\d+\\].*") || response.contains("15"),
                "Should contain topic ID. Got: " + response);
            assertFalse(response.contains("[42]"),
                "Should NOT be same ID as elevator topic. Got: " + response);
        });
    }

    @Test
    public void shouldQueryViewForTopicLookup() {
        // Arrange: Create email with maintenance tag and publish events to populate View
        String topicId = "67890";  // Use known ID instead of random for GREEN phase
        String expectedTopicInfo = topicId;

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

        String boardMemberMessage = "@assistant maintenance?";

        // Wait for View to be updated, then test agent
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            // Act: Call agent to handle chat message
            String response = invokeAgentAndGetResponse("topic-session-3", boardMemberMessage);

            // Assert: Should find the maintenance topic and return its ID
            assertNotNull(response);
            assertTrue(response.contains(expectedTopicInfo) ||
                       response.contains("maintenance") || response.contains("Maintenance"),
                "Should mention maintenance topic or ID. Got: " + response);
        });
    }

    @Test
    public void red_shouldGenerateInquiryToBoardMembersWhenEmailArrives() {
        // RED: When an incoming email arrives, it should generate an inquiry to board members over chat
        // AND set this email as the active inquiry in ActiveInquiryEntity

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
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
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

            // Assert: Should have set this email as the active inquiry
            String activeInquiryEmailId = componentClient.forKeyValueEntity("active-inquiry")
                .method(ActiveInquiryEntity::getActiveInquiryEmailId)
                .invoke();
            assertEquals("email-003", activeInquiryEmailId,
                "Should set email-003 as the active inquiry");
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
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
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
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            InquiriesView.Inquiry inquiry = componentClient.forView()
                .method(InquiriesView::getInquiryByEmailId)
                .invoke(emailId);
            assertNotNull(inquiry, "Inquiry should be generated");
        });

        // Act: Board member responds to inquiry with @assistant mention
        // The chat session should be associated with the inquiry/email
        String chatSessionId = "board-inquiry-session-" + emailId;
        String boardMemberReply = "@assistant I've contacted the plumber. They will come tomorrow at 9 AM.";

        String response = invokeAgentAndGetResponse(chatSessionId, boardMemberReply);

        // Assert: Verify the assistant acknowledged the board member's response
        // Lofi: exact match "Noted. The inquiry has been marked as addressed."
        // AI: semantic match (noted/acknowledged/marked/thank/recorded/updated)
        assertNotNull(response, "Should generate acknowledgment");
        assertFalse(response.isBlank(), "Should have content");

        String lowerResponse = response.toLowerCase();
        assertTrue(
            lowerResponse.contains("noted") ||
            lowerResponse.contains("acknowledged") ||
            lowerResponse.contains("marked") ||
            lowerResponse.contains("thank") ||
            lowerResponse.contains("recorded") ||
            lowerResponse.contains("updated") ||
            lowerResponse.contains("addressed"),
            "Should acknowledge inquiry addressing. Got: " + response
        );
    }

    @Test
    public void red_questionMarkSignalsNewQuestionNotInquiryReply() {
        // RED: When there's an active inquiry, question ending with '?' should be treated as new question,
        // NOT as a reply to the inquiry

        // Arrange: Create email that will generate an inquiry
        String emailId = "email-006";
        Email gardenEmail = Email.create(
            emailId,
            "resident@building.com",
            "Garden maintenance",
            "The garden needs trimming and the sprinklers are broken."
        );

        // Hydrate entity AND publish to view (create active inquiry)
        hydrateEntityAndPublishToView(gardenEmail, emailId);

        // Wait for inquiry to be generated in view
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            InquiriesView.Inquiry inquiry = componentClient.forView()
                .method(InquiriesView::getInquiryByEmailId)
                .invoke(emailId);
            assertNotNull(inquiry, "Inquiry should be generated");
        });

        // Act: Board member asks a NEW question (ends with ?) despite active inquiry
        String chatSessionId = "board-inquiry-session-" + emailId;
        String boardMemberQuestion = "@assistant elevator?";

        String response = invokeAgentAndGetResponse(chatSessionId, boardMemberQuestion);

        // Assert: Should answer the question about elevator, NOT mark inquiry as addressed
        assertNotNull(response);
        assertTrue(response.contains("Here's the topic you are talking about") ||
                   response.contains("elevator") || response.contains("Elevator") ||
                   response.contains("[42]") || response.matches(".*\\[\\d+\\].*"),
            "Should answer question about elevator topic. Got: " + response);
        assertFalse(response.contains("Noted. The inquiry has been marked as addressed.") &&
                    !response.contains("elevator"),
            "Should NOT mark inquiry as addressed when question ends with ?. Got: " + response);
    }

    @Test
    public void red_shouldSendReminderInquiryWhenTimerReaches() {
        // RED: When an email is the active inquiry and reminder timer reaches,
        // assistant should send a reminder message via OutboundChatMessageEntity

        // Arrange: Set up active inquiry directly in KVE
        String emailId = "email-007";

        componentClient.forKeyValueEntity("active-inquiry")
            .method(ActiveInquiryEntity::setActiveInquiry)
            .invoke(emailId);

        // Act: Trigger ReminderAction directly (simulates timer firing)
        timerScheduler.createSingleTimer(
            "reminder-test",
            Duration.ofMillis(0),
            componentClient.forTimedAction()
                .method(ReminderAction::sendReminderForActiveInquiry)
                .deferred());

        // Assert: OutboundChatMessageEntity should have persisted the reminder message
        // Note: The ReminderAction will generate a message ID based on the email ID
        String expectedMessageId = "reminder-" + emailId;
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var chatMessage = componentClient.forEventSourcedEntity(expectedMessageId)
                .method(OutboundChatMessageEntity::getMessage)
                .invoke();

            assertNotNull(chatMessage, "Should have sent a reminder message");

            String messageText = chatMessage.text();
            assertTrue(messageText.toLowerCase().contains("reminder") ||
                       messageText.toLowerCase().contains("following up") ||
                       messageText.contains(emailId),
                "Reminder should reference the active inquiry. Got: " + messageText);
        });
    }
}
