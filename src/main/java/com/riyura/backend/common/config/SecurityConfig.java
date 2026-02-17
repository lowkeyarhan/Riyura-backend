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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${supabase.jwt-secret}")
        private String jwtSecret;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                // Public Endpoints
                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/api/banner/**",
                                                                "/api/movies/**",
                                                                "/api/tv/**",
                                                                "/api/search/**",
                                                                "/api/anime/**")
                                                .permitAll()

                                                // Protected Endpoints
                                                .requestMatchers(
                                                                "/api/profile/**",
                                                                "/api/watch-history/**")
                                                .authenticated()

                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.decoder(jwtDecoder())));

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
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

                System.out.println("JWT Decoder configured with secret length: " + keyBytes.length + " bytes");

                return decoder;
        }

        @Bean
        public RestTemplate restTemplate() {
                return new RestTemplate();
        }
}
