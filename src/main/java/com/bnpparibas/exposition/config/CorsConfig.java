package com.bnpparibas.exposition.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS (Cross-Origin Resource Sharing) Configuration
 *
 * Allows frontend applications running on different origins to access the API
 *
 * Development: Allows localhost with common frontend ports
 * Production: Allows specific domains only
 */
@Configuration
@Slf4j
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:8081}")
    private String[] allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String[] allowedMethods;

    @Value("${cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${cors.exposed-headers:Content-Disposition}")
    private String[] exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    /**
     * Configure CORS for all endpoints
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                log.info("Configuring CORS with allowed origins: {}", Arrays.toString(allowedOrigins));

                registry.addMapping("/**")  // Apply to all endpoints
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods(allowedMethods)
                        .allowedHeaders(allowedHeaders)
                        .exposedHeaders(exposedHeaders)
                        .allowCredentials(allowCredentials)
                        .maxAge(maxAge);
            }
        };
    }

    /**
     * Alternative CORS configuration source
     * Use this if you have Spring Security enabled
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
        configuration.setExposedHeaders(Arrays.asList(exposedHeaders));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configuration source created with {} allowed origins", allowedOrigins.length);

        return source;
    }
}
