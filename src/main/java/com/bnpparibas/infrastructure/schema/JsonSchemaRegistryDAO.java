package com.bnpparibas.infrastructure.schema;

import com.bnpparibas.domain.schema.aggregate.JsonSchemaRegistry;
import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JsonSchemaRegistryDAO extends JpaRepository<JsonSchemaRegistry, Long> {

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
}
