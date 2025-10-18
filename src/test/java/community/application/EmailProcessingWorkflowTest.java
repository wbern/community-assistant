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

    @Test
    public void shouldPersistEmailsToEntity() {
        // Act: Process emails from inbox via workflow
        componentClient.forWorkflow("test-workflow-2")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: Verify first email was persisted to EmailEntity
        var emailEntity1 = componentClient.forEventSourcedEntity("resident@community.com")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(emailEntity1);
        assertEquals("resident@community.com", emailEntity1.getFrom());
        assertEquals("Broken elevator", emailEntity1.getSubject());

        // Assert: Verify second email was persisted
        var emailEntity2 = componentClient.forEventSourcedEntity("tenant@community.com")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(emailEntity2);
        assertEquals("tenant@community.com", emailEntity2.getFrom());
        assertEquals("Noise complaint", emailEntity2.getSubject());
    }
}
