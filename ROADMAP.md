# Community Assistant Roadmap

## Vision
A community assistant that can receive incoming emails, intelligently reply, delegate tasks to board members, track progress, and keep the community board informed.

---

## Current State (What Works ✅)

### Email Processing Pipeline
- ✅ **Automatic email polling** (EmailPollingAction) - fetches new emails every 5 minutes
- ✅ **Environment-based configuration** - polling disabled in tests, enabled in production
- ✅ **Configurable intervals** (EmailPollingConfigEntity) - customizable polling frequency
- ✅ **Email ingestion** via EmailProcessingWorkflow
- ✅ **Event-sourced persistence** (EmailEntity) - emails never lost
- ✅ **AI tagging/classification** (EmailTaggingAgent) - categorizes emails
- ✅ **Cursor-based fetching** (EmailSyncCursorEntity) - processes only new emails
- ✅ **Workflow optimization** - skips already-processed emails to save AI costs
- ✅ **HTTP endpoint** - `/process-inbox` to trigger manual processing

### Tracking & Visibility
- ✅ **Google Sheets sync** (GoogleSheetConsumer) - materializes emails to spreadsheet
- ✅ **Event buffering** (SheetSyncBufferEntity + SheetSyncFlushAction) - batches API calls
- ✅ **Rate limiting protection** - flushes every 10 seconds to avoid quota issues
- ✅ **Idempotency** - defense-in-depth (entity + workflow levels)
- ✅ **Batch duplicate handling** - merges duplicates within same batch (TDD fix)
- ✅ **Active inquiry tracking** (ActiveInquiryEntity) - tracks current email requiring attention
- ✅ **Automated reminder system** (ReminderAction + ActiveInquiryConsumer) - configurable timer-based reminders
- ✅ **Views for inquiry tracking** (InquiriesView, TopicsView) - optimized queries for board visibility

#### Google Sheets Future Improvements
- [ ] **Retry logic with backoff timer** - Add exponential backoff for API rate limit handling
  - **Architecture**: Enhance existing `SheetSyncFlushAction` (TimedAction) - keep domain service unchanged
  - **Approach**: Use TimedAction's built-in exponential backoff (3s → 30s max) + maxRetries
  - **Pattern**: Graceful error handling in TimedAction.execute() - return `effects().done()` on errors
  - **No restructuring needed**: Current Domain Service + TimedAction pattern IS the Akka way
  - Handle 429 Too Many Requests errors gracefully in TimedAction layer
  - Add configurable max retry attempts via TimedAction.maxRetries
  - Log retry attempts for monitoring
  - Preserve domain separation: GoogleSheetSyncService stays pure business logic

### Infrastructure
- ✅ **Domain-driven design** - clean separation (domain/, application/, api/)
- ✅ **Akka SDK 3.4+ best practices** - proper annotations, patterns
- ✅ **Comprehensive tests** - unit, integration, end-to-end with 80% coverage threshold
- ✅ **TDD workflow** - RED-GREEN-REFACTOR discipline with JaCoCo enforcement
- ✅ **Chat interface foundation** - ChatHandlerLofiAgent with pattern-matching responses
- ✅ **AI agent testing infrastructure** - TestModelProvider patterns for reliable agent testing
- ✅ **Timer-based orchestration** - Comprehensive TimedAction patterns (EmailPollingAction, SheetSyncFlushAction, ReminderAction)
- ✅ **Environment-aware configuration** - Production vs test environment behavior via application.conf
- ✅ **Shared constants extraction** - ChatConstants for maintainable magic string elimination

---

## Recent Accomplishments (2025-10-31)

### Automatic Email Polling System
- ✅ **EmailPollingAction** - Automatic email fetching every 5 minutes via TimedAction
- ✅ **EmailPollingConfigEntity** - Configurable polling intervals with default 5-minute setting
- ✅ **Environment-based configuration** - Polling disabled in test environments, enabled in production
- ✅ **ServiceConfiguration bootstrap** - Conditional timer initialization based on environment
- ✅ **Timer shutdown handling** - Graceful error handling during test teardown
- ✅ **Production readiness** - Full test coverage with 85 passing tests, zero failures
- ✅ **Configuration flexibility** - `EMAIL_POLLING_ENABLED` and `EMAIL_POLLING_INTERVAL` environment variables

