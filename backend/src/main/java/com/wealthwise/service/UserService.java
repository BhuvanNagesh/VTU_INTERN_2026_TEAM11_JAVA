package com.wealthwise.service;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * M01 F01.5 — Profile Settings Service
 * Handles updating user profile: name, phone, currency preference.
 * Change password uses current password verification.
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User getProfile(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User updateProfile(String email, Map<String, String> updates) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (updates.containsKey("fullName") && !updates.get("fullName").isBlank()) {
            user.setFullName(updates.get("fullName").trim());
        }
        if (updates.containsKey("phone")) {
            user.setPhone(updates.get("phone").trim());
        }
        if (updates.containsKey("currency")) {
            user.setCurrency(updates.get("currency").trim());
        }
        if (updates.containsKey("panCard")) {
            user.setPanCard(updates.get("panCard").trim().toUpperCase());
        }

        return userRepository.save(user);
    }

    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        if (newPassword.length() < 8) {
            throw new RuntimeException("New password must be at least 8 characters");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
