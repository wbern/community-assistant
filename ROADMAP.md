# Community Assistant Roadmap

## Vision
A community assistant that can receive incoming emails, intelligently reply, delegate tasks to board members, track progress, and keep the community board informed.

---

## Current Capabilities ✅

### Email Processing
- **Automatic Gmail polling** every 5 minutes with real API integration
- **AI tagging and classification** of incoming emails
- **Google Sheets sync** for board visibility and tracking
- **Active inquiry management** with automated reminders
- **Chat interface** for board member @mentions and inquiry addressing

### Technical Foundation
- **Event-sourced architecture** with comprehensive testing
- **Domain-driven design** with clean separation
- **Environment-aware configuration** (production vs test)

---

## What's Next 📋

### 1. Email Sending & Replies
**Goal**: Complete the email loop - receive, process, respond

**Key Components**:
- `EmailOutboxService` interface + Gmail implementation
- `EmailReplyAgent` for AI-generated draft responses  
- `ReplyDraftEntity` for tracking drafts
- Basic approval workflow

### 2. Enhanced Chat Interface
**Goal**: More intelligent @mention handling and topic tracking

**Key Components**:
- `TopicEntity` for long-lived community issue tracking
- `TopicClassifierAgent` for AI-powered topic classification
- Enhanced semantic search: "@assistant about that elevator thing"

### 3. Delegation & Assignment  
**Goal**: Automatically assign emails to appropriate board members

**Key Components**:
- `BoardMember` domain model with responsibilities
- `TaskAssignmentAgent` for intelligent routing based on category/workload
- Assignment tracking in EmailEntity and Google Sheets

### 4. Notifications & Approval Workflow
**Goal**: Keep board members informed and ensure quality control

**Key Components**:
- Email notification system for task assignments
- `ReplyApprovalWorkflow` with confidence-based auto-approval
- Escalation for complex cases

## Success Vision

- Board members spend less time on repetitive email responses
- Residents get faster replies to common questions  
- Nothing falls through the cracks (everything tracked)
- Fair workload distribution among board members
- Board can focus on actual decision-making instead of email admin
