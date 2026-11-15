package com.riyura.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${supabase.jwt-secret}")
        private String jwtSecret;

        @Value("${supabase.issuer-uri}")
        private String issuerUrl;

        @Value("${app.cors.allowed-origins:}")
        private String corsAllowedOrigins;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/api/movies/stream",
                                                                "/api/tv/stream",
                                                                "/api/watchalong/movies/stream",
                                                                "/api/watchalong/tv/stream")
                                                .authenticated()

                                                .requestMatchers("/party-test.js", "/party-test.html",
                                                                "/cache-monitor.html", "/cache-monitor.js")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/api/health",
                                                                "/api/banner/**",
                                                                "/api/movies/**",
                                                                "/api/tv/**",
                                                                "/api/watchalong/movies/player/**",
                                                                "/api/watchalong/tv/player/**",
                                                                "/api/search/**",
                                                                "/api/anime/**",
                                                                "/api/explore/**",
                                                                "/api/test/**",
                                                                "/ws/**")
                                                .permitAll()

                                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                                .permitAll()
                                                .requestMatchers("/actuator/**")
                                                .authenticated()

                                                .requestMatchers(
                                                                "/api/profile/**",
                                                                "/api/watchlist/**",
                                                                "/api/watchalong/party/**")
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
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept",
                                "Cache-Control", "Last-Event-ID"));
                configuration.setExposedHeaders(List.of("Content-Type", "Cache-Control"));
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

                OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuerUrl);
                decoder.setJwtValidator(validator);

                return decoder;
        }

        private List<String> buildAllowedOrigins() {
                if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
                        return List.of();
                }

                List<String> origins = new ArrayList<>();
                for (String origin : corsAllowedOrigins.split(",")) {
                        normalizeOrigin(origin).ifPresent(origins::add);
                }
                return origins.stream().distinct().toList();
        }

        private java.util.Optional<String> normalizeOrigin(String value) {
                try {
                        URI uri = new URI(value.trim());
                        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                                        || uri.getHost() == null
                                        || uri.getUserInfo() != null
                                        || uri.getRawQuery() != null
                                        || uri.getRawFragment() != null
                                        || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                                return java.util.Optional.empty();
                        }
                        return java.util.Optional.of(uri.getScheme().toLowerCase() + "://"
                                        + uri.getHost().toLowerCase()
                                        + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
                } catch (URISyntaxException | NullPointerException ex) {
                        return java.util.Optional.empty();
                }
        }
}
