package community.application.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import community.application.entity.EmailEntity;
import community.application.entity.ActiveInquiryEntity;
import community.application.entity.OutboundChatMessageEntity;
import community.application.entity.OutboundEmailEntity;
import community.application.entity.ReminderConfigEntity;
import community.application.view.InquiriesView;
import community.application.action.ReminderAction;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static community.application.agent.ChatConstants.*;

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
            String activeInquiryEmailId = componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
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
        String chatSessionId = BOARD_INQUIRY_SESSION_PREFIX + emailId;
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
        String chatSessionId = BOARD_INQUIRY_SESSION_PREFIX + emailId;
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
    public void red_shouldScheduleReminderTimerWhenInquiryCreated() {
        // RED: When an email arrives and creates an active inquiry,
        // it should schedule a reminder timer that fires after the configured interval

        // Arrange: Set reminder interval to 5 seconds for testing
        componentClient.forKeyValueEntity("reminder-config")
            .method(ReminderConfigEntity::setInterval)
            .invoke(5);

        String emailId = "email-008";
        Email urgentEmail = Email.create(
            emailId,
            "resident@building.com",
            "Urgent plumbing issue",
            "Water leak in my apartment, please help!"
        );

        // Act: Publish email to trigger inquiry creation and timer scheduling
        publishEmailToView(urgentEmail, emailId);

        // Assert: Verify active inquiry was set
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String activeInquiryEmailId = componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
                .method(ActiveInquiryEntity::getActiveInquiryEmailId)
                .invoke();
            assertEquals(emailId, activeInquiryEmailId,
                "Should set email as active inquiry");
        });

        // Assert: Verify that reminder message is sent after timer fires (proof timer was scheduled)
        String expectedMessageId = "reminder-" + emailId;
        Awaitility.await()
            .pollDelay(6, TimeUnit.SECONDS)  // Wait for timer to fire (5s interval + 1s buffer)
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var chatMessage = componentClient.forEventSourcedEntity(expectedMessageId)
                    .method(OutboundChatMessageEntity::getMessage)
                    .invoke();

                assertNotNull(chatMessage,
                    "Timer should have fired and created reminder message");
                assertTrue(chatMessage.text().toLowerCase().contains("reminder") ||
                           chatMessage.text().toLowerCase().contains("following up"),
                    "Message should be a reminder. Got: " + chatMessage.text());
            });
    }

    @Test
    public void red_shouldSendReminderInquiryWhenTimerReaches() {
        // RED: When an email is the active inquiry and reminder timer reaches,
        // assistant should send a reminder message via OutboundChatMessageEntity

        // Arrange: Set up active inquiry directly in KVE
        String emailId = "email-007";

        componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
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

    @Test
    public void red_shouldSendEmailWhenBoardMemberRequestsEmailToUser() {
        // RED: Integration test - when board member sends text command asking to send email to user,
        // agent should recognize this and use email sending tool to create OutboundEmailEntity
        
        // Arrange: Board member wants to send email reply to a resident
        String boardMemberMessage = "@assistant send email to resident@community.com about elevator repair scheduled tomorrow at 10am";
        String sessionId = "board-session-123";
        
        // Act: Send message to agent
        String response = invokeAgentAndGetResponse(sessionId, boardMemberMessage);
        
        // Assert: Agent should recognize email sending intent and create OutboundEmailEntity
        assertNotNull(response, "Agent should respond to email sending request");
        assertFalse(response.isBlank(), "Response should have content");
        
        // Verify OutboundEmailEntity was created (agent should call the email sending tool)
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            // The agent should have used email sending tool which creates OutboundEmailEntity
            // We expect the entity to be created with some ID (agent will determine appropriate ID)
            var outboundEmail = componentClient.forEventSourcedEntity(OUTBOUND_EMAIL_ENTITY_ID)
                .method(OutboundEmailEntity::getDraft)
                .invoke();
                
            assertNotNull(outboundEmail, "Agent should have created outbound email draft");
            assertTrue(outboundEmail.recipientEmail().contains("resident@community.com"),
                "Should send to requested recipient");
            assertTrue(outboundEmail.subject().toLowerCase().contains("elevator") ||
                       outboundEmail.body().toLowerCase().contains("elevator"),
                "Email should contain elevator repair information");
        });
    }

    @Test
    public void red_shouldUseOriginalThreadIdWhenReplyingToExistingEmail() {
        // RED: When agent is asked to reply to a user from an existing EmailEntity,
        // it should use the original email's thread ID to maintain Gmail conversation threading
        
        // Arrange: Create an existing email that was received (this represents an inquiry)
        String originalEmailId = "email-thread-123";
        Email originalEmail = Email.create(
            originalEmailId,
            "resident@community.com", 
            "Broken elevator in building A",
            "The elevator has been broken for 2 days. When will it be fixed?"
        );
        
        // Hydrate entity so agent can access the original email data
        hydrateEntityAndPublishToView(originalEmail, originalEmailId);
        
        // Wait for email to be available
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var storedEmail = componentClient.forEventSourcedEntity(originalEmailId)
                .method(EmailEntity::getEmail)
                .invoke();
            assertNotNull(storedEmail, "Original email should be stored");
        });
        
        // Act: Board member asks agent to reply to the resident about this specific email
        String boardMemberMessage = "@assistant reply to resident@community.com about email " + originalEmailId + " saying elevator will be fixed tomorrow";
        String sessionId = "board-session-reply-test";
        
        String response = invokeAgentAndGetResponse(sessionId, boardMemberMessage);
        
        // Assert: Agent should create OutboundEmailEntity with original thread reference
        assertNotNull(response, "Agent should respond to reply request");
        assertFalse(response.isBlank(), "Response should have content");
        
        // Verify OutboundEmailEntity was created with thread reference
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var outboundEmail = componentClient.forEventSourcedEntity(OUTBOUND_EMAIL_ENTITY_ID)
                .method(OutboundEmailEntity::getDraft)
                .invoke();
                
            assertNotNull(outboundEmail, "Agent should have created outbound email draft");
            assertEquals(originalEmailId, outboundEmail.originalCaseId(),
                "Outbound email should reference the original email thread ID");
            assertTrue(outboundEmail.recipientEmail().contains("resident@community.com"),
                "Should reply to the original sender");
            assertTrue(outboundEmail.subject().contains("Re:") || outboundEmail.subject().contains("elevator"),
                "Subject should indicate this is a reply or contain original topic");
        });
    }

    @Test
    public void red_shouldCheckActiveInquiryWhenNoMessageIdProvidedAndAskForConfirmation() {
        // RED: When board member asks to send email without specifying message ID,
        // agent should check active inquiry and ask for confirmation with inquiry details
        
        // Arrange: Set up an active inquiry first
        String activeEmailId = "email-active-inquiry-456";
        Email activeInquiry = Email.create(
            activeEmailId,
            "tenant@building.com",
            "Heating broken in apartment 12A",
            "My heating has been broken for 3 days. The temperature is only 15 degrees."
        );
        
        // Set this email as the active inquiry
        componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
            .method(ActiveInquiryEntity::setActiveInquiry)
            .invoke(activeEmailId);
        
        // Hydrate the email entity so agent can access details
        hydrateEntityAndPublishToView(activeInquiry, activeEmailId);
        
        // Wait for active inquiry to be set
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String currentActiveInquiry = componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
                .method(ActiveInquiryEntity::getActiveInquiryEmailId)
                .invoke();
            assertEquals(activeEmailId, currentActiveInquiry, "Active inquiry should be set");
        });
        
        // Act: Board member asks to send email without specifying message ID
        String boardMemberMessage = "@assistant send email to tenant@building.com saying heating will be fixed today";
        String sessionId = "board-session-inquiry-check";
        
        String response = invokeAgentAndGetResponse(sessionId, boardMemberMessage);
        
        // Assert: Agent should ask for confirmation about the active inquiry
        assertNotNull(response, "Agent should respond with confirmation question");
        assertFalse(response.isBlank(), "Response should have content");
        
        // Response should mention the active inquiry and ask for confirmation
        String lowerResponse = response.toLowerCase();
        assertTrue(lowerResponse.contains("heating") || lowerResponse.contains("apartment") || lowerResponse.contains("12a"),
            "Should mention details from active inquiry. Got: " + response);
        assertTrue(lowerResponse.contains("confirm") || lowerResponse.contains("about") || lowerResponse.contains("regarding") || lowerResponse.contains("?"),
            "Should ask for confirmation. Got: " + response);
        
        // No OutboundEmailEntity should be created yet (awaiting confirmation)
        // Check by verifying the entity state is empty (null recipients)
        var outboundEmail = componentClient.forEventSourcedEntity(OUTBOUND_EMAIL_ENTITY_ID)
            .method(OutboundEmailEntity::getDraft)
            .invoke();
        assertNull(outboundEmail.recipientEmail(), 
            "OutboundEmailEntity should not have recipient set before confirmation. Got: " + outboundEmail.recipientEmail());
    }

    @Test
    public void red_shouldSendEmailAfterUserConfirmsActiveInquiry() {
        // RED: When user confirms they want to send email about active inquiry,
        // agent should remember the original request and proceed with email creation
        // using session memory to track the confirmation flow
        
        // Arrange: Set up an active inquiry and get to confirmation state first
        String activeEmailId = "email-confirm-flow-789";
        Email activeInquiry = Email.create(
            activeEmailId,
            "resident@building.com",
            "Parking violation in spot #15",
            "Someone has been parking in my assigned spot for 2 days."
        );
        
        // Set this email as the active inquiry
        componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
            .method(ActiveInquiryEntity::setActiveInquiry)
            .invoke(activeEmailId);
        
        // Hydrate the email entity so agent can access details
        hydrateEntityAndPublishToView(activeInquiry, activeEmailId);
        
        // Wait for active inquiry to be set
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String currentActiveInquiry = componentClient.forKeyValueEntity(ACTIVE_INQUIRY_ENTITY_ID)
                .method(ActiveInquiryEntity::getActiveInquiryEmailId)
                .invoke();
            assertEquals(activeEmailId, currentActiveInquiry, "Active inquiry should be set");
        });
        
        // First, trigger the confirmation question
        String sessionId = "board-session-confirm-flow";
        String initialMessage = "@assistant send email to resident@building.com saying parking violation has been resolved";
        
        String confirmationResponse = invokeAgentAndGetResponse(sessionId, initialMessage);
        
        // Verify we got a confirmation question
        assertNotNull(confirmationResponse);
        assertTrue(confirmationResponse.toLowerCase().contains("parking") || confirmationResponse.toLowerCase().contains("confirm"),
            "Should ask for confirmation about parking inquiry");
        
        // Act: User confirms they want to send email about the active inquiry
        String confirmationMessage = "yes, that's correct";
        
        String finalResponse = invokeAgentAndGetResponse(sessionId, confirmationMessage);
        
        // Assert: Agent should now create the OutboundEmailEntity with thread reference to active inquiry
        assertNotNull(finalResponse, "Agent should respond after confirmation");
        assertFalse(finalResponse.isBlank(), "Response should have content");
        
        // Agent should have created outbound email with active inquiry as thread reference
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var outboundEmail = componentClient.forEventSourcedEntity(OUTBOUND_EMAIL_ENTITY_ID)
                .method(OutboundEmailEntity::getDraft)
                .invoke();
                
            assertNotNull(outboundEmail, "Agent should have created outbound email after confirmation");
            assertEquals("resident@building.com", outboundEmail.recipientEmail(),
                "Should send to requested recipient");
            assertEquals(activeEmailId, outboundEmail.originalCaseId(),
                "Should use active inquiry as thread reference for proper Gmail threading");
            assertTrue(outboundEmail.subject().toLowerCase().contains("parking") ||
                       outboundEmail.body().toLowerCase().contains("parking") ||
                       outboundEmail.body().toLowerCase().contains("resolved"),
                "Email should contain parking resolution information");
        });
    }
}
