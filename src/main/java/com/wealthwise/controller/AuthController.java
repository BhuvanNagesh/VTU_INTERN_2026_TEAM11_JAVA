package com.wealthwise.controller;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // Update with the exact Vite URL (e.g., http://localhost:5173) in production
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody User signUpRequest) {
        try {
            if (userRepository.existsByEmail(signUpRequest.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered!"));
            }

            User savedUser = authService.saveNewUser(signUpRequest);
            String token = jwtService.generateToken(savedUser.getEmail());
            savedUser.setPassword(null); // Hide password in response

            return ResponseEntity.ok(Map.of("token", token, "user", savedUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@RequestBody Map<String, String> loginRequest) {
        try {
            String email = loginRequest.get("email");
            String password = loginRequest.get("password");

            User user = authService.authenticateUser(email, password);
            String token = jwtService.generateToken(user.getEmail());
            user.setPassword(null); // Hide password in response

            return ResponseEntity.ok(Map.of("token", token, "user", user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            authService.generateAndSaveOtp(email);
            return ResponseEntity.ok(Map.of("message", "OTP generated and sent to email successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otp = request.get("otp");
            String newPassword = request.get("newPassword");
            
            authService.verifyOtpAndResetPassword(email, otp, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password has been successfully reset."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}