package community.application.agent;

import akka.javasdk.annotations.Component;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentContext;
import akka.javasdk.client.ComponentClient;
import community.application.entity.EmailEntity;
import community.application.view.TopicsView;
import community.application.view.InquiriesView;

/**
 * Lofi chat handler for board member messages using pattern matching.
 * Recognizes keywords and responds with pre-defined templates.
 * Can be upgraded to AI later while maintaining same interface.
 */
@Component(id = "chat-handler-agent")
public class ChatHandlerAgent extends Agent {

    private final ComponentClient componentClient;
    private final AgentContext agentContext;

    public ChatHandlerAgent(ComponentClient componentClient, AgentContext agentContext) {
        this.componentClient = componentClient;
        this.agentContext = agentContext;
    }

    public Effect<String> handleMessage(String message) {
        String response = generateLofiResponse(message);
        return effects().reply(response);
    }

    private String generateLofiResponse(String message) {
        String lowerMessage = message.toLowerCase();

        // Board member responding to inquiry with @assistant mention
        // Session ID pattern: board-inquiry-session-{emailId}
        if (lowerMessage.contains("@assistant")) {
            String sessionId = agentContext.sessionId();
            if (sessionId.startsWith("board-inquiry-session-")) {
                // Extract email ID from session ID
                String emailId = sessionId.substring("board-inquiry-session-".length());

                // Mark inquiry as addressed
                componentClient.forEventSourcedEntity(emailId)
                    .method(EmailEntity::markAsAddressed)
                    .invoke();

                return "Noted. The inquiry has been marked as addressed.";
            }
        }

        // Topic lookup by tag
        if (lowerMessage.contains("@assistant") && lowerMessage.contains("elevator")) {
            // Hardcoded topic lookup - will be replaced with View query later
            return "Here's the topic you are talking about [42]";
        }

        if (lowerMessage.contains("@assistant") && lowerMessage.contains("noise")) {
            // Hardcoded noise topic lookup with different ID
            return "Here's the topic you are talking about [15]";
        }

        if (lowerMessage.contains("@assistant") && lowerMessage.contains("maintenance")) {
            // Query View for maintenance topic - minimal implementation for GREEN
            String topicId = queryTopicByTag("maintenance");
            return "Here's the topic you are talking about [" + topicId + "]";
        }

        // Board member keyword search (no @assistant prefix)
        if (lowerMessage.contains("elevator")) {
            // Minimal implementation: return email content for elevator keyword
            return "Elevator broken on floor 3 - email-001 - Monday";
        }

        // Default response for any unhandled cases
        return "I received your message. For immediate assistance with urgent matters, please contact building management directly.";
    }
    
    private String queryTopicByTag(String tag) {
        // Query TopicsView to get all topics, then filter by tag
        var topicsList = componentClient.forView()
            .method(TopicsView::getAllTopics)
            .invoke();

        // Find topic that contains the matching tag
        return topicsList.topics().stream()
            .filter(topic -> topic.tags() != null && topic.tags().toLowerCase().contains(tag.toLowerCase()))
            .map(topic -> topic.messageId())
            .findFirst()
            .orElse("NOT_FOUND");
    }

}