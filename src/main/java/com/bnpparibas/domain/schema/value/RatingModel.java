package com.bnpparibas.domain.schema.value;

public record RatingModel(String modelName) {

    public RatingModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("Rating Model cannot be null or empty");
        }

        if (modelName.length() > 50) {
            throw new IllegalArgumentException("Rating Model cannot be greater than 50 characters");
        }

        this.modelName = modelName.trim().toUpperCase();
    }

    public static RatingModel of(String modelName) {
        return new RatingModel(modelName);
    }
}
