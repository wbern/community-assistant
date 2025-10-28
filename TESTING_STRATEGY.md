# Testing Strategy - Agent Testing with Three Tiers

## Overview

The project uses a three-tier testing strategy for AI agents, allowing different levels of testing without requiring full LLM infrastructure.

## Three Agent Tiers

### 1. Fake Agent (Unit Tests)
**Purpose:** Fast, deterministic unit tests with zero dependencies

**Implementation:**
- Uses `TestModelProvider` with `fixedResponse()`
- Returns pre-defined JSON responses
- No actual AI model involved
- Millisecond-level test execution

**Example:**
```java
private final TestModelProvider agentModel = new TestModelProvider();

@Override
protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(EmailTaggingAgent.class, agentModel);
}

@Test
void testTagging() {
    // Pre-define exact response
    EmailTags mockTags = EmailTags.create(
        Set.of("elevator", "urgent"),
        "Elevator malfunction",
        "Building A"
    );
    agentModel.fixedResponse(JsonSupport.encodeToString(mockTags));

    // Test proceeds with fake response
    var result = componentClient.forAgent()
        .method(EmailTaggingAgent::generateTags)
        .invoke(email);
}
```

**Use Cases:**
- TDD Red-Green-Refactor cycles
- CI/CD pipelines
- Fast feedback loops
- Testing error handling and edge cases

### 2. Nano-Sized LLM (Integration Tests)
**Purpose:** Realistic agent behavior testing without production LLM costs

**Model:** SmolLM2-135M (Q4_0) ≈ 92 MB
- Quantized 4-bit model
- Runs locally via Ollama
- Fast inference (~100ms)
- Good enough for testing agent workflows

**Implementation:**
```java
@Override
protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(EmailTaggingAgent.class,
            new OpenAIModelProvider(
                "http://localhost:11434/v1",  // Ollama endpoint
                "smollm2:135m-instruct-q4_0"  // Model name
            ));
}

@Test
void integrationTestWithNanoLLM() {
    // Real LLM interaction, but small and fast
    var result = componentClient.forAgent()
        .method(EmailTaggingAgent::generateTags)
        .invoke(email);

    // Assert on actual LLM output structure
    assertNotNull(result.tags());
    assertTrue(result.tags().size() > 0);
}
```

**Setup:**
```bash
# Install Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Pull SmolLM2-135M
ollama pull smollm2:135m-instruct-q4_0

# Verify running
curl http://localhost:11434/v1/models
```

**Use Cases:**
- Integration tests between services
- Testing agent prompt effectiveness
- Workflow orchestration validation
- Pre-production smoke tests
- Developer testing without API keys

**Benefits:**
- No external API dependencies
- No per-test costs
- Deterministic enough for CI
- Tests real LLM interaction patterns
- Offline development capability

### 3. Real LLM (Production & E2E Tests)
**Purpose:** Production behavior and final validation

**Implementation:**
- Production configuration (Claude, GPT-4, etc.)
- Full capability testing
- End-to-end validation
- Manual QA scenarios

**Use Cases:**
- Pre-release validation
- Performance benchmarking
- Quality assessment
- User acceptance testing

## Testing Pyramid

```
        /\
       /  \        Real LLM
      /____\       (E2E Tests - Few, Expensive)
     /      \
    /        \     Nano LLM
   /__________\    (Integration Tests - Some, Fast)
  /            \
 /              \  Fake Agent
/________________\ (Unit Tests - Many, Instant)
```

## Configuration Strategy

### Environment-Based Selection

**application.conf:**
```hocon
community-assistant {
  testing {
    agent-tier = ${?AGENT_TEST_TIER}  // fake | nano | real

    nano-llm {
      endpoint = "http://localhost:11434/v1"
      model = "smollm2:135m-instruct-q4_0"
    }
  }
}
```

**Test Base Class:**
```java
public abstract class AgentTestSupport extends TestKitSupport {

    protected TestKit.Settings agentTestSettings(Class<? extends Agent> agentClass) {
        String tier = System.getenv().getOrDefault("AGENT_TEST_TIER", "fake");

        return switch(tier) {
            case "fake" -> TestKit.Settings.DEFAULT
                .withModelProvider(agentClass, new TestModelProvider());

            case "nano" -> TestKit.Settings.DEFAULT
                .withModelProvider(agentClass,
                    new OpenAIModelProvider(
                        "http://localhost:11434/v1",
                        "smollm2:135m-instruct-q4_0"));

            case "real" -> TestKit.Settings.DEFAULT;
                // Uses production configuration

            default -> throw new IllegalArgumentException("Unknown tier: " + tier);
        };
    }
}
```

### Maven Profile Configuration

**pom.xml:**
```xml
<profiles>
    <profile>
        <id>test-fake</id>
        <activation><activeByDefault>true</activeByDefault></activation>
        <properties>
            <agent.test.tier>fake</agent.test.tier>
        </properties>
    </profile>

    <profile>
        <id>test-nano</id>
        <properties>
            <agent.test.tier>nano</agent.test.tier>
        </properties>
    </profile>

    <profile>
        <id>test-real</id>
        <properties>
            <agent.test.tier>real</agent.test.tier>
        </properties>
    </profile>
</profiles>
```

**Usage:**
```bash
# Unit tests (default)
mvn test

# Integration tests with nano LLM
mvn test -Ptest-nano

# E2E tests with real LLM
mvn test -Ptest-real
```

## CI/CD Pipeline

```yaml
# .github/workflows/test.yml
name: Test Suite

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run unit tests (fake agents)
        run: mvn test -Ptest-fake

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Install Ollama
        run: curl -fsSL https://ollama.ai/install.sh | sh
      - name: Pull SmolLM2
        run: ollama pull smollm2:135m-instruct-q4_0
      - name: Run integration tests (nano LLM)
        run: mvn test -Ptest-nano

  e2e-tests:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      - name: Run E2E tests (real LLM)
        run: mvn test -Ptest-real
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

## Decision Matrix

| Scenario | Tier | Reason |
|----------|------|--------|
| TDD Red-Green-Refactor | Fake | Instant feedback |
| Unit test edge cases | Fake | Deterministic |
| Test agent prompts work | Nano | Real LLM behavior |
| Test workflow orchestration | Nano | Multi-agent validation |
| Pre-commit hook | Fake | Speed |
| CI/CD pipeline | Fake + Nano | Balance coverage/speed |
| Pre-release validation | Real | Production parity |
| Manual QA | Real | Full capability |
| Developer testing | Nano | No API costs |
| Offline development | Fake or Nano | No internet needed |

## Benefits of This Strategy

1. **Development Speed:** TDD with fake agents is instant
2. **Cost Efficiency:** No API charges for most testing
3. **Offline Capability:** Nano LLM works without internet
4. **Realistic Testing:** Nano LLM tests actual prompt interactions
5. **CI/CD Friendly:** Fast enough for every commit
6. **Production Confidence:** Real LLM tests catch integration issues
7. **Gradual Validation:** Test increasingly realistic scenarios

## Future Enhancements

- **Model Comparison Testing:** Compare outputs across tiers
- **Prompt Optimization:** Use nano tier to iterate quickly
- **Performance Benchmarking:** Measure inference times across tiers
- **Quality Metrics:** Track response quality by tier
