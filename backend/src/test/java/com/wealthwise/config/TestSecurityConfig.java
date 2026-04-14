package com.wealthwise.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only Spring Security configuration for @WebMvcTest slices.
 *
 * Replaces the main SecurityConfig + JwtAuthenticationFilter with a permit-all,
 * no-CSRF policy. Individual test classes exclude JwtAuthenticationFilter via
 * @WebMvcTest(excludeFilters = ...) to prevent the real JWT filter from being
 * registered in the MockMvc filter chain.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
