package com.bnpparibas.application.schema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to import data dictionary from Excel file
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataDictionaryImportRequest {

    /**
     * Description of this import (optional)
     * Example: "Initial schema upload for PLACM v010"
     */
    private String description;

    /**
     * Who is performing this import
     * Example: "john.doe@company.com"
     */
    private String importedBy;

    /**
     * Whether to validate only without saving
     * If true, performs dry-run validation
     */
    private boolean validateOnly = false;

    /**
     * Whether to overwrite existing schemas
     * If false, will fail if schema already exists for a model/version/mechanism
     */
    private boolean overwriteExisting = false;
}
