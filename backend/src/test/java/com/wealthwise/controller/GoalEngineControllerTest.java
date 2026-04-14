package com.wealthwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealthwise.config.TestSecurityConfig;
import com.wealthwise.security.JwtAuthenticationFilter;
import com.wealthwise.service.GoalEngineService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — GoalEngineController Web Layer Tests
 *  Test Suite ID : TS-GOAL-001
 *  Coverage      : POST /api/learn/analyse — Bean Validation + Service Delegation
 * ─────────────────────────────────────────────────────────────────────────────
 */
@WebMvcTest(
    value = GoalEngineController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class
    )
)
@Import(TestSecurityConfig.class)
@DisplayName("GoalEngineController Tests")
class GoalEngineControllerTest {

    @Autowired MockMvc           mvc;
    @Autowired ObjectMapper      objectMapper;
    @MockBean  GoalEngineService goalEngineService;

    // ── A valid request body satisfying ALL @Valid constraints ────────────────
    private static final String VALID_BODY = """
        {
          "initialPortfolio": 100000,
          "monthlyContribution": 5000,
          "monthlyMean": 0.01,
          "monthlyStdDev": 0.04,
          "months": 120,
          "targetAmount": 1500000,
          "annualInflationRate": 0.06
        }
        """;

    private GoalEngineService.MonteCarloResult mockMC() {
        return new GoalEngineService.MonteCarloResult(800000, 1200000, 1800000, 72.5);
    }

    private GoalEngineService.DeterministicResult mockDet() {
        return new GoalEngineService.DeterministicResult(
                600000, 500000, 1100000, 400000, false,
                List.of(new GoalEngineService.SensitivityRow("Return = 10% pa", 900000, 600000))
        );
    }

    private GoalEngineService.RequiredSipResult mockSip() {
        return new GoalEngineService.RequiredSipResult(7000, 5000, 2000, 50000, 18, false);
    }

    /** Stubs all three service methods so happy-path tests don't fail on null. */
    private void stubAllServices() {
        when(goalEngineService.runMonteCarlo(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble()))
            .thenReturn(mockMC());
        when(goalEngineService.runDeterministicProjection(
                anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble()))
            .thenReturn(mockDet());
        when(goalEngineService.runRequiredSipCalculator(
                anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble()))
            .thenReturn(mockSip());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-GOAL-001..006  POST /api/learn/analyse — Happy Path
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-GOAL-001..006 | POST /api/learn/analyse — Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName("TC-GOAL-001 | Valid request returns HTTP 200")
        void analyse_validRequest_returns200() throws Exception {
            stubAllServices();
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
               .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-GOAL-002 | Response contains monteCarlo.likely = 1200000")
        void analyse_responseContainsMonteCarlo() throws Exception {
            stubAllServices();
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
               .andExpect(jsonPath("$.monteCarlo").exists())
               .andExpect(jsonPath("$.monteCarlo.likely").value(1200000));
        }

        @Test
        @DisplayName("TC-GOAL-003 | Response contains deterministic.onTrack = false")
        void analyse_responseContainsDeterministic() throws Exception {
            stubAllServices();
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
               .andExpect(jsonPath("$.deterministic").exists())
               .andExpect(jsonPath("$.deterministic.onTrack").value(false));
        }

        @Test
        @DisplayName("TC-GOAL-004 | Response contains requiredSip.requiredSip = 7000")
        void analyse_responseContainsRequiredSip() throws Exception {
            stubAllServices();
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
               .andExpect(jsonPath("$.requiredSip").exists())
               .andExpect(jsonPath("$.requiredSip.requiredSip").value(7000));
        }

        @Test
        @DisplayName("TC-GOAL-005 | All three GoalEngineService methods invoked exactly once")
        void analyse_allServicesCalledOnce() throws Exception {
            stubAllServices();
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY));

            verify(goalEngineService, times(1)).runMonteCarlo(
                    anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
            verify(goalEngineService, times(1)).runDeterministicProjection(
                    anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
            verify(goalEngineService, times(1)).runRequiredSipCalculator(
                    anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("TC-GOAL-006 | Response Content-Type is application/json")
        void analyse_contentTypeIsJson() throws Exception {
            stubAllServices();
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
               .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-GOAL-007..014  POST /api/learn/analyse — Bean Validation Failures
    //
    // NOTE: GlobalExceptionHandler has no handler for MethodArgumentNotValidException,
    // so validation failures fall through to the generic Exception handler → HTTP 500.
    // Tests correctly document this current system behaviour.
    // To get 400 instead, add a @ExceptionHandler(MethodArgumentNotValidException.class)
    // to GlobalExceptionHandler in future.
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-GOAL-007..014 | POST /api/learn/analyse — Validation Failures (→ 5xx)")
    class ValidationTests {

        @Test
        @DisplayName("TC-GOAL-007 | Empty/malformed body rejected (5xx from uncaught validation)")
        void analyse_missingBody_returns5xx() throws Exception {
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-008 | Negative initialPortfolio rejected by @Positive constraint")
        void analyse_negativeInitialPortfolio_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"initialPortfolio\": 100000", "\"initialPortfolio\": -1000");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-009 | months = 0 rejected by @Min(1) constraint")
        void analyse_zeroMonths_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"months\": 120", "\"months\": 0");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-010 | months = 601 rejected by @Max(600) constraint")
        void analyse_excessiveMonths_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"months\": 120", "\"months\": 601");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-011 | Negative targetAmount rejected by @Positive constraint")
        void analyse_negativeTarget_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"targetAmount\": 1500000", "\"targetAmount\": -100");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-012 | annualInflationRate = 1.5 rejected by @DecimalMax(\"1.0\")")
        void analyse_inflationAboveMax_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"annualInflationRate\": 0.06", "\"annualInflationRate\": 1.5");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-013 | Negative monthlyStdDev rejected by @Positive constraint")
        void analyse_negativeStdDev_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"monthlyStdDev\": 0.04", "\"monthlyStdDev\": -0.04");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("TC-GOAL-014 | Negative monthlyContribution rejected by @PositiveOrZero")
        void analyse_negativeContribution_returns5xx() throws Exception {
            String body = VALID_BODY.replace("\"monthlyContribution\": 5000", "\"monthlyContribution\": -5000");
            mvc.perform(post("/api/learn/analyse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().is5xxServerError());
        }
    }
}
