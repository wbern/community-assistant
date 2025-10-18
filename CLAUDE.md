# Claude Instructions - TDD + Akka SDK

## TDD Workflow (MANDATORY)

**Red-Green-Refactor Cycle:**
1. **RED**: Write ONE failing test (must fail for RIGHT reason, not imports/syntax)
2. **GREEN**: Write MINIMAL code to pass (no extra features)
3. **REFACTOR**: Improve while tests green (both test & impl code)

**Violations:** ❌ Multiple tests at once | Over-implementation | Code before test
**Incremental:** Test fails "undefined" → stub only | "not function" → method stub | assertion → minimal logic
**Spike:** Rare, for unclear problems - explore, discard, restart RED phase
**Commands:** `/red` `/green` `/refactor` `/cycle` `/commit` `/spike`

---

## Akka SDK Reference (v3.4+)

### Documentation Strategy

**Available Docs** (akka-context directory):
- `java/agents.html.md` + `/prompt`, `/calling`, `/memory`, `/structured`, `/failures`, `/extending`, `/streaming`, `/orchestrating`, `/guardrails`, `/evaluating`, `/testing`
- `getting-started/planner-agent/dynamic-team.html.md` - Dynamic orchestration
- `java/event-sourced-entities.html.md`, `java/key-value-entities.html.md`
- `java/views.html.md`, `java/workflows.html.md`, `java/consuming-producing.html.md`
- `java/http-endpoints.html.md`, `java/grpc-endpoints.html.md`
- `java/timed-actions.html.md`, `java/setup-and-dependency-injection.html.md`
- `ai-coding-assistant-guidelines.html.md`

**MANDATORY Reads** (first time per session):
- Workflows → Read `workflows.html.md` BEFORE coding (compensation, recovery, orchestration)
- Agents → Read `agents.html.md` BEFORE coding (LLM, tools, streaming, memory)
- First-time component → Read relevant doc
- Uncertain features or API errors → Read relevant doc

**Use Memorized Patterns For:**
- Similar components after doc read once in session
- Simple CRUD patterns
- Patterns in quick reference below

### Package Structure
```
domain/       - State records, event interfaces (NO Akka deps)
application/  - Entities, Views, Workflows, Agents, Consumers
api/          - HTTP/gRPC Endpoints
```

### Component Types & Naming

**Agent** (`{Purpose}Agent`) - ONE command handler, extends `Agent`, `@Component(id)`
```java
@Component(id = "activity-agent")
public class ActivityAgent extends Agent {
  public Effect<String> query(String msg) {
    return effects().systemMessage("...").userMessage(msg).thenReply();
  }
  @FunctionTool(description = "...")
  private String tool() { return "result"; }
}
```
- Session memory automatic via session ID (workflow ID or UUID)
- Structured: `responseConformsTo(Class)` (preferred) or `responseAs(Class)`
- Tools: `@FunctionTool` methods, `.tools()`, `.mcpTools()`
- Errors: `.onFailure(throwable -> fallback)`
- From workflow: 60s timeout, `maxRetries(2)`

**EventSourced Entity** (`{Domain}Entity`) - Commands → Events → State
```java
@Component(id = "credit-card")
public class CreditCardEntity extends EventSourcedEntity<State, Event> {
  public Effect<Done> charge(ChargeCmd cmd) {
    if (!currentState().hasCredit(cmd.amount()))
      return effects().error("Insufficient credit");
    var event = new CardCharged(cmd.amount());
    return effects().persist(event).thenReply(state -> Done.getInstance());
  }
}
```
- Events: sealed interface with `@TypeName` per event
- State/events in `domain/`, entity in `application/`
- Inject `EventSourcedEntityContext` if accessing entity ID in `emptyState()`

**KeyValue Entity** (`{Domain}Entity`) - Direct state updates, simpler than ESE

**View** (`{Domain}By{Field}View`) - Query projections
```java
public Effect<Row> onEvent(MyEvent event) {
  var id = updateContext().eventSubject().orElse("");
  return switch(event) {
    case Created c -> effects().updateRow(new Row(id, c.data()));
    case Updated u -> effects().updateRow(rowState().withData(u.data()));
  };
}
```
- **CRITICAL**: ESE views use `onEvent(Event)`, KVE views use `onUpdate(State)`
- Multi-row: wrapper record + `SELECT * AS items`
- `@Consume` on TableUpdater, NOT View class

**Workflow** (`{Process}Workflow`) - Multi-step orchestration
```java
@Component(id = "transfer")
public class TransferWorkflow extends Workflow<State> {
  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .stepTimeout(TransferWorkflow::withdraw, ofSeconds(5))
      .stepRecovery(TransferWorkflow::deposit,
        maxRetries(2).failoverTo(TransferWorkflow::compensate))
      .build();
  }

  @StepName("withdraw")
  private StepEffect withdraw() {
    return stepEffects()
      .updateState(currentState().withStatus(WITHDRAWN))
      .thenTransitionTo(TransferWorkflow::deposit);
  }
}
```
- Steps return `StepEffect` (use `stepEffects()`), commands return `Effect<T>` (use `effects()`)
- Use method refs for steps: `TransferWorkflow::stepName`
- Static orchestration: call agents in sequence
- Dynamic orchestration: PlannerAgent selects agents, `.dynamicCall(agentId)`
- Temporary state (not event-sourced)

