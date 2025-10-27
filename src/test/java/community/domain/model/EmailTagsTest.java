package community.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmailTags domain model.
 * RED phase: Testing free-form tag structure from AI.
 */
public class EmailTagsTest {

    @Test
    public void shouldCreateEmailTags() {
        // Arrange
        Set<String> tags = Set.of("urgent", "maintenance", "elevator");
        String summary = "Elevator broken in Building A";
        String location = "Building A, Elevator";

        // Act
        EmailTags emailTags = EmailTags.create(tags, summary, location);

        // Assert
        assertEquals(tags, emailTags.tags());
        assertEquals(summary, emailTags.summary());
        assertEquals(location, emailTags.location());
    }

    @Test
    public void shouldAllowNullLocation() {
        // Arrange
        Set<String> tags = Set.of("question", "general");
        String summary = "General inquiry about parking rules";

        // Act
        EmailTags emailTags = EmailTags.create(tags, summary, null);

        // Assert
        assertNotNull(emailTags);
        assertNull(emailTags.location());
    }

    @Test
    public void shouldAllowEmptyTags() {
        // Arrange: Edge case where AI couldn't determine tags
        Set<String> tags = Set.of();
        String summary = "Unclear email content";

        // Act
        EmailTags emailTags = EmailTags.create(tags, summary, null);

        // Assert
        assertNotNull(emailTags);
        assertTrue(emailTags.tags().isEmpty());
    }
}
