package community.api;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.MockEmailInboxService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for EmailEndpoint.
 * Tests HTTP endpoint that triggers inbox processing.
 */
public class EmailEndpointTest extends TestKitSupport {

    // Use unique test prefix to avoid email ID collisions with other tests
    private static final String TEST_PREFIX = "endpoint-test-" + System.currentTimeMillis();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withDependencyProvider(new akka.javasdk.DependencyProvider() {
                @Override
                public <T> T getDependency(Class<T> clazz) {
                    if (clazz.equals(community.domain.EmailInboxService.class)) {
                        // Provide fresh MockEmailInboxService with unique prefix for test isolation
                        return (T) new MockEmailInboxService(TEST_PREFIX);
                    }
                    return null;
                }
            });
    }

    @Test
    public void shouldProcessInboxViaEndpoint() {
        // Act: POST to /process-inbox endpoint
        var response = httpClient.POST("/process-inbox")
            .responseBodyAs(community.application.EmailProcessingWorkflow.ProcessResult.class)
            .invoke();

        // Extract result from response
        var result = response.body();

        // Assert: Verify 2 emails were processed
        assertNotNull(result);
        assertEquals(2, result.emailsProcessed(), "Should process 2 emails from mock inbox");

        // Assert: Verify tags were generated for both emails
        assertNotNull(result.emailTags());
        assertEquals(2, result.emailTags().size(), "Should have tags for both emails");

        // Verify tags contain expected data (from MockEmailInboxService)
        result.emailTags().forEach(tags -> {
            assertNotNull(tags, "Email tags should not be null");
            assertNotNull(tags.summary(), "Summary should be generated");
        });
    }
}
