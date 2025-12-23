package com.bnpparibas.infrastructure.parser.excel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ExcelColumnMapping {

    DATA_NAME("B", 1, "Data Name"),

    DATA_DEFINITION("D", 3, "Data Definition"),

    RATING_MODEL("AA", 26, "Rating/GRR Model"),

    RATING_MODEL_VERSION("AB", 27, "Rating/GRR Model Version"),

    RATING_MODEL_MECHANISM("AC", 28, "Rating/GRR Mechanism"),

    FIELD_PATH("AD", 29, "Field Path"),

    DATA_TABLE("AE", 30, "Data Table"),

    TABLE_DESCRIPTION("AF", 31, "Table Description"),

    TABLE_TYPE("AG", 32, "Table Type"),

    TABLE_COMMENT("AH", 33, "Table Comment"),

    DATA_TYPE("AI", 34, "Data Type"),

    IS_FOREIGN_KEY("AJ", 35, "Is Foreign Key"),

    FK_TABLE_NAME("AK", 36, "FK Table Name"),

    FK_COLUMN_NAME("AL", 37, "FK Column Name"),

    IS_MANDATORY("AM", 38, "Is Mandatory"),

    LENGTH("AN",  39, "Length"),

    MIN_VALUE("AO", 40, "Min Value"),

    MAX_VALUE("AP", 41, "Max Value"),

    DEFAULT_VALUES("AQ", 42, "Default Values"),

    ALLOWED_VALUES("AR", 43, "Allowed Values"),

    FIELD_DESCRIPTION("AS", 44, "Field Description")

    ;

    private final String columnLetter;
    private final int columnIndex; // 0-based index for POI
    private final String headerName;

    /**
     * Find column mapping by header name (case-insensitive)
     */
    public static ExcelColumnMapping findByHeaderName(String headerName) {
        if (headerName == null || headerName.trim().isEmpty()) {
            return null;
        }

        String normalized = headerName.trim();
        for (ExcelColumnMapping mapping : values()) {
            if (mapping.headerName.equalsIgnoreCase(normalized)) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Find column mapping by column letter
     */
    public static ExcelColumnMapping findByColumnLetter(String columnLetter) {
        if (columnLetter == null || columnLetter.trim().isEmpty()) {
            return null;
        }

        String normalized = columnLetter.trim().toUpperCase();
        for (ExcelColumnMapping mapping : values()) {
            if (mapping.columnLetter.equals(normalized)) {
                return mapping;
            }
        }
        return null;
    }
}
