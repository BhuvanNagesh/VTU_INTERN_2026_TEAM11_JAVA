package com.wealthwise.service;

import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — AuthService Unit Tests
 *  Test Suite ID : TS-AUTH-SVC-001
 *
 *  Tests AuthService business logic in isolation: registration, login,
 *  OTP generation, OTP verification, password reset, expiry handling.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TS-AUTH-SVC-001 | AuthService Business Logic Tests")
class AuthServiceTest {

    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService    emailService;
    @InjectMocks AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("alice@test.com");
        user.setPassword("$hashed$");
        user.setFullName("Alice");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-SVC-001..004  saveNewUser — Registration
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-SVC-001..004 | saveNewUser — Registration")
    class RegistrationTests {

        @Test
        @DisplayName("TC-AUTH-SVC-001 | saveNewUser hashes password before persisting")
        void saveNewUser_hashesPassword() {
            User input = new User();
            input.setEmail("bob@test.com");
            input.setPassword("plaintext");

            when(passwordEncoder.encode("plaintext")).thenReturn("$hashed$");
            when(userRepository.save(any())).thenReturn(input);

            authService.saveNewUser(input);

            verify(passwordEncoder, times(1)).encode("plaintext");
            assertThat(input.getPassword()).isEqualTo("$hashed$");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-002 | saveNewUser calls repository.save exactly once")
        void saveNewUser_callsSaveOnce() {
            when(passwordEncoder.encode(any())).thenReturn("$hashed$");
            when(userRepository.save(any())).thenReturn(user);

            authService.saveNewUser(user);

            verify(userRepository, times(1)).save(user);
        }

        @Test
        @DisplayName("TC-AUTH-SVC-003 | saveNewUser returns saved user object")
        void saveNewUser_returnsSavedUser() {
            when(passwordEncoder.encode(any())).thenReturn("$hashed$");
            when(userRepository.save(any())).thenReturn(user);

            User result = authService.saveNewUser(user);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("TC-AUTH-SVC-004 | saveNewUser never stores plaintext password")
        void saveNewUser_noPlaintext() {
            User input = new User();
            input.setEmail("carol@test.com");
            input.setPassword("super-secret");

            when(passwordEncoder.encode("super-secret")).thenReturn("$bcrypt$");
            when(userRepository.save(any())).thenReturn(input);

            authService.saveNewUser(input);
            assertThat(input.getPassword()).isNotEqualTo("super-secret");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-SVC-005..008  authenticateUser — Login
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-SVC-005..008 | authenticateUser — Login")
    class AuthenticationTests {

        @Test
        @DisplayName("TC-AUTH-SVC-005 | Valid credentials return the matched user")
        void validCredentials_returnsUser() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "$hashed$")).thenReturn(true);

            User result = authService.authenticateUser("alice@test.com", "password");
            assertThat(result.getEmail()).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-006 | Unknown email throws RuntimeException")
        void unknownEmail_throwsException() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.authenticateUser("ghost@test.com", "any"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-007 | Wrong password throws RuntimeException")
        void wrongPassword_throwsException() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$hashed$")).thenReturn(false);

            assertThatThrownBy(() -> authService.authenticateUser("alice@test.com", "wrong"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-008 | Password comparison uses PasswordEncoder (not plaintext equals)")
        void passwordMatchUsesEncoder() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), eq("$hashed$"))).thenReturn(true);

            authService.authenticateUser("alice@test.com", "any-password");

            verify(passwordEncoder, times(1)).matches(anyString(), eq("$hashed$"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-SVC-009..013  generateAndSaveOtp — OTP Generation
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-SVC-009..013 | generateAndSaveOtp — OTP Generation")
    class OtpGenerationTests {

        @Test
        @DisplayName("TC-AUTH-SVC-009 | OTP is exactly 6 digits")
        void otp_is6Digits() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$hashed-otp$");
            when(userRepository.save(any())).thenReturn(user);
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString());

            String otp = authService.generateAndSaveOtp("alice@test.com");
            assertThat(otp).matches("\\d{6}");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-010 | OTP is not stored as plaintext (encoder called)")
        void otp_notStoredAsPlaintext() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$hashed-otp$");
            when(userRepository.save(any())).thenReturn(user);
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString());

            authService.generateAndSaveOtp("alice@test.com");

            // OTP is hashed — passwordEncoder.encode() must be called
            verify(passwordEncoder, times(1)).encode(anyString());
        }

        @Test
        @DisplayName("TC-AUTH-SVC-011 | OTP expiry is set to 5 minutes in the future")
        void otp_expirySet5MinutesAhead() {
            user.setOtpExpiry(null);
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$hashed-otp$");
            when(userRepository.save(any())).thenReturn(user);
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString());

            authService.generateAndSaveOtp("alice@test.com");

            assertThat(user.getOtpExpiry())
                .isAfter(LocalDateTime.now().plusMinutes(4))
                .isBefore(LocalDateTime.now().plusMinutes(6));
        }

        @Test
        @DisplayName("TC-AUTH-SVC-012 | Email is sent via EmailService after OTP saved")
        void otp_emailSent() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$hashed-otp$");
            when(userRepository.save(any())).thenReturn(user);
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString());

