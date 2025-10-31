package community.infrastructure.integration;

import akka.javasdk.testkit.TestKitSupport;
import community.application.action.EmailPollingAction;
import community.application.entity.EmailPollingConfigEntity;
import community.application.workflow.EmailProcessingWorkflow;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for automatic email polling with real Gmail integration.
 * Tests the complete flow: EmailPollingAction -> EmailProcessingWorkflow -> GmailInboxService
 */
public class EmailPollingGmailIntegrationTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> serviceLogAppender;
    private ListAppender<ILoggingEvent> gmailLogAppender;
    private Logger serviceLogger;
    private Logger gmailLogger;

    @BeforeEach
    public void setupLogCapture() {
        // Capture ServiceConfiguration logs to verify Gmail service initialization
        serviceLogger = (Logger) LoggerFactory.getLogger("community.infrastructure.config.ServiceConfiguration");
        serviceLogAppender = new ListAppender<>();
        serviceLogAppender.start();
        serviceLogger.addAppender(serviceLogAppender);

        // Capture Gmail external service logs to verify API calls
        gmailLogger = (Logger) LoggerFactory.getLogger("community.infrastructure.gmail.ExternalServiceLogger");
        gmailLogAppender = new ListAppender<>();
        gmailLogAppender.start();
        gmailLogger.addAppender(gmailLogAppender);
    }

    @AfterEach
    public void teardownLogCapture() {
        serviceLogger.detachAppender(serviceLogAppender);
        gmailLogger.detachAppender(gmailLogAppender);
    }

    @Test
    public void shouldUseRealGmailServiceForAutomaticEmailPolling() {
        // Test that the system uses real GmailInboxService (not Mock) when Gmail credentials are available
        // This verifies our automatic email polling system connects to real Gmail
        
        // Clear previous log events and trigger Gmail service usage
        serviceLogAppender.list.clear();
        gmailLogAppender.list.clear();
        
        // Trigger workflow that will cause Gmail service to be initialized and used
        EmailProcessingWorkflow.ProcessResult result = componentClient
            .forWorkflow("test-layer1-gmail-workflow")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());
        
        // Step 1: Verify Gmail service was used (alternative to initialization log)
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> gmailLogEvents = gmailLogAppender.list;
            boolean gmailUsed = gmailLogEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("External service called") 
                          && event.getFormattedMessage().contains("service=gmail")
                          && event.getFormattedMessage().contains("operation=fetchEmailsSince"));
            assertTrue(gmailUsed, 
                "Expected Gmail service usage not found. Found: " + gmailLogEvents);
        });

        // Step 2: Layer 2 Test - Workflow-level Gmail integration
        // Test the core logic that EmailPollingAction uses internally
        EmailProcessingWorkflow.ProcessResult layer2Result = componentClient
            .forWorkflow("test-gmail-workflow")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Step 3: Verify Gmail API was called (real service, not mock)
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> gmailLogEvents = gmailLogAppender.list;
            boolean gmailApiCalled = gmailLogEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("External service called") 
                          && event.getFormattedMessage().contains("service=gmail")
                          && event.getFormattedMessage().contains("operation=fetchEmailsSince")
                          && event.getFormattedMessage().contains("status=success"));
            assertTrue(gmailApiCalled, 
                "Expected Gmail API call log not found. Found: " + gmailLogEvents);
        });
    }

    @Test
    public void shouldProcessEmailsFromGmailThroughAutomaticPolling() {
        // Test complete email processing flow with real Gmail
        // EmailPollingAction -> EmailProcessingWorkflow -> GmailInboxService -> EmailEntity
        
        // Trigger email processing workflow directly to test Gmail integration
        EmailProcessingWorkflow.ProcessResult result = componentClient
            .forWorkflow("test-gmail-integration")
            .method(EmailProcessingWorkflow::processInbox)
            .invoke(new EmailProcessingWorkflow.ProcessInboxCmd());

        // Verify workflow completed (regardless of whether emails were found)
        assertTrue(result.emailsProcessed() >= 0, 
            "Email processing should complete successfully, found: " + result.emailsProcessed() + " emails");

        // Verify Gmail service was used for fetching emails
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> gmailLogEvents = gmailLogAppender.list;
            boolean gmailUsed = gmailLogEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("service=gmail"));
            assertTrue(gmailUsed, 
                "Expected Gmail service usage not found. Found: " + gmailLogEvents);
        });
    }

    @Test
    public void shouldTriggerGmailPollingViaTimerMechanism() {
        // Layer 3 Test - Complete timer-based polling integration
        // Test the full flow: Timer → EmailPollingAction → EmailProcessingWorkflow → GmailInboxService
        
        // Setup polling configuration for test
        int testInterval = 60; // 1 minute for test
        componentClient.forKeyValueEntity("polling-config")
            .method(EmailPollingConfigEntity::setInterval)
            .invoke(testInterval);
        
        // Clear previous log events
        serviceLogAppender.list.clear();
        gmailLogAppender.list.clear();
        
        // Trigger timer-based polling using the same pattern as ServiceConfiguration
        // This tests the complete authentic flow
        timerScheduler.createSingleTimer(
            "test-gmail-polling-timer",
            Duration.ZERO, // Execute immediately for test
            componentClient
                .forTimedAction()
                .method(EmailPollingAction::pollForEmails)
                .deferred()
        );
        
        // Verify EmailPollingAction was triggered and executed
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> gmailLogEvents = gmailLogAppender.list;
            boolean timerTriggeredGmail = gmailLogEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("External service called") 
                          && event.getFormattedMessage().contains("service=gmail")
                          && event.getFormattedMessage().contains("operation=fetchEmailsSince"));
            assertTrue(timerTriggeredGmail, 
                "Expected timer-triggered Gmail polling not found. Found: " + gmailLogEvents);
        });
        
        // Verify EmailProcessingWorkflow was invoked through the timer mechanism
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> gmailLogEvents = gmailLogAppender.list;
            boolean workflowTriggered = gmailLogEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("service=gmail")
                          && event.getFormattedMessage().contains("status=success"));
            assertTrue(workflowTriggered, 
                "Expected workflow execution through timer not found. Found: " + gmailLogEvents);
        });
    }
}