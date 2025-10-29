package community.application.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INTEGRATION tests for EmailTaggingAgent with SmolLM2.
 *
 * <p>This demonstrates the "Theory" pattern: same test logic as unit tests,
 * but with real LLM instead of mocked responses. Compare assertions to see
 * how SmolLM2 performs vs fake model expectations.
 *
 * <p>Contrast with EmailTaggingAgentUnitTest which uses mocked LLM responses.
 *
 * <h3>Prerequisites</h3>
 * <pre>
 * brew services start ollama
 * ollama pull smollm2:135m-instruct-q4_0
 * </pre>
 *
 * <h3>Run Tests</h3>
 * <pre>
 * # Run only unit tests (fast, mocked)
 * mvn test -Dtest=EmailTaggingAgentUnitTest
 *
 * # Run integration tests (realistic, requires Ollama)
 * mvn test -Dtest=EmailTaggingAgentIntegrationTest
 *
 * # Run both to compare
 * mvn test -Dtest=EmailTaggingAgent*Test
 * </pre>
 */
public class EmailTaggingAgentIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        // Configure to use SmolLM2 via Ollama instead of mocked responses
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.base-url = \"http://localhost:11434/v1\"")
            .withAdditionalConfig("akka.javasdk.agent.openai.model = \"smollm2:135m-instruct-q4_0\"");
    }

    @Test
    public void shouldGenerateTagsForMaintenanceEmail() {
        // SAME test as EmailTaggingAgentTest.shouldGenerateTagsForMaintenanceEmail()
        // but WITHOUT mock - uses real SmolLM2

        Email email = Email.create(
            "msg-urgent-123",
            "resident@community.com",
            "Broken elevator",
            "The elevator in Building A has been broken for 2 days. This is urgent!"
        );

        // Act: Call agent with REAL SmolLM2 (no fixedResponse())
        EmailTags tags = componentClient.forAgent()
            .inSession("test-session-1")
            .method(EmailTaggingAgent::tagEmail)
            .invoke(email);

        // Assert: Semantic validation (not exact match like fake test)
        assertNotNull(tags, "Tags should not be null");
        assertNotNull(tags.tags(), "Tags set should not be null");
        assertFalse(tags.tags().isEmpty(), "Should generate at least one tag");
        assertNotNull(tags.summary(), "Summary should not be null");

        // SmolLM2 should identify elevator/maintenance/urgent content
        String allContent = String.join(" ", tags.tags()) + " " + tags.summary();
        String lowerContent = allContent.toLowerCase();

        assertTrue(
            lowerContent.contains("elevator") ||
            lowerContent.contains("maintenance") ||
            lowerContent.contains("broken") ||
            lowerContent.contains("urgent"),
            "Tags or summary should mention elevator/maintenance/broken/urgent. Got: " + allContent
        );

        System.out.println("[SmolLM2] Generated Tags: " + tags);
    }

    @Test
    public void shouldHandleEmailWithNoLocation() {
        // SAME test as EmailTaggingAgentTest.shouldHandleEmailWithNoLocation()

        Email email = Email.create(
            "msg-parking-456",
            "tenant@community.com",
            "Parking question",
            "What are the rules for overnight guest parking?"
        );

        // Act: Real SmolLM2
        EmailTags tags = componentClient.forAgent()
            .inSession("test-session-2")
            .method(EmailTaggingAgent::tagEmail)
            .invoke(email);

        // Assert: Structure validation
        assertNotNull(tags);
        assertNotNull(tags.tags());
        assertFalse(tags.tags().isEmpty());
        assertNotNull(tags.summary());

        // Semantic validation
        String allContent = String.join(" ", tags.tags()) + " " + tags.summary();
        String lowerContent = allContent.toLowerCase();

        assertTrue(
            lowerContent.contains("parking") ||
            lowerContent.contains("question") ||
            lowerContent.contains("guest") ||
            lowerContent.contains("rules"),
            "Tags or summary should mention parking/question/guest/rules. Got: " + allContent
        );

        // Note: SmolLM2 might or might not extract location (parking area not specified)

        System.out.println("[SmolLM2] Generated Tags (no location): " + tags);
    }
}
