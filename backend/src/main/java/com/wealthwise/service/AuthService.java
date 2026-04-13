package com.wealthwise.service;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService; // Injecting the EmailService

    // =========================
    // Signup & Login Helpers
    // =========================

    public User saveNewUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User authenticateUser(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        return userOpt.get();
    }

    // =========================
    // Forgot Password Logic
    // =========================

    public String generateAndSaveOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        // Cryptographically secure OTP using SecureRandom (not java.util.Random)
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        // Hash before storing — if DB is compromised, attacker cannot use stored value to reset passwords
        user.setResetOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // Send the plaintext OTP to user's email (only valid copy outside the DB)
        emailService.sendOtpEmail(email, otp);
        return otp;
    }

    public void verifyOtpAndResetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getResetOtp() == null || !passwordEncoder.matches(otp, user.getResetOtp())) {
            throw new RuntimeException("Invalid or missing OTP");
        }

        // Checks if the current time is past the 5-minute expiry
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        // Reset password and clear OTP fields
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
    }
}