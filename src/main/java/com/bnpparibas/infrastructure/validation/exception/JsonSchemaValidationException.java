package com.bnpparibas.infrastructure.validation.exception;

import com.bnpparibas.infrastructure.validation.model.ValidationResult;
import lombok.Getter;

@Getter
public class JsonSchemaValidationException extends RuntimeException {

    private final ValidationResult validationResult;

    public JsonSchemaValidationException(ValidationResult validationResult) {
        super(validationResult.getFormattedErrors());
        this.validationResult = validationResult;
    }

    public JsonSchemaValidationException(String message, ValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }

    public JsonSchemaValidationException(String message, ValidationResult validationResult, Throwable cause) {
        super(message, cause);
        this.validationResult = validationResult;
    }
}
