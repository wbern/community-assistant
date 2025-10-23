package community.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

/**
 * HTTP endpoint for chat operations.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/chat")
public class ChatEndpoint {

    private final ComponentClient componentClient;

    public ChatEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String response) {}

    @Post("/message")
    public ChatResponse sendMessage(ChatRequest request) {
        String agentResponse = componentClient.forAgent()
            .inSession("terminal-session")
            .method(community.application.ChatHandlerAgent::handleMessage)
            .invoke(request.message());
        
        return new ChatResponse(agentResponse);
    }
}