### Chat Foundation & Inquiry Management
- ✅ **ChatHandlerLofiAgent** - Pattern-matching chat handler for @assistant mentions
- ✅ **Inquiry addressing workflow** - Board members can mark inquiries as addressed with "@assistant" mentions
- ✅ **Active inquiry tracking** - Single active inquiry management via ActiveInquiryEntity
- ✅ **Automated reminder system** - Timer-based reminders with configurable intervals (default 24h)
- ✅ **Email-to-timer flow** - Complete integration from email arrival to timer scheduling
- ✅ **Topic lookup foundation** - Basic tag-based topic queries (elevator, noise, maintenance)
- ✅ **Shared constants** - ChatConstants.ASSISTANT_MENTION for maintainable code
- ✅ **Comprehensive test coverage** - Timer scheduling flow with proper async testing patterns

### Technical Infrastructure  
- ✅ **Proper Akka SDK timer patterns** - Duration.ofMillis(0) for immediate triggering in tests
- ✅ **TDD discipline completion** - Full RED-GREEN-REFACTOR cycle for timer functionality
- ✅ **TimedAction integration** - ReminderAction with proper componentClient usage
- ✅ **Consumer event handling** - ActiveInquiryConsumer linking email events to timers
- ✅ **Entity state management** - OutboundChatMessageEntity with proper emptyState()

---

## Missing Features (What's Next 📋)

### 1. Chat Interface Integration 🟡

**Current State**: Basic foundation in place with ChatHandlerLofiAgent implementing pattern-matching responses for @assistant mentions and inquiry addressing.

**What's Needed**: Enhanced topic-centric chat interface that enables natural board member communication about community issues.

**Architecture**: See [CHAT_INTERFACE_ARCHITECTURE.md](./CHAT_INTERFACE_ARCHITECTURE.md) for complete architectural analysis.

**Key Components**:
- ✅ `ChatHandlerLofiAgent` - Pattern-matching for @assistant mentions with inquiry addressing
- ✅ `TopicsView` - Basic topic lookup by tags (elevator, noise, maintenance)
- ✅ `InquiriesView` - Query interface for inquiry tracking
- ✅ `OutboundChatMessageEntity` - Stores outbound chat messages for verification
- [ ] `TopicEntity` (EventSourcedEntity) - Long-lived community issue tracking
- [ ] `ConversationEntity` (EventSourcedEntity) - Per-inquiry context management  
- [ ] `TopicClassifierAgent` - AI classification of emails/mentions into topics
- [ ] `TopicLookupAgent` - Semantic search for "@assistant about that elevator thing"
- [ ] `InquiryWorkflow` - Multi-step conversation orchestration
- [ ] Platform adapters (Discord, Slack, HTTP endpoints)

**Integration Points**:
- ✅ Links `EmailEntity` events to inquiry addressing via `ChatHandlerLofiAgent`
- ✅ Active inquiry tracking through `ActiveInquiryConsumer` and timer scheduling
- ✅ Reminder system integration with configurable intervals via `ReminderConfigEntity`
- [ ] Extends existing `EmailProcessingWorkflow` with topic classification
- [ ] Enhances `GoogleSheetConsumer` with topic context columns
- [ ] Reuses `EmailTaggingAgent` for topic classification input
- [ ] Links `EmailEntity` events to `TopicEntity` creation/updates

**TDD Implementation Phases**:
1. ✅ Basic @mention handling and inquiry addressing foundation
2. ✅ Timer-based reminder system with configurable intervals
3. [ ] TopicEntity foundation with event sourcing
4. [ ] Topic classification from emails using AI
5. [ ] Enhanced @mention resolution and semantic topic lookup
6. [ ] Complete inquiry workflow with context gathering
7. [ ] Platform integration (starting with HTTP/webhooks)

**Benefits**:
- ✅ Board members can @mention assistant for inquiry addressing: "@assistant I'll handle this"
- ✅ Automated reminders ensure inquiries don't fall through cracks
- ✅ Basic topic lookup by tags: "@assistant elevator", "@assistant noise"
- [ ] Enhanced natural language: "@assistant status on Building A maintenance?"
- [ ] Complete audit trail of community issue discussions
- ✅ Leverages existing email processing infrastructure
- [ ] Platform-agnostic design (Discord, Slack, web interface)

