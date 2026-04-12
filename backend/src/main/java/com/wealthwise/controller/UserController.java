package com.wealthwise.controller;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** GET /api/user/profile — get current user's profile */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = extractEmail(authHeader);
            User user = userService.getProfile(email);
            user.setPassword(null); // never return password
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully", "user", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/user/change-password — change password with verification */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        try {
            String email = extractEmail(authHeader);
            userService.changePassword(email, body.get("currentPassword"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new RuntimeException("Missing or invalid Authorization header");
        return jwtService.extractEmail(authHeader.substring(7));
    }
}
