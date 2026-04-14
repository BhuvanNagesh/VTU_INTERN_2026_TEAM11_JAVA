package com.wealthwise.security;

import com.wealthwise.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import com.wealthwise.model.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — JwtAuthenticationFilter Unit Tests
 *  Test Suite ID : TS-SEC-001
 *
 *  Tests the JWT filter in isolation using MockHttpServletRequest/Response.
 *  Validates: token extraction, user resolution, 401 responses, pass-through.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Security Tests")
class JwtAuthenticationFilterTest {

    @Mock    JwtService      jwtService;
    @Mock    UserRepository  userRepository;
    @InjectMocks JwtAuthenticationFilter filter;

    MockHttpServletRequest  request;
    MockHttpServletResponse response;
    MockFilterChain         chain;

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain    = new MockFilterChain();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-SEC-001..004  Public path bypass (shouldNotFilter)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-SEC-001..004 | Public Path Bypass")
    class PublicPathTests {

        @Test
        @DisplayName("TC-SEC-001 | /api/auth/ paths bypass JWT filter entirely")
        void authPaths_bypassFilter() throws Exception {
            request.setServletPath("/api/auth/signin");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-002 | /api/schemes paths bypass JWT filter")
        void schemesPaths_bypassFilter() throws Exception {
            request.setServletPath("/api/schemes/search");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-003 | /api/nav paths bypass JWT filter")
        void navPaths_bypassFilter() throws Exception {
            request.setServletPath("/api/nav/119598");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-004 | /api/analytics requires JWT (not bypassed)")
        void analyticsPaths_requireJwt() throws Exception {
            request.setServletPath("/api/analytics/risk");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-SEC-005..008  Missing / Malformed Authorization Header
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-SEC-005..008 | Missing / Malformed Auth Header")
    class MissingTokenTests {

        @Test
        @DisplayName("TC-SEC-005 | No Authorization header → 401 with error JSON")
        void noHeader_returns401() throws Exception {
            request.setServletPath("/api/analytics/risk");
            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("error");
        }

        @Test
        @DisplayName("TC-SEC-006 | Authorization header without Bearer prefix → 401")
        void wrongPrefix_returns401() throws Exception {
            request.setServletPath("/api/transactions");
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("TC-SEC-007 | Empty Bearer token → 401")
        void emptyBearer_returns401() throws Exception {
            request.setServletPath("/api/analytics/risk");
            request.addHeader("Authorization", "Bearer ");
            when(jwtService.extractEmail(anyString())).thenThrow(new io.jsonwebtoken.JwtException("invalid"));
            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("TC-SEC-008 | Filter does NOT call chain.doFilter() on missing header (short-circuit)")
        void noHeader_chainNotCalled() throws Exception {
            request.setServletPath("/api/analytics/risk");
            filter.doFilterInternal(request, response, chain);

            // MockFilterChain tracks invocations; if chain was called, getRequest() is non-null
            assertThat(chain.getRequest()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-SEC-009..012  Valid Token Path
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-SEC-009..012 | Valid JWT Flow")
    class ValidTokenTests {

        @Test
        @DisplayName("TC-SEC-009 | Valid token → userId set as request attribute")
        void validToken_setsUserId() throws Exception {
            User user = new User();
            user.setId(42L);
            user.setEmail("test@ww.com");

            request.setServletPath("/api/analytics/risk");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            when(jwtService.extractEmail("valid.jwt.token")).thenReturn("test@ww.com");
            when(userRepository.findByEmail("test@ww.com")).thenReturn(Optional.of(user));

            filter.doFilterInternal(request, response, chain);

            assertThat(request.getAttribute("userId")).isEqualTo(42L);
        }

        @Test
        @DisplayName("TC-SEC-010 | Valid token → filter chain continues (controller executes)")
        void validToken_chainCalled() throws Exception {
            User user = new User();
            user.setId(7L);
            user.setEmail("chain@ww.com");

            request.setServletPath("/api/transactions");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            when(jwtService.extractEmail("valid.jwt.token")).thenReturn("chain@ww.com");
            when(userRepository.findByEmail("chain@ww.com")).thenReturn(Optional.of(user));

            filter.doFilterInternal(request, response, chain);

            // Chain was called — request is now present
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("TC-SEC-011 | Valid token → response status is NOT 401")
        void validToken_notUnauthorized() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setEmail("success@ww.com");

            request.setServletPath("/api/analytics/sip");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            when(jwtService.extractEmail("valid.jwt.token")).thenReturn("success@ww.com");
            when(userRepository.findByEmail("success@ww.com")).thenReturn(Optional.of(user));

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isNotEqualTo(401);
        }

        @Test
        @DisplayName("TC-SEC-012 | JwtService.extractEmail called exactly once per request")
        void validToken_extractEmailCalledOnce() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setEmail("once@ww.com");

            request.setServletPath("/api/goals");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            when(jwtService.extractEmail("valid.jwt.token")).thenReturn("once@ww.com");
            when(userRepository.findByEmail("once@ww.com")).thenReturn(Optional.of(user));

            filter.doFilterInternal(request, response, chain);

            verify(jwtService, times(1)).extractEmail("valid.jwt.token");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-SEC-013..015  Invalid/Tampered Token
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-SEC-013..015 | Invalid / Tampered Token")
    class InvalidTokenTests {

        @Test
        @DisplayName("TC-SEC-013 | Tampered JWT throws JwtException → 401 response")
        void tamperedToken_returns401() throws Exception {
            request.setServletPath("/api/analytics/risk");
            request.addHeader("Authorization", "Bearer tampered.jwt.token");
            when(jwtService.extractEmail("tampered.jwt.token"))
                .thenThrow(new io.jsonwebtoken.JwtException("Signature mismatch"));

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("error");
        }

        @Test
        @DisplayName("TC-SEC-014 | Token for unknown user (deleted account) → 401")
        void unknownUser_returns401() throws Exception {
            request.setServletPath("/api/transactions");
            request.addHeader("Authorization", "Bearer valid.jwt.orphan");
            when(jwtService.extractEmail("valid.jwt.orphan")).thenReturn("deleted@ww.com");
            when(userRepository.findByEmail("deleted@ww.com")).thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("TC-SEC-015 | Response Content-Type is application/json on 401")
        void unauthorizedResponse_isJson() throws Exception {
            request.setServletPath("/api/analytics/risk");
            filter.doFilterInternal(request, response, chain);

            assertThat(response.getContentType()).contains("application/json");
        }
    }
}
