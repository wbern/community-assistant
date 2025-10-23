# Chat Interface Architecture - Topic-Centric Design

## Overview

This document outlines the architectural design for a chat interface that enables two-way communication between board members and the community assistant. The design follows Akka SDK blueprints and integrates naturally with our existing email processing pipeline.

## Core Architectural Principle: Topic-Centric Model

Instead of session-based conversations, the system models **community issues/topics** as first-class entities. This aligns with how board members actually think and communicate about community matters.

### Key Concepts

**Topic**: A community issue or matter (e.g., "Building A Elevator Maintenance", "Parking Policy Update")
- Long-lived entity that accumulates context over time
- Can be initiated by incoming emails or board member discussions
- Contains complete history of related emails, discussions, and actions

**Conversation**: A specific inquiry about a topic
- Short-lived, per-inquiry context
- Always references a TopicEntity for context
- Ends when the specific inquiry is resolved

## Architectural Research Foundation

### Akka SDK Sample Analysis

Based on analysis of official Akka samples:

**ask-akka-agent patterns**:
- Event-sourced conversation management
- RAG (Retrieval-Augmented Generation) workflow for context
- View integration for conversation history
- Streaming responses for real-time interaction

**agentic-haiku patterns**:
- Multi-agent orchestration with specialized responsibilities
- Quality gates for content validation
- Event-driven microservice architecture
- Observability and monitoring integration

### SessionEntity Pattern (Adapted)

From Akka SDK documentation, SessionEntity is designed for LLM conversation management:
- Event sourcing with `UserMessageAdded` and `AiMessageAdded` events
- Token usage tracking for cost management
- User and session identification for conversation continuity

**Our Adaptation**: Use TopicEntity + ConversationEntity instead of pure SessionEntity to align with board communication patterns.

## Component Architecture

### 1. Core Domain Entities

#### TopicEntity (EventSourcedEntity)
```java
@Component(id = "community-topic")
public class TopicEntity extends EventSourcedEntity<TopicState, TopicEvent> {
    // Events: TopicCreated, EmailAdded, DiscussionAdded, StatusUpdated, ActionItemAdded
    // State: topicId, summary, relatedEmails[], discussions[], currentStatus, assignedMembers[]
}
```

**Responsibilities:**
- Track complete lifecycle of community issues
- Aggregate related emails and discussions
- Maintain current status and action items
- Enable cross-referencing and relationship tracking

#### ConversationEntity (EventSourcedEntity)
```java
@Component(id = "board-conversation") 
public class ConversationEntity extends EventSourcedEntity<ConversationState, ConversationEvent> {
    // Events: ConversationStarted, UserMessageAdded, AssistantResponseSent, TopicReferenced
    // State: conversationId, boardMemberId, referencedTopicId, messages[], resolved
}
```

**Responsibilities:**
- Handle individual chat inquiries
- Link inquiries to relevant topics
- Maintain conversation context and resolution status
- Track board member interactions

### 2. AI Agent Composition

Following the agentic-haiku multi-agent pattern:

#### TopicClassifierAgent
```java
@Component(id = "topic-classifier-agent")
public class TopicClassifierAgent extends Agent {
    // Purpose: Classify emails and mentions into topic categories
    // Input: Email content or chat mention
    // Output: topicId, confidence score, suggested actions
}
```

#### TopicLookupAgent  
```java
@Component(id = "topic-lookup-agent")
public class TopicLookupAgent extends Agent {
    // Purpose: Semantic search across existing topics
    // Handles: "@assistant about that elevator thing" → finds relevant TopicEntity
    // Uses: Vector similarity and fuzzy matching
}
```

#### TopicRelationshipAgent
```java
@Component(id = "topic-relationship-agent")
public class TopicRelationshipAgent extends Agent {
    // Purpose: Identify and link related community issues
    // Handles: Cross-topic discussions and dependencies
    // Output: Topic relationship mappings
}
```

#### ContextGatheringAgent
```java
@Component(id = "context-gathering-agent")
public class ContextGatheringAgent extends Agent {
    // Purpose: Compile comprehensive topic context for responses
    // Input: TopicEntity reference
    // Output: Formatted context with emails, discussions, status
}
```

### 3. Workflow Orchestration

#### InquiryWorkflow
```java
@Component(id = "inquiry-workflow")
public class InquiryWorkflow extends Workflow<InquiryState> {
    // Steps: parseIntent → lookupTopic → gatherContext → generateResponse
    // Handles: Both new topic creation and existing topic updates
    // Quality gates: Permission validation, action confirmation
}
```

**Workflow Steps:**
1. **parseIntent**: Determine if creating new topic or referencing existing
2. **lookupTopic**: Find relevant TopicEntity via semantic search  
3. **gatherContext**: Compile complete topic history and current status
4. **generateResponse**: Format response with full context
5. **executeActions**: Handle any requested actions (with approval gates)

### 4. Optimized Query Views

#### TopicContextView
```java
@Component(id = "topic-context-view")
public class TopicContextView extends View {
    // Fast queries: topics by status, recent activity, board member assignments
    // Vector search: semantic topic matching for @mentions
    // Aggregations: topic summaries, related email counts
}
```

#### BoardActivityView
```java
@Component(id = "board-activity-view")
public class BoardActivityView extends View {
    // Dashboard data: active topics, pending actions, recent discussions
    // Board member activity: contributions, assignments, response times
}
```

### 5. Platform Integration Adapters

