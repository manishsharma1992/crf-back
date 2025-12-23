package com.bnpparibas.application.schema.service;

import com.bnpparibas.application.schema.dto.DataDictionaryImportRequest;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse.EntryImportResult;
import com.bnpparibas.application.schema.exception.DataDictionaryImportException;
import com.bnpparibas.application.schema.mapper.DataDictionaryImportMapper;
import com.bnpparibas.domain.datadictionary.value.DataDictionaryEntry;
import com.bnpparibas.domain.schema.aggregate.JsonSchemaRegistry;
import com.bnpparibas.domain.schema.repository.JsonSchemaRegistryRepository;
import com.bnpparibas.domain.schema.service.JsonSchemaGeneratorService;
import com.bnpparibas.infrastructure.parser.excel.DataDictionaryExcelParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataDictionaryImportService {

    private final DataDictionaryExcelParser excelParser;
    private final JsonSchemaGeneratorService schemaGenerator;
    private final JsonSchemaRegistryRepository schemaRepository;
    private final DataDictionaryImportMapper importMapper;

    @Transactional
    public DataDictionaryImportResponse importDataDictionary(
            InputStream inputStream,
            DataDictionaryImportRequest request) {

        log.info("Starting data dictionary import. ValidateOnly={}, OverwriteExisting={}, ImportedBy={}",
                request.isValidateOnly(), request.isOverwriteExisting(), request.getImportedBy());

        LocalDateTime startTime = LocalDateTime.now();

        try {
            // Step 1: Parse Excel file
            List<DataDictionaryEntry> entries = parseExcelFile(inputStream);

            log.info("Parsed {} entries from Excel", entries.size());

            // Step 2: Process each entry
            List<EntryImportResult> entryResults = new ArrayList<>();
            int successCount = 0;
            int failedCount = 0;
            int skippedCount = 0;

            for (DataDictionaryEntry entry : entries) {
                EntryImportResult result = processEntry(entry, request);
                entryResults.add(result);

                switch (result.getStatus()) {
                    case SUCCESS:
                    case VALIDATED:
                        successCount++;
                        break;
                    case FAILED:
                        failedCount++;
                        break;
                    case SKIPPED:
                        skippedCount++;
                        break;
                }
            }

            // Step 3: Build response using regular setters
            DataDictionaryImportResponse response = new DataDictionaryImportResponse();
            response.setSuccess(failedCount == 0);
            response.setProcessedAt(startTime);
            response.setTotalEntries(entries.size());
            response.setSuccessfulEntries(successCount);
            response.setFailedEntries(failedCount);
            response.setSkippedEntries(skippedCount);
            response.setEntryResults(entryResults);

            log.info("Import completed. Success={}, Total={}, Successful={}, Failed={}, Skipped={}",
                    response.isSuccess(), entries.size(), successCount, failedCount, skippedCount);

            return response;

        } catch (Exception e) {
            log.error("Data dictionary import failed", e);

            DataDictionaryImportResponse response = new DataDictionaryImportResponse();
            response.setSuccess(false);
            response.setProcessedAt(startTime);
            response.setTotalEntries(0);
            response.setSuccessfulEntries(0);
            response.setFailedEntries(0);
            response.setSkippedEntries(0);
            response.setErrorMessage(String.format("Import failed: %s", e.getMessage()));

            return response;
        }
    }

    private List<DataDictionaryEntry> parseExcelFile(InputStream inputStream) throws Exception {
        try {
            return excelParser.parseDataDictionary(inputStream);
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            throw new DataDictionaryImportException(
                    "Failed to parse Excel file: " + e.getMessage(),
                    e
            );
        }
    }

    private EntryImportResult processEntry(
            DataDictionaryEntry entry,
            DataDictionaryImportRequest request) {

        String modelKey = String.format("%s-%s-%s",
                entry.ratingModel().modelName(),
                entry.ratingModelVersion().version(),
                entry.ratingMechanism().getValue());

        log.debug("Processing entry: {}", modelKey);

        try {
            // Step 1: Generate JSON Schema
            JsonNode jsonSchema = schemaGenerator.generateJsonSchema(entry);

            log.debug("Generated JSON Schema for {}, schema size: {} bytes",
                    modelKey, jsonSchema.toString().length());

            // Step 2: Check if validate-only mode
            if (request.isValidateOnly()) {
                log.info("Validate-only mode: Schema generated successfully for {}", modelKey);
                return importMapper.toValidatedResult(entry);  // ✅ Using mapper
            }

            // Step 3: Check if schema already exists
            Optional<JsonSchemaRegistry> existingSchema = schemaRepository
                    .findByRatingModelAndRatingModelVersionAndRatingMechanismAndActiveTrue(
                            entry.ratingModel(),
                            entry.ratingModelVersion(),
                            entry.ratingMechanism()
                    );

            if (existingSchema.isPresent() && !request.isOverwriteExisting()) {
                log.info("Schema already exists for {}, skipping (overwrite=false)", modelKey);
                return importMapper.toSkippedResult(entry, existingSchema.get());  // ✅ Using mapper
            }

            // Step 4: Save schema (create new or update existing)
            JsonSchemaRegistry savedSchema = saveSchema(
                    entry,
                    jsonSchema,
                    existingSchema,
                    request
            );

            log.info("Successfully saved schema for {}, schema_id={}, version={}",
                    modelKey, savedSchema.getId(), savedSchema.getSchemaVersion().versionNumber());

            return importMapper.toSuccessResult(entry, savedSchema);  // ✅ Using mapper

        } catch (Exception e) {
            log.error("Failed to process entry: {}", modelKey, e);
            return importMapper.toFailedResult(entry, e.getMessage());  // ✅ Using mapper
        }
    }

    private JsonSchemaRegistry saveSchema(
            DataDictionaryEntry entry,
            JsonNode jsonSchema,
            Optional<JsonSchemaRegistry> existingSchema,
            DataDictionaryImportRequest request) {

        if (existingSchema.isEmpty()) {
            // Case 1: No existing schema - create new
            log.debug("Creating new schema for {}-{}-{}",
                    entry.ratingModel().modelName(),
                    entry.ratingModelVersion().version(),
                    entry.ratingMechanism().getValue());

            JsonSchemaRegistry newSchema = JsonSchemaRegistry.createNew(
                    entry.ratingModel(),
                    entry.ratingModelVersion(),
                    entry.ratingMechanism(),
                    jsonSchema,
                    buildDescription(entry, request)
            );

            log.debug("Schema to save - type: {}", jsonSchema.getClass().getName());
            log.debug("Schema content preview: {}", jsonSchema.toString().substring(0, Math.min(200, jsonSchema.toString().length())));
            log.debug("Is Object Node: {}", jsonSchema.isObject());
            log.debug("Has $schema field: {}", jsonSchema.has("$schema"));

            JsonSchemaRegistry saved = schemaRepository.save(newSchema);

            // ✅ Verify after save
            log.debug("Saved schema ID: {}", saved.getId());
            log.debug("Retrieved schema preview: {}",
                    saved.getJsonSchema().toString().substring(0, Math.min(200, saved.getJsonSchema().toString().length())));

            return saved;
        } else {
            // Case 2: Existing schema found
            JsonSchemaRegistry existing = existingSchema.get();

            // Check if schemas are identical
            if (areSchemasIdentical(existing.getJsonSchema(), jsonSchema)) {
                log.info("Schema unchanged for {}-{}-{}, no update needed",
                        entry.ratingModel().modelName(),
                        entry.ratingModelVersion().version(),
                        entry.ratingMechanism().getValue());

                return existing;
            }

            // Schemas are different - deprecate old and create new version
            log.info("Schema changed for {}-{}-{}, creating new version",
                    entry.ratingModel().modelName(),
                    entry.ratingModelVersion().version(),
                    entry.ratingMechanism().getValue());

            existing.deprecate();
            schemaRepository.save(existing);

            JsonSchemaRegistry newVersion = existing.createNewVersion(
                    jsonSchema,
                    buildChangeNotes(entry, request)
            );

            return schemaRepository.save(newVersion);
        }
    }

    private boolean areSchemasIdentical(JsonNode schema1, JsonNode schema2) {
        return schema1.equals(schema2);
    }

    private String buildDescription(
            DataDictionaryEntry entry,
            DataDictionaryImportRequest request) {

        if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
            return request.getDescription();
        }

        return String.format(
                "Schema for %s %s %s - imported from data dictionary",
                entry.ratingModel().modelName(),
                entry.ratingModelVersion().version(),
                entry.ratingMechanism().getValue()
        );
    }

    private String buildChangeNotes(
            DataDictionaryEntry entry,
            DataDictionaryImportRequest request) {

        if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
            return "Updated from data dictionary: " + request.getDescription();
        }

        return String.format(
                "Schema updated from data dictionary import - %d fields processed",
                entry.fields().size()
        );
    }
}
