package com.bnpparibas.domain.schema.value;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public enum SqlDataType {

    // ========== Integer Types ==========
    SMALLINT("smallint", "integer", null),
    INTEGER("integer", "integer", null),
    INT("int", "integer", null),
    BIGINT("bigint", "integer", null),
    SERIAL("serial", "integer", null),
    BIGSERIAL("bigserial", "integer", null),

    // ========== Numeric/Decimal Types ==========
    NUMERIC("numeric", "number", null),  // Can have precision: numeric(14,10)
    DECIMAL("decimal", "number", null),   // Can have precision: decimal(14,10)
    REAL("real", "number", null),
    DOUBLE_PRECISION("double precision", "number", null),
    FLOAT("float", "number", null),
    MONEY("money", "number", null),

    // ========== String/Character Types ==========
    CHAR("char", "string", null),
    VARCHAR("varchar", "string", null),
    TEXT("text", "string", null),
    STRING("string", "string", null),

    // ========== Boolean Type ==========
    BOOLEAN("boolean", "boolean", null),
    BOOL("bool", "boolean", null),

    // ========== Date/Time Types ==========
    DATE("date", "string", "date"),
    TIME("time", "string", "time"),
    TIMESTAMP("timestamp", "string", "date-time"),
    TIMESTAMPTZ("timestamptz", "string", "date-time"),
    TIMESTAMP_WITH_TIME_ZONE("timestamp with time zone", "string", "date-time"),
    INTERVAL("interval", "string", null),

    // ========== UUID ==========
    UUID("uuid", "string", "uuid"),

    // ========== JSON Types ==========
    JSON("json", "object", null),
    JSONB("jsonb", "object", null),

    // ========== Binary Data ==========
    BYTEA("bytea", "string", "byte"),

    // ========== Network Address Types ==========
    INET("inet", "string", "ipv4"),  // IP address
    CIDR("cidr", "string", null),    // Network address
    MACADDR("macaddr", "string", null), // MAC address
    MACADDR8("macaddr8", "string", null), // MAC address (EUI-64 format)

    // ========== Bit String Types ==========
    BIT("bit", "string", null),
    BIT_VARYING("bit varying", "string", null),

    // ========== Text Search Types ==========
    TSVECTOR("tsvector", "string", null),
    TSQUERY("tsquery", "string", null),

    // ========== XML Type ==========
    XML("xml", "string", null),

    // ========== Range Types ==========
    INT4RANGE("int4range", "object", null),
    INT8RANGE("int8range", "object", null),
    NUMRANGE("numrange", "object", null),
    TSRANGE("tsrange", "object", null),
    TSTZRANGE("tstzrange", "object", null),
    DATERANGE("daterange", "object", null),

    // ========== Geometric Types (for spatial data) ==========
    POINT("point", "object", null),
    LINE("line", "object", null),
    LSEG("lseg", "object", null),
    BOX("box", "object", null),
    PATH("path", "object", null),
    POLYGON("polygon", "object", null),
    CIRCLE("circle", "object", null),

    // ========== Array Types (handled specially in parsing) ==========
    // Arrays are detected by [] suffix, e.g., "string[]", "bigint[]"
    ;

    private final String sqlTypeName;
    private final String jsonSchemaType;
    private final String jsonSchemaFormat;

    public static SqlDataType fromString(String value) {
        if(value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL data type cannot be null or empty");
        }

        String normalized = value.trim().toLowerCase();

        // Check for exact matches first
        for (SqlDataType type : values()) {
            if (type.sqlTypeName.equalsIgnoreCase(normalized)) {
                return type;
            }
        }

        // Check for parameterized types (e.g., varchar(255), numeric(14,10))
        String baseType = normalized.split("\\(")[0].trim();
        for (SqlDataType type : values()) {
            if (type.sqlTypeName.equalsIgnoreCase(baseType)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                String.format("Unknown SQL data type: %s. Valid types are: %s",
                        value, getValidTypesList())
        );
    }

    public static String getValidTypesList() {
        return Arrays.stream(values())
                .map(SqlDataType::getSqlTypeName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Get comma-separated list for Excel data validation dropdown
     * Excludes aliases and includes array notation examples
     */
    public static String getExcelValidationList() {
        return String.join(",",
                // Integer types
                "smallint", "integer", "bigint", "serial", "bigserial",

                // Numeric types with examples
                "numeric", "numeric(p,s)", "decimal", "decimal(p,s)", "real", "double precision", "float", "money",

                // String types with examples
                "char", "char(n)", "varchar", "varchar(n)", "text", "string",

                // Boolean
                "boolean",

                // Date/Time
                "date", "time", "timestamp", "timestamptz", "timestamp with time zone", "interval",

                // UUID
                "uuid",

                // JSON
                "json", "jsonb",

                // Binary
                "bytea",

                // Network
                "inet", "cidr", "macaddr", "macaddr8",

                // Bit
                "bit", "bit varying",

                // Text Search
                "tsvector", "tsquery",

                // XML
                "xml",

                // Range types
                "int4range", "int8range", "numrange", "tsrange", "tstzrange", "daterange",

                // Geometric
                "point", "line", "lseg", "box", "path", "polygon", "circle",

                // Array examples
                "string[]", "bigint[]", "integer[]", "numeric[]", "date[]", "timestamp[]", "boolean[]", "jsonb[]"
        );
    }
}
