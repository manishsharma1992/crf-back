-- File: src/main/resources/db/migration/V2.0__cleanup_counterparty_rating_and_its_child_table.sql

-- ============================================================================
-- Migration: Cleanup counterparty_rating table
-- Description: Remove all columns except core fields + JSONB + audit fields
--
-- Keeps:
--   - id (PK)
--   - counterparty_id (FK)
--   - model_specific_overrides (JSONB)
--   - created_by, created_timestamp (audit)
--   - modified_by, modified_timestamp (audit)
--
-- Removes: All other legacy columns
-- ============================================================================

DO $$
DECLARE
   column_name TEXT;
   columns_to_drop TEXT[] := ARRAY[
                    'main_industry_id',
                    'main_industry_override_comment',
                    'country_of_business_id',
                    'country_of_business_override_comment',
                    'main_geographical_zone_of_sales',
                    'has_eligible_support_entity',
                    'reference_currency_id',
                    'closing_date',
                    'full_model_rating_date',
                    'full_model_rating',
                    'market_position',
                    'leverage',
                    'coverage',
                    'gearing',
                    'liquidity',
                    'sales',
                    'full_model_probability_of_default',
                    'model_adjusted_market_position',
                    'support_counterparty_rating_id',
                    'support_score',
                    'sector_penalty_id',
                    'ipi_standard_deviation_id',
                    'override_rating',
                    'override_probability_of_default',
                    'override_rating_date',
                    'override_rating_comment',
                    'override_token',
                    'token_rationale',
                    'impact_on_rating',
                    'is_esg_driver_liability_arisen',
                    'is_large_override_validated_by_cro_or_delegate',
                    'comments',
                    'last_srp_used',
                    'current_srp',
                    'srp_override_comment',
                    'propagation_schemes_eligibility',
                    'subsidiary_registration',
                    'are_sales_located_in_cob',
                    'sugrr_rating_id',
                    'override_country_of_business_id',
                    'override_main_industry_id',
                    'has_secondary_token',
                    'secondary_override_token',
                    'secondary_token_rationale',
                    'secondary_impact_on_rating',
                    'is_secondary_esg_driver_liability_arisen'
                   ];
BEGIN
    RAISE NOTICE 'Starting counterparty_rating table cleanup...';
    FOREACH column_name IN ARRAY columns_to_drop
    LOOP
        -- Check if column exists before dropping
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE
                table_name = 'counterparty_rating'
                AND column_name = column_name
        ) THEN
            EXECUTE format('ALTER TABLE counterparty_rating DROP COLUMN %I;', column_name);
            RAISE NOTICE '✓ Dropped column: %', column_name;
        ELSE
            RAISE NOTICE 'Column % does not exists, skipping', column_name;
        END IF;
    END LOOP;

    RAISE NOTICE 'counterparty_rating table cleanup completed';
END $$;

-- ============================================================================
-- Verify final table structure
-- ============================================================================

DO $$
DECLARE
   column_count INTEGER;
   expected_columns TEXT[] := ARRAY[
                        'id',
                        'counterparty_id',
                        'model_specific_overrides',
                        'created_by',
                        'created_timestamp',
                        'modified_by',
                        'modified_timestamp'
                    ];
                    missing_columns TEXT[];
BEGIN
    -- Check for missing columns
    SELECT ARRAY_AGG(col)
    INTO missing_columns
    FROM UNNEST(expected_columns) AS col
    WHERE NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'counterparty_rating'
        AND column_name = col
    );
    IF missing_columns IS NOT NULL THEN
       RAISE WARNING 'Missing expected columns: %', missing_columns;
    END IF;

    -- Count remaining columns
    SELECT COUNT(*)
    INTO column_count
    FROM information_schema.columns
    WHERE table_name = 'counterparty_rating';

    RAISE NOTICE 'Final column count: %', column_count;
    RAISE NOTICE 'Excepted columns present: %', expected_columns;
END $$;

