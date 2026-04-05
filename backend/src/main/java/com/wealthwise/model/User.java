package com.wealthwise.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users") // "users" is correct because "user" is a reserved keyword in PostgreSQL
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Explicitly mapping to snake_case for PostgreSQL compatibility
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "phone")
    private String phone;
    
    @Column(name = "currency")
    private String currency;
    
    @Column(name = "pan_card")
    private String panCard;

    @Column(name = "reset_otp")
    private String resetOtp;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    // Risk profile from M04 questionnaire — CONSERVATIVE | MODERATE | AGGRESSIVE
    @Column(name = "risk_profile", columnDefinition = "VARCHAR(50) DEFAULT 'MODERATE'")
    private String riskProfile = "MODERATE";

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Default constructor required by JPA
    public User() {}

    // Parameterized constructor
    public User(String fullName, String email, String password, String phone, String currency, String panCard) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.currency = currency;
        this.panCard = panCard;
    }

    // =========================
    // Getters and Setters
    // =========================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPanCard() { return panCard; }
    public void setPanCard(String panCard) { this.panCard = panCard; }

    public String getResetOtp() { return resetOtp; }
    public void setResetOtp(String resetOtp) { this.resetOtp = resetOtp; }

    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getRiskProfile() { return riskProfile; }
    public void setRiskProfile(String riskProfile) { this.riskProfile = riskProfile; }
}