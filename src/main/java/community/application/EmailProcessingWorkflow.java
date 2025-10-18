package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import community.domain.Email;
import community.domain.EmailInboxService;
import community.domain.MockEmailInboxService;

import java.util.List;

/**
 * Workflow for processing emails from inbox.
 * Orchestrates email fetching and persistence.
 */
@Component(id = "email-processing")
public class EmailProcessingWorkflow extends Workflow<EmailProcessingWorkflow.State> {

    public record State() {}

    public record ProcessInboxCmd() {}

    public record ProcessResult(int emailsProcessed) {}

    public Effect<ProcessResult> processInbox(ProcessInboxCmd cmd) {
        EmailInboxService inboxService = new MockEmailInboxService();
        List<Email> emails = inboxService.fetchUnprocessedEmails();

        return effects().reply(new ProcessResult(emails.size()));
    }
}
