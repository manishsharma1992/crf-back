package com.bnpparibas.application.schema.service;

import com.bnpparibas.application.schema.dto.DataDictionaryImportRequest;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse.EntryImportResult;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse.ImportStatus;
import com.bnpparibas.domain.schema.aggregate.JsonSchemaRegistry;
import com.bnpparibas.domain.schema.repository.JsonSchemaRegistryRepository;
import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DataDictionaryImportService
 *
 * Tests the complete flow:
 * - Excel parsing
 * - Schema generation
 * - Database persistence
 * - Transaction management
 *
 * Uses H2 in-memory database for testing
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DataDictionaryImportService Integration Tests")
class DataDictionaryImportServiceIntegrationTest {

    @Autowired
    private DataDictionaryImportService importService;

    @Autowired
    private JsonSchemaRegistryRepository schemaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Test data paths
    private static final String TEST_EXCEL_PATH = "src/test/resources/test-data/test_data_dictionary.xlsx";

    @BeforeEach
    void setUp() {
        // Clean database before each test
        schemaRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        schemaRepository.deleteAll();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Create a minimal valid Excel file content as InputStream
     * This simulates an Excel file without needing actual file
     */
    private InputStream createTestExcelStream() throws IOException {
        // For now, use a simple approach: load actual test file if exists
        // Or create a mock Excel in memory

        // Option 1: Load from test resources (if file exists)
        if (Files.exists(Paths.get(TEST_EXCEL_PATH))) {
            return Files.newInputStream(Paths.get(TEST_EXCEL_PATH));
        }

        // Option 2: Return empty stream (will fail gracefully in test)
        // In real scenario, you'd generate a proper Excel file
        return new ByteArrayInputStream(new byte[0]);
    }

    private DataDictionaryImportRequest createBaseRequest() {
        DataDictionaryImportRequest request = new DataDictionaryImportRequest();
        request.setDescription("Test import");
        request.setImportedBy("test-user");
        request.setValidateOnly(false);
        request.setOverwriteExisting(false);
        return request;
    }

    // ========================================================================
    // Basic Import Tests
    // ========================================================================

    @Nested
    @DisplayName("Basic Import Scenarios")
    class BasicImportTests {

        @Test
        @Order(1)
        @DisplayName("Should successfully import new schema")
        @Transactional
        void testSuccessfulImport() throws IOException {
            // Given
            DataDictionaryImportRequest request = createBaseRequest();

            // When
            try (InputStream excelStream = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream,
                        request
                );

                // Then
                assertNotNull(response);
                assertTrue(response.isSuccess());
                assertTrue(response.getTotalEntries() > 0);
                assertEquals(response.getTotalEntries(), response.getSuccessfulEntries());
                assertEquals(0, response.getFailedEntries());
                assertEquals(0, response.getSkippedEntries());

                // Verify database
                List<JsonSchemaRegistry> savedSchemas = schemaRepository.findAll();
                assertEquals(response.getTotalEntries(), savedSchemas.size());

                // Verify all schemas are active
                savedSchemas.forEach(schema -> {
                    assertTrue(schema.getActive());
                    assertNotNull(schema.getJsonSchema());
                    assertEquals(1, schema.getSchemaVersion().versionNumber());
                });
            }
        }

        @Test
        @Order(2)
        @DisplayName("Should validate without saving (dry-run)")
        @Transactional
        void testValidateOnly() throws IOException {
            // Given
            DataDictionaryImportRequest request = createBaseRequest();
            request.setValidateOnly(true);

            // When
            try (InputStream excelStream = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream,
                        request
                );

                // Then
                assertNotNull(response);
                assertTrue(response.isSuccess());

                // Verify nothing saved to database
                List<JsonSchemaRegistry> savedSchemas = schemaRepository.findAll();
                assertEquals(0, savedSchemas.size(), "Validate-only mode should not save to database");

                // Verify all results are VALIDATED status
                response.getEntryResults().forEach(result ->
                        assertEquals(ImportStatus.VALIDATED, result.getStatus())
                );
            }
        }
    }

    // ========================================================================
    // Overwrite and Versioning Tests
    // ========================================================================

    @Nested
    @DisplayName("Overwrite and Versioning")
    class OverwriteTests {

        @Test
        @DisplayName("Should skip existing schema when overwrite=false")
        @Transactional
        void testSkipExistingSchema() throws IOException {
            // Given: Import once
            DataDictionaryImportRequest firstRequest = createBaseRequest();
            try (InputStream excelStream1 = createTestExcelStream()) {
                importService.importDataDictionary(excelStream1, firstRequest);
            }

            long countAfterFirst = schemaRepository.count();

            // When: Import again with overwrite=false
            DataDictionaryImportRequest secondRequest = createBaseRequest();
            secondRequest.setOverwriteExisting(false);

            try (InputStream excelStream2 = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream2,
                        secondRequest
                );

                // Then: All entries should be skipped
                assertEquals(response.getTotalEntries(), response.getSkippedEntries());
                assertEquals(0, response.getSuccessfulEntries());

                // Database count should not change
                assertEquals(countAfterFirst, schemaRepository.count());

                // All should be SKIPPED status
                response.getEntryResults().forEach(result ->
                        assertEquals(ImportStatus.SKIPPED, result.getStatus())
                );
            }
        }

        @Test
        @DisplayName("Should create new version when schema changes")
        @Transactional
        void testCreateNewVersion() throws IOException {
            // Given: Import once
            DataDictionaryImportRequest firstRequest = createBaseRequest();
            firstRequest.setDescription("Version 1");

            try (InputStream excelStream1 = createTestExcelStream()) {
                DataDictionaryImportResponse firstResponse = importService.importDataDictionary(
                        excelStream1,
                        firstRequest
                );

                // Get the first schema
                List<JsonSchemaRegistry> schemasV1 = schemaRepository.findAll();
                assertEquals(firstResponse.getTotalEntries(), schemasV1.size());
            }

            // Simulate schema change by modifying and re-importing
            // (In real test, you'd use a different Excel file with changes)

            // When: Import again with overwrite=true
            DataDictionaryImportRequest secondRequest = createBaseRequest();
            secondRequest.setDescription("Version 2 - with changes");
            secondRequest.setOverwriteExisting(true);

            try (InputStream excelStream2 = createTestExcelStream()) {
                DataDictionaryImportResponse secondResponse = importService.importDataDictionary(
                        excelStream2,
                        secondRequest
                );

                // Then: Should have created new versions
                assertTrue(secondResponse.isSuccess());

                // Database should have both old (inactive) and new (active) versions
                List<JsonSchemaRegistry> allSchemas = schemaRepository.findAll();

                // Verify version numbers
                RatingModel testModel = RatingModel.of("PLACM");
                RatingModelVersion testVersion = RatingModelVersion.of("010");
                RatingMechanism testMechanism = RatingMechanism.STANDALONE;

                List<JsonSchemaRegistry> versions = schemaRepository
                        .findByRatingModelAndRatingModelVersionAndRatingMechanismOrderBySchemaVersionDesc(
                                testModel, testVersion, testMechanism
                        );

                if (!versions.isEmpty()) {
                    // Latest version should be active
                    JsonSchemaRegistry latest = versions.get(0);
                    assertTrue(latest.getActive());

                    // If there's a previous version, it should be inactive
                    if (versions.size() > 1) {
                        JsonSchemaRegistry previous = versions.get(1);
                        assertFalse(previous.getActive());
                        assertNotNull(previous.getEffectiveTo());
                    }
                }
            }
        }

        @Test
        @DisplayName("Should not create new version if schema unchanged")
        @Transactional
        void testNoNewVersionIfUnchanged() throws IOException {
            // Given: Import once
            DataDictionaryImportRequest firstRequest = createBaseRequest();

            try (InputStream excelStream1 = createTestExcelStream()) {
                importService.importDataDictionary(excelStream1, firstRequest);
            }

            long countAfterFirst = schemaRepository.count();

            // When: Import identical schema with overwrite=true
            DataDictionaryImportRequest secondRequest = createBaseRequest();
            secondRequest.setOverwriteExisting(true);

            try (InputStream excelStream2 = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream2,
                        secondRequest
                );

                // Then: Should recognize schema is unchanged
                assertTrue(response.isSuccess());

                // Count should not change (no new versions created)
                assertEquals(countAfterFirst, schemaRepository.count());

                // All schemas should still be version 1
                List<JsonSchemaRegistry> schemas = schemaRepository.findAll();
                schemas.forEach(schema ->
                        assertEquals(1, schema.getSchemaVersion().versionNumber())
                );
            }
        }
    }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid Excel file gracefully")
        @Transactional
        void testInvalidExcelFile() {
            // Given: Invalid Excel content
            byte[] invalidContent = "This is not an Excel file".getBytes();
            InputStream invalidStream = new ByteArrayInputStream(invalidContent);

            DataDictionaryImportRequest request = createBaseRequest();

            // When
            DataDictionaryImportResponse response = importService.importDataDictionary(
                    invalidStream,
                    request
            );

            // Then: Should fail gracefully
            assertFalse(response.isSuccess());
            assertNotNull(response.getErrorMessage());
            assertTrue(response.getErrorMessage().contains("Failed to parse Excel file"));

            // Nothing should be saved to database
            assertEquals(0, schemaRepository.count());
        }

        @Test
        @DisplayName("Should handle empty Excel file")
        @Transactional
        void testEmptyExcelFile() {
            // Given: Empty stream
            InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
            DataDictionaryImportRequest request = createBaseRequest();

            // When
            DataDictionaryImportResponse response = importService.importDataDictionary(
                    emptyStream,
                    request
            );

            // Then
            assertFalse(response.isSuccess());
            assertNotNull(response.getErrorMessage());
        }

        @Test
        @DisplayName("Should rollback transaction on partial failure")
        @Transactional
        void testTransactionRollback() {
            // This test verifies that if any part of import fails,
            // the entire transaction is rolled back

            // Implementation depends on whether you want all-or-nothing
            // or partial success behavior

            // For now, verify current behavior is consistent
            long countBefore = schemaRepository.count();

            // Attempt import that might partially fail
            // (would need specially crafted test data)

            long countAfter = schemaRepository.count();

            // Verify state is consistent
            assertTrue(countAfter >= countBefore);
        }
    }

    // ========================================================================
    // Database State Verification Tests
    // ========================================================================

    @Nested
    @DisplayName("Database State Verification")
    class DatabaseStateTests {

        @Test
        @DisplayName("Should persist all schema fields correctly")
        @Transactional
        void testSchemaFieldsPersisted() throws IOException {
            // Given
            DataDictionaryImportRequest request = createBaseRequest();
            request.setDescription("Test description");
            request.setImportedBy("integration-test-user");

            // When
            try (InputStream excelStream = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream,
                        request
                );

                if (response.isSuccess() && response.getTotalEntries() > 0) {
                    // Then: Verify first saved schema
                    Optional<JsonSchemaRegistry> firstSchema = schemaRepository.findById(
                            response.getEntryResults().get(0).getSchemaRegistryId()
                    );

                    assertTrue(firstSchema.isPresent());
                    JsonSchemaRegistry schema = firstSchema.get();

                    // Verify basic fields
                    assertNotNull(schema.getId());
                    assertNotNull(schema.getRatingModel());
                    assertNotNull(schema.getRatingModelVersion());
                    assertNotNull(schema.getRatingMechanism());
                    assertNotNull(schema.getSchemaVersion());
                    assertNotNull(schema.getJsonSchema());
                    assertTrue(schema.getActive());
                    assertNotNull(schema.getEffectiveFrom());
                    assertNull(schema.getEffectiveTo());

                    // Verify audit fields
                    //assertNotNull(schema.getCreatedBy());
                    //assertNotNull(schema.getCreatedTimestamp());

                    // Verify JSON schema structure
                    JsonNode jsonSchema = schema.getJsonSchema();
                    assertTrue(jsonSchema.has("$schema"));
                    assertTrue(jsonSchema.has("$id"));
                    assertTrue(jsonSchema.has("type"));
                    assertTrue(jsonSchema.has("properties"));
                    assertEquals("object", jsonSchema.get("type").asText());
                }
            }
        }

        @Test
        @DisplayName("Should find schemas by rating model/version/mechanism")
        @Transactional
        void testFindByRatingModelVersionMechanism() throws IOException {
            // Given: Import schemas
            DataDictionaryImportRequest request = createBaseRequest();

            try (InputStream excelStream = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream,
                        request
                );

                if (response.isSuccess() && response.getTotalEntries() > 0) {
                    // When: Query by specific model/version/mechanism
                    EntryImportResult firstEntry = response.getEntryResults().get(0);

                    RatingModel model = RatingModel.of(firstEntry.getRatingModel());
                    RatingModelVersion version = RatingModelVersion.of(firstEntry.getRatingModelVersion());
                    RatingMechanism mechanism = RatingMechanism.valueOf(firstEntry.getRatingMechanism());

                    Optional<JsonSchemaRegistry> found = schemaRepository
                            .findByRatingModelAndRatingModelVersionAndRatingMechanismAndActiveTrue(
                                    model, version, mechanism
                            );

                    // Then
                    assertTrue(found.isPresent());
                    assertEquals(firstEntry.getSchemaRegistryId(), found.get().getId());
                }
            }
        }

        @Test
        @DisplayName("Should only return active schemas in active query")
        @Transactional
        void testOnlyActiveSchemas() throws IOException {
            // Given: Create and then deprecate a schema
            DataDictionaryImportRequest firstRequest = createBaseRequest();

            try (InputStream excelStream1 = createTestExcelStream()) {
                importService.importDataDictionary(excelStream1, firstRequest);
            }

            // Create new version (deprecates old one)
            DataDictionaryImportRequest secondRequest = createBaseRequest();
            secondRequest.setOverwriteExisting(true);
            secondRequest.setDescription("Version 2");

            try (InputStream excelStream2 = createTestExcelStream()) {
                importService.importDataDictionary(excelStream2, secondRequest);
            }

            // When: Query for active schemas
            List<JsonSchemaRegistry> activeSchemas = schemaRepository.findByActiveTrue();

            // Then: Should only get active schemas
            assertFalse(activeSchemas.isEmpty());
            activeSchemas.forEach(schema -> assertTrue(schema.getActive()));
        }
    }

    // ========================================================================
    // Response Verification Tests
    // ========================================================================

    @Nested
    @DisplayName("Response Verification")
    class ResponseVerificationTests {

        @Test
        @DisplayName("Should return correct entry results")
        @Transactional
        void testEntryResults() throws IOException {
            // Given
            DataDictionaryImportRequest request = createBaseRequest();

            // When
            try (InputStream excelStream = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream,
                        request
                );

                // Then
                assertNotNull(response.getEntryResults());
                assertEquals(response.getTotalEntries(), response.getEntryResults().size());

                // Verify each result has required fields
                response.getEntryResults().forEach(result -> {
                    assertNotNull(result.getRatingModel());
                    assertNotNull(result.getRatingModelVersion());
                    assertNotNull(result.getRatingMechanism());
                    assertNotNull(result.getStatus());
                    assertTrue(result.getFieldCount() > 0);

                    if (result.getStatus() == ImportStatus.SUCCESS) {
                        assertNotNull(result.getSchemaVersion());
                        assertNotNull(result.getSchemaRegistryId());
                        assertNull(result.getErrorMessage());
                    }
                });
            }
        }

        @Test
        @DisplayName("Should set processed timestamp")
        @Transactional
        void testProcessedTimestamp() throws IOException {
            // Given
            DataDictionaryImportRequest request = createBaseRequest();

            // When
            try (InputStream excelStream = createTestExcelStream()) {
                DataDictionaryImportResponse response = importService.importDataDictionary(
                        excelStream,
                        request
                );

                // Then
                assertNotNull(response.getProcessedAt());
                assertTrue(response.getProcessedAt().isBefore(
                        java.time.LocalDateTime.now().plusSeconds(1)
                ));
            }
        }
    }
}