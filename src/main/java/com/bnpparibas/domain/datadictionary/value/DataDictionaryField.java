package com.bnpparibas.domain.datadictionary.value;

import lombok.Builder;

@Builder
public record DataDictionaryField(
        String dataName,
        String dataDefinition,
        String fieldPath,
        String dataType,
        Boolean isForeignKey,
        String foreignKeyTable,
        String foreignKeyColumn,
        Boolean isMandatory,
        Integer length,
        String minValue,
        String maxValue,
        String defaultValue,
        String allowedValues,
        String fieldDescription
) {

    public void validate() {
        if (fieldPath == null || fieldPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Field path is required");
        }

        if (dataType == null || dataType.trim().isEmpty()) {
            throw new IllegalArgumentException("Data type is required for field: " + fieldPath);
        }

        if (Boolean.TRUE.equals(isForeignKey)) {
            if (foreignKeyTable == null || foreignKeyTable.trim().isEmpty()) {
                throw new IllegalArgumentException("Foreign key table is required when isForeignKey is true for field: " + fieldPath);
            }
            if (foreignKeyColumn == null || foreignKeyColumn.trim().isEmpty()) {
                throw new IllegalArgumentException("Foreign key column is required when isForeignKey is true for field: " + fieldPath);
            }
        }
    }

    public boolean isInJsonb() {
        return fieldPath.startsWith("model_specific_overrides.");
    }

    public String getJsonbPath() {
        if (!isInJsonb()) {
            return null;
        }
        return fieldPath.substring("model_specific_overrides.".length());
    }
}
