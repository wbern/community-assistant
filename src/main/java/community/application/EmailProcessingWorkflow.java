package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

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
        return effects().reply(new ProcessResult(0));
    }
}
