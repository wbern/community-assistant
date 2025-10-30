package community.application.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized utility for structured logging of TimedAction executions.
 * Provides consistent logging across all TimedAction components.
 */
public class TimedActionLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(TimedActionLogger.class);
    
    /**
     * Log a TimedAction execution.
     * 
     * @param actionType The action type (e.g., "reminder-action", "sheet-sync-flush-action")
     * @param actionMethod The method being executed (e.g., "scheduleNextFlush", "scheduleReminder")
     */
    public static void logExecution(String actionType, String actionMethod) {
        logger.info("TimedAction executed: actionType={}, actionMethod={}", 
                   actionType, actionMethod);
    }
    
    /**
     * Log a TimedAction execution with additional context.
     * 
     * @param actionType The action type
     * @param actionMethod The method being executed
     * @param additionalInfo Additional context about the execution
     */
    public static void logExecution(String actionType, String actionMethod, String additionalInfo) {
        logger.info("TimedAction executed: actionType={}, actionMethod={}, info={}", 
                   actionType, actionMethod, additionalInfo);
    }
}