package com.bnpparibas.domain.rating.aggregate;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "counterparty_rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounterPartyRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the counterparty this rating belongs to
     */
    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    /**
     * Model-specific override data stored as JSONB
     * Structure validated against JSON Schema from json_schema_registry
     *
     * Contains fields like:
     * - main_industry_id
     * - country_of_business_id
     * - rating_details
     * - financial_drivers
     * - override_rating_details
     * etc.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "model_specific_overrides", nullable = false, columnDefinition = "jsonb")
    private JsonNode modelSpecificOverrides;


}
