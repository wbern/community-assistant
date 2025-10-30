package community.application.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized utility for structured logging of HTTP endpoint access.
 * Provides consistent logging across all HTTP endpoints.
 */
public class HttpEndpointLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpEndpointLogger.class);
    
    /**
     * Log an HTTP endpoint access.
     * 
     * @param endpoint The endpoint name (e.g., "chat", "email")
     * @param method The HTTP method (e.g., "POST", "GET")
     * @param path The request path (e.g., "/chat/message", "/process-inbox/")
     * @param status The HTTP status code (e.g., 200, 404)
     */
    public static void logAccess(String endpoint, String method, String path, int status) {
        logger.info("HTTP endpoint accessed: endpoint={}, method={}, path={}, status={}", 
                   endpoint, method, path, status);
    }
    
    /**
     * Log an HTTP endpoint access with additional context.
     * 
     * @param endpoint The endpoint name
     * @param method The HTTP method
     * @param path The request path
     * @param status The HTTP status code
     * @param additionalInfo Additional context about the request
     */
    public static void logAccess(String endpoint, String method, String path, int status, String additionalInfo) {
        logger.info("HTTP endpoint accessed: endpoint={}, method={}, path={}, status={}, info={}", 
                   endpoint, method, path, status, additionalInfo);
    }
}