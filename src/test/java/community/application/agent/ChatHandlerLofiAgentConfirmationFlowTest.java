package community.application.agent;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RED: Focused unit test for ChatHandlerLofiAgent confirmation flow.
 * Tests that vague email requests trigger confirmation rather than immediate email creation.
 */
public class ChatHandlerLofiAgentConfirmationFlowTest extends TestKitSupport {

    @Test
    public void red_shouldAskForConfirmationWhenVagueEmailRequestWithActiveInquiry() {
        // RED: This test should fail because current lofi agent creates emails immediately
        // instead of asking for confirmation on vague requests
        
        // Act: Send vague email request through public API (no specific email ID mentioned)
        String vagueRequest = "@assistant send email to tenant@building.com saying heating will be fixed";
        String sessionId = "test-session-123";
        
        String response = componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(vagueRequest);
        
        // Assert: Should ask for confirmation about active inquiry, NOT create email immediately
        assertNotNull(response, "Agent should respond to vague email request");
        assertTrue(response.contains("active inquiry") || response.contains("confirm"), 
            "Should ask for confirmation about active inquiry instead of creating email immediately. Got: " + response);
        assertFalse(response.contains("Email draft created") || response.contains("Email created"), 
            "Should NOT create email immediately for vague requests. Got: " + response);
    }

    @Test
    public void red_shouldCreateEmailAfterUserConfirmsActiveInquiry() {
        // RED: This test should fail because current lofi agent doesn't handle confirmation responses
        // When user confirms with "yes", agent should create OutboundEmailEntity with active inquiry as thread reference
        
        // Arrange: First, simulate the vague request that stores state
        String vagueRequest = "@assistant send email to tenant@building.com saying heating will be fixed";
        String sessionId = "test-session-456";
        
        // First interaction: Make vague request (should store state)
        componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(vagueRequest);
        
        // Act: Send confirmation response in the SAME session
        String confirmationResponse = "yes, that's correct";
        
        String response = componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(confirmationResponse);
        
        // Assert: Should create email and indicate it was created
        assertNotNull(response, "Agent should respond to confirmation");
        assertTrue(response.contains("Email created") || response.contains("created"), 
            "Should indicate email was created after confirmation. Got: " + response);
        assertFalse(response.contains("confirm") || response.contains("active inquiry"), 
            "Should NOT ask for confirmation again. Got: " + response);
    }
}