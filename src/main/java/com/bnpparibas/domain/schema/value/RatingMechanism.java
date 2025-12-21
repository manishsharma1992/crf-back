package com.bnpparibas.domain.schema.value;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum RatingMechanism {

    STANDALONE("STANDALONE"),
    INHERITANCE("INHERITANCE"),
    PROPAGATION("PROPAGATION");
    private final String value;

    RatingMechanism(String value) {
        this.value = value;
    }

    public static RatingMechanism fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Rating Mechanism cannot be null or empty");
        }

        for (RatingMechanism ratingMechanism : values()) {
            if(ratingMechanism.getValue().equalsIgnoreCase(value.trim())) {
                return ratingMechanism;
            }
        }

        throw new IllegalArgumentException(
                String.format("Invalid rating mechanism: %s. Valid values are: %s", value, Arrays.toString(values()))
        );
    }
}