**Consumer** (`{Purpose}Consumer`) - Event consumption
```java
@Component(id = "counter-consumer")
@Consume.FromEventSourcedEntity(CounterEntity.class)
@Produce.ToTopic("events")
public class CounterConsumer extends Consumer {
  public Effect onEvent(CounterEvent event) {
    var id = messageContext().eventSubject().get();
    return effects().produce(event, Metadata.EMPTY.add("ce-subject", id));
  }
}
```

**HTTP Endpoint** (`{Domain}Endpoint`) - NO `@Component`, has `@HttpEndpoint(path)`, `@Acl`
```java
@HttpEndpoint("/cards")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CreditCardEndpoint {
  @Post("/{id}/charge")
  public ChargeResponse charge(@PathParam String id, ChargeRequest req) {
    componentClient.forEventSourcedEntity(id)
      .method(CreditCardEntity::charge).invoke(req);
    return new ChargeResponse(...);
  }
}
```
- Inject `ComponentClient`, return API-specific types (NOT domain)
- Synchronous: `.invoke()` not `.invokeAsync()`

**gRPC Endpoint** (`{Domain}GrpcEndpointImpl`) - Implements proto interface
- Define `.proto` in `src/main/proto`, class name with `Impl` suffix
- Use private `toApi()` converters

### Critical Patterns

**Domain Logic in Records:**
```java
public record CreditCard(int balance, int limit, boolean active) {
  public CreditCard charge(int amount) {
    if (!hasCredit(amount)) throw new IllegalStateException();
    return withBalance(balance + amount);
  }
  boolean hasCredit(int amount) { return balance + amount <= limit; }
}
```

**Agent Testing:**
```java
private final TestModelProvider agentModel = new TestModelProvider();

@Override
protected TestKit.Settings testKitSettings() {
  return TestKit.Settings.DEFAULT
    .withModelProvider(MyAgent.class, agentModel);
}

@Test
void test() {
  agentModel.fixedResponse(JsonSupport.encodeToString(mockResponse));
  var result = componentClient.forAgent().inSession("sid")
    .method(MyAgent::query).invoke(request);
}
```

**Entity Unit Test:**
```java
var testKit = EventSourcedTestKit.of("id", MyEntity::new);
testKit.method(MyEntity::charge).invoke(cmd);
assertThat(testKit.getState()).satisfies(...);
```

**View Integration Test:**
```java
@Override
protected TestKit.Settings testKitSettings() {
  return TestKit.Settings.DEFAULT
    .withEventSourcedEntityIncomingMessages(MyEntity.class);
}

@Test
void test() {
  testKit.getEventSourcedEntityIncomingMessages(MyEntity.class)
    .publish(event, "entity-id");
  Awaitility.await().atMost(10, SECONDS).untilAsserted(() -> {
    var result = componentClient.forView()
      .method(MyView::query).invoke(param);
    assertThat(result).isNotNull();
  });
}
```

**Endpoint Integration Test:**
```java
httpClient.POST("/path").withRequestBody(req)
  .responseBodyAs(Response.class).invoke();
// Use httpClient, NOT componentClient
```

### Common Mistakes ❌ → ✅

❌ `io.akka.*` → ✅ `akka.*`
❌ Akka deps in domain → ✅ Plain Java in domain
❌ ESE view `onUpdate()` → ✅ ESE view `onEvent()`
❌ `componentClient` in endpoint tests → ✅ `httpClient`
❌ Domain objects from endpoints → ✅ API-specific types
❌ Logic in entities → ✅ Logic in domain objects
❌ `definition()` in Workflow → ✅ `settings()`
❌ String step names → ✅ Method refs `::stepName`
❌ `@ComponentId` → ✅ `@Component(id = "")`
❌ `testKit.call()` → ✅ `testKit.method().invoke()`
❌ Multiple Agent command handlers → ✅ ONE command handler
❌ `responseAs()` with manual JSON → ✅ `responseConformsTo()`
❌ No `@Acl` on endpoints → ✅ Always add `@Acl`
❌ `@Consume` on View → ✅ `@Consume` on TableUpdater
❌ Return `CompletionStage` → ✅ Synchronous `.invoke()`

### Self-Review Checklist
- [ ] Imports: `akka.*` not `io.akka.*`
- [ ] Events: all have `@TypeName`
- [ ] Agent: ONE handler, `responseConformsTo()`, error handling
- [ ] Workflow: Read docs first, use `settings()`, method refs
- [ ] View: `onEvent()` for ESE, wrapper record for multi-row
- [ ] Endpoint: Has `@Acl`, synchronous, API types
- [ ] Tests: `TestModelProvider` for agents, `httpClient` for endpoints
