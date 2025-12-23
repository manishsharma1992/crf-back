package com.bnpparibas.infrastructure.schema;

import com.bnpparibas.domain.schema.aggregate.JsonSchemaRegistry;
import com.bnpparibas.domain.schema.repository.JsonSchemaRegistryRepository;
import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class JsonSchemaRegistryRepositoryImpl implements JsonSchemaRegistryRepository {

    private final JsonSchemaRegistryDAO  jsonSchemaRegistryDAO;

    @Override
    public Optional<JsonSchemaRegistry> findByRatingModelAndRatingModelVersionAndRatingMechanismAndActiveTrue(RatingModel ratingModel, RatingModelVersion ratingModelVersion, RatingMechanism ratingMechanism) {
        return jsonSchemaRegistryDAO.findByRatingModelAndRatingModelVersionAndRatingMechanismAndActiveTrue(ratingModel, ratingModelVersion, ratingMechanism);
    }

    @Override
    public List<JsonSchemaRegistry> findByActiveTrue() {
        return jsonSchemaRegistryDAO.findByActiveTrue();
    }

    @Override
    public List<JsonSchemaRegistry> findByRatingModelAndRatingModelVersionAndRatingMechanismOrderBySchemaVersionDesc(RatingModel ratingModel, RatingModelVersion ratingModelVersion, RatingMechanism ratingMechanism) {
        return jsonSchemaRegistryDAO.findByRatingModelAndRatingModelVersionAndRatingMechanismOrderBySchemaVersionDesc(ratingModel, ratingModelVersion, ratingMechanism);
    }

    @Override
    public JsonSchemaRegistry save(JsonSchemaRegistry jsonSchemaRegistry) {
        return jsonSchemaRegistryDAO.save(jsonSchemaRegistry);
    }
}
