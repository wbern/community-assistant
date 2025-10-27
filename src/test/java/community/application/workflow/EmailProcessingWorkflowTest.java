package community.application.workflow;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import community.domain.model.EmailTags;
import community.infrastructure.mock.MockEmailInboxService;
import community.application.entity.EmailSyncCursorEntity;
import community.application.agent.EmailTaggingAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmailProcessingWorkflow.
 * RED phase: Testing workflow that orchestrates email fetch and persistence.
 */
public class EmailProcessingWorkflowTest extends TestKitSupport {

    private final TestModelProvider agentModel = new TestModelProvider();

    // Use unique ID prefix per test METHOD to ensure test isolation
    // Each test gets a unique prefix based on timestamp + random UUID
    private String currentTestPrefix;

    /**
     * Set unique test prefix before each test to ensure isolation.
     * This ensures EVERY test gets unique email IDs from MockEmailInboxService.
     */
    @BeforeEach
    public void setUpTestPrefix() {
        currentTestPrefix = "test-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Initialize workflow with unique cursor to ensure test isolation.
     * Each test gets different email IDs via cursor-based caching in MockEmailInboxService.
     */
    private void initializeWorkflowCursor(String workflowId, String uniqueDate) {
        Instant uniqueCursor = Instant.parse(uniqueDate);
        componentClient.forKeyValueEntity(workflowId)
            .method(EmailSyncCursorEntity::updateCursor)
            .invoke(uniqueCursor);
    }

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withModelProvider(EmailTaggingAgent.class, agentModel)
            .withDependencyProvider(new akka.javasdk.DependencyProvider() {
                @Override
                public <T> T getDependency(Class<T> clazz) {
                    if (clazz.equals(community.domain.port.EmailInboxService.class)) {
                        // Create NEW instance per test method using currentTestPrefix
                        // This ensures each test gets unique email IDs and proper isolation
                        String prefix = currentTestPrefix != null ? currentTestPrefix : "test-default";
                        return (T) new MockEmailInboxService(prefix);
                    }
                    return null;
                }
            });
    }

    @Test
    public void shouldProcessInboxEmails() {
        // Arrange: Set unique cursor for test isolation
        String workflowId = "test-workflow-1";
        initializeWorkflowCursor(workflowId, "2025-01-01T00:00:00Z");

        // Act: Start workflow to process emails from inbox
        var result = componentClient.forWorkflow(workflowId)
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: Workflow should report successful processing
        assertNotNull(result);
        assertEquals(2, result.emailsProcessed());
    }

    @Test
    public void shouldPersistEmailsToEntity() {
        // Arrange: Set unique cursor for test isolation
        String workflowId = "test-workflow-2";
        initializeWorkflowCursor(workflowId, "2025-01-02T00:00:00Z");

        // Act: Process emails from inbox via workflow
        var result = componentClient.forWorkflow(workflowId)
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: Verify emails were processed successfully
        assertNotNull(result);
        assertEquals(2, result.emailsProcessed(), "Should process 2 emails");

        // Verify tags were generated for both emails
        assertNotNull(result.emailTags());
        assertEquals(2, result.emailTags().size(), "Should have tags for both emails");
    }

    @Test
    public void shouldCallTaggingAgentForEachEmail() {
        // Arrange: Set unique cursor + Mock AI response
        String workflowId = "test-workflow-3";
        initializeWorkflowCursor(workflowId, "2025-01-03T00:00:00Z");

        EmailTags mockTags = EmailTags.create(
            Set.of("test-tag", "automated"),
            "Mock summary for testing",
            null
        );
        agentModel.fixedResponse(JsonSupport.encodeToString(mockTags));

        // Act: Process emails via workflow (should call agent for each email)
        var result = componentClient.forWorkflow(workflowId)
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

        // Arrange: Set unique cursor for test isolation
        String workflowId = "test-workflow-multi-sender";
        initializeWorkflowCursor(workflowId, "2025-01-04T00:00:00Z");

        // Act: Process emails from inbox via workflow
        var result = componentClient.forWorkflow(workflowId)
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Assert: Both emails should be processed (proves they were stored separately)
        // If messageId collided, only 1 would be processed
        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.emailsProcessed(),
            "Should process 2 emails from same sender (proves separate storage)");

