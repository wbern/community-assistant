package community.domain;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Real Google Sheets implementation of SheetSyncService.
 * Syncs SheetRow data to Google Sheets using the Google Sheets API v4.
 *
 * Sheet Structure:
 * Column A: messageId (unique identifier)
 * Column B: from
 * Column C: subject
 * Column D: body
 * Column E: tags
 * Column F: summary
 * Column G: location
 */
public class GoogleSheetSyncService implements SheetSyncService {

    private static final String APPLICATION_NAME = "Community Assistant";
    private static final String SHEET_NAME = "Emails"; // Default sheet name
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    private final Sheets sheetsService;
    private final String spreadsheetId;

    /**
     * Initialize Google Sheets service with default credentials from environment.
     * Expects GOOGLE_APPLICATION_CREDENTIALS environment variable to be set.
     */
    public GoogleSheetSyncService(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
        try {
            this.sheetsService = createSheetsService();
            initializeSheetIfNeeded();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize Google Sheets service", e);
        }
    }

    /**
     * Create Sheets service with credentials from environment.
     */
    private Sheets createSheetsService() throws GeneralSecurityException, IOException {
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null || credentialsPath.isEmpty()) {
            throw new IllegalStateException("GOOGLE_APPLICATION_CREDENTIALS environment variable not set");
        }

        GoogleCredentials credentials = GoogleCredentials
            .fromStream(new FileInputStream(credentialsPath))
            .createScoped(SCOPES);

        return new Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials)
        )
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /**
     * Initialize sheet with header row if it doesn't exist.
     */
    private void initializeSheetIfNeeded() throws IOException {
        // Check if sheet exists and has headers
        String range = SHEET_NAME + "!A1:G1";
        ValueRange response = sheetsService.spreadsheets().values()
            .get(spreadsheetId, range)
            .execute();

        if (response.getValues() == null || response.getValues().isEmpty()) {
            // Create header row
            List<Object> headers = Arrays.asList(
                "Message ID", "From", "Subject", "Body", "Tags", "Summary", "Location"
            );
            ValueRange headerRange = new ValueRange()
                .setValues(Collections.singletonList(headers));

            sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, headerRange)
                .setValueInputOption("RAW")
                .execute();
        }
    }

    @Override
    public void upsertRow(String messageId, SheetRow row) {
        try {
            // Find existing row by messageId
            Integer existingRowIndex = findRowByMessageId(messageId);

            if (existingRowIndex != null) {
                // Update existing row with merge logic
                updateExistingRow(existingRowIndex, row);
            } else {
                // Append new row
                appendNewRow(row);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to upsert row for messageId: " + messageId, e);
        }
    }

    /**
     * Find row index by messageId (searches column A).
     * Returns null if not found, or the row number (1-based, including header).
     */
    private Integer findRowByMessageId(String messageId) throws IOException {
        String range = SHEET_NAME + "!A:A";
        ValueRange response = sheetsService.spreadsheets().values()
            .get(spreadsheetId, range)
            .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return null;
        }

        // Skip header row (index 0), start from row 1
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (!row.isEmpty() && messageId.equals(row.get(0).toString())) {
                return i + 1; // Convert to 1-based row number
            }
        }

        return null;
    }

    /**
     * Update existing row with merge logic (null/empty = preserve existing).
     */
    private void updateExistingRow(int rowIndex, SheetRow newRow) throws IOException {
        // First, read existing row
        String range = SHEET_NAME + "!A" + rowIndex + ":G" + rowIndex;
        ValueRange response = sheetsService.spreadsheets().values()
            .get(spreadsheetId, range)
            .execute();

        List<Object> existingValues = new ArrayList<>(Arrays.asList("", "", "", "", "", "", ""));
        if (response.getValues() != null && !response.getValues().isEmpty()) {
            List<Object> existing = response.getValues().get(0);
            for (int i = 0; i < Math.min(existing.size(), existingValues.size()); i++) {
                existingValues.set(i, existing.get(i));
            }
        }

        // Merge with new values (null/empty = preserve existing)
        List<Object> mergedValues = Arrays.asList(
            newRow.messageId(), // messageId always from new row
            mergeValue(newRow.from(), existingValues.get(1)),
            mergeValue(newRow.subject(), existingValues.get(2)),
            mergeValue(newRow.body(), existingValues.get(3)),
            mergeValue(newRow.tags(), existingValues.get(4)),
            mergeValue(newRow.summary(), existingValues.get(5)),
            mergeValue(newRow.location(), existingValues.get(6))
        );

        // Update row
        ValueRange valueRange = new ValueRange()
            .setValues(Collections.singletonList(mergedValues));

        sheetsService.spreadsheets().values()
            .update(spreadsheetId, range, valueRange)
            .setValueInputOption("RAW")
            .execute();
    }

    /**
     * Merge value: if new value is null/empty, use existing; otherwise use new.
     */
    private Object mergeValue(String newValue, Object existingValue) {
        if (newValue == null || newValue.isEmpty()) {
            return existingValue;
        }
        return newValue;
    }

    /**
     * Append new row to the end of the sheet.
     */
    private void appendNewRow(SheetRow row) throws IOException {
        List<Object> values = Arrays.asList(
            row.messageId(),
            row.from() != null ? row.from() : "",
            row.subject() != null ? row.subject() : "",
            row.body() != null ? row.body() : "",
            row.tags() != null ? row.tags() : "",
            row.summary() != null ? row.summary() : "",
            row.location() != null ? row.location() : ""
        );

        ValueRange appendRange = new ValueRange()
            .setValues(Collections.singletonList(values));

        sheetsService.spreadsheets().values()
            .append(spreadsheetId, SHEET_NAME + "!A:G", appendRange)
            .setValueInputOption("RAW")
            .setInsertDataOption("INSERT_ROWS")
            .execute();
    }
}
