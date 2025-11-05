package community.application.agent;

/**
 * Shared constants for chat functionality.
 * 
 * Contains magic values used across both lofi and AI agent implementations
 * to ensure consistency and avoid duplication.
 */
public final class ChatConstants {
    
    public static final String ASSISTANT_MENTION = "@assistant";
    
    /**
     * Fixed entity ID used for outbound email entities in agent operations.
     * Both agents use this same ID for test compatibility and consistency.
     */
    public static final String OUTBOUND_EMAIL_ENTITY_ID = "email-reply-1";
    
    /**
     * Entity ID for the active inquiry key-value entity.
     */
    public static final String ACTIVE_INQUIRY_ENTITY_ID = "active-inquiry";
    
    /**
     * Session ID prefix pattern for board inquiry sessions.
     */
    public static final String BOARD_INQUIRY_SESSION_PREFIX = "board-inquiry-session-";
    
    /**
     * Confirmation keywords that indicate user agreement.
     */
    public static final String[] CONFIRMATION_KEYWORDS = {"yes", "correct", "confirm", "that's right"};
    
    private ChatConstants() {
        // Utility class - prevent instantiation
    }
}