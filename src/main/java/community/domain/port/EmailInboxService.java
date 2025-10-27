package community.domain.port;

import community.domain.model.Email;

import java.time.Instant;
import java.util.List;

/**
 * Service interface for fetching emails from an inbox.
 * Abstracts the email retrieval mechanism to allow different implementations.
 */
public interface EmailInboxService {

    /**
     * Fetches unprocessed emails from the inbox.
     *
     * @return list of unprocessed emails
     */
    List<Email> fetchUnprocessedEmails();

    /**
     * Fetches emails received after the specified cursor timestamp.
     * This enables cursor-based synchronization for external email systems.
     *
     * @param since cursor timestamp - only emails received after this time are returned
     * @return list of emails received after the cursor
     */
    List<Email> fetchEmailsSince(Instant since);
}
