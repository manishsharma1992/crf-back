package com.bnpparibas.domain.datadictionary.value;

import com.bnpparibas.domain.schema.value.RatingMechanism;
import com.bnpparibas.domain.schema.value.RatingModel;
import com.bnpparibas.domain.schema.value.RatingModelVersion;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Builder
public record DataDictionaryEntry(
        RatingModel ratingModel,
        RatingModelVersion ratingModelVersion,
        RatingMechanism ratingMechanism,
        List<DataDictionaryField> fields
) {
    public void validate() {
        if (ratingModel == null) {
            throw new IllegalArgumentException("Rating model is required");
        }
        if (ratingModelVersion == null) {
            throw new IllegalArgumentException("Rating model version is required");
        }
        if (ratingMechanism == null) {
            throw new IllegalArgumentException("Rating mechanism is required");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("At least one field is required");
        }

        // Validate all fields
        fields.forEach(DataDictionaryField::validate);
    }

    public List<DataDictionaryField> getJsonbFields() {
        return fields.stream()
                .filter(DataDictionaryField::isInJsonb)
                .collect(Collectors.toList());
    }

    public Map<String, DataDictionaryField> getFieldsByPath() {
        return fields.stream()
                .collect(Collectors.toMap(
                        DataDictionaryField::fieldPath,
                        field -> field
                ));
    }
}
