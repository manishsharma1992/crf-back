package com.bnpparibas.exposition.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security with CORS support");

        http
                // Enable CORS with our configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Disable CSRF (not needed for stateless REST API)
                .csrf(AbstractHttpConfigurer::disable)

                // Configure authorization
                .authorizeHttpRequests(authz -> authz
                        // Allow all requests for now (you can add authentication later)
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
