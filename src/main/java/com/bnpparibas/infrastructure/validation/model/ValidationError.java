package com.bnpparibas.infrastructure.validation.model;

import lombok.Builder;

@Builder
public record ValidationError(
        String path,
        String message,
        ValidationSeverity severity,
        String keyword,
        String schemaPath,
        Object invalidValue,
        String expectedValue,
        String details
) {
}
