# Component Map - What Exists in the System

Simple visual map of all Akka SDK components.

## Component Types

```mermaid
mindmap
  root((Community<br/>Assistant))
    API
      ChatEndpoint
      EmailEndpoint
    Entities
      EmailEntity
      SheetSyncBufferEntity
      EmailSyncCursorEntity
    Agents
      ChatHandlerAgent
      EmailTaggingAgent
    Workflows
      EmailProcessingWorkflow
    Views
      InquiriesView
      TopicsView
    Actions
      GoogleSheetConsumer
      SheetSyncFlushAction
    External
      Gmail API
      Google Sheets API
```

## By Akka SDK Type

```mermaid
graph LR
    subgraph Endpoints
        A1[ChatEndpoint<br/>HTTP]
        A2[EmailEndpoint<br/>HTTP]
    end

    subgraph EventSourced
        B1[EmailEntity<br/>Email + Status]
    end

    subgraph KeyValue
        C1[SheetSyncBufferEntity<br/>Batch buffer]
        C2[EmailSyncCursorEntity<br/>Last sync time]
    end

    subgraph Agents
        D1[ChatHandlerAgent<br/>Board chat]
        D2[EmailTaggingAgent<br/>AI tagging]
    end

    subgraph Workflows
        E1[EmailProcessingWorkflow<br/>Email pipeline]
    end

    subgraph Views
        F1[InquiriesView<br/>Email inquiries]
        F2[TopicsView<br/>Tagged topics]
    end

    subgraph Consumers/Actions
        G1[GoogleSheetConsumer<br/>Event listener]
        G2[SheetSyncFlushAction<br/>5 min timer]
    end

    style EventSourced fill:#e3f2fd
    style KeyValue fill:#f3e5f5
    style Agents fill:#fff3e0
    style Workflows fill:#e8f5e9
    style Views fill:#fce4ec
```

## Quick Reference

### EventSourced Entities (Event Sourcing)
- **EmailEntity** - Email lifecycle with status tracking
  - Events: EmailReceived, TagsGenerated, InquiryAddressed
  - Commands: receiveEmail(), addTags(), markAsAddressed()

### KeyValue Entities (Simple State)
- **SheetSyncBufferEntity** - Buffers rows before Google Sheets sync
- **EmailSyncCursorEntity** - Tracks last processed email timestamp

### Agents (AI)
- **ChatHandlerAgent** - Handles board member @assistant mentions
- **EmailTaggingAgent** - Generates tags/summary/location for emails

### Workflows (Orchestration)
- **EmailProcessingWorkflow** - fetch → store → tag → sync pipeline

### Views (Read Models)
- **InquiriesView** - Projects emails as inquiries for board members
- **TopicsView** - Projects tagged emails as topics

### Consumers (Event Listeners)
- **GoogleSheetConsumer** - Listens to EmailEntity events, buffers to sheets

### TimedActions (Scheduled)
- **SheetSyncFlushAction** - Flushes buffer every 5 minutes

## Event Flow

```mermaid
graph TD
    Email[📧 Email Arrives]
    Entity[EmailEntity]
    Views[Views<br/>InquiriesView<br/>TopicsView]
    Consumer[GoogleSheetConsumer]
    Buffer[SheetSyncBufferEntity]
    Timer[SheetSyncFlushAction<br/>every 5 min]
    Sheets[📊 Google Sheets]

    Email --> Entity
    Entity -->|Events| Views
    Entity -->|Events| Consumer
    Consumer --> Buffer
    Timer --> Buffer
    Buffer --> Sheets

    style Email fill:#e1f5ff
    style Sheets fill:#e1f5ff
```

## File Locations

```
src/main/java/community/
├── api/
│   ├── ChatEndpoint.java          # Board chat interface
│   └── EmailEndpoint.java         # Email processing trigger
├── application/
│   ├── agent/
│   │   ├── ChatHandlerAgent.java  # Board inquiry handler
│   │   └── EmailTaggingAgent.java # AI tagging
│   ├── entity/
│   │   ├── EmailEntity.java       # Email + status
│   │   ├── SheetSyncBufferEntity.java
│   │   └── EmailSyncCursorEntity.java
│   ├── view/
│   │   ├── InquiriesView.java     # Email inquiries
│   │   └── TopicsView.java        # Tagged topics
│   ├── workflow/
│   │   └── EmailProcessingWorkflow.java
│   └── action/
│       ├── GoogleSheetConsumer.java
│       └── SheetSyncFlushAction.java
├── domain/
│   └── model/
│       ├── Email.java             # Email + Status enum
│       ├── EmailTags.java
│       └── SheetRow.java
└── infrastructure/
    ├── gmail/
    │   └── GmailInboxService.java
    └── sheets/
        └── GoogleSheetSyncService.java
```
