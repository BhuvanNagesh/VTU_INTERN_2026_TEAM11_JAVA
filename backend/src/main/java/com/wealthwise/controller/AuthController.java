package com.wealthwise.controller;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173") // Allow Vite frontend
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody User signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already taken!"));
        }

        // Hash the password before saving
        signUpRequest.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        
        User savedUser = userRepository.save(signUpRequest);
        String token = jwtService.generateToken(savedUser.getEmail());

        // Remove password from response for security
        savedUser.setPassword(null);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", savedUser
        ));
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        User user = userOpt.get();
        String token = jwtService.generateToken(user.getEmail());

        // Remove password from response for security
        user.setPassword(null);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", user
        ));
    }
}