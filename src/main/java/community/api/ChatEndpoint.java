package community.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import community.application.action.HttpEndpointLogger;

/**
 * HTTP endpoint for chat operations.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/chat")
public class ChatEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public ChatEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String response) {}

    @Post("/message")
    public ChatResponse sendMessage(ChatRequest request) {
        HttpEndpointLogger.logAccess("chat", "POST", "/chat/message", 200);
        
        String agentResponse = componentClient.forAgent()
            .inSession("terminal-session")
            .method(community.application.agent.ChatHandlerLofiAgent::handleMessage)
            .invoke(request.message());

        return new ChatResponse(agentResponse);
    }
}