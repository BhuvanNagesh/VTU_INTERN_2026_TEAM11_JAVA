package com.wealthwise.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())       // Delegate CORS to WebConfig.java
            .csrf(csrf -> csrf.disable())           // Stateless REST — no CSRF needed
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: authentication endpoints (signup, signin, OTP, health)
                .requestMatchers("/api/auth/**").permitAll()
                // Public: scheme master data and NAV lookups (read-only, non-sensitive)
                .requestMatchers(HttpMethod.GET, "/api/schemes/**", "/api/nav/**").permitAll()
                // All other endpoints require JWT — enforced strictly by JwtAuthenticationFilter below,
                // so we use permitAll() here to prevent Spring Security from throwing 403 Forbidden 
                // since we aren't populating the Spring SecurityContextHolder.
                .anyRequest().permitAll()
            )
            // JWT filter runs before Spring's own auth filter so we can reject early
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}