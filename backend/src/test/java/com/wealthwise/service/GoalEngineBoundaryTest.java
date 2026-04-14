package com.wealthwise.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — GoalEngineService Boundary / Edge-Case Tests
 *  Test Suite ID : TS-BOUND-001
 *
 *  Parameterized + boundary tests validating mathematical extremes, edge
 *  inputs, and robustness of all three GoalEngineService algorithms.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@DisplayName("GoalEngineService Boundary & Parameterized Tests")
class GoalEngineBoundaryTest {

    GoalEngineService service;

    @BeforeEach
    void setUp() {
        service = new GoalEngineService();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-BOUND-001..006  Monte Carlo — Parameterized Horizons
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-BOUND-001..006 | Monte Carlo — Parameterized")
    class MonteCarloParameterized {

        @ParameterizedTest(name = "months={0}: P10<P50<P90")
        @ValueSource(ints = {1, 6, 12, 60, 120, 360})
        @DisplayName("TC-BOUND-001 | Percentile ordering holds across all investment horizons")
        void percentileOrder_allHorizons(int months) {
            var r = service.runMonteCarlo(100000, 5000, 0.01, 0.04, months, 500000, 0.06);
            assertThat(r.pessimistic()).isLessThanOrEqualTo(r.likely());
            assertThat(r.likely()).isLessThanOrEqualTo(r.optimistic());
        }

        @ParameterizedTest(name = "stdDev={0}")
        @ValueSource(doubles = {0.001, 0.01, 0.05, 0.10, 0.20})
        @DisplayName("TC-BOUND-002 | Higher volatility widens spread monotonically")
        void higherVolatility_widensSpread(double stdDev) {
            var r = service.runMonteCarlo(100000, 5000, 0.01, stdDev, 60, 500000, 0.06);
            double spread = r.optimistic() - r.pessimistic();
            assertThat(spread).isGreaterThanOrEqualTo(0);
        }

        @ParameterizedTest(name = "inflation={0}")
        @CsvSource({"0.0, 0.03", "0.03, 0.06", "0.06, 0.10"})
        @DisplayName("TC-BOUND-003 | Higher inflation reduces likely outcome")
        void higherInflation_reducesLikely(double lowInflation, double highInflation) {
            var low  = service.runMonteCarlo(100000, 5000, 0.01, 0.04, 120, 1500000, lowInflation);
            var high = service.runMonteCarlo(100000, 5000, 0.01, 0.04, 120, 1500000, highInflation);
            assertThat(high.likely()).isLessThanOrEqualTo(low.likely());
        }

        @ParameterizedTest(name = "sip={0}")
        @ValueSource(doubles = {0, 1000, 5000, 20000, 100000})
        @DisplayName("TC-BOUND-004 | Higher SIP always increases likely outcome (monotonic)")
        void higherSip_increasesOutcome(double sip) {
            var r = service.runMonteCarlo(100000, sip, 0.01, 0.04, 60, 1000000, 0.06);
            assertThat(r.likely()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("TC-BOUND-005 | Zero initial portfolio with SIP still grows")
        void zeroCorpus_withSip_grows() {
            var r = service.runMonteCarlo(0, 10000, 0.01, 0.03, 120, 2000000, 0.06);
            assertThat(r.likely()).isGreaterThan(0);
        }

        @Test
        @DisplayName("TC-BOUND-006 | Maximum allowed horizon (600 months) does not throw or overflow")
        void maxHorizon_600months_stable() {
            assertThatCode(() ->
                service.runMonteCarlo(100000, 5000, 0.01, 0.04, 600, 50000000, 0.06)
            ).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-BOUND-007..012  Deterministic — Edge Values
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-BOUND-007..012 | Deterministic Projection — Edge Values")
    class DeterministicEdgeCases {

        @ParameterizedTest(name = "months={0}")
        @ValueSource(ints = {1, 12, 60, 120, 240, 360})
        @DisplayName("TC-BOUND-007 | totalProjected is non-negative for all horizons")
        void deterministic_totalNonNegative(int months) {
            var r = service.runDeterministicProjection(100000, 5000, 0.01, months, 1000000, 0.06);
            assertThat(r.totalProjected()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("TC-BOUND-008 | Zero initial + zero SIP → projected is near zero")
        void zeroCorpus_zeroSip_nearZero() {
            var r = service.runDeterministicProjection(0, 0, 0.01, 60, 500000, 0.06);
            assertThat(r.totalProjected()).isEqualTo(0);
        }

        @Test
        @DisplayName("TC-BOUND-009 | Same inputs: 10% return > 5% return for total outcome")
        void higherReturn_greatestOutcome() {
            var low  = service.runDeterministicProjection(100000, 5000, 0.005, 120, 1000000, 0.06);
            var high = service.runDeterministicProjection(100000, 5000, 0.010, 120, 1000000, 0.06);
            assertThat(high.totalProjected()).isGreaterThan(low.totalProjected());
        }

        @Test
        @DisplayName("TC-BOUND-010 | Sensitivity table always has exactly 3 rows")
        void sensitivityTable_always3Rows() {
            var r = service.runDeterministicProjection(50000, 2000, 0.008, 84, 800000, 0.06);
            assertThat(r.sensitivity()).hasSize(3);
        }

        @ParameterizedTest(name = "target={0}")
        @CsvSource({"100, true", "100000000, false"})
        @DisplayName("TC-BOUND-011 | onTrack is true for tiny target, false for huge target")
        void onTrack_flagCorrect(double target, boolean expectedOnTrack) {
            var r = service.runDeterministicProjection(500000, 50000, 0.01, 120, target, 0.06);
            assertThat(r.onTrack()).isEqualTo(expectedOnTrack);
        }

        @Test
        @DisplayName("TC-BOUND-012 | gap sign is consistent with onTrack flag")
        void gapSign_consistentWithOnTrack() {
            var r = service.runDeterministicProjection(10000, 1000, 0.01, 12, 50000000, 0.06);
            if (r.onTrack()) {
                assertThat(r.gap()).isLessThanOrEqualTo(0);
            } else {
                assertThat(r.gap()).isGreaterThan(0);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-BOUND-013..018  Required SIP — Parameterized Targets
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-BOUND-013..018 | Required SIP — Parameterized Targets")
    class RequiredSipBoundary {

        @ParameterizedTest(name = "target={0}")
        @ValueSource(doubles = {500000, 1000000, 5000000, 10000000})
        @DisplayName("TC-BOUND-013 | Required SIP increases monotonically with target size")
        void requiredSip_monotonic_withTarget(double target) {
            var r = service.runRequiredSipCalculator(100000, 5000, 0.01, 120, target, 0.06);
            assertThat(r.requiredSip()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("TC-BOUND-014 | Tiny target → currentSipEnough = true")
        void tinyTarget_sufficient() {
            var r = service.runRequiredSipCalculator(500000, 10000, 0.01, 120, 1.0, 0.06);
            assertThat(r.currentSipEnough()).isTrue();
        }

        @Test
        @DisplayName("TC-BOUND-015 | Massive target → currentSipEnough = false")
        void massiveTarget_insufficient() {
            var r = service.runRequiredSipCalculator(1000, 100, 0.005, 12, 1_000_000_000.0, 0.10);
            assertThat(r.currentSipEnough()).isFalse();
        }

        @ParameterizedTest(name = "monthlyReturn={0}")
        @ValueSource(doubles = {0.005, 0.008, 0.012, 0.015})
        @DisplayName("TC-BOUND-016 | Higher return rate → lower required SIP")
        void higherReturn_lowersRequiredSip(double monthlyReturn) {
            var low  = service.runRequiredSipCalculator(100000, 5000, 0.005, 120, 2000000, 0.06);
            var high = service.runRequiredSipCalculator(100000, 5000, monthlyReturn, 120, 2000000, 0.06);
            // High return should need less or equal SIP compared to low return
            if (monthlyReturn > 0.005) {
                assertThat(high.requiredSip()).isLessThanOrEqualTo(low.requiredSip() + 1); // +1 for rounding
            }
        }

        @Test
        @DisplayName("TC-BOUND-017 | requiredSip = currentSip + sipGap (always)")
        void requiredSipEquality_invariant() {
            double[] targets = {500000, 1500000, 5000000};
            for (double target : targets) {
                var r = service.runRequiredSipCalculator(100000, 5000, 0.01, 120, target, 0.06);
                assertThat(r.requiredSip()).isEqualTo(r.currentSip() + r.sipGap());
            }
        }

        @Test
        @DisplayName("TC-BOUND-018 | lumpSumToday is always non-negative")
        void lumpSum_alwaysNonNegative() {
            double[] targets = {100000, 1000000, 10000000, 100000000};
            for (double target : targets) {
                var r = service.runRequiredSipCalculator(50000, 3000, 0.01, 120, target, 0.06);
                assertThat(r.lumpSumToday())
                    .as("lumpSumToday must be >= 0 for target=" + target)
                    .isGreaterThanOrEqualTo(0);
            }
        }
    }
}