---

### 2. Email Sending & Replies ❌

**Current Gap**: We can receive and classify emails but cannot send responses.

**What's Needed**:
- [ ] Gmail API integration for sending emails
- [ ] `EmailOutboxService` interface (similar to EmailInboxService pattern)
- [ ] `GmailOutboxService` implementation using Gmail API
- [ ] `ReplyDraftEntity` (EventSourcedEntity) to store draft replies
- [ ] `EmailReplyAgent` (Agent) to generate draft responses using AI
- [ ] Approval workflow before sending

**Technical Approach**:
```java
// Domain service interface
public interface EmailOutboxService {
    void sendEmail(String to, String subject, String body, String inReplyTo);
    void sendReply(String originalMessageId, String replyBody);
}

// AI agent for drafting replies
@Component(id = "email-reply-agent")
public class EmailReplyAgent extends Agent {
    public Effect<EmailReply> draftReply(Email original, EmailTags tags) {
        // Use AI to draft contextual reply based on email content and tags
        // Consider urgency, category, previous replies
    }
}

// Event-sourced entity for reply tracking
@Component(id = "reply-draft")
public class ReplyDraftEntity extends EventSourcedEntity<State, Event> {
    // Events: DraftCreated, DraftApproved, DraftRejected, EmailSent
    // State: draftText, status, approvedBy, sentAt
}
```

**Integration Points**:
- EmailProcessingWorkflow could trigger draft generation after tagging
- New `ReplyApprovalWorkflow` for board member review
- Google Sheets could show "Draft Ready" status

---

### 2. Delegation & Assignment ❌

**Current Gap**: Emails are tagged but not assigned to specific board members.

**What's Needed**:
- [ ] `BoardMember` domain model (name, email, responsibilities)
- [ ] `BoardMemberEntity` (KeyValueEntity) to store member info
- [ ] Assignment logic based on email tags/category
- [ ] `TaskAssignmentAgent` to intelligently assign based on:
  - Category (maintenance → facilities manager)
  - Urgency (urgent → board president)
  - Workload balance
  - Specialization
- [ ] Add "assigned_to" field to SheetRow

**Technical Approach**:
```java
// Domain model
public record BoardMember(
    String id,
    String name,
    String email,
    Set<String> responsibilities,  // e.g., "maintenance", "financial"
    int currentWorkload
) {}

// Assignment agent
@Component(id = "task-assignment-agent")
public class TaskAssignmentAgent extends Agent {
    public Effect<Assignment> assignTask(Email email, EmailTags tags, List<BoardMember> members) {
        // AI determines best board member based on:
        // - Tag categories matching responsibilities
        // - Current workload
        // - Urgency level
    }
}

// Update EmailEntity to include assignment
public sealed interface Event {
    @TypeName("email-received")
    record EmailReceived(Email email) implements Event {}

    @TypeName("tags-generated")
    record TagsGenerated(EmailTags tags) implements Event {}

    @TypeName("task-assigned")
    record TaskAssigned(String boardMemberId, Instant assignedAt) implements Event {}
}
```

**Integration**:
- EmailProcessingWorkflow extended: fetch emails → tag → assign → sync
- Google Sheets shows assigned member
- Notifications sent to assigned member

---

### 3. Notifications & Informing ❌

**Current Gap**: Board members aren't notified when tasks are assigned or require attention.

**What's Needed**:
- [ ] Email notification system
- [ ] Notification preferences (immediate, daily digest, none)
- [ ] Different notification types:
  - Task assigned to you
  - Urgent issue requires attention
  - Reply draft ready for approval
  - Weekly summary
- [ ] `NotificationEntity` (EventSourcedEntity) to track sent notifications
- [ ] `NotificationScheduler` (TimedAction) for digest delivery