        // Verify both emails have tags (proves both were stored and tagged)
        assertEquals(2, result.emailTags().size(),
            "Should have tags for both emails");
    }

    @Test
    public void shouldFetchOnlyEmailsAfterCursor() {
        // RED PHASE: Test that workflow uses cursor to fetch only new emails
        // GIVEN: A cursor set to 2025-10-20T10:00:00Z
        // MockEmailInboxService has emails at 09:00 and 11:00
        // Only the 11:00 email should be processed
        String workflowId = "test-workflow-cursor";
        Instant cursor = Instant.parse("2025-10-20T10:00:00Z");

        // Initialize cursor (using workflow ID as cursor ID for isolation)
        componentClient.forKeyValueEntity(workflowId)
            .method(EmailSyncCursorEntity::updateCursor)
            .invoke(cursor);

        // WHEN: Process inbox
        var result = componentClient.forWorkflow("test-workflow-cursor")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // THEN: Only 1 email should be processed (the one after cursor)
        assertNotNull(result);
        assertEquals(1, result.emailsProcessed(),
            "Should only process emails after cursor (11:00 > 10:00)");

        // Verify one email tag was generated
        assertEquals(1, result.emailTags().size(),
            "Should have tags for 1 email (the one after cursor)");
    }

    @Test
    public void shouldUpdateCursorAfterProcessing() {
        // RED PHASE: Test that workflow updates cursor to latest processed email timestamp
        // Arrange: Set unique cursor for test isolation
        String workflowId = "test-workflow-update-cursor";
        initializeWorkflowCursor(workflowId, "2025-01-05T00:00:00Z");

        // WHEN: Process inbox (will fetch both emails: 09:00 and 11:00)
        componentClient.forWorkflow(workflowId)
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // THEN: Cursor should be updated to latest email timestamp (11:00)
        Instant updatedCursor = componentClient.forKeyValueEntity(workflowId)
            .method(EmailSyncCursorEntity::getCursor)
            .invoke();

        Instant expectedCursor = Instant.parse("2025-10-20T11:00:00Z");
        assertEquals(expectedCursor, updatedCursor,
            "Cursor should be updated to timestamp of latest processed email");
    }

    @Test
    public void shouldSkipAlreadyProcessedEmails() {
        // RED PHASE: Test workflow-level optimization that skips already-processed emails
        // This demonstrates defense-in-depth: workflow skips to avoid redundant AI calls,
        // entity idempotency prevents duplicate events if workflow is bypassed

        // Arrange: Set unique cursor for test isolation + Mock AI response
        String workflowId = "test-workflow-skip";
        initializeWorkflowCursor(workflowId, "2025-01-06T00:00:00Z");

        EmailTags mockTags = EmailTags.create(
            Set.of("skip-test"),
            "Skip test",
            null
        );
        agentModel.fixedResponse(JsonSupport.encodeToString(mockTags));

        // WHEN: Process emails first time (should process 2 new emails)
        var result1 = componentClient.forWorkflow(workflowId)
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // THEN: First run should process 2 emails
        assertNotNull(result1);
        assertEquals(2, result1.emailsProcessed(),
            "First run should process 2 new emails");
        assertEquals(2, result1.emailTags().size());

        // Reset cursor to BEFORE processed emails to simulate workflow replay/retry
        // This tests that workflow skips already-processed emails even when they're fetched again
        componentClient.forKeyValueEntity(workflowId)
            .method(EmailSyncCursorEntity::updateCursor)
            .invoke(Instant.parse("2025-01-06T00:00:00Z"));

        // WHEN: Process emails second time (cursor reset, same emails fetched, but already processed)
        var result2 = componentClient.forWorkflow(workflowId)
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // THEN: Second run should skip both already-processed emails
        assertNotNull(result2);
        assertEquals(0, result2.emailsProcessed(),
            "Second run should skip both already-processed emails (workflow optimization)");

        // But should still return tags for both emails (from entity state)
        assertEquals(2, result2.emailTags().size(),
            "Should return existing tags for skipped emails");
    }

}
