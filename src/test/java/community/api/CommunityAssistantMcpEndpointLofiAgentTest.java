package community.api;

import community.application.agent.ChatHandlerLofiAgent;

/**
 * Integration test for MCP endpoint with Lofi agent implementation.
 * Tests that the MCP endpoint properly integrates with ChatHandlerLofiAgent.
 * 
 * Follows same pattern as ChatHandlerLofiAgentUnitTest but adds MCP endpoint layer.
 */
public class CommunityAssistantMcpEndpointLofiAgentTest extends CommunityAssistantMcpEndpointTestBase {

    @Override
    protected String invokeAgentAndGetResponse(String sessionId, String message) {
        // Direct agent call (same as ChatHandlerLofiAgentUnitTest)
        return componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(message);
    }

    @Override
    protected String invokeMcpEndpointAndGetResponse(String message, String sessionId) {
        // Through MCP endpoint layer
        return new CommunityAssistantMcpEndpoint(testKit.getComponentClient())
            .chat(message, sessionId, "lofi-agent");
    }
}