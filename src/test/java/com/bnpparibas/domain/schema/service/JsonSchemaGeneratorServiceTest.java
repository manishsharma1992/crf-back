package com.bnpparibas.domain.schema.service;

import com.bnpparibas.domain.datadictionary.value.DataDictionaryEntry;
import com.bnpparibas.domain.datadictionary.value.DataDictionaryField;
import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for JsonSchemaGeneratorService
 *
 * Coverage targets:
 * - Line coverage: 80%+
 * - Conditional coverage: 80%+
 *
 * Test strategy:
 * - Pure unit tests (no Spring context)
 * - Test domain logic in isolation
 * - Use real ObjectMapper (no mocking needed)
 * - Organized by feature/scenario
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JsonSchemaGeneratorService Tests")
class JsonSchemaGeneratorServiceTest {

    private JsonSchemaGeneratorService service;
    private ObjectMapper objectMapper;

    @BeforeAll
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new JsonSchemaGeneratorService(objectMapper);
    }

    // ========================================================================
    // Test Data Builders
    // ========================================================================

    private DataDictionaryEntry.DataDictionaryEntryBuilder baseEntryBuilder() {
        return DataDictionaryEntry.builder()
                .ratingModel(RatingModel.of("PLACM"))
                .ratingModelVersion(RatingModelVersion.of("010"))
                .ratingMechanism(RatingMechanism.STANDALONE);
    }

    private DataDictionaryField.DataDictionaryFieldBuilder baseFieldBuilder() {
        return DataDictionaryField.builder()
                .dataName("Test Field")
                .dataDefinition("Test definition")
                .fieldPath("model_specific_overrides.test_field")
                .dataType("string")
                .isMandatory(false);
    }

    // ========================================================================
    // Nested Test Classes for Organization
    // ========================================================================

    @Nested
    @DisplayName("Schema Generation - Basic Structure")
    class SchemaGenerationBasicTests {

        @Test
        @DisplayName("Should generate valid JSON Schema 2020-12 structure")
        void testBasicSchemaStructure() {
            // Given
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("string")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            // When
            JsonNode schema = service.generateJsonSchema(entry);

            // Then
            assertNotNull(schema);
            assertTrue(schema.isObject());

            // Verify schema metadata
            assertEquals("https://json-schema.org/draft/2020-12/schema",
                    schema.get("$schema").asText());
            assertEquals("object", schema.get("type").asText());
            assertTrue(schema.has("$id"));
            assertTrue(schema.has("title"));
            assertTrue(schema.has("description"));
            assertTrue(schema.has("properties"));
            assertEquals(false, schema.get("additionalProperties").asBoolean());
        }

        @Test
        @DisplayName("Should generate correct schema ID")
        void testSchemaId() {
            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(baseFieldBuilder().build()))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            String expectedId = "https://bnpparibas.com/schemas/placm/010/standalone/model-specific-overrides.json";
            assertEquals(expectedId, schema.get("$id").asText());
        }

        @Test
        @DisplayName("Should generate correct schema title")
        void testSchemaTitle() {
            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(baseFieldBuilder().build()))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            String expectedTitle = "PLACM 010 STANDALONE Model Specific Overrides Schema";
            assertEquals(expectedTitle, schema.get("title").asText());
        }

        @Test
        @DisplayName("Should include properties node")
        void testPropertiesNode() {
            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(baseFieldBuilder().build()))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            assertTrue(schema.has("properties"));
            assertTrue(schema.get("properties").isObject());
        }
    }

    @Nested
    @DisplayName("Data Type Parsing")
    class DataTypeParsingTests {

        @ParameterizedTest
        @CsvSource({
                "string, string",
                "varchar, string",
                "text, string",
                "char, string",
                "integer, integer",
                "bigint, integer",
                "smallint, integer",
                "serial, integer",
                "boolean, boolean",
                "bool, boolean",
                "date, string",
                "timestamp, string",
                "uuid, string",
                "jsonb, object"
        })
        @DisplayName("Should parse simple data types correctly")
        void testSimpleDataTypes(String sqlType, String expectedJsonType) {
            // Given
            DataDictionaryField field = baseFieldBuilder()
                    .dataType(sqlType)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            // When
            JsonNode schema = service.generateJsonSchema(entry);

            // Then
            JsonNode properties = schema.get("properties");
            JsonNode testField = properties.get("test_field");

            assertEquals(expectedJsonType, testField.get("type").asText());
        }

        @ParameterizedTest
        @CsvSource({
                "'numeric(14,10)', 14, 10",
                "'numeric(10,2)', 10, 2",
                "'decimal(18,4)', 18, 4",
                "'numeric(5,5)', 5, 5"
        })
        @DisplayName("Should parse numeric types with precision and scale")
        void testNumericWithPrecisionScale(String sqlType, int expectedPrecision, int expectedScale) {
            // Given
            DataDictionaryField field = baseFieldBuilder()
                    .dataType(sqlType)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            // When
            JsonNode schema = service.generateJsonSchema(entry);

            // Then
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals("number", testField.get("type").asText());
            assertEquals(expectedPrecision, testField.get("x-numeric-precision").asInt());
            assertEquals(expectedScale, testField.get("x-numeric-scale").asInt());
            assertTrue(testField.has("minimum"));
            assertTrue(testField.has("maximum"));
        }

        @ParameterizedTest
        @CsvSource({
                "'varchar(255)', 255",
                "'varchar(100)', 100",
                "'char(10)', 10",
                "'char(1)', 1"
        })
        @DisplayName("Should parse string types with length")
        void testStringWithLength(String sqlType, int expectedLength) {
            // Given
            DataDictionaryField field = baseFieldBuilder()
                    .dataType(sqlType)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            // When
            JsonNode schema = service.generateJsonSchema(entry);

            // Then
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals("string", testField.get("type").asText());
            assertEquals(expectedLength, testField.get("maxLength").asInt());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "string[]",
                "bigint[]",
                "numeric(14,10)[]",
                "varchar(255)[]"
        })
        @DisplayName("Should parse array types")
        void testArrayTypes(String sqlType) {
            // Given
            DataDictionaryField field = baseFieldBuilder()
                    .dataType(sqlType)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            // When
            JsonNode schema = service.generateJsonSchema(entry);

            // Then
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals("array", testField.get("type").asText());
            assertTrue(testField.has("items"));
            assertTrue(testField.get("uniqueItems").asBoolean());
        }
    }

    @Nested
    @DisplayName("Numeric Bounds Calculation")
    class NumericBoundsTests {

        @Test
        @DisplayName("Should calculate bounds for numeric(14,10)")
        void testNumericBounds14_10() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(14,10)")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            // Max: 9999.9999999999 (4 integer digits, 10 decimal digits)
            assertEquals(9999.9999999999, testField.get("maximum").asDouble(), 0.0000000001);
            assertEquals(-9999.9999999999, testField.get("minimum").asDouble(), 0.0000000001);
        }

        @Test
        @DisplayName("Should calculate bounds for numeric(10,2)")
        void testNumericBounds10_2() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(10,2)")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            // Max: 99999999.99 (8 integer digits, 2 decimal digits)
            assertEquals(99999999.99, testField.get("maximum").asDouble(), 0.01);
            assertEquals(-99999999.99, testField.get("minimum").asDouble(), 0.01);
        }

        @Test
        @DisplayName("Should calculate bounds for numeric(5,5)")
        void testNumericBoundsAllDecimals() {
            // numeric(5,5) = 0.99999 (all digits after decimal)
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(5,5)")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.get("maximum").asDouble() < 1.0);
            assertTrue(testField.get("minimum").asDouble() > -1.0);
        }
    }

    @Nested
    @DisplayName("Field Constraints")
    class FieldConstraintsTests {

        @Test
        @DisplayName("Should add string length constraints")
        void testStringLengthConstraints() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("varchar(100)")
                    .length(50)  // Override with explicit length
                    .minValue("10")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals(50, testField.get("maxLength").asInt());
            assertEquals(10, testField.get("minLength").asInt());
        }

        @Test
        @DisplayName("Should add numeric min/max constraints")
        void testNumericMinMaxConstraints() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("integer")
                    .minValue("0")
                    .maxValue("100")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals(0, testField.get("minimum").asDouble());
            assertEquals(100, testField.get("maximum").asDouble());
        }

        @Test
        @DisplayName("Should add array minItems/maxItems constraints")
        void testArrayLengthConstraints() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("string[]")
                    .minValue("1")
                    .maxValue("10")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals(1, testField.get("minItems").asInt());
            assertEquals(10, testField.get("maxItems").asInt());
        }

        @Test
        @DisplayName("Should add UUID pattern")
        void testUuidPattern() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("uuid")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("pattern"));
            assertTrue(testField.get("pattern").asText().contains("^[0-9a-fA-F]{8}"));
        }

        @Test
        @DisplayName("Should add date format")
        void testDateFormat() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("date")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals("date", testField.get("format").asText());
        }
    }

    @Nested
    @DisplayName("Enum and Default Values")
    class EnumAndDefaultTests {

        @Test
        @DisplayName("Should add string enum values")
        void testStringEnum() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("string")
                    .allowedValues("ACTIVE, INACTIVE, PENDING")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("enum"));
            ArrayNode enumArray = (ArrayNode) testField.get("enum");
            assertEquals(3, enumArray.size());
            assertEquals("ACTIVE", enumArray.get(0).asText());
            assertEquals("INACTIVE", enumArray.get(1).asText());
            assertEquals("PENDING", enumArray.get(2).asText());
        }

        @Test
        @DisplayName("Should add integer enum values")
        void testIntegerEnum() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("integer")
                    .allowedValues("1, 2, 3, 5, 8")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("enum"));
            ArrayNode enumArray = (ArrayNode) testField.get("enum");
            assertEquals(5, enumArray.size());
            assertEquals(1, enumArray.get(0).asInt());
            assertEquals(8, enumArray.get(4).asInt());
        }

        @Test
        @DisplayName("Should add string default value")
        void testStringDefaultValue() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("string")
                    .defaultValue("hello")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("default"));
            assertEquals("hello", testField.get("default").asText());
        }

        @Test
        @DisplayName("Should add integer default value")
        void testIntegerDefaultValue() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("integer")
                    .defaultValue("42")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("default"));
            assertEquals(42, testField.get("default").asInt());
        }

        @Test
        @DisplayName("Should add boolean default value")
        void testBooleanDefaultValue() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("boolean")
                    .defaultValue("true")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("default"));
            assertTrue(testField.get("default").asBoolean());
        }

        @Test
        @DisplayName("Should add numeric default value")
        void testNumericDefaultValue() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(10,2)")
                    .defaultValue("3.14")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("default"));
            assertEquals(3.14, testField.get("default").asDouble(), 0.01);
        }
    }

    @Nested
    @DisplayName("Foreign Key and Metadata")
    class ForeignKeyAndMetadataTests {

        @Test
        @DisplayName("Should add foreign key metadata")
        void testForeignKeyMetadata() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("bigint")
                    .isForeignKey(true)
                    .foreignKeyTable("industry")
                    .foreignKeyColumn("id")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertTrue(testField.has("x-foreign-key"));
            JsonNode fkInfo = testField.get("x-foreign-key");
            assertEquals("industry", fkInfo.get("table").asText());
            assertEquals("id", fkInfo.get("column").asText());
        }

        @Test
        @DisplayName("Should add x-sql-type metadata")
        void testSqlTypeMetadata() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(14,10)")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals("numeric(14,10)", testField.get("x-sql-type").asText());
        }

        @Test
        @DisplayName("Should add field title and description")
        void testFieldTitleAndDescription() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataName("Main Industry")
                    .dataDefinition("Primary industry classification")
                    .fieldDescription("Used for risk assessment")
                    .dataType("bigint")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            assertEquals("Main Industry", testField.get("title").asText());
            assertTrue(testField.get("description").asText().contains("Primary industry"));
            assertTrue(testField.get("description").asText().contains("risk assessment"));
        }
    }

    @Nested
    @DisplayName("Required Fields")
    class RequiredFieldsTests {

        @Test
        @DisplayName("Should mark mandatory fields as required")
        void testMandatoryFields() {
            DataDictionaryField field1 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.country_id")
                    .dataType("bigint")
                    .isMandatory(true)
                    .build();

            DataDictionaryField field2 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.rating_id")
                    .dataType("bigint")
                    .isMandatory(true)
                    .build();

            DataDictionaryField field3 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.optional_field")
                    .dataType("string")
                    .isMandatory(false)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field1, field2, field3))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            assertTrue(schema.has("required"));
            ArrayNode required = (ArrayNode) schema.get("required");
            assertEquals(2, required.size());

            // Check both required fields are present
            boolean hasCountryId = false;
            boolean hasRatingId = false;
            for (JsonNode field : required) {
                String fieldName = field.asText();
                if ("country_id".equals(fieldName)) hasCountryId = true;
                if ("rating_id".equals(fieldName)) hasRatingId = true;
            }
            assertTrue(hasCountryId);
            assertTrue(hasRatingId);
        }

        @Test
        @DisplayName("Should not add required array if no mandatory fields")
        void testNoRequiredFields() {
            DataDictionaryField field = baseFieldBuilder()
                    .isMandatory(false)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            assertFalse(schema.has("required"));
        }
    }

    @Nested
    @DisplayName("Nested Objects")
    class NestedObjectsTests {

        @Test
        @DisplayName("Should create nested object structure")
        void testNestedObjects() {
            DataDictionaryField field1 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.rating_details.full_rating")
                    .dataType("string")
                    .build();

            DataDictionaryField field2 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.rating_details.rating_date")
                    .dataType("date")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field1, field2))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode properties = schema.get("properties");

            // Verify nested structure
            assertTrue(properties.has("rating_details"));
            JsonNode ratingDetails = properties.get("rating_details");
            assertEquals("object", ratingDetails.get("type").asText());
            assertTrue(ratingDetails.has("properties"));

            JsonNode nestedProps = ratingDetails.get("properties");
            assertTrue(nestedProps.has("full_rating"));
            assertTrue(nestedProps.has("rating_date"));
        }

        @Test
        @DisplayName("Should handle deeply nested objects")
        void testDeeplyNestedObjects() {
            DataDictionaryField field = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.level1.level2.level3.deep_field")
                    .dataType("string")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode properties = schema.get("properties");

            // Navigate through nested structure
            JsonNode level1 = properties.get("level1");
            assertNotNull(level1);
            assertEquals("object", level1.get("type").asText());

            JsonNode level2 = level1.get("properties").get("level2");
            assertNotNull(level2);

            JsonNode level3 = level2.get("properties").get("level3");
            assertNotNull(level3);

            JsonNode deepField = level3.get("properties").get("deep_field");
            assertNotNull(deepField);
            assertEquals("string", deepField.get("type").asText());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for null data type")
        void testNullDataType() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType(null)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> service.generateJsonSchema(entry));
        }

        @Test
        @DisplayName("Should throw exception for empty data type")
        void testEmptyDataType() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> service.generateJsonSchema(entry));
        }

        @Test
        @DisplayName("Should throw exception for invalid numeric precision/scale")
        void testInvalidNumericPrecisionScale() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(5,10)") // scale > precision
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> service.generateJsonSchema(entry));
        }

        @Test
        @DisplayName("Should throw exception for zero precision")
        void testZeroPrecision() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("numeric(0,0)")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> service.generateJsonSchema(entry));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle field with only mandatory name")
        void testMinimalField() {
            DataDictionaryField field = DataDictionaryField.builder()
                    .fieldPath("model_specific_overrides.simple_field")
                    .dataType("string")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            assertNotNull(schema);
            assertTrue(schema.get("properties").has("simple_field"));
        }

        @Test
        @DisplayName("Should handle whitespace in allowed values")
        void testAllowedValuesWithWhitespace() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("string")
                    .allowedValues("  VALUE1  , VALUE2 ,  VALUE3  ")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);
            JsonNode testField = schema.get("properties").get("test_field");

            ArrayNode enumArray = (ArrayNode) testField.get("enum");
            assertEquals(3, enumArray.size());
            assertEquals("VALUE1", enumArray.get(0).asText());
            assertEquals("VALUE2", enumArray.get(1).asText());
            assertEquals("VALUE3", enumArray.get(2).asText());
        }

        @Test
        @DisplayName("Should handle invalid min/max values gracefully")
        void testInvalidMinMaxValues() {
            DataDictionaryField field = baseFieldBuilder()
                    .dataType("integer")
                    .minValue("not-a-number")
                    .maxValue("also-not-a-number")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field))
                    .build();

            // Should not throw, just ignore invalid values
            assertDoesNotThrow(() -> service.generateJsonSchema(entry));
        }

        @Test
        @DisplayName("Should handle case-insensitive data types")
        void testCaseInsensitiveDataTypes() {
            DataDictionaryField field1 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.field1")
                    .dataType("VARCHAR(100)")
                    .build();

            DataDictionaryField field2 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.field2")
                    .dataType("NUMERIC(10,2)")
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field1, field2))
                    .build();

            JsonNode schema = service.generateJsonSchema(entry);

            assertEquals("string", schema.get("properties").get("field1").get("type").asText());
            assertEquals("number", schema.get("properties").get("field2").get("type").asText());
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenariosTests {

        @Test
        @DisplayName("Should generate complete schema for complex entry")
        void testComplexSchemaGeneration() {
            // Create a complex entry with multiple field types
            DataDictionaryField field1 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.country_id")
                    .dataType("bigint")
                    .isForeignKey(true)
                    .foreignKeyTable("country")
                    .foreignKeyColumn("id")
                    .isMandatory(true)
                    .build();

            DataDictionaryField field2 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.rating_details.full_rating")
                    .dataType("string")
                    .dataName("Full Model Rating")
                    .build();

            DataDictionaryField field3 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.rating_details.probability")
                    .dataType("numeric(14,10)")
                    .build();

            DataDictionaryField field4 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.allowed_ratings")
                    .dataType("string[]")
                    .allowedValues("AAA, AA, A, BBB, BB, B")
                    .build();

            DataDictionaryField field5 = baseFieldBuilder()
                    .fieldPath("model_specific_overrides.status")
                    .dataType("string")
                    .allowedValues("ACTIVE, INACTIVE")
                    .defaultValue("ACTIVE")
                    .isMandatory(true)
                    .build();

            DataDictionaryEntry entry = baseEntryBuilder()
                    .fields(List.of(field1, field2, field3, field4, field5))
                    .build();

            // Generate schema
            JsonNode schema = service.generateJsonSchema(entry);

            // Verify structure
            assertNotNull(schema);
            assertTrue(schema.has("properties"));
            assertTrue(schema.has("required"));

            // Verify required fields
            ArrayNode required = (ArrayNode) schema.get("required");
            assertEquals(2, required.size());

            // Verify nested objects
            JsonNode properties = schema.get("properties");
            assertTrue(properties.has("rating_details"));
            assertTrue(properties.has("country_id"));
            assertTrue(properties.has("allowed_ratings"));
            assertTrue(properties.has("status"));

            // Verify foreign key
            JsonNode countryId = properties.get("country_id");
            assertTrue(countryId.has("x-foreign-key"));

            // Verify array
            JsonNode allowedRatings = properties.get("allowed_ratings");
            assertEquals("array", allowedRatings.get("type").asText());

            // Verify enum and default
            JsonNode status = properties.get("status");
            assertTrue(status.has("enum"));
            assertTrue(status.has("default"));
        }
    }
}