package community.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import community.application.workflow.EmailProcessingWorkflow;
import akka.javasdk.http.AbstractHttpEndpoint;
import community.application.action.HttpEndpointLogger;

/**
 * HTTP endpoint for email processing operations.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/process-inbox")
public class EmailEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public EmailEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post
    public EmailProcessingWorkflow.ProcessResult processInbox() {
        HttpEndpointLogger.logAccess("email", "POST", "/process-inbox/", 200);
        
        return componentClient.forWorkflow("email-processor")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());
    }
}
