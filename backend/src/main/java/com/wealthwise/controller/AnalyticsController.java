package com.wealthwise.controller;

import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.AnalyticsService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired private AnalyticsService analyticsService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/risk")
    public ResponseEntity<?> getRiskProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = extractUserId(authHeader);
            return ResponseEntity.ok(analyticsService.getRiskProfile(userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    @GetMapping("/sip")
    public ResponseEntity<?> getSipIntelligence(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = extractUserId(authHeader);
            return ResponseEntity.ok(analyticsService.getSipIntelligence(userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    @GetMapping("/overlap")
    public ResponseEntity<?> getFundOverlap(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = extractUserId(authHeader);
            return ResponseEntity.ok(analyticsService.getFundOverlapMatrix(userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    @PatchMapping("/risk-profile")
    public ResponseEntity<?> saveRiskProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        try {
            Long userId = extractUserId(authHeader);
            String profile = body.get("riskProfile");
            if (profile == null || profile.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "riskProfile field is required"));
            analyticsService.saveRiskProfile(userId, profile);
            return ResponseEntity.ok(Map.of("message", "Risk profile updated to " + profile));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    /**
     * Extracts userId from Bearer JWT.
     * Returns HTTP 401 for expired/invalid tokens (not 500).
     */
    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new SecurityException("Missing or invalid Authorization header");
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            return userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("User not found"))
                .getId();
        } catch (JwtException e) {
            throw new SecurityException("Token invalid or expired — please log in again");
        }
    }

    private String sanitize(String msg) {
        if (msg == null) return "An error occurred";
        return msg.replaceAll("(?i)com\\.\\w+(\\.\\w+)*:\\s*", "")
                  .replaceAll("(?i)java\\.\\w+(\\.\\w+)*:\\s*", "").trim();
    }
}
