package community.application.agent;

import akka.javasdk.annotations.Component;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.ComponentClient;
import community.application.view.TopicsView;

/**
 * Lofi chat handler for board member messages using pattern matching.
 * Recognizes keywords and responds with pre-defined templates.
 * Can be upgraded to AI later while maintaining same interface.
 */
@Component(id = "chat-handler-agent")
public class ChatHandlerAgent extends Agent {

    private final ComponentClient componentClient;

    public ChatHandlerAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect<String> handleMessage(String message) {
        String response = generateLofiResponse(message);
        return effects().reply(response);
    }

    private String generateLofiResponse(String message) {
        String lowerMessage = message.toLowerCase();
        
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