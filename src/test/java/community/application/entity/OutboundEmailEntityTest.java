package community.application.entity;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OutboundEmailEntity using Akka TestKit.
 * RED phase: Testing EventSourced Entity for outbound email lifecycle tracking.
 */
public class OutboundEmailEntityTest {

    @Test
    public void shouldCreateOutboundEmailWhenChatHandlerDecidestoSendReply() {
        // RED: Test that when chat handler agent decides to send email reply, 
        // an OutboundEmailEntity entry is created
        EventSourcedTestKit<OutboundEmailEntity.State, OutboundEmailEntity.Event, OutboundEmailEntity> testKit =
            EventSourcedTestKit.of(OutboundEmailEntity::new);

        String originalCaseId = "case-123";
        String recipientEmail = "resident@community.com";
        String subject = "Re: Broken elevator";
        String body = "Thank you for reporting the elevator issue. We have contacted maintenance.";

        OutboundEmailEntity.CreateDraftCommand command = 
            new OutboundEmailEntity.CreateDraftCommand(originalCaseId, recipientEmail, subject, body);

        EventSourcedResult<String> result = testKit.method(OutboundEmailEntity::createDraft)
            .invoke(command);

        assertTrue(result.isReply());
        assertEquals("Draft created", result.getReply());

        // Verify event was emitted
        assertEquals(1, result.getAllEvents().size());
        assertTrue(result.getAllEvents().get(0) instanceof OutboundEmailEntity.Event.DraftCreated);

        OutboundEmailEntity.Event.DraftCreated event =
            (OutboundEmailEntity.Event.DraftCreated) result.getAllEvents().get(0);
        assertEquals(originalCaseId, event.originalCaseId());
        assertEquals(recipientEmail, event.recipientEmail());
    }
}