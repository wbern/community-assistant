package community.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import community.domain.Email;
import community.domain.EmailInboxService;
import community.domain.EmailTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Workflow for processing emails from inbox.
 * Orchestrates email fetching and persistence.
 */
@Component(id = "email-processing")
public class EmailProcessingWorkflow extends Workflow<EmailProcessingWorkflow.State> {

    private static final Logger log = LoggerFactory.getLogger(EmailProcessingWorkflow.class);

    private final ComponentClient componentClient;
    private final EmailInboxService inboxService;

    public EmailProcessingWorkflow(ComponentClient componentClient, EmailInboxService inboxService) {
        this.componentClient = componentClient;
        this.inboxService = inboxService;
    }

    private String getCursorId() {
        return commandContext().workflowId();
    }

    public record State() {}

    public record ProcessInboxCmd() {}

    public record ProcessResult(int emailsProcessed, List<EmailTags> emailTags) {}

    public Effect<ProcessResult> processInbox(ProcessInboxCmd cmd) {
        // Read cursor to determine which emails to fetch
        // Use workflow ID as cursor ID for test isolation
        String cursorId = getCursorId();
        Instant cursor;

        try {
            cursor = componentClient.forKeyValueEntity(cursorId)
                .method(EmailSyncCursorEntity::getCursor)
                .invoke();
            log.debug("Read cursor for workflow {}: {}", cursorId, cursor);
        } catch (Exception e) {
            log.error("Failed to read cursor for workflow {}", cursorId, e);
            throw new RuntimeException("Failed to read email sync cursor", e);
        }

        List<Email> emails = inboxService.fetchEmailsSince(cursor);
        log.debug("Workflow {}: Fetched {} emails after cursor {}", cursorId, emails.size(), cursor);

        List<EmailTags> allTags = new ArrayList<>();
        int skippedCount = 0;

        // Persist each email to EmailEntity and generate tags
        // Entity-level idempotency ensures duplicate events are not created on replay
        // Workflow-level optimization: skip already-processed emails to avoid redundant AI calls
        for (Email email : emails) {
            // Check if email already fully processed (defense-in-depth with entity idempotency)
            Boolean isProcessed = componentClient.forEventSourcedEntity(email.getMessageId())
                .method(EmailEntity::isFullyProcessed)
                .invoke();

            if (Boolean.TRUE.equals(isProcessed)) {
                log.debug("Skipping already-processed email: {} in workflow {}",
                    email.getMessageId(), cursorId);
                // Retrieve existing tags for response
                EmailTags existingTags = componentClient.forEventSourcedEntity(email.getMessageId())
                    .method(EmailEntity::getTags)
                    .invoke();
                allTags.add(existingTags);
                skippedCount++;
                continue;
            }

            log.debug("Processing email: {} in workflow {}",
                email.getMessageId(), cursorId);

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

        // Update cursor to latest processed email timestamp
        if (!emails.isEmpty()) {
            Instant latestTimestamp = emails.stream()
                .map(Email::getReceivedAt)
                .max(Instant::compareTo)
                .orElse(cursor);

            try {
                componentClient.forKeyValueEntity(cursorId)
                    .method(EmailSyncCursorEntity::updateCursor)
                    .invoke(latestTimestamp);
                log.debug("Updated cursor for workflow {} from {} to {}",
                    cursorId, cursor, latestTimestamp);
            } catch (Exception e) {
                log.error("Failed to update cursor for workflow {} to {}",
                    cursorId, latestTimestamp, e);
                throw new RuntimeException("Failed to update email sync cursor", e);
            }
        } else {
            log.debug("No new emails to process for workflow {}, cursor remains at {}",
                cursorId, cursor);
        }

        int newlyProcessed = emails.size() - skippedCount;
        if (skippedCount > 0) {
            log.info("Processed {} new emails, skipped {} already-processed for workflow {}",
                newlyProcessed, skippedCount, cursorId);
        }

        return effects().reply(new ProcessResult(newlyProcessed, allTags));
    }
}
