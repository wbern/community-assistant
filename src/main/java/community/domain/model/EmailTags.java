package community.domain.model;

import java.util.Set;

/**
 * Domain model for AI-generated email tags.
 * Contains free-form tags, summary, and optional location.
 */
public record EmailTags(
    Set<String> tags,
    String summary,
    String location
) {
    /**
     * Factory method to create EmailTags.
     */
    public static EmailTags create(
        Set<String> tags,
        String summary,
        String location
    ) {
        return new EmailTags(tags, summary, location);
    }
}
