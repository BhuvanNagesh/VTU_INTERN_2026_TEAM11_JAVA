package com.wealthwise.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // This creates the PasswordEncoder bean that AuthService is looking for
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // This configures web security to allow access to your auth endpoints
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless REST APIs
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()          // Public: signup/login
                .requestMatchers("/api/schemes/search").permitAll()   // Public: scheme search autocomplete
                .requestMatchers("/api/schemes/count").permitAll()    // Public: scheme count
                .requestMatchers("/api/schemes/nav/**").permitAll()   // Public: NAV lookup
                .requestMatchers("/api/schemes/*").permitAll()        // Public: scheme detail
                .anyRequest().permitAll()  // Controllers handle JWT validation manually
            );
        
        return http.build();
    }
}