package community.application.agent;

import akka.javasdk.testkit.TestKit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTEGRATION tests for ChatHandlerAIAgent with SmolLM2.
 *
 * <p>Extends {@link ChatHandlerAgentTestBase} which defines the behavioral contract
 * that all ChatHandler agents must fulfill.
 *
 * <p>This test class configures SmolLM2 and tests the AI implementation against
 * the same behavioral contract as the LofiAgent.
 *
 * <h3>Agent Implementation: AI/LLM (SmolLM2)</h3>
 * <ul>
 *   <li>Natural language understanding</li>
 *   <li>AI-generated responses</li>
 *   <li>Requires SmolLM2 via Ollama</li>
 * </ul>
 *
 * <h3>Feature Parity Validation</h3>
 * <p>This test suite will reveal which behaviors the AI agent lacks compared to
 * the LofiAgent. Failed tests indicate missing functionality that needs:
 * <ul>
 *   <li>System prompt instructions</li>
 *   <li>@FunctionTool implementations</li>
 *   <li>Additional training/fine-tuning</li>
 * </ul>
 *
 * <h3>Prerequisites</h3>
 * <pre>
 * brew services start ollama
 * ollama pull smollm2:135m-instruct-q4_0
 * </pre>
 *
 * <p>Compare with {@link ChatHandlerLofiAgentUnitTest} which tests the same
 * behavioral contract but with pattern-matching implementation.
 */
public class ChatHandlerSmolAgentIntegrationTest extends ChatHandlerAgentTestBase {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandlerSmolAgentIntegrationTest.class);

    @BeforeEach
    void logTestStart(TestInfo testInfo) {
        logger.info("▶️  STARTING TEST: {}", testInfo.getDisplayName());
    }

    @AfterEach
    void logTestEnd(TestInfo testInfo) {
        logger.info("✅ COMPLETED TEST: {}", testInfo.getDisplayName());
    }

    @Override
    protected TestKit.Settings testKitSettings() {
        // Configure SmolLM2 via Ollama with performance optimizations
        return super.testKitSettings()
            .withAdditionalConfig("akka.javasdk.agent.openai.base-url = \"http://localhost:11434/v1\"")
            .withAdditionalConfig("akka.javasdk.agent.openai.model = \"smollm2:135m-instruct-q4_0\"")
            .withAdditionalConfig("akka.javasdk.agent.openai.temperature = 0.1")  // Low temperature for faster, deterministic responses
            .withAdditionalConfig("akka.javasdk.agent.openai.max-tokens = 100");  // Limit response length (100 tokens for tool results)
    }

    @Override
    protected String invokeAgentAndGetResponse(String sessionId, String message) {
        return componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerAIAgent::handleMessage)
            .invoke(message);
    }
}
