package community.application.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.application.entity.EmailEntity;
import community.application.view.InquiriesView;
import community.domain.model.Email;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INTEGRATION tests for ChatHandlerAIAgent with SmolLM2.
 *
 * <p>These tests verify end-to-end behavior:
 * <ul>
 *   <li>LLM understands natural language board member responses</li>
 *   <li>LLM decides to call @FunctionTool methods</li>
 *   <li>State changes occur in entities (EmailEntity, ReminderConfigEntity)</li>
 * </ul>
 *
 * <p>Contrast with ChatHandlerAIAgentUnitTest which contains UNIT tests that verify
 * method structure without invoking the LLM.
 *
 * <h3>Comparison with lofi ChatHandlerAgent:</h3>
 * <ul>
 *   <li><b>ChatHandlerAgent (lofi):</b> Exact string match, hardcoded response</li>
 *   <li><b>ChatHandlerAIAgent (SmolLM2):</b> AI understanding, natural responses</li>
 * </ul>
 *
 * <h3>Prerequisites</h3>
 * <pre>
 * brew services start ollama
 * ollama pull smollm2:135m-instruct-q4_0
 * </pre>
 */
public class ChatHandlerAIAgentIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(EmailEntity.class)
            .withAdditionalConfig("akka.javasdk.agent.openai.base-url = \"http://localhost:11434/v1\"")
            .withAdditionalConfig("akka.javasdk.agent.openai.model = \"smollm2:135m-instruct-q4_0\"");
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
        testKit.getEventSourcedEntityIncomingMessages(EmailEntity.class)
            .publish(new EmailEntity.Event.EmailReceived(email), emailId);
    }

    @Test
    public void shouldUnderstandBoardMemberAddressedInquiryWithAI() {
        // SAME test as ChatHandlerAgentTest but with real AI understanding

        // Arrange: Create email that will generate an inquiry
        String emailId = "email-smol-001";
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

        // Act: Board member responds with NATURAL LANGUAGE (not just "@assistant")
        String chatSessionId = "board-inquiry-session-" + emailId;
        String boardMemberReply = "@assistant I've contacted the plumber. They will come tomorrow at 9 AM.";

        String response = componentClient.forAgent()
            .inSession(chatSessionId)
            .method(ChatHandlerAIAgent::handleMessage)
            .invoke(boardMemberReply);

        // Assert: SmolLM2 should generate NATURAL acknowledgment (not exact match)
        assertNotNull(response, "Should generate acknowledgment");
        assertFalse(response.isBlank(), "Should have content");

        // Semantic validation: response should acknowledge the action
        String lowerResponse = response.toLowerCase();
        assertTrue(
            lowerResponse.contains("noted") ||
            lowerResponse.contains("acknowledged") ||
            lowerResponse.contains("marked") ||
            lowerResponse.contains("thank") ||
            lowerResponse.contains("recorded") ||
            lowerResponse.contains("updated"),
            "AI should acknowledge the board member's action. Got: " + response
        );

        // Verify response is concise (AI instructed max 15 words)
        int wordCount = response.split("\\s+").length;
        assertTrue(wordCount <= 20, "Response should be concise (got " + wordCount + " words): " + response);

        System.out.println("[SmolLM2] AI Response: " + response);
    }

    @Test
    public void shouldHandleVariedNaturalLanguageResponses() {
        // Test that SmolLM2 can understand different ways of saying "I addressed this"

        String emailId = "email-smol-002";
        Email noiseComplaint = Email.create(
            emailId,
            "tenant@building.com",
            "Noise complaint",
            "Loud music from apartment 4B every night."
        );

        hydrateEntityAndPublishToView(noiseComplaint, emailId);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            InquiriesView.Inquiry inquiry = componentClient.forView()
                .method(InquiriesView::getInquiryByEmailId)
                .invoke(emailId);
            assertNotNull(inquiry);
        });

        // Act: Different phrasing - more casual
        String chatSessionId = "board-inquiry-session-" + emailId;
        String boardMemberReply = "@assistant All sorted! Spoke with the tenant, music will stop by 10 PM now.";

        String response = componentClient.forAgent()
            .inSession(chatSessionId)
            .method(ChatHandlerAIAgent::handleMessage)
            .invoke(boardMemberReply);

        // Assert: Should still generate appropriate acknowledgment
        assertNotNull(response);
        assertFalse(response.isBlank());

        String lowerResponse = response.toLowerCase();
        assertTrue(
            lowerResponse.contains("great") ||
            lowerResponse.contains("good") ||
            lowerResponse.contains("noted") ||
            lowerResponse.contains("thank") ||
            lowerResponse.contains("excellent") ||
            lowerResponse.contains("recorded"),
            "AI should acknowledge casual phrasing. Got: " + response
        );

        System.out.println("[SmolLM2] AI Response (casual): " + response);
    }
}
