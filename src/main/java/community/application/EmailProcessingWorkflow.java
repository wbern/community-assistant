package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import community.domain.Email;
import community.domain.EmailInboxService;
import community.domain.EmailTags;
import community.domain.MockEmailInboxService;

import java.util.ArrayList;
import java.util.List;

/**
 * Workflow for processing emails from inbox.
 * Orchestrates email fetching and persistence.
 */
@Component(id = "email-processing")
public class EmailProcessingWorkflow extends Workflow<EmailProcessingWorkflow.State> {

    private final ComponentClient componentClient;

    public EmailProcessingWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record State() {}

    public record ProcessInboxCmd() {}

    public record ProcessResult(int emailsProcessed, List<EmailTags> emailTags) {}

    public Effect<ProcessResult> processInbox(ProcessInboxCmd cmd) {
        EmailInboxService inboxService = new MockEmailInboxService();
        List<Email> emails = inboxService.fetchUnprocessedEmails();

        List<EmailTags> allTags = new ArrayList<>();

        // Persist each email to EmailEntity and generate tags
        for (Email email : emails) {
            // Persist email using messageId as entity ID (allows multiple emails from same sender)
            componentClient.forEventSourcedEntity(email.getMessageId())
                .method(EmailEntity::receiveEmail)
                .invoke(email);

            // Generate tags using AI agent
            EmailTags tags = componentClient.forAgent()
                .inSession(email.getMessageId())  // Use messageId as session ID for consistency
                .method(EmailTaggingAgent::tagEmail)
                .invoke(email);

            // Persist tags to EmailEntity using messageId
            componentClient.forEventSourcedEntity(email.getMessageId())
                .method(EmailEntity::addTags)
                .invoke(tags);

            allTags.add(tags);
        }

        return effects().reply(new ProcessResult(emails.size(), allTags));
    }
}
