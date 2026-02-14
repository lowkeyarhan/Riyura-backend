package com.riyura.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure URL permissions
                .authorizeHttpRequests(auth -> auth
                        // Allow public access to all endpoints starting with /api/
                        .requestMatchers("/api/**").permitAll()
                        // Actually, for Development, let's permit EVERYTHING
                        .anyRequest().permitAll())

                // 3. Disable the default "Form Login" (The HTML page)
                .formLogin(AbstractHttpConfigurer::disable)

                // 4. Disable "HTTP Basic Auth" (The browser popup)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}