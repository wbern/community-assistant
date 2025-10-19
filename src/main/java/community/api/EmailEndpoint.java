package community.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import community.application.EmailProcessingWorkflow;

/**
 * HTTP endpoint for email processing operations.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/process-inbox")
public class EmailEndpoint {

    private final ComponentClient componentClient;

    public EmailEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post
    public HttpResponse processInbox() {
        componentClient.forWorkflow("email-processor")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        return HttpResponses.accepted();
    }
}
