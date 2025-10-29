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
 *
 * <p>Contrast with ChatHandlerAIAgent which uses LLM for natural language understanding.
 */
@Component(id = "chat-handler-lofi-agent")
public class ChatHandlerLofiAgent extends Agent {

    private final ComponentClient componentClient;
    private final AgentContext agentContext;

    public ChatHandlerLofiAgent(ComponentClient componentClient, AgentContext agentContext) {
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
        // Only treat as inquiry reply if message does NOT end with '?'
        if (lowerMessage.contains("@assistant") && !message.trim().endsWith("?")) {
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

        // Topic lookup by tag - queries TopicsView for real data
        if (lowerMessage.contains("@assistant") && lowerMessage.contains("elevator")) {
            String topicId = queryTopicByTag("elevator");
            return "Here's the topic you are talking about [" + topicId + "]";
        }

        if (lowerMessage.contains("@assistant") && lowerMessage.contains("noise")) {
            String topicId = queryTopicByTag("noise");
            return "Here's the topic you are talking about [" + topicId + "]";
        }

        if (lowerMessage.contains("@assistant") && lowerMessage.contains("maintenance")) {
            String topicId = queryTopicByTag("maintenance");
            return "Here's the topic you are talking about [" + topicId + "]";
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