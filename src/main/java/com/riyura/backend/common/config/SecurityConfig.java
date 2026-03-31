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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${supabase.jwt-secret}")
        private String jwtSecret;

        @Value("${app.frontend-url}")
        private String frontendUrl;

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
                                                                "/api/test/**",
                                                                "/actuator/**",
                                                                "/ws/**")
                                                .permitAll()

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
                // 1. Supabase JWT secret - use it directly as bytes
                byte[] keyBytes = jwtSecret.getBytes();

                // 2. Create the SecretKeySpec using HMAC SHA256
                SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

                // 3. Build the decoder with HS256 algorithm
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                                .macAlgorithm(MacAlgorithm.HS256)
                                .build();

                // 4. For Supabase, only validate signature and timestamps, skip issuer
                // validation
                OAuth2TokenValidator<Jwt> validator = JwtValidators
                                .createDefaultWithIssuer("https://jlfcixnfwvyyltasemke.supabase.co/auth/v1");
                decoder.setJwtValidator(validator);

                return decoder;
        }

        @Bean
        public RestTemplate restTemplate() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();
                JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
                factory.setReadTimeout(Duration.ofSeconds(10));
                return new RestTemplate(factory);
        }

        private List<String> buildAllowedOrigins() {
                // Always allow localhost for development
                if (frontendUrl.contains("localhost")) {
                        return Arrays.asList("http://localhost:3000", "http://localhost:8080");
                }
                // In production, allow both the configured frontend URL and localhost for dev
                return Arrays.asList(frontendUrl, "http://localhost:3000", "http://localhost:8080");
        }
}
