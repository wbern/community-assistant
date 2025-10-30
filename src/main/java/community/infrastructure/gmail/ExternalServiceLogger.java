package community.infrastructure.gmail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GREEN phase: Minimal implementation to make test pass.
 */
public class ExternalServiceLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceLogger.class);
    
    public static void logServiceCall(String service, String operation, String status) {
        logger.info("External service called: service={}, operation={}, status={}", 
                   service, operation, status);
    }
}