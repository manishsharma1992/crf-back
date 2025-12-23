-- Flyway Migration Script
-- Description: Create json_schema_registry table for storing JSON Schema definitions
-- Author: Manish
-- Version: V1.0
-- ============================================================================

-- ============================================================================
-- CREATE TABLE: json_schema_registry
-- ============================================================================

CREATE TABLE IF NOT EXISTS json_schema_registry (
    -- Primary Key
                                                    id BIGSERIAL PRIMARY KEY,

    -- Business Keys
                                                    rating_model VARCHAR(50) NOT NULL,
    rating_model_version VARCHAR(20) NOT NULL,
    rating_model_mechanism VARCHAR(20) NOT NULL,

    -- Schema Information
    schema_version INTEGER NOT NULL DEFAULT 1,
    json_schema JSONB NOT NULL,
    json_schema_standard VARCHAR(20) NOT NULL DEFAULT '2020-12',

    -- Lifecycle Management
    active BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP NULL,

    -- Documentation
    description TEXT,
    change_notes TEXT,

    -- Validation
    validation_notes TEXT,

    -- Audit Fields
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    modified_timestamp TIMESTAMP
    );

-- ============================================================================
-- ADD TABLE COMMENT
-- ============================================================================

COMMENT ON TABLE json_schema_registry IS
'Registry storing JSON Schema definitions for model_specific_overrides JSONB validation. Each schema corresponds to a specific rating model, version, and mechanism combination.';

-- ============================================================================
-- ADD COLUMN COMMENTS
-- ============================================================================

COMMENT ON COLUMN json_schema_registry.id IS
'Unique identifier for the schema registry entry';

COMMENT ON COLUMN json_schema_registry.rating_model IS
'Rating model identifier (e.g., PLACM, CORP, BANK)';

COMMENT ON COLUMN json_schema_registry.rating_model_version IS
'Version of the rating model (e.g., 010, 020)';

COMMENT ON COLUMN json_schema_registry.rating_model_mechanism IS
'Rating mechanism type: STANDALONE, INHERITANCE, or PROPAGATION';

COMMENT ON COLUMN json_schema_registry.schema_version IS
'Version number of this schema. Increments when schema changes for the same rating model/version/mechanism combination.';

COMMENT ON COLUMN json_schema_registry.json_schema IS
'Complete JSON Schema definition in JSONB format conforming to the specified JSON Schema standard';

COMMENT ON COLUMN json_schema_registry.json_schema_standard IS
'JSON Schema specification version used (e.g., draft-07, draft-2020-12)';

COMMENT ON COLUMN json_schema_registry.active IS
'Indicates if this schema is currently active. Only one schema can be active per model/version/mechanism combination.';

COMMENT ON COLUMN json_schema_registry.effective_from IS
'Date/time when this schema becomes active and should be used for validation';

COMMENT ON COLUMN json_schema_registry.effective_to IS
'Date/time when this schema was deprecated and replaced. NULL means currently active.';

COMMENT ON COLUMN json_schema_registry.description IS
'Human-readable description of this schema and its purpose';

COMMENT ON COLUMN json_schema_registry.change_notes IS
'Notes describing changes made in this schema version compared to previous version';

COMMENT ON COLUMN json_schema_registry.validation_notes IS
'Additional validation rules or notes that cannot be expressed in JSON Schema format';

COMMENT ON COLUMN json_schema_registry.created_by IS
'User or system identifier that created this schema entry';

COMMENT ON COLUMN json_schema_registry.created_timestamp IS
'Timestamp when this schema entry was created';

COMMENT ON COLUMN json_schema_registry.modified_by IS
'User or system identifier that last modified this schema entry';

COMMENT ON COLUMN json_schema_registry.modified_timestamp IS
'Timestamp when this schema entry was last modified';

-- ============================================================================
-- ADD CHECK CONSTRAINTS
-- ============================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_effective_dates'
        AND conrelid = 'json_schema_registry'::regclass
    ) THEN
ALTER TABLE json_schema_registry
    ADD CONSTRAINT chk_effective_dates CHECK (
        effective_to IS NULL OR effective_to > effective_from
        );
RAISE NOTICE 'Constraint chk_effective_dates created successfully';
ELSE
        RAISE NOTICE 'Constraint chk_effective_dates already exists, skipping';
END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_schema_version'
        AND conrelid = 'json_schema_registry'::regclass
    ) THEN
ALTER TABLE json_schema_registry
    ADD CONSTRAINT chk_schema_version CHECK (
        schema_version > 0
        );
RAISE NOTICE 'Constraint chk_schema_version created successfully';
ELSE
        RAISE NOTICE 'Constraint chk_schema_version already exists, skipping';
