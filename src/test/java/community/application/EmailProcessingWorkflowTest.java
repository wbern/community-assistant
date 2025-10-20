package community.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import community.domain.EmailTags;
import community.domain.MockEmailInboxService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmailProcessingWorkflow.
 * RED phase: Testing workflow that orchestrates email fetch and persistence.
 */
public class EmailProcessingWorkflowTest extends TestKitSupport {

    private final TestModelProvider agentModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withModelProvider(EmailTaggingAgent.class, agentModel);
    }

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

        // Assert: Verify first email was persisted to EmailEntity using messageId
        // MockEmailInboxService returns emails with IDs "msg-elevator-001" and "msg-elevator-002"
        var emailEntity1 = componentClient.forEventSourcedEntity("msg-elevator-001")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(emailEntity1);
        assertEquals("resident@community.com", emailEntity1.getFrom());
        assertEquals("Broken elevator", emailEntity1.getSubject());

        // Assert: Verify second email was persisted using its messageId
        var emailEntity2 = componentClient.forEventSourcedEntity("msg-elevator-002")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(emailEntity2);
        assertEquals("resident@community.com", emailEntity2.getFrom());
        assertEquals("Elevator still broken", emailEntity2.getSubject());
    }

    @Test
    public void shouldCallTaggingAgentForEachEmail() {
        // Arrange: Mock AI response (will be used for all agent calls)
        EmailTags mockTags = EmailTags.create(
            Set.of("test-tag", "automated"),
            "Mock summary for testing",
            null
        );
        agentModel.fixedResponse(JsonSupport.encodeToString(mockTags));

        // Act: Process emails via workflow (should call agent for each email)
        var result = componentClient.forWorkflow("test-workflow-3")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: Workflow completes with tags for each email
        assertNotNull(result);
        assertEquals(2, result.emailsProcessed());

        // Verify tags were generated for both emails
        assertNotNull(result.emailTags());
        assertEquals(2, result.emailTags().size());

        // Verify tags are not null and contain data
        result.emailTags().forEach(tags -> {
            assertNotNull(tags);
            assertNotNull(tags.tags());
            assertFalse(tags.tags().isEmpty(), "Tags should not be empty");
            assertNotNull(tags.summary());
        });
    }

    @Test
    public void shouldStoreMultipleEmailsFromSameSenderSeparately() {
        // RED: This test verifies the critical bug fix: using messageId as entity ID
        // allows multiple emails from the same sender to be stored separately

        // MockEmailInboxService now returns 2 emails from "resident@community.com"
        // with messageIds "msg-elevator-001" and "msg-elevator-002"

        // Act: Process emails from inbox via workflow
        componentClient.forWorkflow("test-workflow-multi-sender")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: First email should be stored under its messageId
        var email1 = componentClient.forEventSourcedEntity("msg-elevator-001")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(email1, "First email should be stored");
        assertEquals("msg-elevator-001", email1.getMessageId());
        assertEquals("Broken elevator", email1.getSubject());

        // Assert: Second email should be stored under its messageId (not overwrite first)
        var email2 = componentClient.forEventSourcedEntity("msg-elevator-002")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(email2, "Second email should be stored separately");
        assertEquals("msg-elevator-002", email2.getMessageId());
        assertEquals("Elevator still broken", email2.getSubject());
    }
}
