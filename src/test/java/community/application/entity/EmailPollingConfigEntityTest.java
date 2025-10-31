package community.application.entity;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED phase: Testing EmailPollingConfigEntity for configurable email polling intervals.
 */
public class EmailPollingConfigEntityTest extends TestKitSupport {

    @Test
    public void red_shouldStoreAndRetrievePollingInterval() {
        // RED: EmailPollingConfigEntity should store configurable polling intervals
        // and return default interval when no config is set
        
        String entityId = "email-polling-config";
        int customInterval = 600; // 10 minutes
        
        // Act: Set custom polling interval
        var result = componentClient.forKeyValueEntity(entityId)
            .method(EmailPollingConfigEntity::setInterval)
            .invoke(customInterval);
        
        // Assert: Should return the set interval
        assertThat(result.intervalSeconds()).isEqualTo(customInterval);
        
        // Act: Get the interval
        var retrievedInterval = componentClient.forKeyValueEntity(entityId)
            .method(EmailPollingConfigEntity::getInterval)
            .invoke();
        
        // Assert: Should return the previously set interval
        assertThat(retrievedInterval).isEqualTo(customInterval);
    }
    
    @Test
    public void red_shouldReturnDefaultIntervalWhenNotConfigured() {
        // RED: EmailPollingConfigEntity should return default interval (300 seconds = 5 minutes)
        // when no configuration has been set
        
        String entityId = "email-polling-config-default";
        
        // Act: Get interval without setting any config
        var defaultInterval = componentClient.forKeyValueEntity(entityId)
            .method(EmailPollingConfigEntity::getInterval)
            .invoke();
        
        // Assert: Should return default 5-minute interval
        assertThat(defaultInterval).isEqualTo(300); // 5 minutes
    }
}