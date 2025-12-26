package com.bnpparibas.infrastructure.validation.validator;

import com.bnpparibas.infrastructure.validation.exception.JsonSchemaValidationException;
import com.bnpparibas.infrastructure.validation.model.ValidationError;
import com.bnpparibas.infrastructure.validation.model.ValidationResult;
import com.bnpparibas.infrastructure.validation.model.ValidationSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic JSON Schema Validator
 *
 * Validates any JsonNode against any JSON Schema (Draft 7 / 2020-12)
 *
 * Usage:
 * <pre>
 * ValidationResult result = validator.validate(schema, data);
 * if (!result.isValid()) {
 *     throw new JsonSchemaValidationException(result);
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonSchemaValidator {

    private final ObjectMapper objectMapper;

    /**
     * Validate JsonNode against JSON Schema
     *
     * @param schema JSON Schema as JsonNode
     * @param data Data to validate as JsonNode
     * @return ValidationResult with success/failure and errors
     */
    public ValidationResult validate(JsonNode schema, JsonNode data) {
        return validate(schema, data, false);
    }

    /**
     * Validate JsonNode against JSON Schema
     *
     * @param schema JSON Schema as JsonNode
     * @param data Data to validate as JsonNode
     * @param throwOnError If true, throws exception on validation failure
     * @return ValidationResult with success/failure and errors
     * @throws JsonSchemaValidationException if throwOnError=true and validation fails
     */
    public ValidationResult validate(JsonNode schema, JsonNode data, boolean throwOnError) {

        log.debug("Starting JSON Schema validation");

        try {
            // Convert Jackson JsonNode to org.json.JSONObject
            String schemaJson = objectMapper.writeValueAsString(schema);
            String dataJson = objectMapper.writeValueAsString(data);

            JSONObject schemaObject = new JSONObject(new JSONTokener(schemaJson));
            JSONObject dataObject = new JSONObject(new JSONTokener(dataJson));

            // Load schema
            Schema loadedSchema = SchemaLoader.load(schemaObject);

            // Extract schema ID for reporting
            String schemaId = schema.has("$id") ? schema.get("$id").asText() : "unknown";

            // Perform validation
            try {
                loadedSchema.validate(dataObject);

                // Validation passed
                log.debug("JSON Schema validation passed");
                return ValidationResult.success(schemaId);

            } catch (ValidationException e) {
                // Validation failed - collect all errors
                List<ValidationError> errors = collectValidationErrors(e);

                log.warn("JSON Schema validation failed with {} error(s)", errors.size());

                ValidationResult result = ValidationResult.failure(schemaId, errors);

                if (throwOnError) {
                    throw new JsonSchemaValidationException(result);
                }

                return result;
            }

        } catch (JsonSchemaValidationException e) {
            // Re-throw validation exceptions
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during JSON Schema validation", e);

            // Create error result for unexpected exceptions
            ValidationError error = ValidationError.builder()
                    .path("$")
                    .message("Schema validation failed due to internal error: " + e.getMessage())
                    .severity(ValidationSeverity.ERROR)
                    .keyword("internal-error")
                    .details(e.getClass().getName())
                    .build();

            ValidationResult result = ValidationResult.builder()
                    .valid(false)
                    .error(error)
                    .message("Internal validation error")
                    .build();

            if (throwOnError) {
                throw new JsonSchemaValidationException("Schema validation error", result, e);
            }

            return result;
        }
    }

    /**
     * Validate and throw exception if validation fails
     *
     * @param schema JSON Schema
     * @param data Data to validate
     * @throws JsonSchemaValidationException if validation fails
     */
    public void validateAndThrow(JsonNode schema, JsonNode data) throws JsonSchemaValidationException {
        validate(schema, data, true);
    }


    /**
     * Collect all validation errors from ValidationException
     *
     * Everit validation can have nested errors, we need to flatten them
     */
    private List<ValidationError> collectValidationErrors(ValidationException exception) {
        List<ValidationError> errors = new ArrayList<>();
        collectErrorsRecursive(exception, errors, "$");
        return errors;
    }

    /**
     * Recursively collect validation errors
     */
    private void collectErrorsRecursive(
            ValidationException exception,
            List<ValidationError> errors,
            String currentPath) {

        // Add current error
        ValidationError error = ValidationError.builder()
                .path(buildPath(currentPath, exception.getPointerToViolation()))
                .message(exception.getMessage())
                .severity(ValidationSeverity.ERROR)
                .keyword(exception.getKeyword())
                .schemaPath(exception.getSchemaLocation())
                .build();

        errors.add(error);

        // Process nested errors (for oneOf, anyOf, allOf, etc.)
        if (exception.getCausingExceptions() != null) {
            for (ValidationException cause : exception.getCausingExceptions()) {
                collectErrorsRecursive(cause, errors, currentPath);
            }
        }
    }

    /**
     * Build JSON path from pointer
     */
    private String buildPath(String basePath, String pointer) {
        if (pointer == null || pointer.isEmpty() || pointer.equals("#")) {
            return basePath;
        }

        // Convert JSON pointer to dot notation
        // #/properties/financial_drivers/properties/leverage → $.financial_drivers.leverage

        return pointer
                .replace("#/", "$.")
                .replace("/properties/", ".")
                .replace("/items/", "[*].")
                .replace("/", ".");
    }
}
