package com.wealthwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealthwise.config.TestSecurityConfig;
import com.wealthwise.model.User;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtAuthenticationFilter;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — AuthController Web Layer Tests
 *  Test Suite ID : TS-AUTH-001
 *  Coverage      : Sign Up, Sign In, Forgot Password, Reset Password, Health
 * ─────────────────────────────────────────────────────────────────────────────
 */
@WebMvcTest(
    value = AuthController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class
    )
)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired MockMvc         mvc;
    @Autowired ObjectMapper    objectMapper;
    @MockBean  AuthService     authService;
    @MockBean  JwtService      jwtService;
    @MockBean  UserRepository  userRepository;

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-001..006  POST /api/auth/signup
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-001..006 | POST /api/auth/signup")
    class SignUpTests {

        @Test
        @DisplayName("TC-AUTH-001 | Successful signup returns 200 with token")
        void signup_success() throws Exception {
            User savedUser = new User();
            savedUser.setEmail("new@user.com");
            savedUser.setFullName("New User");

            when(userRepository.existsByEmail("new@user.com")).thenReturn(false);
            when(authService.saveNewUser(any())).thenReturn(savedUser);
            when(jwtService.generateToken("new@user.com")).thenReturn("mock-jwt-token");

            mvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"new@user.com\",\"password\":\"Pass@1234\",\"fullName\":\"New User\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").value("mock-jwt-token"));
        }

        @Test
        @DisplayName("TC-AUTH-002 | Duplicate email returns 400 with error message")
        void signup_duplicateEmail_returns400() throws Exception {
            when(userRepository.existsByEmail("existing@user.com")).thenReturn(true);

            mvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"existing@user.com\",\"password\":\"Pass@1234\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("Email is already registered!"));
        }

        @Test
        @DisplayName("TC-AUTH-003 | Service exception during signup returns 400")
        void signup_serviceThrows_returns400() throws Exception {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(authService.saveNewUser(any())).thenThrow(new RuntimeException("DB error"));

            mvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@b.com\",\"password\":\"test1234\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("DB error"));
        }

        @Test
        @DisplayName("TC-AUTH-004 | Signup response does NOT expose password field")
        void signup_responseDoesNotIncludePassword() throws Exception {
            User savedUser = new User();
            savedUser.setEmail("no@pass.com");
            savedUser.setPassword("bcrypt_hash_here");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(authService.saveNewUser(any())).thenReturn(savedUser);
            when(jwtService.generateToken(anyString())).thenReturn("tok");

            mvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"no@pass.com\",\"password\":\"secret\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.user.password").doesNotExist());
        }

        @Test
        @DisplayName("TC-AUTH-005 | Signup response contains both token and user keys")
        void signup_responseContainsBothFields() throws Exception {
            User savedUser = new User();
            savedUser.setEmail("both@fields.com");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(authService.saveNewUser(any())).thenReturn(savedUser);
            when(jwtService.generateToken(anyString())).thenReturn("abc.def.ghi");

            mvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"both@fields.com\",\"password\":\"test1234\"}"))
               .andExpect(jsonPath("$.token").exists())
               .andExpect(jsonPath("$.user").exists());
        }

        @Test
        @DisplayName("TC-AUTH-006 | Email-uniqueness check gates saveNewUser (not called on dupe)")
        void signup_checksEmailExistenceFirst() throws Exception {
            when(userRepository.existsByEmail("x@y.com")).thenReturn(true);

            mvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"x@y.com\",\"password\":\"pass\"}"))
               .andExpect(status().isBadRequest());

            verify(authService, never()).saveNewUser(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-007..012  POST /api/auth/signin
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-007..012 | POST /api/auth/signin")
    class SignInTests {

        @Test
        @DisplayName("TC-AUTH-007 | Valid credentials returns 200 with JWT token")
        void signin_success() throws Exception {
            User user = new User();
            user.setEmail("valid@user.com");

            when(authService.authenticateUser("valid@user.com", "correct_pass")).thenReturn(user);
            when(jwtService.generateToken("valid@user.com")).thenReturn("valid-token");

            mvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"valid@user.com\",\"password\":\"correct_pass\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").value("valid-token"));
        }

        @Test
        @DisplayName("TC-AUTH-008 | Wrong credentials returns 401 with error message")
        void signin_invalidCredentials_returns401() throws Exception {
            when(authService.authenticateUser(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            mvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"wrong@user.com\",\"password\":\"wrong_pass\"}"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }

        @Test
        @DisplayName("TC-AUTH-009 | Signin response does NOT expose password")
        void signin_passwordNotInResponse() throws Exception {
            User user = new User();
            user.setEmail("safe@user.com");
            user.setPassword("should_be_nulled");

            when(authService.authenticateUser(anyString(), anyString())).thenReturn(user);
            when(jwtService.generateToken(anyString())).thenReturn("tok");

            mvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"safe@user.com\",\"password\":\"pass\"}"))
               .andExpect(jsonPath("$.user.password").doesNotExist());
        }

        @Test
        @DisplayName("TC-AUTH-010 | Signin response contains both token and user keys")
        void signin_responseHasBothKeys() throws Exception {
            User user = new User();
            user.setEmail("complete@response.com");
            when(authService.authenticateUser(anyString(), anyString())).thenReturn(user);
            when(jwtService.generateToken(anyString())).thenReturn("jwt123");

            mvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"complete@response.com\",\"password\":\"pw\"}"))
               .andExpect(jsonPath("$.token").exists())
               .andExpect(jsonPath("$.user").exists());
        }

        @Test
        @DisplayName("TC-AUTH-011 | AuthService.authenticateUser called with exact email + password")
        void signin_serviceCalledWithCorrectArgs() throws Exception {
            User u = new User();
            u.setEmail("check@args.com");
            when(authService.authenticateUser("check@args.com", "mypassword")).thenReturn(u);
            when(jwtService.generateToken(anyString())).thenReturn("t");

            mvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"check@args.com\",\"password\":\"mypassword\"}"));

            verify(authService).authenticateUser("check@args.com", "mypassword");
        }

        @Test
        @DisplayName("TC-AUTH-012 | JwtService.generateToken called with authenticated user's email")
        void signin_jwtServiceCalledWithEmail() throws Exception {
            User u = new User();
            u.setEmail("jwt@call.com");
            when(authService.authenticateUser(anyString(), anyString())).thenReturn(u);
            when(jwtService.generateToken("jwt@call.com")).thenReturn("token");

            mvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"jwt@call.com\",\"password\":\"pw\"}"));

            verify(jwtService).generateToken("jwt@call.com");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-013..016  POST /api/auth/forgot-password & reset-password
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-013..016 | Forgot & Reset Password")
    class PasswordResetTests {

        @Test
        @DisplayName("TC-AUTH-013 | Forgot password returns 200 with success message")
        void forgotPassword_returns200() throws Exception {
            when(authService.generateAndSaveOtp(anyString())).thenReturn("123456");

            mvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"reset@user.com\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("OTP generated and sent to email successfully."));
        }

        @Test
        @DisplayName("TC-AUTH-014 | Forgot password returns 400 when email not found")
        void forgotPassword_unknownEmail_returns400() throws Exception {
            when(authService.generateAndSaveOtp(anyString()))
                    .thenThrow(new RuntimeException("Email not registered"));

            mvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ghost@user.com\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("Email not registered"));
        }

        @Test
        @DisplayName("TC-AUTH-015 | Reset password returns 200 on valid OTP")
        void resetPassword_returns200() throws Exception {
            doNothing().when(authService)
                    .verifyOtpAndResetPassword(anyString(), anyString(), anyString());

            mvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"r@user.com\",\"otp\":\"123456\",\"newPassword\":\"NewPass@1\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("Password has been successfully reset."));
        }

        @Test
        @DisplayName("TC-AUTH-016 | Reset password returns 400 on wrong OTP")
        void resetPassword_wrongOtp_returns400() throws Exception {
            doThrow(new RuntimeException("Invalid OTP"))
                    .when(authService).verifyOtpAndResetPassword(anyString(), anyString(), anyString());

            mvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"r@user.com\",\"otp\":\"000000\",\"newPassword\":\"NewPass@1\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("Invalid OTP"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-AUTH-017..018  GET /api/auth/health
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-AUTH-017..018 | GET /api/auth/health")
    class HealthTests {

        @Test
        @DisplayName("TC-AUTH-017 | Health check returns 200 with status=UP")
        void health_returns200() throws Exception {
            mvc.perform(get("/api/auth/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("TC-AUTH-018 | Health endpoint requires no authentication")
        void health_isPubliclyAccessible() throws Exception {
            mvc.perform(get("/api/auth/health"))
               .andExpect(status().isOk());
        }
    }
}
