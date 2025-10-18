package community.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.MockEmailInboxService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmailProcessingWorkflow.
 * RED phase: Testing workflow that orchestrates email fetch and persistence.
 */
public class EmailProcessingWorkflowTest extends TestKitSupport {

    @Test
    public void shouldProcessInboxEmails() {
        // Act: Start workflow to process emails from inbox
        var result = componentClient.forWorkflow("test-workflow-1")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: Workflow should report successful processing
        assertNotNull(result);
        assertEquals(2, result.emailsProcessed());
    }
}
