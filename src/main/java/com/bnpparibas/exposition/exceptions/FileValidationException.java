package com.bnpparibas.exposition.exceptions;

public class FileValidationException extends Exception {

    public FileValidationException(String message) {
        super(message);
    }

    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
