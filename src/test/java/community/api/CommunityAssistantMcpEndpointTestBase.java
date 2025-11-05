package community.api;

import akka.javasdk.testkit.TestKit;
import community.application.agent.ChatHandlerAgentTestBase;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for MCP endpoint tests.
 * 
 * Defines the behavioral contract that MCP endpoints must fulfill when 
 * integrating with different agent implementations (AI and Lofi agents).
 * 
 * Subclasses specify which agent implementation to test through the endpoint
 * by implementing the invokeAgentAndGetResponse method (same pattern as ChatHandlerAgentTestBase).
 */
public abstract class CommunityAssistantMcpEndpointTestBase extends ChatHandlerAgentTestBase {

    /**
     * Test that MCP endpoint properly integrates with the agent implementation.
     * Uses same pattern as direct agent tests but goes through MCP endpoint layer.
     */

    @Test
    void shouldIntegrateWithAgentViaEndpointForElevatorQuery() {
        // Arrange: Create topic data for agent to find via MCP endpoint
        String topicId = "elevator-topic-42";
        Email elevatorEmail = Email.create(
            topicId,
            "maintenance@building.com",
            "Elevator Maintenance Schedule",
            "Monthly elevator inspection and maintenance procedures"
        );
        EmailTags tags = EmailTags.create(
            Set.of("elevator", "maintenance"),
            "Elevator maintenance topic",
            "Building A, Elevator Bank"
        );

        // Publish events to populate TopicsView for agent to query
        publishEmailWithTagsToView(elevatorEmail, tags, topicId);

        String message = "Can you help me with elevator maintenance?";
        String sessionId = "mcp-session-123";
        
        // Wait for view to be populated, then test MCP endpoint integration
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            // When: Test agent through direct call (same as base class tests)
            String directResponse = invokeAgentAndGetResponse(sessionId, message);
            
            // When: Test same agent through MCP endpoint layer
            String mcpResponse = invokeMcpEndpointAndGetResponse(message, sessionId);
            
            // Then: Both should work and give similar results
            assertNotNull(directResponse, "Should get response from direct agent call");
            assertNotNull(mcpResponse, "Should get response from MCP endpoint");
            assertFalse(directResponse.isBlank(), "Direct response should have content");
            assertFalse(mcpResponse.isBlank(), "MCP response should have content");
            
            // Both should work - content depends on agent implementation
            // AI agents may find topics, Lofi agents may give generic responses
            assertTrue(directResponse.toLowerCase().contains("elevator") || 
                      directResponse.toLowerCase().contains("maintenance") ||
                      directResponse.contains(topicId) ||
                      directResponse.matches(".*\\[\\d+\\].*") ||
                      directResponse.toLowerCase().contains("received your message"),
                "Direct agent should respond appropriately. Got: " + directResponse);
                
            assertTrue(mcpResponse.toLowerCase().contains("elevator") || 
                      mcpResponse.toLowerCase().contains("maintenance") ||
                      mcpResponse.contains(topicId) ||
                      mcpResponse.matches(".*\\[\\d+\\].*") ||
                      mcpResponse.toLowerCase().contains("received your message"),
                "MCP endpoint should respond appropriately. Got: " + mcpResponse);
        });
    }

    /**
     * Subclasses must implement to specify how to call the agent through MCP endpoint.
     * This allows testing the same agent through both direct calls and MCP endpoint.
     */
    protected abstract String invokeMcpEndpointAndGetResponse(String message, String sessionId);
}