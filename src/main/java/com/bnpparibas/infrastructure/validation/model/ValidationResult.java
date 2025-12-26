package com.bnpparibas.infrastructure.validation.model;

import lombok.Builder;
import lombok.Singular;

import java.util.List;
import java.util.stream.Collectors;

@Builder
public record ValidationResult(
        boolean valid,
        @Singular List<ValidationError> errors,
        String schemaId,
        String message
        ) {

    /**
     * Get only ERROR severity errors
     */
    public List<ValidationError> getErrorsOnly() {
        if (errors == null) {
            return List.of();
        }
        return errors.stream()
                .filter(e -> e.severity() == ValidationSeverity.ERROR)
                .collect(Collectors.toList());
    }

    /**
     * Get only WARNING severity errors
     */
    public List<ValidationError> getWarnings() {
        if (errors == null) {
            return List.of();
        }
        return errors.stream()
                .filter(e -> e.severity() == ValidationSeverity.WARNING)
                .collect(Collectors.toList());
    }

    /**
     * Get total error count
     */
    public int getErrorCount() {
        return errors != null ? errors.size() : 0;
    }

    /**
     * Check if there are any errors
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Get formatted error messages
     */
    public String getFormattedErrors() {
        if (errors == null || errors.isEmpty()) {
            return "No validation errors";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Validation failed with %d error(s):\n", errors.size()));

        for (int i = 0; i < errors.size(); i++) {
            ValidationError error = errors.get(i);
            sb.append(String.format("  %d. [%s] %s: %s\n",
                    i + 1,
                    error.severity(),
                    error.path(),
                    error.message()
            ));
        }

        return sb.toString();
    }

    /**
     * Create a successful validation result
     */
    public static ValidationResult success(String schemaId) {
        return ValidationResult.builder()
                .valid(true)
                .schemaId(schemaId)
                .message("Validation passed successfully")
                .build();
    }

    /**
     * Create a failed validation result
     */
    public static ValidationResult failure(String schemaId, List<ValidationError> errors) {
        return ValidationResult.builder()
                .valid(false)
                .schemaId(schemaId)
                .errors(errors)
                .message(String.format("Validation failed with %d error(s)", errors.size()))
                .build();
    }
}
