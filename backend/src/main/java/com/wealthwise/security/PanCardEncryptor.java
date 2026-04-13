package com.wealthwise.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts PAN card numbers in the database
 * using AES-256-GCM (authenticated encryption — prevents tampering).
 *
 * The encryption key is derived from app.jwt.secret via SHA-256 so no extra env var is needed.
 *
 * IMPORTANT: If the JWT secret changes, existing encrypted PAN values will be unreadable.
 * Do not rotate app.jwt.secret without migrating PAN data first.
 */
@Component
@Converter
public class PanCardEncryptor implements AttributeConverter<String, String> {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LEN = 12; // bytes
    private static final int    GCM_TAG_LEN = 128; // bits

    // Key is derived once at startup from the JWT secret
    private SecretKey secretKey;

    @Value("${app.jwt.secret}")
    public void setJwtSecret(String jwtSecret) throws Exception {
        // Derive a 256-bit AES key by hashing the JWT secret with SHA-256
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes   = sha.digest(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.secretKey    = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String panCard) {
        if (panCard == null || panCard.isBlank()) return panCard;
        try {
            // Generate a random 12-byte IV for each encryption
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] cipherText = cipher.doFinal(panCard.getBytes(StandardCharsets.UTF_8));

            // Store IV + ciphertext together (IV is safe to store in plaintext)
            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                .put(iv).put(cipherText).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("PAN encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) return dbValue;
        try {
            byte[] combined = Base64.getDecoder().decode(dbValue);
            // If it doesn't look like encrypted data (e.g., legacy plain text PAN), return as-is
            if (combined.length <= GCM_IV_LEN) return dbValue;

            byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_LEN);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LEN, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, return as-is (handles pre-migration plain text PAN values)
            return dbValue;
        }
    }
}