**Technical Approach**:
```java
// Domain model
public record Notification(
    String id,
    NotificationType type,
    String recipientEmail,
    String subject,
    String body,
    Instant scheduledFor,
    NotificationStatus status
) {}

public enum NotificationType {
    TASK_ASSIGNED,
    URGENT_ATTENTION,
    DRAFT_READY,
    DAILY_DIGEST,
    WEEKLY_SUMMARY
}

// Notification consumer
@Component(id = "notification-consumer")
@Consume.FromEventSourcedEntity(EmailEntity.class)
public class NotificationConsumer extends Consumer {
    public Effect onEvent(EmailEntity.Event event) {
        return switch (event) {
            case TaskAssigned assigned -> {
                // Send "New task assigned" email to board member
                yield effects().done();
            }
            // Other events...
        };
    }
}

// Digest scheduler
@Component(id = "daily-digest-scheduler")
public class DailyDigestScheduler extends TimedAction {
    @Override
    public Duration interval() {
        return Duration.ofHours(24);
    }

    public Effect execute() {
        // Aggregate day's activity
        // Send digest to all board members
        return effects().done();
    }
}
```

---

### 4. Real Gmail Integration ❌

**Current Gap**: Using `MockEmailInboxService` - not connected to real inbox.

**What's Needed**:
- [ ] Gmail API credentials/OAuth setup
- [ ] `GmailInboxService` implementation
- [ ] Gmail API client library integration
- [ ] Label-based filtering (e.g., only "CommunityBoard" label)
- [ ] Attachment handling
- [ ] HTML email parsing
- [ ] Thread/conversation tracking

**Technical Approach**:
```java
public class GmailInboxService implements EmailInboxService {
    private final Gmail gmailClient;

    @Override
    public List<Email> fetchEmailsSince(Instant since) {
        // Use Gmail API:
        // 1. Query messages after timestamp
        // 2. Filter by label (e.g., "CommunityBoard")
        // 3. Fetch message details
        // 4. Parse to Email domain objects
        // 5. Handle attachments, HTML, threads
    }
}
```

**Configuration**:
```conf
# application.conf
gmail {
    credentials-path = ${?GMAIL_CREDENTIALS_PATH}
    label = "CommunityBoard"
    max-results = 100
}
```

**Security Considerations**:
- OAuth 2.0 for authentication
- Service account vs. user account
- Token refresh handling
- Secure credential storage

---

### 5. Intelligent Reply Generation ❌

**Current Gap**: AI tags emails but doesn't suggest responses.

**What's Needed**:
- [ ] Context-aware reply generation
- [ ] Template library for common scenarios
- [ ] Tone/style configuration (formal, friendly)
- [ ] Multi-language support (if needed)
- [ ] Reference previous conversations
- [ ] Include relevant policies/rules

**Technical Approach**:
```java
@Component(id = "reply-generator-agent")
public class ReplyGeneratorAgent extends Agent {

    public Effect<DraftReply> generateReply(GenerateReplyRequest request) {
        // System prompt with:
        // - Community board context
        // - Tone guidelines
        // - Template library
        // - Relevant policies

        return effects()
            .systemMessage(buildSystemPrompt(request))
            .userMessage(buildUserPrompt(request))
            .responseConformsTo(DraftReply.class)
            .onFailure(throwable -> fallbackTemplate(request))
            .thenReply();
    }

    private String buildSystemPrompt(GenerateReplyRequest request) {
        return """
            You are a helpful community board assistant.

            Guidelines:
            - Be professional but warm
            - Reference community policies when relevant
            - Offer clear next steps
            - Set realistic expectations

            Available templates:
            %s

            Community policies:
            %s
            """.formatted(getTemplates(), getPolicies());
    }
}

public record DraftReply(
    String replyText,
    Set<String> suggestedActions,  // e.g., "schedule_maintenance"
    String templateUsed,
    double confidence
) {}
```

**Template Examples**:
- Maintenance requests → "We've logged your request. Expected resolution: X days"
- General inquiries → "Thank you for reaching out. [Answer]"
- Complaints → "We apologize for the inconvenience. [Action plan]"
- Suggestions → "Thank you for your suggestion. We'll discuss at next board meeting"

---

### 6. Approval Workflow ❌

**Current Gap**: No human-in-the-loop before sending replies.

**What's Needed**:
- [ ] Approval workflow for generated replies
- [ ] Different approval levels based on:
  - Reply confidence score
  - Email urgency
  - Category sensitivity
- [ ] Board member interface to review/edit/approve
- [ ] Auto-approval for high-confidence simple replies
- [ ] Escalation for complex cases

