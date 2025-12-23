package com.bnpparibas.domain.schema.service;

import com.bnpparibas.domain.datadictionary.value.DataDictionaryEntry;
import com.bnpparibas.domain.datadictionary.value.DataDictionaryField;
import com.bnpparibas.domain.schema.value.DataTypeInfo;
import com.bnpparibas.domain.schema.value.NumericBounds;
import com.bnpparibas.domain.schema.value.SqlDataType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class JsonSchemaGeneratorService {

    private final ObjectMapper objectMapper;

    /**
     * Pattern to extract length from varchar(n) or char(n)
     * Example: varchar(255) -> groups: [varchar, 255]
     */
    private static final Pattern LENGTH_PATTERN = Pattern.compile("(varchar|char)\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to extract precision and scale from numeric(p,s)
     * Example: numeric(14,10) -> groups: [14, 10]
     */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("numeric\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to detect array types
     * Example: string[] -> group: [string]
     */
    private static final Pattern ARRAY_PATTERN = Pattern.compile("(.+)\\[\\s*\\]");

    /**
     * Generate JSON Schema 2020-12 from Data Dictionary Entry
     */
    public JsonNode generateJsonSchema(DataDictionaryEntry dataDictionaryEntry) {
        dataDictionaryEntry.validate();

        log.info("Generating JSON Schema 2020-12 for {}-{}-{}",
                dataDictionaryEntry.ratingModel().modelName(),
                dataDictionaryEntry.ratingModelVersion().version(),
                dataDictionaryEntry.ratingMechanism().getValue());

        ObjectNode schema = buildSchemaFromFields(dataDictionaryEntry);

        log.debug("Generated schema: {}", schema.toPrettyString());
        return schema;
    }

    private ObjectNode buildSchemaFromFields(DataDictionaryEntry dataDictionaryEntry) {
        ObjectNode schema = objectMapper.createObjectNode();

        // Schema metadata - JSON Schema 2020-12
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$id", buildSchemaId(dataDictionaryEntry));
        schema.put("type", "object");
        schema.put("title", buildSchemaTitle(dataDictionaryEntry));
        schema.put("description", buildSchemaDescription(dataDictionaryEntry));

        // Build properties from JSONB fields
        ObjectNode properties = objectMapper.createObjectNode();
        Set<String> requiredFields = new HashSet<>();

        List<DataDictionaryField> jsonbFields = dataDictionaryEntry.getJsonbFields();

        for (DataDictionaryField field : jsonbFields) {
            String jsonbPath = field.getJsonbPath();
            addFieldToSchemaProperties(properties, jsonbPath, field);

            // Track required fields at top level
            if (Boolean.TRUE.equals(field.isMandatory())) {
                String topLevelField = extractTopLevelFieldName(jsonbPath);
                requiredFields.add(topLevelField);
            }
        }

        schema.set("properties", properties);

        // Add required array if there are required fields
        if (!requiredFields.isEmpty()) {
            ArrayNode required = objectMapper.createArrayNode();
            requiredFields.stream().sorted().forEach(required::add);
            schema.set("required", required);
        }

        // Disallow additional properties for strict validation
        schema.put("additionalProperties", false);

        return schema;
    }

    private void addFieldToSchemaProperties(ObjectNode properties, String jsonbPath, DataDictionaryField field) {
        String[] pathParts = jsonbPath.split("\\.");

        if (pathParts.length == 1) {
            // Root level field
            properties.set(pathParts[0], createFieldDefinition(field));
        } else {
            // Nested field - build hierarchy
            ObjectNode current = properties;

            for (int i = 0; i < pathParts.length - 1; i++) {
                String part = pathParts[i];

                if (!current.has(part)) {
                    // Create nested object structure
                    ObjectNode nestedObject = objectMapper.createObjectNode();
                    nestedObject.put("type", "object");
                    nestedObject.set("properties", objectMapper.createObjectNode());
                    current.set(part, nestedObject);
                }

                JsonNode node = current.get(part);
                if (node.has("properties")) {
                    current = (ObjectNode) node.get("properties");
                } else {
                    log.warn("Path conflict at: {} in path {}", part, jsonbPath);
                    return;
                }
            }

            // Add the final field
            String finalFieldName = pathParts[pathParts.length - 1];
            current.set(finalFieldName, createFieldDefinition(field));
        }
    }

    private ObjectNode createFieldDefinition(DataDictionaryField field) {
        ObjectNode fieldDef = objectMapper.createObjectNode();

        String dataType = field.dataType();

        // Parse the data type
        DataTypeInfo typeInfo = parseDataType(dataType);

        // Set the JSON Schema type
        fieldDef.put("type", typeInfo.jsonType());

        // Add title (business name)
        if (field.dataName() != null && !field.dataName().trim().isEmpty()) {
            fieldDef.put("title", field.dataName());
        }

        // Add description
        String description = buildFieldDescription(field);
        if (description != null && !description.trim().isEmpty()) {
            fieldDef.put("description", description);
        }

        // Handle based on type
        switch (typeInfo.jsonType()) {
            case "string":
                addStringConstraints(fieldDef, field, typeInfo);
                break;
            case "number":
            case "integer":
                addNumericConstraints(fieldDef, field, typeInfo);
                break;
            case "array":
                addArrayDefinition(fieldDef, field, typeInfo);
                break;
            case "object":
                // For jsonb type
                fieldDef.put("additionalProperties", true);
                break;
        }

        // Add enum for allowed values
        if (field.allowedValues() != null && !field.allowedValues().trim().isEmpty()) {
            addAllowedValues(fieldDef, field.allowedValues(), typeInfo);
        }

        // Add default value
        if (field.defaultValue() != null && !field.defaultValue().trim().isEmpty()) {
            addDefaultValue(fieldDef, field.defaultValue(), typeInfo);
        }

        // Add foreign key metadata (custom extension)
        if (Boolean.TRUE.equals(field.isForeignKey())) {
            ObjectNode foreignKeyInfo = objectMapper.createObjectNode();
            foreignKeyInfo.put("table", field.foreignKeyTable());
            foreignKeyInfo.put("column", field.foreignKeyColumn());
            fieldDef.set("x-foreign-key", foreignKeyInfo);
        }

        // Add custom extension for original SQL type
        fieldDef.put("x-sql-type", dataType);

        return fieldDef;
    }

    private DataTypeInfo parseDataType(String dataType) {
        if (dataType == null || dataType.trim().isEmpty()) {
            throw new IllegalArgumentException("Data type cannot be null or empty");
        }

        String normalized = dataType.trim();

        // Check for array types: string[], bigint[], numeric(14,10)[], etc.
        Matcher arrayMatcher = ARRAY_PATTERN.matcher(normalized);
        if (arrayMatcher.matches()) {
            String elementTypeStr = arrayMatcher.group(1).trim();
            DataTypeInfo elementType = parseDataType(elementTypeStr);
            return DataTypeInfo.array(elementType);
        }

        // Check for numeric with precision and scale: numeric(14,10), decimal(10,2)
        Matcher numericMatcher = NUMERIC_PATTERN.matcher(normalized);
        if (numericMatcher.matches()) {
            int precision = Integer.parseInt(numericMatcher.group(1));
            int scale = Integer.parseInt(numericMatcher.group(2));

            validateNumericPrecisionScale(precision, scale, normalized);

            // Determine if it's numeric or decimal
            String baseType = normalized.split("\\(")[0].trim();
            SqlDataType sqlType = SqlDataType.fromString(baseType);

            return DataTypeInfo.numeric(sqlType, precision, scale);
        }

        // Check for char/varchar with length: varchar(255), char(10)
        Matcher lengthMatcher = LENGTH_PATTERN.matcher(normalized);
        if (lengthMatcher.matches()) {
            String baseType = lengthMatcher.group(1);
            int length = Integer.parseInt(lengthMatcher.group(2));

            // ADD VALIDATION
            if (length <= 0) {
                throw new IllegalArgumentException(
                        String.format("Length must be positive for type: %s", normalized)
                );
            }
            if (length > 10485760) { // PostgreSQL varchar limit
                log.warn("Length {} exceeds PostgreSQL varchar limit for type: {}", length, normalized);
            }

            SqlDataType sqlType = SqlDataType.fromString(baseType);
            return DataTypeInfo.stringWithLength(sqlType, length);
        }

        // Simple type without parameters
        SqlDataType sqlType = SqlDataType.fromString(normalized);
        return DataTypeInfo.simple(sqlType);
    }

    private void validateNumericPrecisionScale(int precision, int scale, String originalType) {
        if (precision <= 0) {
            throw new IllegalArgumentException(
                    String.format("Precision must be positive for type: %s", originalType)
            );
        }
        if (scale < 0) {
            throw new IllegalArgumentException(
                    String.format("Scale cannot be negative for type: %s", originalType)
            );
        }
        if (scale > precision) {
            throw new IllegalArgumentException(
                    String.format("Scale (%d) cannot be greater than precision (%d) for type: %s",
                            scale, precision, originalType)
            );
        }
        if (precision > 1000) { // PostgreSQL limit
            log.warn("Precision {} exceeds typical PostgreSQL limit of 1000 for type: {}",
                    precision, originalType);
        }
    }

    private void addStringConstraints(ObjectNode fieldDef, DataDictionaryField field, DataTypeInfo typeInfo) {
        // Add format if specified in type info
        if (typeInfo.jsonFormat() != null) {
            fieldDef.put("format", typeInfo.jsonFormat());
        }

        // Add length constraint from type definition (e.g., varchar(255))
        if (typeInfo.length() != null) {
            fieldDef.put("maxLength", typeInfo.length());
        }

        // Override with explicit length from data dictionary if provided
        if (field.length() != null && field.length() > 0) {
            fieldDef.put("maxLength", field.length());
        }

        // Add minLength if specified in minValue
        if (field.minValue() != null && !field.minValue().trim().isEmpty()) {
            try {
                int minLength = Integer.parseInt(field.minValue());
                if (minLength > 0) {
                    fieldDef.put("minLength", minLength);
                }
            } catch (NumberFormatException ignored) {
                // Not a valid length, ignore
            }
        }

        // Add pattern for specific types
        if (typeInfo.sqlDataType() != null) {
            switch (typeInfo.sqlDataType()) {
                case UUID:
                    // UUID pattern: 8-4-4-4-12 hex digits
                    fieldDef.put("pattern", "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                    break;
                case INET:
                    // IPv4 or IPv6 pattern
                    fieldDef.put("pattern", "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
                    break;
                case MACADDR:
                case MACADDR8:
                    // MAC address pattern
                    fieldDef.put("pattern", "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
                    break;
            }
        }
    }

    private void addNumericConstraints(ObjectNode fieldDef, DataDictionaryField field, DataTypeInfo typeInfo) {
        // If we have precision and scale from numeric(p,s), calculate bounds
        if (typeInfo.precision() != null && typeInfo.scale() != null) {
            NumericBounds bounds = calculateNumericBounds(typeInfo.precision(), typeInfo.scale());

            fieldDef.put("minimum", bounds.minimum());
            fieldDef.put("maximum", bounds.maximum());

            // Add custom extensions for precision and scale
            fieldDef.put("x-numeric-precision", typeInfo.precision());
            fieldDef.put("x-numeric-scale", typeInfo.scale());

            log.debug("Numeric field {} with precision={}, scale={}, bounds=[{}, {}]",
                    field.fieldPath(), typeInfo.precision(), typeInfo.scale(),
                    bounds.minimum(), bounds.maximum());
        }

        // Override with explicit min/max from data dictionary if provided
        if (field.minValue() != null && !field.minValue().trim().isEmpty()) {
            try {
                double min = Double.parseDouble(field.minValue());
                fieldDef.put("minimum", min);
            } catch (NumberFormatException e) {
                log.warn("Invalid minimum value for field {}: {}", field.fieldPath(), field.minValue());
            }
        }

        if (field.maxValue() != null && !field.maxValue().trim().isEmpty()) {
            try {
                double max = Double.parseDouble(field.maxValue());
                fieldDef.put("maximum", max);
            } catch (NumberFormatException e) {
                log.warn("Invalid maximum value for field {}: {}", field.fieldPath(), field.maxValue());
            }
        }
    }

    private void addArrayDefinition(ObjectNode fieldDef, DataDictionaryField field, DataTypeInfo typeInfo) {
        // Get the element type info
        DataTypeInfo elementTypeInfo = typeInfo.arrayElementType();

        if (elementTypeInfo != null) {
            ObjectNode items = objectMapper.createObjectNode();
            items.put("type", elementTypeInfo.jsonType());

            // If element type is numeric with precision/scale
            if (elementTypeInfo.precision() != null && elementTypeInfo.scale() != null) {
                NumericBounds bounds = calculateNumericBounds(
                        elementTypeInfo.precision(),
                        elementTypeInfo.scale()
                );
                items.put("minimum", bounds.minimum());
                items.put("maximum", bounds.maximum());
                items.put("x-numeric-precision", elementTypeInfo.precision());
                items.put("x-numeric-scale", elementTypeInfo.scale());
            }

            // If there are allowed values, add them as enum for array items
            if (field.allowedValues() != null && !field.allowedValues().trim().isEmpty()) {
                ArrayNode enumValues = objectMapper.createArrayNode();
                String[] values = field.allowedValues().split(",");
                for (String value : values) {
                    String trimmed = value.trim();
                    addEnumValue(enumValues, trimmed, elementTypeInfo);
                }
                if (enumValues.size() > 0) {
                    items.set("enum", enumValues);
                }
            }

            fieldDef.set("items", items);
        } else {

            log.warn("Array type without element type info for field: {}, defaulting to string[]",
                    field.fieldPath());

            // Fallback: default to string array
            ObjectNode items = objectMapper.createObjectNode();
            items.put("type", "string");
            fieldDef.set("items", items);
        }

        // Arrays should have unique items (no duplicates)
        fieldDef.put("uniqueItems", true);

        // Add array length constraints if specified
        if (field.minValue() != null && !field.minValue().trim().isEmpty()) {
            try {
                int minItems = Integer.parseInt(field.minValue());
                if (minItems >= 0) {
                    fieldDef.put("minItems", minItems);
                }
            } catch (NumberFormatException ignored) {
                // Not a valid array length
            }
        }

        if (field.maxValue() != null && !field.maxValue().trim().isEmpty()) {
            try {
                int maxItems = Integer.parseInt(field.maxValue());
                if (maxItems > 0) {
                    fieldDef.put("maxItems", maxItems);
                }
            } catch (NumberFormatException ignored) {
                // Not a valid array length
            }
        }
    }

    private void addAllowedValues(ObjectNode fieldDef, String allowedValues, DataTypeInfo typeInfo) {
        ArrayNode enumArray = objectMapper.createArrayNode();
        String[] values = allowedValues.split(",");

        for (String value : values) {
            String trimmed = value.trim();
            addEnumValue(enumArray, trimmed, typeInfo);
        }

        if (enumArray.size() > 0) {
            fieldDef.set("enum", enumArray);
        }
    }

    private void addEnumValue(ArrayNode enumArray, String value, DataTypeInfo typeInfo) {
        try {
            switch (typeInfo.jsonType()) {
                case "integer":
                    enumArray.add(Long.parseLong(value));
                    break;
                case "number":
                    enumArray.add(new BigDecimal(value));
                    break;
                case "boolean":
                    enumArray.add(Boolean.parseBoolean(value));
                    break;
                default:
                    enumArray.add(value);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid enum value '{}' for type {}, using as string", value, typeInfo.jsonType());
            enumArray.add(value);
        }
    }

    private void addDefaultValue(ObjectNode fieldDef, String defaultValue, DataTypeInfo typeInfo) {
        try {
            switch (typeInfo.jsonType()) {
                case "integer":
                    fieldDef.put("default", Long.parseLong(defaultValue));
                    break;
                case "number":
                    fieldDef.put("default", new BigDecimal(defaultValue));
                    break;
                case "boolean":
                    fieldDef.put("default", Boolean.parseBoolean(defaultValue));
                    break;
                case "array":
                    if ("[]".equals(defaultValue.trim())) {
                        fieldDef.set("default", objectMapper.createArrayNode());
                    }
                    break;
                case "object":
                    if ("{}".equals(defaultValue.trim())) {
                        fieldDef.set("default", objectMapper.createObjectNode());
                    }
                    break;
                default:
                    fieldDef.put("default", defaultValue);
            }
        } catch (Exception e) {
            log.warn("Failed to parse default value '{}' for type {}, using as string",
                    defaultValue, typeInfo.jsonType());
            fieldDef.put("default", defaultValue);
        }
    }

    /**
     * Calculate numeric bounds based on precision and scale
     * For numeric(14, 10): precision=14, scale=10
     * Max digits before decimal = 14 - 10 = 4
     * Max value = 9999.9999999999
     * Min value = -9999.9999999999
     */
    private NumericBounds calculateNumericBounds(int precision, int scale) {

        if (precision <= 0 || scale < 0 || scale > precision) {
            throw new IllegalArgumentException(
                    String.format("Invalid numeric type: precision=%d, scale=%d", precision, scale)
            );
        }

        // Calculate maximum number of digits before the decimal point
        int integerDigits = precision - scale;

        if (integerDigits <= 0) {
            // All digits are after decimal (e.g., numeric(5,5) = 0.99999)
            BigDecimal max = BigDecimal.ONE.subtract(
                    BigDecimal.ONE.divide(BigDecimal.TEN.pow(scale), scale, RoundingMode.DOWN)
            );
            return new NumericBounds(max.negate().doubleValue(), max.doubleValue());
        }

        if (precision > 308) {  // Double.MAX_VALUE is ~10^308
            log.warn("Precision {} may cause overflow when converting to double. Consider using String validation.", precision);
        }

        // Build max value: e.g., for (14,10) -> 9999.9999999999
        StringBuilder maxValueStr = new StringBuilder();

        // Integer part: integerDigits number of 9s
        for (int i = 0; i < integerDigits; i++) {
            maxValueStr.append("9");
        }

        // Decimal part: scale number of 9s
        if (scale > 0) {
            maxValueStr.append(".");
            for (int i = 0; i < scale; i++) {
                maxValueStr.append("9");
            }
        }

        BigDecimal max = new BigDecimal(maxValueStr.toString());
        BigDecimal min = max.negate();

        return new NumericBounds(min.doubleValue(), max.doubleValue());
    }

    private String buildSchemaId(DataDictionaryEntry entry) {
        return String.format("https://bnpparibas.com/schemas/%s/%s/%s/model-specific-overrides.json",
                entry.ratingModel().modelName().toLowerCase(),
                entry.ratingModelVersion().version(),
                entry.ratingMechanism().getValue().toLowerCase());
    }

    private String buildSchemaTitle(DataDictionaryEntry entry) {
        return String.format("%s %s %s Model Specific Overrides Schema",
                entry.ratingModel().modelName(),
                entry.ratingModelVersion().version(),
                entry.ratingMechanism().getValue());
    }

    private String buildSchemaDescription(DataDictionaryEntry entry) {
        return String.format(
                "JSON Schema (2020-12) for model_specific_overrides JSONB field. " +
                        "Defines the structure and validation rules for rating model %s version %s using %s mechanism.",
                entry.ratingModel().modelName(),
                entry.ratingModelVersion().version(),
                entry.ratingMechanism().getValue()
        );
    }

    private String buildFieldDescription(DataDictionaryField field) {
        StringBuilder desc = new StringBuilder();

        if (field.dataDefinition() != null && !field.dataDefinition().trim().isEmpty()) {
            desc.append(field.dataDefinition());
        }

        if (field.fieldDescription() != null && !field.fieldDescription().trim().isEmpty()) {
            if (desc.length() > 0) {
                desc.append(" ");
            }
            desc.append(field.fieldDescription());
        }

        return desc.length() > 0 ? desc.toString() : null;
    }

    private String extractTopLevelFieldName(String jsonbPath) {
        return jsonbPath.split("\\.")[0];
    }

}
