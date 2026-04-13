package com.wealthwise.controller;

import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.ReturnsService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnsController {

    @Autowired private ReturnsService returnsService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    /**
     * Portfolio-level returns: total invested, current value, XIRR, absolute return
     * GET /api/returns/portfolio
     */
    @GetMapping("/portfolio")
    public ResponseEntity<?> portfolio(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = extractUserId(authHeader);
            return ResponseEntity.ok(returnsService.getPortfolioReturns(userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    /**
     * Per-scheme returns
     * GET /api/returns/scheme/{amfiCode}
     */
    @GetMapping("/scheme/{amfiCode}")
    public ResponseEntity<?> byScheme(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String amfiCode) {
        try {
            Long userId = extractUserId(authHeader);
            Map<String, Object> result = returnsService.getSchemeReturns(userId, amfiCode);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

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
