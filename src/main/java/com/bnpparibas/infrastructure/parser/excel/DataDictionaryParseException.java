package com.bnpparibas.infrastructure.parser.excel;

public class DataDictionaryParseException extends Exception {

    public DataDictionaryParseException(String message) {
        super(message);
    }

    public DataDictionaryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