END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_rating_mechanism'
        AND conrelid = 'json_schema_registry'::regclass
    ) THEN
ALTER TABLE json_schema_registry
    ADD CONSTRAINT chk_rating_mechanism CHECK (
        rating_model_mechanism IN ('STANDALONE', 'INHERITANCE', 'PROPAGATION')
        );
RAISE NOTICE 'Constraint chk_rating_mechanism created successfully';
ELSE
        RAISE NOTICE 'Constraint chk_rating_mechanism already exists, skipping';
END IF;
END $$;

-- ============================================================================
-- CREATE UNIQUE INDEX
-- ============================================================================

-- Ensures only ONE active schema per model/version/mechanism combination
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'uk_active_schema'
    ) THEN
CREATE UNIQUE INDEX uk_active_schema
    ON json_schema_registry (rating_model, rating_model_version, rating_model_mechanism)
    WHERE active = true;

COMMENT ON INDEX uk_active_schema IS
        'Ensures only one active schema exists per rating model/version/mechanism combination';

        RAISE NOTICE 'Unique index uk_active_schema created successfully';
ELSE
        RAISE NOTICE 'Unique index uk_active_schema already exists, skipping';
END IF;
END $$;


COMMENT ON INDEX uk_active_schema IS
'Ensures only one active schema exists per rating model/version/mechanism combination';

-- ============================================================================
-- CREATE INDEXES FOR PERFORMANCE
-- ============================================================================

-- Index for temporal queries (what schema was active at a specific time)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_schema_effective_dates'
    ) THEN
CREATE INDEX idx_schema_effective_dates
    ON json_schema_registry (effective_from, effective_to);

COMMENT ON INDEX idx_schema_effective_dates IS
        'Supports temporal queries to find schemas active at specific points in time';

        RAISE NOTICE 'Index idx_schema_effective_dates created successfully';
ELSE
        RAISE NOTICE 'Index idx_schema_effective_dates already exists, skipping';
END IF;
END $$;

COMMENT ON INDEX idx_schema_effective_dates IS
'Supports temporal queries to find schemas active at specific points in time';

-- Index for common lookup pattern
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_schema_lookup'
    ) THEN
CREATE INDEX idx_schema_lookup
    ON json_schema_registry (rating_model, rating_model_version, rating_model_mechanism, active);

COMMENT ON INDEX idx_schema_lookup IS
        'Optimizes lookups by rating model, version, mechanism, and active status';

        RAISE NOTICE 'Index idx_schema_lookup created successfully';
ELSE
        RAISE NOTICE 'Index idx_schema_lookup already exists, skipping';
END IF;
END $$;

COMMENT ON INDEX idx_schema_lookup IS
'Optimizes lookups by rating model, version, mechanism, and active status';

-- Index for schema version queries
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_schema_version'
    ) THEN
CREATE INDEX idx_schema_version
    ON json_schema_registry (rating_model, rating_model_version, rating_model_mechanism, schema_version);

COMMENT ON INDEX idx_schema_version IS
        'Supports queries to retrieve specific schema versions or find latest version';

        RAISE NOTICE 'Index idx_schema_version created successfully';
ELSE
        RAISE NOTICE 'Index idx_schema_version already exists, skipping';
END IF;
END $$;

COMMENT ON INDEX idx_schema_version IS
'Supports queries to retrieve specific schema versions or find latest version';

-- GIN index for JSON Schema queries (if you need to search within the schema itself)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_json_schema_content'
    ) THEN
CREATE INDEX idx_json_schema_content
    ON json_schema_registry USING GIN (json_schema);

COMMENT ON INDEX idx_json_schema_content IS
        'Enables efficient queries within the JSON Schema content using GIN indexing';

        RAISE NOTICE 'Index idx_json_schema_content created successfully';
ELSE
        RAISE NOTICE 'Index idx_json_schema_content already exists, skipping';
END IF;
END $$;

COMMENT ON INDEX idx_json_schema_content IS
'Enables efficient queries within the JSON Schema content using GIN indexing';

-- Index for audit queries
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_schema_audit'
    ) THEN
CREATE INDEX idx_schema_audit
    ON json_schema_registry (created_by, created_timestamp);

COMMENT ON INDEX idx_schema_audit IS
        'Supports audit queries by creator and creation timestamp';

        RAISE NOTICE 'Index idx_schema_audit created successfully';
ELSE
        RAISE NOTICE 'Index idx_schema_audit already exists, skipping';
END IF;
END $$;

COMMENT ON INDEX idx_schema_audit IS
'Supports audit queries by creator and creation timestamp';