package com.wealthwise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed.origins:http://localhost:5173,http://localhost:3000,http://localhost:4173,https://wealthwise-frontend-8xmb.onrender.com}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Security response headers on every response.
     * These mitigate common web vulnerabilities without requiring Spring Security changes.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                // Prevents MIME-type sniffing (e.g. serving a text file as executable)
                res.setHeader("X-Content-Type-Options", "nosniff");
                // Prevents clickjacking via iframes
                res.setHeader("X-Frame-Options", "DENY");
                // Basic XSS filter for older browsers
                res.setHeader("X-XSS-Protection", "1; mode=block");
                // Forces HTTPS for 1 year (HSTS) — only effective in production
                res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                // Restrict referrer info
                res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                return true;
            }
        });
    }
}
