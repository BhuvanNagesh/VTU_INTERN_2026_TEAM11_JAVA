package com.wealthwise.controller;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.UserService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M01 F01.5 — Profile Settings Controller
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    /** GET /api/user/profile — get current user's profile (PAN masked in response) */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = extractEmail(authHeader);
            User user = userService.getProfile(email);
            user.setPassword(null);          // never return hashed password
            maskPan(user);                   // mask PAN: ABCDE****F
            return ResponseEntity.ok(user);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    /** PUT /api/user/profile — update fullName, phone, currency, panCard */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> updates) {
        try {
            String email = extractEmail(authHeader);
            User updated = userService.updateProfile(email, updates);
            updated.setPassword(null);
            maskPan(updated);
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully", "user", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    /** POST /api/user/change-password — change password with verification */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        try {
            String email = extractEmail(authHeader);
            String current = body.get("currentPassword");
            String next = body.get("newPassword");
            if (current == null || current.isBlank() || next == null || next.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "currentPassword and newPassword are required"));
            if (next.length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters"));
            userService.changePassword(email, current, next);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitize(e.getMessage())));
        }
    }

    /**
     * Masks the PAN card in the User object before sending to client.
     * Format: ABCDE1234F → ABCDE****F
     * PAN is sensitive financial identity data (IT Act) — never return raw.
     */
    private void maskPan(User user) {
        String pan = user.getPanCard();
        if (pan != null && pan.length() == 10) {
            user.setPanCard(pan.substring(0, 5) + "****" + pan.charAt(9));
        }
    }

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new SecurityException("Missing or invalid Authorization header");
        try {
            return jwtService.extractEmail(authHeader.substring(7));
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
