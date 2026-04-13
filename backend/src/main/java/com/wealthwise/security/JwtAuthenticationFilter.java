package com.wealthwise.security;

import com.wealthwise.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT Authentication Filter — enforces authentication on all /api/** endpoints
 * except the explicitly whitelisted public paths below.
 *
 * On success: stores resolved userId in request attribute "userId" so controllers
 * can read it with: (Long) request.getAttribute("userId")
 *
 * On failure: writes HTTP 401 JSON and stops the filter chain.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Public paths that don't need a JWT
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/api/auth/",
        "/api/schemes",
        "/api/nav"
    );

    @Autowired private JwtService      jwtService;
    @Autowired private UserRepository  userRepository;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            String email = jwtService.extractEmail(token);
            Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("User not found"))
                .getId();

            // Store userId so controllers can retrieve it without repeating JWT logic
            request.setAttribute("userId", userId);
            filterChain.doFilter(request, response);

        } catch (JwtException | SecurityException e) {
            writeUnauthorized(response, "Token invalid or expired — please log in again");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