**Technical Approach**:
```java
// Workflow for reply approval
@Component(id = "reply-approval-workflow")
public class ReplyApprovalWorkflow extends Workflow<State> {

    public record State(
        String emailId,
        String draftId,
        ApprovalStatus status,
        String assignedReviewer,
        Instant deadline
    ) {}

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .stepTimeout(ReplyApprovalWorkflow::waitForApproval, ofHours(24))
            .stepRecovery(ReplyApprovalWorkflow::waitForApproval,
                maxRetries(1).failoverTo(ReplyApprovalWorkflow::escalate))
            .build();
    }

    public Effect<String> start(StartApprovalCmd cmd) {
        // Determine if auto-approve or needs review
        if (canAutoApprove(cmd.confidence(), cmd.category())) {
            return effects().transitionTo(ReplyApprovalWorkflow::sendReply);
        } else {
            return effects().transitionTo(ReplyApprovalWorkflow::waitForApproval);
        }
    }

    @StepName("wait-for-approval")
    private StepEffect waitForApproval() {
        // Wait for board member to approve/reject/edit
        // Timeout triggers escalation
    }

    @StepName("send-reply")
    private StepEffect sendReply() {
        // Send via EmailOutboxService
        // Record sent timestamp
    }
}
```

**UI Integration**:
- Google Sheets column: "Draft Status" (Pending Review, Approved, Sent)
- Email notification to reviewer with approval link
- Simple web UI or email-based approval (reply "APPROVE" or "REJECT")

---

### 7. Progress Tracking & Metrics ❌

**Current Gap**: Limited visibility into overall system health and performance.

**What's Needed**:
- [ ] Dashboard for board overview
- [ ] Metrics:
  - Emails received (today, week, month)
  - Average response time
  - Tasks assigned per member
  - Approval rate for AI drafts
  - Categories breakdown
  - Urgent items pending
- [ ] Views for querying
- [ ] Scheduled reports

**Technical Approach**:
```java
// View for metrics
@Component(id = "email-metrics-view")
public class EmailMetricsView extends View {

    public record MetricsRow(
        String period,      // "2025-10-21"
        int emailsReceived,
        int repliesSent,
        int tasksAssigned,
        int urgentPending,
        Map<String, Integer> categoryBreakdown
    ) {}

    @Table("email_metrics")
    public static class EmailMetricsTable extends TableUpdater<MetricsRow> {
        // Aggregate events to metrics
    }

    @Query("SELECT * FROM email_metrics WHERE period = :period")
    public QueryEffect<MetricsRow> getMetrics(String period) {
        return queryResult();
    }
}
```

---

## Implementation Phases

### Phase 1: Core Reply Capabilities (Sprint 1-2)
**Goal**: Enable basic email sending and reply drafting

1. Implement `EmailOutboxService` interface + Gmail implementation
2. Build `EmailReplyAgent` for draft generation
3. Create `ReplyDraftEntity` for tracking drafts
4. Add simple manual approval (via email or command)
5. Test end-to-end: receive → tag → draft → approve → send

**Success Criteria**:
- Can send test replies via Gmail API
- AI generates reasonable draft responses
- Board member can approve and send

---

### Phase 2: Delegation & Assignment (Sprint 3-4)
**Goal**: Automatically assign tasks to appropriate board members

1. Create `BoardMember` domain model
2. Build `TaskAssignmentAgent` for intelligent routing
3. Add assignment events to EmailEntity
4. Update Google Sheets sync to show assignments
5. Implement basic email notifications

**Success Criteria**:
- Incoming emails automatically assigned based on category
- Board members receive "Task Assigned" emails
- Google Sheet shows assigned member per email

---

### Phase 3: Approval Workflow (Sprint 5-6)
**Goal**: Human-in-the-loop for reply quality control

1. Build `ReplyApprovalWorkflow`
2. Implement confidence-based auto-approval
3. Add web UI for review/edit/approve (or email-based)
4. Add escalation for timeouts
5. Track approval metrics

**Success Criteria**:
- High-confidence replies sent automatically
- Low-confidence drafts require approval
- Approvers can edit before sending
- Timeout → escalation to senior board member

---

### Phase 4: Enhanced Intelligence (Sprint 7-8)
**Goal**: Improve AI quality and context awareness

1. Add conversation threading
2. Build template library
3. Implement policy/rule integration
4. Add sentiment analysis
5. Multi-turn conversations support