#### ChatPlatformAdapter Interface
```java
public interface ChatPlatformAdapter {
    void sendMessage(String conversationId, String message);
    void streamResponse(String conversationId, Publisher<String> responseStream);
    void handleMention(String boardMemberId, String message);
}
```

**Implementations:**
- `DiscordAdapter`: Discord webhook/bot integration
- `SlackAdapter`: Slack bot API integration  
- `HttpChatEndpoint`: REST API for web interfaces
- `EmailReplyAdapter`: Email-based interaction

## Integration with Existing System

### Enhanced EmailProcessingWorkflow
```java
public class EmailProcessingWorkflow extends Workflow<EmailProcessingState> {
    // Existing: fetchEmails → tagEmails → syncToSheets
    // New: classifyTopic → createOrUpdateTopicEntity
    // Links: EmailEntity events → TopicEntity events
}
```

### Enhanced GoogleSheetConsumer
```java
@Component(id = "google-sheet-consumer") 
public class GoogleSheetConsumer extends Consumer {
    // New columns: TopicID, RelatedTopics, BoardMemberDiscussions
    // Enhanced context: Complete topic timeline in spreadsheet
}
```

### Existing Component Reuse
- **EmailTaggingAgent**: Becomes input to TopicClassifierAgent
- **EmailEntity**: Events flow to TopicEntity creation/updates
- **SheetSyncBufferEntity**: Extended to include topic context

## Communication Flows

### Flow 1: Email Creates/Updates Topic
```
Email arrives → EmailProcessingWorkflow → EmailTaggingAgent → TopicClassifierAgent
    ↓
TopicEntity (new or existing)
    ↓  
GoogleSheetConsumer (with topic context)
```

### Flow 2: Board Member @mentions Topic
```
"@assistant status on elevator issue?" → InquiryWorkflow
    ↓
TopicLookupAgent → TopicEntity lookup
    ↓
ContextGatheringAgent → Full topic context
    ↓
ConversationEntity creation + Response
```

### Flow 3: Proactive Notifications
```
EmailEntity.emailReceived → TopicEntity.emailAdded
    ↓
NotificationConsumer → ChatPlatformAdapter
    ↓
"New email added to Building A maintenance topic"
```

## Topic Lifecycle Management

### Topic States
- **New**: Recently created, needs board attention
- **Active**: Under discussion or action
- **Pending**: Waiting for external response
- **InProgress**: Actions being executed
- **Resolved**: Issue closed
- **Archived**: Historical reference only

### State Transitions
- Email arrival can reactivate archived topics
- Board member discussions move topics to active
- Action completion transitions to resolved
- Time-based archival for inactive topics

## Event Sourcing Strategy

### TopicEntity Events
```java
public sealed interface TopicEvent {
    @TypeName("topic-created")
    record TopicCreated(String topicId, String summary, String createdBy) implements TopicEvent {}
    
    @TypeName("email-added")
    record EmailAdded(String emailId, String topicId) implements TopicEvent {}
    
    @TypeName("discussion-added") 
    record DiscussionAdded(String conversationId, String boardMemberId) implements TopicEvent {}
    
    @TypeName("status-updated")
    record StatusUpdated(TopicStatus oldStatus, TopicStatus newStatus, String reason) implements TopicEvent {}
    
    @TypeName("action-item-added")
    record ActionItemAdded(String actionId, String description, String assignedTo) implements TopicEvent {}
}
```

### ConversationEntity Events  
```java
public sealed interface ConversationEvent {
    @TypeName("conversation-started")
    record ConversationStarted(String conversationId, String boardMemberId, String topicId) implements ConversationEvent {}
    
    @TypeName("user-message-added")
    record UserMessageAdded(String message, Instant timestamp) implements ConversationEvent {}
    
    @TypeName("assistant-response-sent")
    record AssistantResponseSent(String response, List<String> referencedTopics) implements ConversationEvent {}
    
    @TypeName("topic-referenced")
    record TopicReferenced(String topicId, String context) implements ConversationEvent {}
}
```

## Security and Permissions

### Board Member Authentication
- Integration with existing board member identification
- Role-based access (president, treasurer, member)
- Audit trail of all interactions

### Action Authorization
- Sensitive actions require confirmation
- Multi-step approval for significant changes
- Automatic escalation for urgent matters

### Data Privacy
- All conversations event-sourced for accountability
- Board member PII handling compliance
- Secure platform integration (Discord/Slack tokens)

## Observability and Monitoring

### Metrics Collection
- Topic creation and resolution rates
- Board member engagement patterns
- Response time and accuracy metrics
- Platform usage distribution

### Health Monitoring
- Agent response times and error rates
- Topic lookup accuracy
- Integration health (Discord/Slack connectivity)
- Event sourcing replay capability

## Future Extensions

### Advanced AI Capabilities
- Predictive topic escalation
- Automated action item generation
- Sentiment analysis of board discussions
- Multi-language support for diverse communities

### Enhanced Integrations
- Calendar integration for action items
- Document management system links
- External vendor communication
- Resident portal integration

### Analytics and Insights
- Board efficiency metrics
- Topic trend analysis
- Response time optimization
- Community satisfaction tracking

## Implementation Strategy

This architecture provides the foundation for TDD implementation. Each component can be developed incrementally:

1. **Start with TopicEntity**: Core domain logic with event sourcing
2. **Add topic classification**: AI agent for email → topic mapping
3. **Implement topic lookup**: Semantic search for @mentions
4. **Build inquiry workflow**: Complete conversation orchestration
5. **Add platform adapters**: Chat interface implementations

The design ensures that each TDD cycle builds naturally on previous components while maintaining architectural integrity and Akka SDK best practices.