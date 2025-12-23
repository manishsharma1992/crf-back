package com.bnpparibas.application.schema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of data dictionary import operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataDictionaryImportResponse {

    /**
     * Overall success status
     */
    private boolean success;

    /**
     * When this import was processed
     */
    private LocalDateTime processedAt;

    /**
     * Total number of entries processed
     */
    private int totalEntries;

    /**
     * Number of entries successfully imported
     */
    private int successfulEntries;

    /**
     * Number of entries that failed
     */
    private int failedEntries;

    /**
     * Number of entries skipped (if overwriteExisting=false)
     */
    private int skippedEntries;

    /**
     * Details for each entry processed
     */
    private List<EntryImportResult> entryResults = new ArrayList<>();

    /**
     * Overall error message if import failed
     */
    private String errorMessage;

    /**
     * Result for a single entry (model/version/mechanism)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryImportResult {

        /**
         * Rating model name (e.g., "PLACM")
         */
        private String ratingModel;

        /**
         * Rating model version (e.g., "010")
         */
        private String ratingModelVersion;

        /**
         * Rating mechanism (e.g., "STANDALONE")
         */
        private String ratingMechanism;

        /**
         * Number of fields in this entry
         */
        private int fieldCount;

        /**
         * Status of this entry import
         */
        private ImportStatus status;

        /**
         * Schema version that was created/updated
         */
        private Integer schemaVersion;

        /**
         * Database ID of saved schema registry entry
         */
        private Long schemaRegistryId;

        /**
         * Error message if this entry failed
         */
        private String errorMessage;
    }

    /**
     * Status of an individual entry import
     */
    public enum ImportStatus {
        SUCCESS,       // Successfully imported/updated
        SKIPPED,       // Skipped because schema exists and overwrite=false
        FAILED,        // Failed due to validation or save error
        VALIDATED      // Dry-run validation only
    }
}
