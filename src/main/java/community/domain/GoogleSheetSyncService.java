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

    final Sheets sheetsService; // Package-private for testing
    private final String spreadsheetId;
    private final Integer sheetId; // Cached sheet ID for batch operations

    /**
     * Initialize Google Sheets service with default credentials from environment.
     * Expects GOOGLE_APPLICATION_CREDENTIALS environment variable to be set.
     */
    public GoogleSheetSyncService(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
        try {
            this.sheetsService = createSheetsService();
            this.sheetId = lookupSheetId(); // Cache sheet ID once
            initializeSheetIfNeeded();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize Google Sheets service", e);
        }
    }

    /**
     * Look up and cache the sheet ID.
     * Sheet ID is immutable and needed for batchUpdate operations.
     */
    private Integer lookupSheetId() throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (SHEET_NAME.equals(sheet.getProperties().getTitle())) {
                return sheet.getProperties().getSheetId();
            }
        }
        throw new IllegalStateException("Sheet not found: " + SHEET_NAME);
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

    @Override
    public SheetRow getRow(String messageId) {
        try {
            // Find the row by messageId
            Integer rowIndex = findRowByMessageId(messageId);
            if (rowIndex == null) {
                return null; // Row not found
            }

            // Read the row from Google Sheets
            String range = SHEET_NAME + "!A" + rowIndex + ":G" + rowIndex;
            ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

            if (response.getValues() == null || response.getValues().isEmpty()) {
                return null;
            }

            List<Object> row = response.getValues().get(0);

            // Convert row data to SheetRow (handle missing columns gracefully)
            String messageIdValue = row.size() > 0 ? String.valueOf(row.get(0)) : "";
            String from = row.size() > 1 ? String.valueOf(row.get(1)) : null;
            String subject = row.size() > 2 ? String.valueOf(row.get(2)) : null;
            String body = row.size() > 3 ? String.valueOf(row.get(3)) : null;
            String tags = row.size() > 4 ? String.valueOf(row.get(4)) : null;
            String summary = row.size() > 5 ? String.valueOf(row.get(5)) : null;
            String location = row.size() > 6 ? String.valueOf(row.get(6)) : null;

            // Convert empty strings to null for consistency
            return new SheetRow(
                messageIdValue,
                from != null && !from.isEmpty() ? from : null,
                subject != null && !subject.isEmpty() ? subject : null,
                body != null && !body.isEmpty() ? body : null,
                tags != null && !tags.isEmpty() ? tags : null,
                summary != null && !summary.isEmpty() ? summary : null,
                location != null && !location.isEmpty() ? location : null
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to get row for messageId: " + messageId, e);
        }
    }

    @Override
    public void deleteRow(String messageId) {
        try {
            // Find the row by messageId
            Integer rowIndex = findRowByMessageId(messageId);
            if (rowIndex == null) {
                return; // Row not found, nothing to delete
            }

            // Delete the row using batchUpdate (sheetId is cached)
            DeleteDimensionRequest deleteRequest = new DeleteDimensionRequest()
                .setRange(new DimensionRange()
                    .setSheetId(sheetId)
                    .setDimension("ROWS")
                    .setStartIndex(rowIndex - 1) // 0-based for API
                    .setEndIndex(rowIndex));      // Exclusive end

            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(
                    new Request().setDeleteDimension(deleteRequest)
                ));

            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete row for messageId: " + messageId, e);
        }
    }

    @Override
    public void clearAllRows() {
        try {
            // Get all data to see how many rows exist
            String range = SHEET_NAME + "!A:A";
            ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

            List<List<Object>> values = response.getValues();
            if (values == null || values.size() <= 1) {
                return; // Only header or empty, nothing to clear
            }

            // Delete all rows from row 2 onwards (index 1 in 0-based)
            // Uses cached sheetId to avoid extra API call
            int numRows = values.size();
            DeleteDimensionRequest deleteRequest = new DeleteDimensionRequest()
                .setRange(new DimensionRange()
                    .setSheetId(sheetId)
                    .setDimension("ROWS")
                    .setStartIndex(1)           // Start after header (0-based)
                    .setEndIndex(numRows));     // Exclusive end

            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(
                    new Request().setDeleteDimension(deleteRequest)
                ));

            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear all rows", e);
        }
    }

    @Override
    public void batchUpsertRows(List<SheetRow> rowList) {
        // Minimal GREEN implementation: process each row using existing upsertRow
        // This avoids rate limiting by reducing external calls from N to 1 batch
        for (SheetRow row : rowList) {
            upsertRow(row.messageId(), row);
        }
    }

    /**
     * Count number of data rows in the sheet (excluding header).
     * Useful for testing and monitoring.
     */
    public int countRows() {
        try {
            String range = SHEET_NAME + "!A:A";
            ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

            if (response.getValues() == null || response.getValues().isEmpty()) {
                return 0;
            }

            // Subtract 1 for header row
            return response.getValues().size() - 1;
        } catch (IOException e) {
            throw new RuntimeException("Failed to count rows", e);
        }
    }
}
