package com.bnpparibas.domain.schema.repository;

import com.bnpparibas.domain.schema.aggregate.JsonSchemaRegistry;
import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;

import java.util.List;
import java.util.Optional;


public interface JsonSchemaRegistryRepository {

    /**
     * Find active schema for specific model/version/mechanism
     */
    Optional<JsonSchemaRegistry> findByRatingModelAndRatingModelVersionAndRatingMechanismAndActiveTrue(
            RatingModel ratingModel,
            RatingModelVersion ratingModelVersion,
            RatingMechanism ratingMechanism
    );

    /**
     * Find all active schemas
     */
    List<JsonSchemaRegistry> findByActiveTrue();

    /**
     * Find all versions for a specific model/version/mechanism
     */
    List<JsonSchemaRegistry> findByRatingModelAndRatingModelVersionAndRatingMechanismOrderBySchemaVersionDesc(
            RatingModel ratingModel,
            RatingModelVersion ratingModelVersion,
            RatingMechanism ratingMechanism
    );

    JsonSchemaRegistry save(JsonSchemaRegistry jsonSchemaRegistry);
}