            authService.generateAndSaveOtp("alice@test.com");

            verify(emailService, times(1)).sendOtpEmail(eq("alice@test.com"), anyString());
        }

        @Test
        @DisplayName("TC-AUTH-SVC-013 | Unknown email → RuntimeException (no OTP generated)")
        void unknownEmail_noOtpGenerated() {
            when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.generateAndSaveOtp("nobody@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No account found");

            verify(emailService, never()).sendOtpEmail(anyString(), anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-SVC-014..018  verifyOtpAndResetPassword — Password Reset
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-SVC-014..018 | verifyOtpAndResetPassword — Password Reset")
    class PasswordResetTests {

        @BeforeEach
        void setUpOtp() {
            user.setResetOtp("$hashed-otp$");
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(4)); // still valid
        }

        @Test
        @DisplayName("TC-AUTH-SVC-014 | Valid OTP + within expiry resets password")
        void validOtp_resetsPassword() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("123456", "$hashed-otp$")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("$new-hashed$");
            when(userRepository.save(any())).thenReturn(user);

            authService.verifyOtpAndResetPassword("alice@test.com", "123456", "NewPass123");

            assertThat(user.getPassword()).isEqualTo("$new-hashed$");
            assertThat(user.getResetOtp()).isNull();
            assertThat(user.getOtpExpiry()).isNull();
        }

        @Test
        @DisplayName("TC-AUTH-SVC-015 | Wrong OTP → RuntimeException")
        void wrongOtp_throwsException() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("000000", "$hashed-otp$")).thenReturn(false);

            assertThatThrownBy(() ->
                authService.verifyOtpAndResetPassword("alice@test.com", "000000", "newPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or missing OTP");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-016 | Expired OTP → RuntimeException")
        void expiredOtp_throwsException() {
            user.setOtpExpiry(LocalDateTime.now().minusMinutes(1)); // expired 1 min ago
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("123456", "$hashed-otp$")).thenReturn(true);

            assertThatThrownBy(() ->
                authService.verifyOtpAndResetPassword("alice@test.com", "123456", "newPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("TC-AUTH-SVC-017 | After reset, OTP fields are cleared (prevents OTP reuse)")
        void afterReset_otpCleared() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("123456", "$hashed-otp$")).thenReturn(true);
            when(passwordEncoder.encode(any())).thenReturn("$new$");
            when(userRepository.save(any())).thenReturn(user);

            authService.verifyOtpAndResetPassword("alice@test.com", "123456", "NewPass");

            assertThat(user.getResetOtp()).isNull();
            assertThat(user.getOtpExpiry()).isNull();
        }

        @Test
        @DisplayName("TC-AUTH-SVC-018 | New password is encoded before saving")
        void newPassword_isEncoded() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("123456", "$hashed-otp$")).thenReturn(true);
            when(passwordEncoder.encode("NewSecurePass")).thenReturn("$bcrypt-new$");
            when(userRepository.save(any())).thenReturn(user);

            authService.verifyOtpAndResetPassword("alice@test.com", "123456", "NewSecurePass");

            verify(passwordEncoder, times(1)).encode("NewSecurePass");
            assertThat(user.getPassword()).isEqualTo("$bcrypt-new$");
        }
    }
}
