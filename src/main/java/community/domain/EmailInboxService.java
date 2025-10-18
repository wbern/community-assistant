package community.domain;

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
}
