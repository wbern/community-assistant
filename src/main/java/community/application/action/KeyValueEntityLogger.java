package community.application.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized utility for structured logging of KeyValueEntity state changes.
 * Provides consistent logging across all KeyValueEntity operations.
 */
public class KeyValueEntityLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(KeyValueEntityLogger.class);
    
    /**
     * Log a KeyValueEntity state change operation.
     * 
     * @param entityType The type of entity (e.g., "reminder-config", "active-inquiry")
     * @param entityId The entity ID
     * @param operation The operation performed (e.g., "setInterval", "setState")
     */
    public static void logStateChange(String entityType, String entityId, String operation) {
        logger.info("KeyValueEntity state changed: entityType={}, entityId={}, operation={}", 
                   entityType, entityId, operation);
    }
    
    /**
     * Log a KeyValueEntity state change operation with additional context.
     * 
     * @param entityType The type of entity
     * @param entityId The entity ID
     * @param operation The operation performed
     * @param additionalInfo Additional context about the operation
     */
    public static void logStateChange(String entityType, String entityId, String operation, String additionalInfo) {
        logger.info("KeyValueEntity state changed: entityType={}, entityId={}, operation={}, info={}", 
                   entityType, entityId, operation, additionalInfo);
    }
}