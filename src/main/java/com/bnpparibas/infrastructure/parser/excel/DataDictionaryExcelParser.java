package com.bnpparibas.infrastructure.parser.excel;

import com.bnpparibas.domain.datadictionary.value.DataDictionaryEntry;
import com.bnpparibas.domain.datadictionary.value.DataDictionaryField;
import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Data Dictionary Excel files using streaming API
 * Memory-efficient parser that can handle large Excel files (1000+ rows)
 *
 * Architecture:
 * 1. StreamingExcelReader handles low-level SAX parsing
 * 2. This class handles business logic (field extraction, validation, grouping)
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class DataDictionaryExcelParser {

    private final StreamingExcelReader streamingExcelReader;

    private static final int HEADER_ROW_INDEX = 2; // Row 3 in Excel (0-based index = 2)
    private static final int DATA_START_ROW_INDEX = 3; // Row 4 in Excel (0-based index = 3)
    private static final String RAW_DATA_SHEET_NAME = "Raw Data";

    /**
     * Parse the data dictionary Excel file
     *
     * @param inputStream Excel file input stream
     * @return List of DataDictionaryEntry grouped by model/version/mechanism
     * @throws Exception if parsing fails
     */
    public List<DataDictionaryEntry> parseDataDictionary(InputStream inputStream) throws Exception {
        log.info("Starting data dictionary Excel parsing (streaming mode)");

        // Collector for all parsed fields with their metadata
        List<FieldWithMetadata> fieldsWithMetadata = new ArrayList<>();

        // State: Track if we've validated headers
        boolean[] headersValidated = {false};

        streamingExcelReader.processSheet(inputStream, RAW_DATA_SHEET_NAME, (rowIndex, cellValues) -> {
            // Step 1: Validate headers (only once)
            if (rowIndex == HEADER_ROW_INDEX) {
                validateHeaders(cellValues);
                headersValidated[0] = true;
                log.debug("Headers validated successfully");
                return; // Skip header row
            }

            // Step 2: Skip rows before data starts
            if (rowIndex < DATA_START_ROW_INDEX) {
                return;
            }

            // Step 3: Process data rows
            try {
                FieldWithMetadata fieldWithMetadata = parseRow(rowIndex, cellValues);

                if (fieldWithMetadata != null) {
                    fieldsWithMetadata.add(fieldWithMetadata);

                    if (fieldsWithMetadata.size() % 100 == 0) {
                        log.info("Parsed {} fields so far...", fieldsWithMetadata.size());
                    }
                }
            } catch (Exception e) {
                String errorMsg = String.format(
                        "Error parsing row %d: %s",
                        rowIndex + 1,
                        e.getMessage()
                );
                log.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
        });

        if (!headersValidated[0]) {
            throw new DataDictionaryParseException(
                    "Headers not found or invalid. Expected header row at row 3"
            );
        }

        log.info("Successfully parsed {} fields from Excel", fieldsWithMetadata.size());

        // Step 4: Group fields by model/version/mechanism
        List<DataDictionaryEntry> entries = groupIntoEntries(fieldsWithMetadata);

        log.info("Created {} data dictionary entries", entries.size());

        return entries;
    }

    /**
     * Validate that headers match expected columns
     */
    private void validateHeaders(List<String> cellValues) throws DataDictionaryParseException {

        log.debug("Validating headers from row 3");

        // Validate critical columns
        validateHeaderCell(cellValues, ExcelColumnMapping.FIELD_PATH, "Field Path is required");
        validateHeaderCell(cellValues, ExcelColumnMapping.DATA_TYPE, "Data Type is required");
        validateHeaderCell(cellValues, ExcelColumnMapping.RATING_MODEL, "Rating Model is required");
        validateHeaderCell(cellValues, ExcelColumnMapping.RATING_MODEL_VERSION, "Rating Model Version is required");
        validateHeaderCell(cellValues, ExcelColumnMapping.RATING_MODEL_MECHANISM, "Rating Mechanism is required");

        log.debug("All required headers found");
    }

    /**
     * Validate a single header cell
     */
    private void validateHeaderCell(
            List<String> cellValues,
            ExcelColumnMapping mapping,
            String errorMessage) throws DataDictionaryParseException {

        String actualValue = getCellValue(cellValues, mapping.getColumnIndex());

        if (actualValue == null || !mapping.getHeaderName().equalsIgnoreCase(actualValue.trim())) {
            throw new DataDictionaryParseException(
                    String.format(
                            "%s. Expected '%s' at column %s but found '%s'",
                            errorMessage,
                            mapping.getHeaderName(),
                            mapping.getColumnLetter(),
                            actualValue
                    )
            );
        }
    }


    /**
     * Parse a single row into FieldWithMetadata
     *
     * @param rowIndex 0-based row index
     * @param cellValues List of cell values for this row
     * @return FieldWithMetadata or null if row should be skipped
     */
    private FieldWithMetadata parseRow(int rowIndex, List<String> cellValues) {

        // Check if row is empty
        if (isEmptyRow(cellValues)) {
            log.debug("Skipping empty row at index {}", rowIndex);
            return null;
        }

        // Field Path is the key field - if missing, skip this row
        String fieldPath = getCellValue(cellValues, ExcelColumnMapping.FIELD_PATH);

        if (fieldPath == null || fieldPath.trim().isEmpty()) {
            log.debug("Skipping row {} - no field path", rowIndex + 1);
            return null;
        }

        // Extract model/version/mechanism metadata
        String model = getCellValue(cellValues, ExcelColumnMapping.RATING_MODEL);
        String version = getCellValue(cellValues, ExcelColumnMapping.RATING_MODEL_VERSION);
        String mechanism = getCellValue(cellValues, ExcelColumnMapping.RATING_MODEL_MECHANISM);

        // Validate metadata
        if (model == null || model.trim().isEmpty()) {
            log.warn("Row {} has field path '{}' but missing rating model - skipping",
                    rowIndex + 1, fieldPath);
            return null;
        }
        if (version == null || version.trim().isEmpty()) {
            log.warn("Row {} has field path '{}' but missing rating model version - skipping",
                    rowIndex + 1, fieldPath);
            return null;
        }
        if (mechanism == null || mechanism.trim().isEmpty()) {
            log.warn("Row {} has field path '{}' but missing rating mechanism - skipping",
                    rowIndex + 1, fieldPath);
            return null;
        }

        // Parse the field
        DataDictionaryField field = DataDictionaryField.builder()
                .dataName(getCellValue(cellValues, ExcelColumnMapping.DATA_NAME))
                .dataDefinition(getCellValue(cellValues, ExcelColumnMapping.DATA_DEFINITION))
                .fieldPath(fieldPath.trim())
                .dataType(getCellValue(cellValues, ExcelColumnMapping.DATA_TYPE))
                .isForeignKey(parseBooleanValue(getCellValue(cellValues, ExcelColumnMapping.IS_FOREIGN_KEY)))
                .foreignKeyTable(getCellValue(cellValues, ExcelColumnMapping.FK_TABLE_NAME))
                .foreignKeyColumn(getCellValue(cellValues, ExcelColumnMapping.FK_COLUMN_NAME))
                .isMandatory(parseBooleanValue(getCellValue(cellValues, ExcelColumnMapping.IS_MANDATORY)))
                .length(parseIntegerValue(getCellValue(cellValues, ExcelColumnMapping.LENGTH)))
                .minValue(getCellValue(cellValues, ExcelColumnMapping.MIN_VALUE))
                .maxValue(getCellValue(cellValues, ExcelColumnMapping.MAX_VALUE))
                .defaultValue(getCellValue(cellValues, ExcelColumnMapping.DEFAULT_VALUES))
                .allowedValues(getCellValue(cellValues, ExcelColumnMapping.ALLOWED_VALUES))
                .fieldDescription(getCellValue(cellValues, ExcelColumnMapping.FIELD_DESCRIPTION))
                .build();

        // Validate the field
        try {
            field.validate();
        } catch (Exception e) {
            log.error("Field validation failed for row {}: {}", rowIndex + 1, e.getMessage());
            throw new RuntimeException(
                    String.format("Invalid field at row %d: %s", rowIndex + 1, e.getMessage()),
                    e
            );
        }

        log.debug("Parsed field: {} from row {}", field.fieldPath(), rowIndex + 1);

        return new FieldWithMetadata(
                field,
                model.trim(),
                version.trim(),
                mechanism.trim(),
                rowIndex + 1 // Excel row number (1-based for user-friendly error messages)
        );
    }


    /**
     * Group fields into DataDictionaryEntry objects by model/version/mechanism
     */
    private List<DataDictionaryEntry> groupIntoEntries(List<FieldWithMetadata> fieldsWithMetadata)
            throws DataDictionaryParseException {

        log.info("Grouping {} fields by model/version/mechanism", fieldsWithMetadata.size());

        // Group fields by their key
        Map<GroupingKey, List<DataDictionaryField>> groupedFields = new LinkedHashMap<>();

        for (FieldWithMetadata item : fieldsWithMetadata) {
            GroupingKey key = new GroupingKey(
                    item.model(),
                    item.version(),
                    item.mechanism()
            );

            groupedFields.computeIfAbsent(key, k -> new ArrayList<>()).add(item.field());
        }

        log.info("Found {} unique model/version/mechanism combinations", groupedFields.size());

        // Convert to DataDictionaryEntry objects
        List<DataDictionaryEntry> entries = new ArrayList<>();

        for (Map.Entry<GroupingKey, List<DataDictionaryField>> entry : groupedFields.entrySet()) {
            GroupingKey key = entry.getKey();
            List<DataDictionaryField> fields = entry.getValue();

            try {
                DataDictionaryEntry dataDictionaryEntry = DataDictionaryEntry.builder()
                        .ratingModel(RatingModel.of(key.model()))
                        .ratingModelVersion(RatingModelVersion.of(key.version()))
                        .ratingMechanism(RatingMechanism.fromString(key.mechanism()))
                        .fields(fields)
                        .build();

                // Validate the complete entry
                dataDictionaryEntry.validate();

                entries.add(dataDictionaryEntry);

                log.info("Created entry for {}-{}-{} with {} fields",
                        key.model(), key.version(), key.mechanism(), fields.size());

            } catch (Exception e) {
                String errorMsg = String.format(
                        "Error creating entry for %s-%s-%s: %s",
                        key.model(), key.version(), key.mechanism(), e.getMessage()
                );
                log.error(errorMsg, e);
                throw new DataDictionaryParseException(errorMsg, e);
            }
        }

        return entries;
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Get cell value by column index, handling out-of-bounds gracefully
     */
    private String getCellValue(List<String> cellValues, int columnIndex) {
        if (cellValues == null || columnIndex < 0 || columnIndex >= cellValues.size()) {
            return null;
        }
        return cellValues.get(columnIndex);
    }

    /**
     * Get cell value by ExcelColumnMapping
     */
    private String getCellValue(List<String> cellValues, ExcelColumnMapping mapping) {
        return getCellValue(cellValues, mapping.getColumnIndex());
    }

    /**
     * Parse boolean value from string
     * Accepts: "Y", "Yes", "true", "1" (case-insensitive) → true
     * Everything else → false or null
     */
    private Boolean parseBooleanValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String normalized = value.trim().toLowerCase();
        return normalized.equals("y") ||
                normalized.equals("yes") ||
                normalized.equals("true") ||
                normalized.equals("1");
    }

    /**
     * Parse integer value from string
     */
    private Integer parseIntegerValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value: '{}', returning null", value);
            return null;
        }
    }

    /**
     * Check if a row is empty (all cells are null or blank)
     */
    private boolean isEmptyRow(List<String> cellValues) {
        if (cellValues == null || cellValues.isEmpty()) {
            return true;
        }

        for (String value : cellValues) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Internal record to hold a field with its model/version/mechanism metadata
     */
    private record FieldWithMetadata(
            DataDictionaryField field,
            String model,
            String version,
            String mechanism,
            int excelRowNumber
    ) {}

    /**
     * Grouping key for fields by model/version/mechanism
     */
    private record GroupingKey(
            String model,
            String version,
            String mechanism
    ) {

        @Override
        public String toString() {
            return model + "-" + version + "-" + mechanism;
        }
    }

}
