package com.bnpparibas.domain.schema.value;


public record RatingModelVersion(String version) {

    public RatingModelVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Rating Model Version cannot be null or empty");
        }

        if (version.length() > 20) {
            throw new IllegalArgumentException("Rating Model Version cannot be longer than 20 characters");
        }
        this.version = version.trim();
    }

    public static RatingModelVersion of(String version) {
        return new RatingModelVersion(version);
    }
}
