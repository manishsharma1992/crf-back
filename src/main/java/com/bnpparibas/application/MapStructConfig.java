package com.bnpparibas.application;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct configuration for all mappers
 *
 * Settings:
 * - componentModel = "spring" → Generates Spring beans
 * - injectionStrategy = CONSTRUCTOR → Uses constructor injection
 * - unmappedTargetPolicy = ERROR → Fails if we forget to map a field
 */
@MapperConfig(
        componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface MapStructConfig {
    // Shared configuration - no methods needed
}
