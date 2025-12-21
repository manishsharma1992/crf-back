package com.bnpparibas.domain.schema.value;

public record DataTypeInfo(
        String jsonType,              // "string", "number", "integer", "boolean", "array", "object"
        String jsonFormat,            // "date", "date-time", "time", "uuid", "email", etc.
        DataTypeInfo arrayElementType, // For array types, the element type
        Integer precision,            // For numeric(p,s), the precision (p)
        Integer scale,                // For numeric(p,s), the scale (s)
        Integer length,               // For varchar(n), char(n), the length (n)
        SqlDataType sqlDataType      // Original SQL type enum
) {

    /**
     * Factory method for simple types
     */
    public static DataTypeInfo simple(SqlDataType sqlType) {
        return new DataTypeInfo(
                sqlType.getJsonSchemaType(),
                sqlType.getJsonSchemaFormat(),
                null,
                null,
                null,
                null,
                sqlType
        );
    }

    /**
     * Factory method for numeric with precision/scale
     */
    public static DataTypeInfo numeric(SqlDataType sqlType, int precision, int scale) {
        return new DataTypeInfo(
                sqlType.getJsonSchemaType(),
                sqlType.getJsonSchemaFormat(),
                null,
                precision,
                scale,
                null,
                sqlType
        );
    }

    /**
     * Factory method for string with length
     */
    public static DataTypeInfo stringWithLength(SqlDataType sqlType, int length) {
        return new DataTypeInfo(
                sqlType.getJsonSchemaType(),
                sqlType.getJsonSchemaFormat(),
                null,
                null,
                null,
                length,
                sqlType
        );
    }

    /**
     * Factory method for array types
     */
    public static DataTypeInfo array(DataTypeInfo elementType) {
        return new DataTypeInfo(
                "array",
                null,
                elementType,
                null,
                null,
                null,
                null
        );
    }
}