**Success Criteria**:
- System references previous emails in thread
- Replies use appropriate templates
- Policy violations detected and flagged
- Follow-up questions handled appropriately

---

### Phase 5: Analytics & Optimization (Sprint 9-10)
**Goal**: Visibility and continuous improvement

1. Build metrics views
2. Create dashboard (web UI or enhanced Google Sheet)
3. Scheduled digest reports
4. A/B testing for reply quality
5. Feedback loop for AI improvement

**Success Criteria**:
- Board has visibility into system performance
- Can identify bottlenecks and trends
- AI quality improves over time based on feedback

---

## Technical Considerations

### Gmail API Integration
- **Authentication**: OAuth 2.0 service account
- **Quotas**: 1 billion quota units/day (sufficient for community board)
- **Rate Limiting**: Use same buffering pattern as Google Sheets
- **Testing**: Mock for tests, real API for integration tests

### AI Agent Design
- **Context Window**: Keep email threads manageable
- **Structured Output**: Use `responseConformsTo()` for reliability
- **Error Handling**: Fallback to templates on AI failure
- **Testing**: `TestModelProvider` for predictable tests

### Data Privacy & Security
- **Email Content**: Sensitive - use event sourcing for audit trail
- **Personal Data**: GDPR considerations if in EU
- **Credentials**: Never commit to git - use environment variables
- **Access Control**: Board members only (add `@Acl` annotations)

### Scalability
- **Current**: Single community board (~hundreds of emails/month)
- **Future**: Multi-tenant (multiple communities)
- **Bottlenecks**: Gmail API quotas, AI costs
- **Optimization**: Batching, caching, smart rate limiting

### Cost Management
- **AI Costs**: Skip already-processed emails (already implemented ✅)
- **Gmail API**: Free tier should be sufficient
- **Google Sheets**: Free tier covers thousands of updates/day
- **Monitoring**: Track API usage to avoid surprise bills

---

## Open Questions

1. **Board Member Onboarding**: How do we add/remove/update board members?
   - Admin UI needed?
   - Configuration file?
   - Self-service portal?

2. **Multi-language Support**: Does community need non-English support?
   - Swedish? Other languages?
   - AI can handle but needs explicit configuration

3. **Attachment Handling**: What to do with email attachments?
   - Store in Google Drive?
   - Reference in Sheet?
   - AI can't process images yet (or can we use vision models?)

4. **Legal/Compliance**: Are there regulations around automated replies?
   - Disclaimer needed?
   - "This is an automated response" notice?
   - Retention policies?

5. **Escalation Paths**: Who handles edge cases?
   - Board president?
   - Rotating duty?
   - Emergency contact?

---

## What Success Looks Like

- Board members spend less time on repetitive email responses
- Residents get faster replies to common questions
- Nothing falls through the cracks (everything tracked in the Sheet)
- Fair workload distribution among board members
- Board can focus on actual decision-making instead of email admin

---

## Next Immediate Steps

### Short-term (Next 1-2 weeks)
1. **Enhanced chat interface** - Improve ChatHandlerLofiAgent with more sophisticated pattern matching
2. **AI-powered topic classification** - Upgrade from tag-based to semantic topic lookup  
3. **Topic entity foundation** - Implement TopicEntity for long-lived issue tracking
4. **Multi-timer support** - Handle multiple active inquiries simultaneously

### Medium-term (Next month)
1. **Gmail API integration** - Replace MockEmailInboxService with real Gmail connectivity
2. **Email sending capabilities** - EmailOutboxService interface and implementation
3. **Basic reply generation** - EmailReplyAgent for AI-generated draft responses
4. **Approval workflow foundation** - Human-in-the-loop for reply quality control

### Validation & Feedback
1. **Board member testing** - Get feedback on current inquiry addressing workflow
2. **Performance monitoring** - Ensure timer system scales with multiple inquiries
3. **Chat interface usability** - Validate @assistant mention patterns with users

---

## References

- [Akka SDK Documentation](https://doc.akka.io/)
- [Gmail API Guide](https://developers.google.com/gmail/api)
- [Google Sheets API](https://developers.google.com/sheets/api)
- CLAUDE.md (project TDD standards)
- Current codebase event sourcing patterns
