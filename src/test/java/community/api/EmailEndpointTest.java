package community.api;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.application.EmailEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for EmailEndpoint.
 * Tests HTTP endpoint that triggers inbox processing.
 */
public class EmailEndpointTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT;
    }

    @Test
    public void shouldProcessInboxViaEndpoint() {
        // Act: POST to /process-inbox endpoint
        httpClient.POST("/process-inbox")
            .invoke();

        // Assert: Verify first email was persisted to EmailEntity using messageId
        // MockEmailInboxService returns emails with IDs "msg-elevator-001" and "msg-elevator-002"
        var email1 = componentClient.forEventSourcedEntity("msg-elevator-001")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(email1);
        assertEquals("resident@community.com", email1.getFrom());
        assertEquals("Broken elevator", email1.getSubject());

        // Assert: Verify second email was persisted using its messageId
        var email2 = componentClient.forEventSourcedEntity("msg-elevator-002")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(email2);
        assertEquals("resident@community.com", email2.getFrom());
        assertEquals("Elevator still broken", email2.getSubject());
    }
}
