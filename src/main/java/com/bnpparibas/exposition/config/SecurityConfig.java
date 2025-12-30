package com.bnpparibas.exposition.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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

                // Disable CSRF (safe for stateless REST APIs)
                // Justification:
                // 1. This is a stateless REST API (no cookies/sessions)
                // 2. Session management is STATELESS (see below)
                // 3. Authentication will be token-based (JWT/OAuth)
                // 4. API is consumed by other services, not browser forms
                // 5. CSRF attacks require cookie-based authentication
                //
                // OWASP: "CSRF protection is not needed for APIs that do not use cookies"
                // Reference: https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html
                .csrf(AbstractHttpConfigurer::disable) // Sonar: S4502 - Safe for stateless REST APIs

                // Configure authorization
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // API endpoints - will require authentication in future
                        .requestMatchers("/api/**").permitAll() // TODO: Add authentication

                        // All other requests
                        .anyRequest().authenticated()
                )

                // STATELESS session management - key for CSRF safety
                // No server-side sessions = No CSRF vulnerability
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

}
