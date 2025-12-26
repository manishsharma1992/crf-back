package com.bnpparibas.infrastructure.validation.model;

/**
 * Severity level for validation errors
 */
public enum ValidationSeverity {

    /**
     * Critical error - validation must fail
     */
    ERROR,

    /**
     * Warning - validation passes but with concerns
     */
    WARNING,

    /**
     * Informational message
     */
    INFO
}
