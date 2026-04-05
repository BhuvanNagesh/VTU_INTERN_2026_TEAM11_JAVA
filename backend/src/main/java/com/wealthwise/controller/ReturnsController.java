package com.wealthwise.controller;

import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.ReturnsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/returns")
@CrossOrigin(origins = "*")
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
            Map<String, Object> result = returnsService.getPortfolioReturns(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new RuntimeException("Missing or invalid Authorization header");
        String token = authHeader.substring(7);
        String email = jwtService.extractEmail(token);
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"))
            .getId();
    }
}
