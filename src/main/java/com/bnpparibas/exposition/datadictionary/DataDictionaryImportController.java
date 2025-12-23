package com.bnpparibas.exposition.datadictionary;

import com.bnpparibas.application.schema.dto.DataDictionaryImportRequest;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse;
import com.bnpparibas.application.schema.service.DataDictionaryImportService;
import com.bnpparibas.exposition.exceptions.FileValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/api/data-dictionary")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Dictionary", description = "Data Dictionary Import API")
public class DataDictionaryImportController {

    private final DataDictionaryImportService importService;

    // File validation constants
    private static final Long MAX_FILE_SIZE = 500L * 1024L * 1024L; // 500MB
    private static final String ALLOWED_CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ALLOWED_CONTENT_TYPE_XLS = "application/vnd.ms-excel";

    /**
     * Import data dictionary from Excel file
     *
     * @param file Excel file (.xlsx or .xls)
     * @param description Optional description for this import
     * @param overwriteExisting Whether to overwrite existing schemas (default: false)
     * @param authentication Current authenticated user
     * @return Import result summary
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import data dictionary from Excel",
            description = "Parse Excel file, generate JSON schemas, and save to database. " +
                    "Supports .xlsx and .xls formats. Maximum file size: 10MB."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Import completed (check response.success for actual status)",
                    content = @Content(schema = @Schema(implementation = DataDictionaryImportResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file (wrong format, too large, empty, etc.)"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during import"
            )
    })
    public ResponseEntity<DataDictionaryImportResponse> importDataDictionary(
            @Parameter(description = "Excel file containing data dictionary", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Description of this import (optional)")
            @RequestParam(value = "description", required = false) String description,

            @Parameter(description = "Whether to overwrite existing schemas (default: false)")
            @RequestParam(value = "overwriteExisting", required = false, defaultValue = "false")
            boolean overwriteExisting,

            Authentication authentication) {

        log.info("Received data dictionary import request. File: {}, Size: {} bytes, OverwriteExisting: {}",
                file.getOriginalFilename(), file.getSize(), overwriteExisting);

        try {
            // Step 1: Validate file
            validateFile(file);

            // Step 2: Get current user
            String importedBy = authentication != null ? authentication.getName() : "system";

            // Step 3: Build request
            DataDictionaryImportRequest request = new DataDictionaryImportRequest();
            request.setDescription(description);
            request.setImportedBy(importedBy);
            request.setValidateOnly(false);
            request.setOverwriteExisting(overwriteExisting);

            // Step 4: Process import
            try (InputStream inputStream = file.getInputStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        inputStream,
                        request
                );

                // Step 5: Return response with appropriate HTTP status
                HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;

                log.info("Import completed. Success: {}, Successful: {}, Failed: {}, Skipped: {}",
                        response.isSuccess(),
                        response.getSuccessfulEntries(),
                        response.getFailedEntries(),
                        response.getSkippedEntries());

                return ResponseEntity.status(status).body(response);
            }

        } catch (FileValidationException e) {
            log.warn("File validation failed: {}", e.getMessage());

            DataDictionaryImportResponse errorResponse = new DataDictionaryImportResponse();
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage(e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during import", e);

            DataDictionaryImportResponse errorResponse = new DataDictionaryImportResponse();
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Import failed: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Validate data dictionary Excel file without importing
     *
     * Performs dry-run validation:
     * - Parses Excel file
     * - Generates JSON schemas
     * - Does NOT save to database
     *
     * @param file Excel file to validate
     * @param authentication Current authenticated user
     * @return Validation results
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Validate data dictionary Excel (dry-run)",
            description = "Parse Excel and generate schemas without saving to database. " +
                    "Useful for testing before actual import."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Validation completed",
                    content = @Content(schema = @Schema(implementation = DataDictionaryImportResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid file"),
            @ApiResponse(responseCode = "500", description = "Validation error")
    })
    public ResponseEntity<DataDictionaryImportResponse> validateDataDictionary(
            @Parameter(description = "Excel file to validate", required = true)
            @RequestParam("file") MultipartFile file,

            Authentication authentication) {

        log.info("Received data dictionary validation request. File: {}, Size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            // Step 1: Validate file
            validateFile(file);

            // Step 2: Get current user
            String validatedBy = authentication != null ? authentication.getName() : "system";

            // Step 3: Build request with validateOnly=true
            DataDictionaryImportRequest request = new DataDictionaryImportRequest();
            request.setDescription("Validation only - no import");
            request.setImportedBy(validatedBy);
            request.setValidateOnly(true);  // ✅ Dry-run mode
            request.setOverwriteExisting(false);

            // Step 4: Process validation
            try (InputStream inputStream = file.getInputStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        inputStream,
                        request
                );

                log.info("Validation completed. Success: {}, Entries validated: {}",
                        response.isSuccess(), response.getTotalEntries());

                return ResponseEntity.ok(response);
            }

        } catch (FileValidationException e) {
            log.warn("File validation failed: {}", e.getMessage());

            DataDictionaryImportResponse errorResponse = new DataDictionaryImportResponse();
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage(e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during validation", e);

            DataDictionaryImportResponse errorResponse = new DataDictionaryImportResponse();
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Validation failed: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Validate uploaded file
     *
     * Checks:
     * 1. File is not null or empty
     * 2. File size is within limit
     * 3. File has correct extension
     * 4. File has correct content type
     *
     * @param file File to validate
     * @throws FileValidationException if validation fails
     */
    private void validateFile(MultipartFile file) throws FileValidationException {

        // Check 1: File exists
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("File is required and cannot be empty");
        }

        // Check 2: File size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileValidationException(
                    String.format("File size exceeds maximum limit of %d MB. Actual size: %.2f MB",
                            MAX_FILE_SIZE / (1024 * 1024),
                            file.getSize() / (1024.0 * 1024.0))
            );
        }

        // Check 3: File extension
        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new FileValidationException(
                    "Invalid file format. Only .xlsx and .xls files are supported. Received: " + filename
            );
        }

        // Check 4: Content type
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals(ALLOWED_CONTENT_TYPE_XLSX) &&
                        !contentType.equals(ALLOWED_CONTENT_TYPE_XLS))) {
            log.warn("Unexpected content type: {}. File: {}", contentType, filename);
            // Don't fail on content type - browsers can send different types
            // Just log warning
        }

        log.debug("File validation passed: {}, size: {} bytes, type: {}",
                filename, file.getSize(), contentType);
    }

}
