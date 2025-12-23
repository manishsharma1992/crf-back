package com.bnpparibas.application.schema.mapper;

import com.bnpparibas.application.MapStructConfig;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse.EntryImportResult;
import com.bnpparibas.application.schema.dto.DataDictionaryImportResponse;
import com.bnpparibas.domain.datadictionary.value.DataDictionaryEntry;
import com.bnpparibas.domain.schema.aggregate.JsonSchemaRegistry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface DataDictionaryImportMapper {

    /**
     * Map domain entry to result DTO
     *
     * @param entry Domain entry
     * @param status Import status
     * @param schema Saved schema (optional)
     * @param errorMessage Error message (optional)
     * @return Entry import result
     */
    @Mapping(target = "ratingModel", source = "entry.ratingModel.modelName")
    @Mapping(target = "ratingModelVersion", source = "entry.ratingModelVersion.version")
    @Mapping(target = "ratingMechanism", expression = "java(entry.ratingMechanism().getValue())")
    @Mapping(target = "fieldCount", expression = "java(entry.fields().size())")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "schemaVersion", expression = "java(schema != null ? schema.getSchemaVersion().versionNumber() : null)")
    @Mapping(target = "schemaRegistryId", expression = "java(schema != null ? schema.getId() : null)")
    @Mapping(target = "errorMessage", source = "errorMessage")
    EntryImportResult toEntryResult(
            DataDictionaryEntry entry,
            DataDictionaryImportResponse.ImportStatus status,
            JsonSchemaRegistry schema,
            String errorMessage
    );

    /**
     * Create success result
     */
    default EntryImportResult toSuccessResult(
            DataDictionaryEntry entry,
            JsonSchemaRegistry schema) {
        return toEntryResult(
                entry,
                DataDictionaryImportResponse.ImportStatus.SUCCESS,
                schema,
                null
        );
    }

    /**
     * Create validated result (dry-run)
     */
    default EntryImportResult toValidatedResult(DataDictionaryEntry entry) {
        return toEntryResult(
                entry,
                DataDictionaryImportResponse.ImportStatus.VALIDATED,
                null,
                null
        );
    }

    /**
     * Create skipped result
     */
    default EntryImportResult toSkippedResult(
            DataDictionaryEntry entry,
            JsonSchemaRegistry existingSchema) {
        return toEntryResult(
                entry,
                DataDictionaryImportResponse.ImportStatus.SKIPPED,
                existingSchema,
                null
        );
    }

    /**
     * Create failed result
     */
    default EntryImportResult toFailedResult(
            DataDictionaryEntry entry,
            String errorMessage) {
        return toEntryResult(
                entry,
                DataDictionaryImportResponse.ImportStatus.FAILED,
                null,
                errorMessage
        );
    }
}
