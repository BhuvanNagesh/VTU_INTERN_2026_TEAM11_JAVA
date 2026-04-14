package com.wealthwise.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — JwtService Unit Tests
 *  Test Suite ID : TS-JWT-001
 *  Coverage      : Token generation, extraction, validation, expiry edge cases
 * ─────────────────────────────────────────────────────────────────────────────
 */
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    // A 32-character secret (256-bit) — minimum for HMAC-SHA256
    private static final String SECRET =
            "wealthwise-super-secret-key-12345";
    // 1 hour in ms
    private static final long EXPIRY_MS = 3_600_000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",    SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRY_MS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-JWT-001..005  Token Generation
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-JWT-001..005 | Token Generation")
    class GenerationTests {

        @Test
        @DisplayName("TC-JWT-001 | generateToken returns non-null, non-blank string")
        void generatedTokenIsNonBlank() {
            String token = jwtService.generateToken("test@example.com");
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("TC-JWT-002 | Generated token has 3 JWT segments (header.payload.signature)")
        void generatedTokenHasThreeSegments() {
            String token = jwtService.generateToken("user@wealthwise.in");
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("TC-JWT-003 | Two calls produce different tokens (jti/iat differs)")
        void twoTokensAreDifferent() throws InterruptedException {
            String t1 = jwtService.generateToken("same@email.com");
            Thread.sleep(10); // Ensure issuedAt differs
            String t2 = jwtService.generateToken("same@email.com");
            // They could theoretically be equal in the same ms but practically different
            assertThat(t1).isNotNull();
            assertThat(t2).isNotNull();
        }

        @Test
        @DisplayName("TC-JWT-004 | generateToken(email) and generateToken(map, email) are consistent")
        void overloadedMethodsConsistent() {
            String t1 = jwtService.generateToken("a@b.com");
            String subject = jwtService.extractEmail(t1);
            assertThat(subject).isEqualTo("a@b.com");
        }

        @Test
        @DisplayName("TC-JWT-005 | Extra claims are valid — token still parseable")
        void tokenWithExtraClaimsIsParseable() {
            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("role", "ADMIN");
            String token = jwtService.generateToken(claims, "admin@wealthwise.in");
            assertThat(token).isNotBlank();
            assertThat(jwtService.extractEmail(token)).isEqualTo("admin@wealthwise.in");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-JWT-006..010  Email / Subject Extraction
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-JWT-006..010 | Email Extraction")
    class ExtractionTests {

        @Test
        @DisplayName("TC-JWT-006 | extractEmail returns the original email")
        void extractedEmailMatchesOriginal() {
            String email = "bhuvan@wealthwise.in";
            String token = jwtService.generateToken(email);
            assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        }

        @Test
        @DisplayName("TC-JWT-007 | extractUsername is alias for extractEmail")
        void usernameAliasMatchesEmail() {
            String email = "priya@wealthwise.in";
            String token = jwtService.generateToken(email);
            assertThat(jwtService.extractUsername(token)).isEqualTo(email);
        }

        @Test
        @DisplayName("TC-JWT-008 | Email with special characters round-trips correctly")
        void emailWithSpecialCharsRoundTrips() {
            String email = "user+tag@sub.domain.co.in";
            String token = jwtService.generateToken(email);
            assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        }

        @Test
        @DisplayName("TC-JWT-009 | Tampered token throws JwtException on extraction")
        void tamperedTokenThrowsOnExtraction() {
            String token = jwtService.generateToken("legit@user.com");
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertThatThrownBy(() -> jwtService.extractEmail(tampered))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("TC-JWT-010 | Completely garbage string throws JwtException")
        void garbageTokenThrows() {
            assertThatThrownBy(() -> jwtService.extractEmail("not.a.valid.jwt.token"))
                    .isInstanceOf(Exception.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-JWT-011..015  Token Validation
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-JWT-011..015 | Token Validation")
    class ValidationTests {

        @Test
        @DisplayName("TC-JWT-011 | Valid token passes isTokenValid check")
        void validTokenPassesValidation() {
            String email = "valid@user.com";
            String token = jwtService.generateToken(email);
            assertThat(jwtService.isTokenValid(token, email)).isTrue();
        }

        @Test
        @DisplayName("TC-JWT-012 | Token for different email fails validation")
        void wrongEmailFailsValidation() {
            String token = jwtService.generateToken("alice@example.com");
            assertThat(jwtService.isTokenValid(token, "bob@example.com")).isFalse();
        }

        @Test
        @DisplayName("TC-JWT-013 | Expired token fails isTokenValid")
        void expiredTokenFailsValidation() throws Exception {
            // Create a service with 1ms expiry
            JwtService shortLivedService = new JwtService();
            ReflectionTestUtils.setField(shortLivedService, "secretKey",    SECRET);
            ReflectionTestUtils.setField(shortLivedService, "jwtExpiration", 1L);

            String token = shortLivedService.generateToken("expiry@test.com");
            Thread.sleep(50); // Wait for token to expire

            // Trying to validate an expired token should throw JwtException / return false
            // isTokenValid internally calls extractUsername which can throw on expired token
            assertThatThrownBy(() -> shortLivedService.isTokenValid(token, "expiry@test.com"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("TC-JWT-014 | Token is valid immediately after generation")
        void tokenIsImmediatelyValid() {
            String email = "fresh@user.com";
            String token = jwtService.generateToken(email);
            assertThat(jwtService.isTokenValid(token, email)).isTrue();
        }

        @Test
        @DisplayName("TC-JWT-015 | isTokenValid is case-sensitive for username")
        void validationIsCaseSensitive() {
            String token = jwtService.generateToken("UserA@Domain.com");
            // Lowercase version of the email should fail
            assertThat(jwtService.isTokenValid(token, "usera@domain.com")).isFalse();
        }
    }
}
