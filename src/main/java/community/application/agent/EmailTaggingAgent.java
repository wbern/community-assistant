package community.application.agent;

import akka.javasdk.annotations.Component;
import akka.javasdk.agent.Agent;
import community.domain.model.Email;
import community.domain.model.EmailTags;

/**
 * AI Agent for generating free-form tags from email content.
 * Uses LLM to analyze email and produce structured tags.
 */
@Component(id = "email-tagging-agent")
public class EmailTaggingAgent extends Agent {

    private static final String SYSTEM_PROMPT = """
        You are an email classification assistant for a residential community board.

        Your task is to analyze incoming emails and generate:
        1. Free-form tags that describe the email (e.g., "urgent", "maintenance", "elevator", "complaint")
        2. A one-sentence summary of the email
        3. Location mentioned in the email (if any, otherwise null)

        Guidelines for tags:
        - Generate 1-5 relevant tags
        - Use lowercase, hyphenated format (e.g., "noise-complaint", "building-a")
        - Consider urgency, category, location, and domain
        - Be specific where possible (e.g., "elevator" rather than just "maintenance")

        Guidelines for summary:
        - One sentence maximum
        - Capture the key issue or request
        - Use active voice

        Guidelines for location:
        - Extract specific building, unit, or area if mentioned
        - Use format like "Building A, Elevator" or "Unit 205"
        - Return null if no specific location is mentioned

        Respond ONLY with a valid JSON object matching the EmailTags structure.
        """;

    public Effect<EmailTags> tagEmail(Email email) {
        String userMessage = String.format("""
            From: %s
            Subject: %s

            %s
            """,
            email.getFrom(),
            email.getSubject(),
            email.getBody()
        );

        // Remove fallback - let AI failures propagate to caller for explicit handling
        return effects()
            .systemMessage(SYSTEM_PROMPT)
            .userMessage(userMessage)
            .responseConformsTo(EmailTags.class)
            .thenReply();
    }
}
