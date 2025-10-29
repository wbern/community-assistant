package community.application.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UNIT tests for ChatHandlerAIAgent.
 *
 * <p>These tests verify the structure and availability of @FunctionTool methods
 * without actually invoking the LLM or testing integration with other components.
 *
 * <p>For integration tests that verify the LLM calls the tool and updates state,
 * see ChatHandlerAIAgentIntegrationTest.
 */
public class ChatHandlerAIAgentUnitTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.base-url = \"http://localhost:11434/v1\"")
            .withAdditionalConfig("akka.javasdk.agent.openai.model = \"smollm2:135m-instruct-q4_0\"");
    }

    /**
     * UNIT TEST: Verify @FunctionTool method exists.
     * Does NOT test LLM calling the tool or state changes.
     */
    @Test
    public void shouldHaveSetReminderIntervalFunctionTool() {
        // Verify the agent has a method for setting reminder intervals
        Method[] methods = ChatHandlerAIAgent.class.getDeclaredMethods();
        boolean hasSetIntervalTool = Arrays.stream(methods)
            .anyMatch(m -> m.getName().equals("setReminderInterval") ||
                          m.getName().equals("configureReminder"));

        assertTrue(hasSetIntervalTool,
            "ChatHandlerAIAgent should have a @FunctionTool method for setting reminder intervals");
    }
}
