package community.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.client.ComponentClient;
import community.application.agent.ChatHandlerAIAgent;
import community.application.agent.ChatHandlerLofiAgent;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(serverName = "community-assistant", serverVersion = "1.0.0")
public class CommunityAssistantMcpEndpoint {

    private final ComponentClient componentClient;

    public CommunityAssistantMcpEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @McpTool(
        name = "chat_with_ai_agent",
        description = "Chat with AI agent that uses LLM for intelligent responses"
    )
    public String chatWithAIAgent(String message, String sessionId) {
        return componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerAIAgent::handleMessage)
            .invoke(message);
    }

    @McpTool(
        name = "chat_with_lofi_agent", 
        description = "Chat with Lofi agent that uses pattern matching for fast responses"
    )
    public String chatWithLofiAgent(String message, String sessionId) {
        return componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(message);
    }

    public String chat(String message, String sessionId, String agentType) {
        return switch (agentType) {
            case "ai-agent" -> chatWithAIAgent(message, sessionId);
            case "lofi-agent" -> chatWithLofiAgent(message, sessionId);
            default -> "Unknown agent type: " + agentType;
        };
    }
}