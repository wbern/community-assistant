package community.api;

import community.application.agent.ChatHandlerAIAgent;

/**
 * Integration test for MCP endpoint with AI agent implementation.
 * Tests that the MCP endpoint properly integrates with ChatHandlerAIAgent.
 * 
 * Follows same pattern as ChatHandlerAIAgentIntegrationTest but adds MCP endpoint layer.
 */
public class CommunityAssistantMcpEndpointAIAgentTest extends CommunityAssistantMcpEndpointTestBase {

    @Override
    protected String invokeAgentAndGetResponse(String sessionId, String message) {
        // Direct agent call (same as ChatHandlerAIAgentIntegrationTest)
        return componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerAIAgent::handleMessage)
            .invoke(message);
    }

    @Override
    protected String invokeMcpEndpointAndGetResponse(String message, String sessionId) {
        // Through MCP endpoint layer
        return new CommunityAssistantMcpEndpoint(testKit.getComponentClient())
            .chat(message, sessionId, "ai-agent");
    }
}