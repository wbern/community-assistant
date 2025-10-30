package community.application.action;

import akka.javasdk.testkit.TestKitSupport;
import community.api.ChatEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED phase: Testing HTTP endpoint access logging.
 */
public class HttpEndpointLoggerTest extends TestKitSupport {

    private ListAppender<ILoggingEvent> chatLogAppender;
    private ListAppender<ILoggingEvent> emailLogAppender;
    private Logger chatEndpointLogger;
    private Logger emailEndpointLogger;

    @BeforeEach
    public void setupLogCapture() {
        // Setup log capture for HttpEndpointLogger
        chatEndpointLogger = (Logger) LoggerFactory.getLogger(HttpEndpointLogger.class);
        chatLogAppender = new ListAppender<>();
        chatLogAppender.start();
        chatEndpointLogger.addAppender(chatLogAppender);
        
        // Use same logger for both tests since centralized
        emailEndpointLogger = chatEndpointLogger;
        emailLogAppender = chatLogAppender;
    }

    @AfterEach
    public void teardownLogCapture() {
        chatEndpointLogger.detachAppender(chatLogAppender);
        emailEndpointLogger.detachAppender(emailLogAppender);
    }

    @Test
    public void red_shouldLogWhenChatEndpointReceivesRequest() {
        // RED: When ChatEndpoint receives a POST request,
        // there should be structured logging of the HTTP request and response
        
        var request = new ChatEndpoint.ChatRequest("Hello, this is a test message");
        
        // Act: Make HTTP request to ChatEndpoint
        var response = httpClient.POST("/chat/message")
            .withRequestBody(request)
            .responseBodyAs(ChatEndpoint.ChatResponse.class)
            .invoke();
        
        // Assert: HTTP endpoint access should be logged with request/response details
        // We expect a log entry containing: endpoint=chat, method=POST, path=/chat/message, status=200
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = chatLogAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("HTTP endpoint accessed") 
                          && event.getFormattedMessage().contains("endpoint=chat")
                          && event.getFormattedMessage().contains("method=POST")
                          && event.getFormattedMessage().contains("path=/chat/message")
                          && event.getFormattedMessage().contains("status=200"));
            assertTrue(found, "Expected HTTP endpoint access log not found in: " + logEvents);
        });
    }

    @Test
    public void red_shouldLogWhenEmailEndpointReceivesRequest() {
        // RED: When EmailEndpoint receives a POST request,
        // there should be structured logging of the HTTP request and response
        
        // Act: Make HTTP request to EmailEndpoint
        var response = httpClient.POST("/process-inbox/")
            .responseBodyAs(community.application.workflow.EmailProcessingWorkflow.ProcessResult.class)
            .invoke();
        
        // Assert: HTTP endpoint access should be logged with request/response details  
        // We expect a log entry containing: endpoint=email, method=POST, path=/process-inbox/, status=200
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logEvents = emailLogAppender.list;
            boolean found = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("HTTP endpoint accessed") 
                          && event.getFormattedMessage().contains("endpoint=email")
                          && event.getFormattedMessage().contains("method=POST")
                          && event.getFormattedMessage().contains("path=/process-inbox/")
                          && event.getFormattedMessage().contains("status=200"));
            assertTrue(found, "Expected EmailEndpoint HTTP access log not found in: " + logEvents);
        });
    }
}