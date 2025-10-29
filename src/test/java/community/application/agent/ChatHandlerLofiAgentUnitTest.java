package community.application.agent;

/**
 * Tests for ChatHandlerLofiAgent (pattern-matching implementation).
 *
 * <p>Extends {@link ChatHandlerAgentTestBase} which defines the behavioral contract
 * that all ChatHandler agents must fulfill.
 *
 * <p>This test class simply configures which agent to test. All test methods
 * are inherited from the base class.
 *
 * <h3>Agent Implementation: Pattern Matching (Lofi)</h3>
 * <ul>
 *   <li>Fast, deterministic keyword matching</li>
 *   <li>Hardcoded response templates</li>
 *   <li>Question mark detection for inquiry vs question</li>
 * </ul>
 *
 * <p>Compare with {@link ChatHandlerSmolAgentIntegrationTest} which tests the same
 * behavioral contract but with AI/LLM implementation.
 */
public class ChatHandlerLofiAgentUnitTest extends ChatHandlerAgentTestBase {

    @Override
    protected String invokeAgentAndGetResponse(String sessionId, String message) {
        return componentClient.forAgent()
            .inSession(sessionId)
            .method(ChatHandlerLofiAgent::handleMessage)
            .invoke(message);
    }
}
