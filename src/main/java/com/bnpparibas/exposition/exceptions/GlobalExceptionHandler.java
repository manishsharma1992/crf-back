package com.bnpparibas.exposition.exceptions;

import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle file size exceeded exception
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<DataDictionaryImportResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex) {

        log.error("File size exceeded maximum limit", ex);

        DataDictionaryImportResponse response = new DataDictionaryImportResponse();
        response.setSuccess(false);
        response.setProcessedAt(LocalDateTime.now());
        response.setErrorMessage("File size exceeds maximum allowed size. Please upload a smaller file.");

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DataDictionaryImportResponse> handleGenericException(Exception ex) {

        log.error("Unexpected error in REST controller", ex);

        DataDictionaryImportResponse response = new DataDictionaryImportResponse();
        response.setSuccess(false);
        response.setProcessedAt(LocalDateTime.now());
        response.setErrorMessage("An unexpected error occurred: " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
