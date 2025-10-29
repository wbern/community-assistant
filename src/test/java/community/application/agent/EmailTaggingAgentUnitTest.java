package community.application.agent;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import community.domain.model.Email;
import community.domain.model.EmailTags;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UNIT tests for EmailTaggingAgent.
 * Tests AI-driven email tagging with mocked LLM responses (TestModelProvider).
 *
 * <p>For integration tests with real SmolLM2, see EmailTaggingAgentIntegrationTest.
 */
public class EmailTaggingAgentUnitTest extends TestKitSupport {

    private final TestModelProvider agentModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withModelProvider(EmailTaggingAgent.class, agentModel);
    }

    @Test
    public void shouldGenerateTagsForMaintenanceEmail() {
        // Arrange: Mock AI response with tags
        EmailTags mockTags = EmailTags.create(
            Set.of("urgent", "maintenance", "elevator"),
            "Elevator broken in Building A",
            "Building A, Elevator"
        );
        agentModel.fixedResponse(JsonSupport.encodeToString(mockTags));

        Email email = Email.create(
            "msg-urgent-123",
            "resident@community.com",
            "Broken elevator",
            "The elevator in Building A has been broken for 2 days. This is urgent!"
        );

        // Act: Call agent to generate tags
        EmailTags tags = componentClient.forAgent()
            .inSession("test-session-1")
            .method(EmailTaggingAgent::tagEmail)
            .invoke(email);

        // Assert: Tags should match mocked response
        assertNotNull(tags);
        assertEquals(3, tags.tags().size());
        assertTrue(tags.tags().contains("urgent"));
        assertTrue(tags.tags().contains("maintenance"));
        assertTrue(tags.tags().contains("elevator"));
        assertEquals("Elevator broken in Building A", tags.summary());
        assertEquals("Building A, Elevator", tags.location());
    }

    @Test
    public void shouldHandleEmailWithNoLocation() {
        // Arrange: Mock AI response without location
        EmailTags mockTags = EmailTags.create(
            Set.of("question", "general"),
            "Question about parking rules",
            null
        );
        agentModel.fixedResponse(JsonSupport.encodeToString(mockTags));

        Email email = Email.create(
            "msg-parking-456",
            "tenant@community.com",
            "Parking question",
            "What are the rules for overnight guest parking?"
        );

        // Act
        EmailTags tags = componentClient.forAgent()
            .inSession("test-session-2")
            .method(EmailTaggingAgent::tagEmail)
            .invoke(email);

        // Assert
        assertNotNull(tags);
        assertTrue(tags.tags().contains("question"));
        assertNull(tags.location());
    }
}
