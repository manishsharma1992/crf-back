package com.bnpparibas.domain.schema.aggregate;

import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import com.bnpparibas.domain.schema.value.SchemaVersion;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "json_schema_registry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JsonSchemaRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverride(name = "modelName", column = @Column(name = "rating_model", nullable = false, length = 50))
    private RatingModel ratingModel;

    @Embedded
    @AttributeOverride(name = "version", column = @Column(name = "rating_model_version", nullable = false, length = 20))
    private RatingModelVersion ratingModelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_model_mechanism", nullable = false, length = 20)
    private RatingMechanism ratingMechanism;

    @Embedded
    @AttributeOverride(name = "versionNumber", column = @Column(name = "schema_version", nullable = false))
    private SchemaVersion schemaVersion;

    @Type(JsonBinaryType.class)
    @Column(name = "json_schema", nullable = false, columnDefinition = "jsonb")
    private JsonNode jsonSchema;

    @Column(name = "json_schema_standard", nullable = false, length = 20)
    private String jsonSchemaStandard;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "change_notes", columnDefinition = "text")
    private String changeNotes;

    @Column(name = "validation_notes", columnDefinition = "text")
    private String validationNotes;

    public static JsonSchemaRegistry createNew(
            RatingModel ratingModel,
            RatingModelVersion ratingModelVersion,
            RatingMechanism ratingMechanism,
            JsonNode jsonSchema,
            String description
    ) {
        JsonSchemaRegistry registry = new JsonSchemaRegistry();

        registry.ratingModel = ratingModel;
        registry.ratingModelVersion = ratingModelVersion;
        registry.ratingMechanism = ratingMechanism;
        registry.schemaVersion = SchemaVersion.initial();
        registry.jsonSchema = jsonSchema;
        registry.jsonSchemaStandard = "draft-07";
        registry.active = true;
        registry.effectiveFrom = LocalDateTime.now();
        registry.description = description;

        registry.validate();
        return registry;
    }

    public JsonSchemaRegistry createNewVersion(
            JsonNode newJsonSchema,
            String changeNotes) {

        JsonSchemaRegistry newVersion = new JsonSchemaRegistry();
        newVersion.ratingModel = this.ratingModel;
        newVersion.ratingModelVersion = this.ratingModelVersion;
        newVersion.ratingMechanism = this.ratingMechanism;
        newVersion.schemaVersion = this.schemaVersion.increment();
        newVersion.jsonSchema = newJsonSchema;
        newVersion.jsonSchemaStandard = this.jsonSchemaStandard;
        newVersion.active = true;
        newVersion.effectiveFrom = LocalDateTime.now();
        newVersion.description = this.description;
        newVersion.changeNotes = changeNotes;

        newVersion.validate();
        return newVersion;
    }

    public void deprecate() {
        if (!this.active) {
            throw new IllegalStateException("Schema is already deprecated");
        }
        this.active = false;
        this.effectiveTo = LocalDateTime.now();
    }

    private void validate() {
        if (ratingModel == null) {
            throw new IllegalArgumentException("Rating model is required");
        }
        if (ratingModelVersion == null) {
            throw new IllegalArgumentException("Rating model version is required");
        }
        if (ratingMechanism == null) {
            throw new IllegalArgumentException("Rating mechanism is required");
        }
        if (schemaVersion == null) {
            throw new IllegalArgumentException("Schema version is required");
        }
        if (jsonSchema == null) {
            throw new IllegalArgumentException("JSON schema is required");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from date is required");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("Effective to date must be after effective from date");
        }
    }
}
