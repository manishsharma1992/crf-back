package com.bnpparibas.domain.schema.value;

public record SchemaVersion(Integer versionNumber) {

    public SchemaVersion {
        if (versionNumber == null || versionNumber < 1) {
            throw new IllegalArgumentException("Schema Version must be a positive integer");
        }
    }

    public static SchemaVersion initial() {
        return new SchemaVersion(1);
    }

    public SchemaVersion increment() {
        return new SchemaVersion(this.versionNumber + 1);
    }

    public static SchemaVersion of(Integer versionNumber) {
        return new SchemaVersion(versionNumber);
    }


}
