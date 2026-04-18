package com.riyura.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${supabase.jwt-secret}")
        private String jwtSecret;

        @Value("${supabase.issuer-url}")
        private String issuerUrl;

        @Value("${app.frontend-url}")
        private String frontendUrl;

        private final Environment environment;

        public SecurityConfig(Environment environment) {
                this.environment = environment;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                // Protected stream endpoints (history-based resume needs user context)
                                                .requestMatchers(
                                                                "/api/movies/stream",
                                                                "/api/tv/stream")
                                                .authenticated()

                                                // Public Endpoints
                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/api/health",
                                                                "/api/banner/**",
                                                                "/api/movies/**",
                                                                "/api/tv/**",
                                                                "/api/search/**",
                                                                "/api/anime/**",
                                                                "/api/explore/**",
                                                                "/ws/**")
                                                .permitAll()

                                                // Testing endpoints — only in dev profile
                                                .requestMatchers("/api/test/**")
                                                .permitAll()

                                                // Actuator — allow health, restrict the rest
                                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                                .permitAll()
                                                .requestMatchers("/actuator/**")
                                                .authenticated()

                                                // Protected Endpoints
                                                .requestMatchers(
                                                                "/api/profile/**",
                                                                "/api/watchlist/**",
                                                                "/api/party/**")
                                                .authenticated()

                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.decoder(jwtDecoder())));

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(buildAllowedOrigins());
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public JwtDecoder jwtDecoder() {
                byte[] keyBytes = jwtSecret.getBytes();
                SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

                NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                                .macAlgorithm(MacAlgorithm.HS256)
                                .build();

                // Issuer URL is now configurable via application.yaml
                OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuerUrl);
                decoder.setJwtValidator(validator);

                return decoder;
        }

        private List<String> buildAllowedOrigins() {
                boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev")
                                || frontendUrl.contains("localhost");

                if (isDev) {
                        return Arrays.asList("http://localhost:3000", "http://localhost:8080");
                }
                // Production: only the configured frontend URL
                return List.of(frontendUrl);
        }
}
