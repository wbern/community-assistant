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

        // Assert: Verify first email was persisted to EmailEntity
        var email1 = componentClient.forEventSourcedEntity("resident@community.com")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(email1);
        assertEquals("resident@community.com", email1.getFrom());
        assertEquals("Broken elevator", email1.getSubject());

        // Assert: Verify second email was persisted
        var email2 = componentClient.forEventSourcedEntity("tenant@community.com")
            .method(EmailEntity::getEmail)
            .invoke();

        assertNotNull(email2);
        assertEquals("tenant@community.com", email2.getFrom());
        assertEquals("Noise complaint", email2.getSubject());
    }
}
