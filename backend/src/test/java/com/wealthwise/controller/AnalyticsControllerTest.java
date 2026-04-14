package com.wealthwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealthwise.config.TestSecurityConfig;
import com.wealthwise.security.JwtAuthenticationFilter;
import com.wealthwise.service.AnalyticsService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — AnalyticsController Web Layer Tests
 *  Test Suite ID : TS-ANA-001
 *  Coverage      : Risk Profile, SIP Intelligence, Overlap, Risk Profile PATCH
 * ─────────────────────────────────────────────────────────────────────────────
 */
@WebMvcTest(
    value = AnalyticsController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class
    )
)
@Import(TestSecurityConfig.class)
@DisplayName("AnalyticsController Tests")
class AnalyticsControllerTest {

    @Autowired MockMvc          mvc;
    @Autowired ObjectMapper     objectMapper;
    @MockBean  AnalyticsService analyticsService;

    /** Injects userId=1 as the controller expects (set by JwtAuthenticationFilter in production). */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    withUser(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.requestAttr("userId", 1L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-ANA-001..005  GET /api/analytics/risk
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-ANA-001..005 | GET /api/analytics/risk")
    class RiskProfileGetTests {

        @Test
        @DisplayName("TC-ANA-001 | Returns 200 with riskScore and category")
        void getRiskProfile_returns200() throws Exception {
            when(analyticsService.getRiskProfile(1L))
                    .thenReturn(Map.of("riskScore", 72, "category", "AGGRESSIVE"));

            mvc.perform(withUser(get("/api/analytics/risk")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.riskScore").value(72))
               .andExpect(jsonPath("$.category").value("AGGRESSIVE"));
        }

        @Test
        @DisplayName("TC-ANA-002 | Returns 400 with error message when service throws")
        void getRiskProfile_returns400OnError() throws Exception {
            when(analyticsService.getRiskProfile(1L))
                    .thenThrow(new RuntimeException("No data"));

            mvc.perform(withUser(get("/api/analytics/risk")))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("No data"));
        }

        @Test
        @DisplayName("TC-ANA-003 | Response contains volatility and sharpeRatio fields")
        void getRiskProfile_responseHasExpectedKeys() throws Exception {
            when(analyticsService.getRiskProfile(1L))
                    .thenReturn(Map.of(
                        "riskScore", 65, "category", "MODERATE",
                        "volatility", 0.18, "sharpeRatio", 1.25
                    ));

            mvc.perform(withUser(get("/api/analytics/risk")))
               .andExpect(jsonPath("$.category").value("MODERATE"))
               .andExpect(jsonPath("$.volatility").exists())
               .andExpect(jsonPath("$.sharpeRatio").exists());
        }

        @Test
        @DisplayName("TC-ANA-004 | AnalyticsService.getRiskProfile called exactly once")
        void getRiskProfile_serviceCalledOnce() throws Exception {
            when(analyticsService.getRiskProfile(1L)).thenReturn(Map.of("riskScore", 50));

            mvc.perform(withUser(get("/api/analytics/risk")));
            verify(analyticsService, times(1)).getRiskProfile(1L);
        }

        @Test
        @DisplayName("TC-ANA-005 | Response Content-Type is application/json")
        void getRiskProfile_contentTypeIsJson() throws Exception {
            when(analyticsService.getRiskProfile(1L))
                    .thenReturn(Map.of("riskScore", 40, "category", "CONSERVATIVE"));

            mvc.perform(withUser(get("/api/analytics/risk")))
               .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-ANA-006..009  GET /api/analytics/sip
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-ANA-006..009 | GET /api/analytics/sip")
    class SipIntelligenceTests {

        @Test
        @DisplayName("TC-ANA-006 | Returns 200 with SIP intelligence payload")
        void getSipIntelligence_returns200() throws Exception {
            when(analyticsService.getSipIntelligence(1L))
                    .thenReturn(Map.of("activeSips", 3, "totalOutflow", 15000));

            mvc.perform(withUser(get("/api/analytics/sip")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.activeSips").value(3));
        }

        @Test
        @DisplayName("TC-ANA-007 | Returns 400 with error message when service throws")
        void getSipIntelligence_returns400OnError() throws Exception {
            when(analyticsService.getSipIntelligence(1L))
                    .thenThrow(new RuntimeException("SIP calculation failed"));

            mvc.perform(withUser(get("/api/analytics/sip")))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("SIP calculation failed"));
        }

        @Test
        @DisplayName("TC-ANA-008 | Response contains streak and projection values")
        void getSipIntelligence_responseHasKeys() throws Exception {
            when(analyticsService.getSipIntelligence(1L))
                    .thenReturn(Map.of("streak", 12, "projection", 500000));

            mvc.perform(withUser(get("/api/analytics/sip")))
               .andExpect(jsonPath("$.streak").value(12))
               .andExpect(jsonPath("$.projection").value(500000));
        }

        @Test
        @DisplayName("TC-ANA-009 | Response Content-Type is application/json")
        void getSipIntelligence_contentTypeIsJson() throws Exception {
            when(analyticsService.getSipIntelligence(1L))
                    .thenReturn(Map.of("activeSips", 2));

            mvc.perform(withUser(get("/api/analytics/sip")))
               .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-ANA-010..013  GET /api/analytics/overlap
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-ANA-010..013 | GET /api/analytics/overlap")
    class OverlapTests {

        @Test
        @DisplayName("TC-ANA-010 | Returns 200 with overlap matrix payload")
        void getFundOverlap_returns200() throws Exception {
            when(analyticsService.getFundOverlapMatrix(1L))
                    .thenReturn(Map.of("funds", List.of(), "overlapMatrix", List.of()));

            mvc.perform(withUser(get("/api/analytics/overlap")))
               .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-ANA-011 | Returns 400 with error when overlap service throws")
        void getFundOverlap_returns400OnError() throws Exception {
            when(analyticsService.getFundOverlapMatrix(1L))
                    .thenThrow(new RuntimeException("Overlap calc failed"));

            mvc.perform(withUser(get("/api/analytics/overlap")))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("Overlap calc failed"));
        }

        @Test
        @DisplayName("TC-ANA-012 | Response contains funds array")
        void getFundOverlap_hasFundsArray() throws Exception {
            when(analyticsService.getFundOverlapMatrix(1L))
                    .thenReturn(Map.of("funds", List.of("FundA", "FundB"), "overlapMatrix", List.of()));

            mvc.perform(withUser(get("/api/analytics/overlap")))
               .andExpect(jsonPath("$.funds").isArray())
               .andExpect(jsonPath("$.funds[0]").value("FundA"));
        }

        @Test
        @DisplayName("TC-ANA-013 | Service is called with correct userId from request attribute")
        void getFundOverlap_serviceCalledWithUserId() throws Exception {
            when(analyticsService.getFundOverlapMatrix(1L))
                    .thenReturn(Map.of("funds", List.of()));

            mvc.perform(withUser(get("/api/analytics/overlap")));
            verify(analyticsService).getFundOverlapMatrix(1L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-ANA-014..018  PATCH /api/analytics/risk-profile
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-ANA-014..018 | PATCH /api/analytics/risk-profile")
    class RiskProfilePatchTests {

        @Test
        @DisplayName("TC-ANA-014 | Valid riskProfile returns 200 with confirmation message")
        void saveRiskProfile_returns200() throws Exception {
            doNothing().when(analyticsService).saveRiskProfile(1L, "MODERATE");

            mvc.perform(withUser(patch("/api/analytics/risk-profile"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"riskProfile\":\"MODERATE\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("Risk profile updated to MODERATE"));
        }

        @Test
        @DisplayName("TC-ANA-015 | Missing riskProfile field returns 400")
        void saveRiskProfile_returns400WhenFieldMissing() throws Exception {
            mvc.perform(withUser(patch("/api/analytics/risk-profile"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("riskProfile field is required"));
        }

        @Test
        @DisplayName("TC-ANA-016 | Blank riskProfile returns 400")
        void saveRiskProfile_returns400WhenFieldBlank() throws Exception {
            mvc.perform(withUser(patch("/api/analytics/risk-profile"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"riskProfile\":\"\"}"))
               .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-ANA-017 | Service called with correct userId and profile value")
        void saveRiskProfile_serviceCalledWithCorrectArgs() throws Exception {
            doNothing().when(analyticsService).saveRiskProfile(1L, "AGGRESSIVE");

            mvc.perform(withUser(patch("/api/analytics/risk-profile"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"riskProfile\":\"AGGRESSIVE\"}"));

            verify(analyticsService).saveRiskProfile(1L, "AGGRESSIVE");
        }

        @Test
        @DisplayName("TC-ANA-018 | Returns 400 when saveRiskProfile service throws")
        void saveRiskProfile_returns400OnServiceError() throws Exception {
            doThrow(new RuntimeException("Save failed"))
                    .when(analyticsService).saveRiskProfile(anyLong(), anyString());

            mvc.perform(withUser(patch("/api/analytics/risk-profile"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"riskProfile\":\"MODERATE\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("Save failed"));
        }
    }
}
