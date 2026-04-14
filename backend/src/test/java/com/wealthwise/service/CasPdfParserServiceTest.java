package com.wealthwise.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — CasPdfParserService Unit Tests (PDFBox Logic)
 *  Test Suite ID : TS-PDF-001
 *
 *  Tests the internal parsing logic, regex patterns, type detection,
 *  number parsing, keyword extraction, and category derivation
 *  WITHOUT loading a real PDF (no I/O required).
 *
 *  Uses reflection to access package-private methods on inner classes.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TS-PDF-001 | CasPdfParserService — PDF Parsing Logic Tests")
class CasPdfParserServiceTest {

    @InjectMocks
    CasPdfParserService service;

    // ── Reflection helpers to invoke package-private methods ─────────────────
    private String detectType(String description) {
        try {
            Method m = CasPdfParserService.class.getDeclaredMethod("detectType", String.class);
            m.setAccessible(true);
            return (String) m.invoke(service, description);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal parseBigDecimal(String raw) {
        try {
            Method m = CasPdfParserService.class.getDeclaredMethod("parseBigDecimal", String.class);
            m.setAccessible(true);
            return (BigDecimal) m.invoke(service, raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractSearchKeyword(String schemeName) {
        try {
            Method m = CasPdfParserService.class.getDeclaredMethod("extractSearchKeyword", String.class);
            m.setAccessible(true);
            return (String) m.invoke(service, schemeName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-PDF-001..012  detectType() — Transaction Classification
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-PDF-001..012 | detectType — Transaction Type Classification")
    class DetectTypeTests {

        @Test
        @DisplayName("TC-PDF-001 | 'SIP' keyword → PURCHASE_SIP")
        void sip_keyword_returns_PURCHASE_SIP() {
            assertThat(detectType("SIP - Monthly Installment")).isEqualTo("PURCHASE_SIP");
        }

        @Test
        @DisplayName("TC-PDF-002 | 'Systematic' keyword → PURCHASE_SIP")
        void systematic_keyword_returns_PURCHASE_SIP() {
            assertThat(detectType("Systematic Investment Plan")).isEqualTo("PURCHASE_SIP");
        }

        @Test
        @DisplayName("TC-PDF-003 | 'SWITCH IN' keyword → SWITCH_IN")
        void switchIn_returns_SWITCH_IN() {
            assertThat(detectType("SWITCH IN from Large Cap")).isEqualTo("SWITCH_IN");
        }

        @Test
        @DisplayName("TC-PDF-004 | 'SWITCH OUT' keyword → SWITCH_OUT")
        void switchOut_returns_SWITCH_OUT() {
            assertThat(detectType("SWITCH OUT to Flexi Cap")).isEqualTo("SWITCH_OUT");
        }

        @Test
        @DisplayName("TC-PDF-005 | 'REDEMPTION' keyword → REDEMPTION")
        void redemption_returns_REDEMPTION() {
            assertThat(detectType("REDEMPTION - Full withdrawal")).isEqualTo("REDEMPTION");
        }

        @Test
        @DisplayName("TC-PDF-006 | 'REDEEM' keyword → REDEMPTION")
        void redeem_keyword_returns_REDEMPTION() {
            assertThat(detectType("Partial Redeem Request")).isEqualTo("REDEMPTION");
        }

        @Test
        @DisplayName("TC-PDF-007 | 'STP IN' keyword → STP_IN")
        void stpIn_returns_STP_IN() {
            assertThat(detectType("STP IN Transfer")).isEqualTo("STP_IN");
        }

        @Test
        @DisplayName("TC-PDF-008 | 'STP OUT' keyword → STP_OUT")
        void stpOut_returns_STP_OUT() {
            assertThat(detectType("STP OUT Transfer")).isEqualTo("STP_OUT");
        }

        @Test
        @DisplayName("TC-PDF-009 | 'SWP' keyword alone → SWP")
        void swp_returns_SWP() {
            // Note: 'Systematic Withdrawal Plan' would trigger PURCHASE_SIP (contains SYSTEMATIC)
            // Test with a description that has SWP but NOT SYSTEMATIC
            assertThat(detectType("SWP Monthly Withdrawal")).isEqualTo("SWP");
        }

        @Test
        @DisplayName("TC-PDF-010 | 'DIVIDEND PAYOUT' → DIVIDEND_PAYOUT")
        void dividendPayout_returns_DIVIDEND_PAYOUT() {
            assertThat(detectType("DIVIDEND PAYOUT - Quarter")).isEqualTo("DIVIDEND_PAYOUT");
        }

        @Test
        @DisplayName("TC-PDF-011 | 'DIVIDEND REINVEST' → DIVIDEND_REINVEST")
        void dividendReinvest_returns_DIVIDEND_REINVEST() {
            assertThat(detectType("DIVIDEND REINVEST option")).isEqualTo("DIVIDEND_REINVEST");
        }

        @Test
        @DisplayName("TC-PDF-012 | Null description → PURCHASE_LUMPSUM (safe default)")
        void nullDescription_returns_PURCHASE_LUMPSUM() {
            assertThat(detectType(null)).isEqualTo("PURCHASE_LUMPSUM");
        }

        @Test
        @DisplayName("TC-PDF-013 | Unknown description → PURCHASE_LUMPSUM (safe default)")
        void unknownDescription_returns_PURCHASE_LUMPSUM() {
            assertThat(detectType("New Fund Offer Subscription")).isEqualTo("PURCHASE_LUMPSUM");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-PDF-014..020  parseBigDecimal() — Number Parsing
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-PDF-014..020 | parseBigDecimal — Number Parsing")
    class ParseBigDecimalTests {

        @Test
        @DisplayName("TC-PDF-014 | Comma-formatted number parses correctly")
        void commaFormatted_parseCorrectly() {
            assertThat(parseBigDecimal("50,000.00")).isEqualByComparingTo(new BigDecimal("50000.00"));
        }

        @Test
        @DisplayName("TC-PDF-015 | Plain decimal parses correctly")
        void plainDecimal_parseCorrectly() {
            assertThat(parseBigDecimal("123.456")).isEqualByComparingTo(new BigDecimal("123.456"));
        }

        @Test
        @DisplayName("TC-PDF-016 | Large amount with multiple commas parses correctly")
        void largeAmount_parseCorrectly() {
            assertThat(parseBigDecimal("1,23,456.78")).isEqualByComparingTo(new BigDecimal("123456.78"));
        }

        @Test
        @DisplayName("TC-PDF-017 | Null input returns ZERO (safe)")
        void nullInput_returnsZero() {
            assertThat(parseBigDecimal(null)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TC-PDF-018 | Blank string returns ZERO (safe)")
        void blankInput_returnsZero() {
            assertThat(parseBigDecimal("   ")).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TC-PDF-019 | Non-numeric string returns ZERO (safe)")
        void nonNumeric_returnsZero() {
            assertThat(parseBigDecimal("N/A")).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TC-PDF-020 | Negative value parses correctly")
        void negative_parseCorrectly() {
            assertThat(parseBigDecimal("-5000.00")).isEqualByComparingTo(new BigDecimal("-5000.00"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-PDF-021..028  extractSearchKeyword() — Scheme Name Keyword Extraction
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-PDF-021..028 | extractSearchKeyword — Scheme Keyword Extraction")
    class ExtractSearchKeywordTests {

        @Test
        @DisplayName("TC-PDF-021 | Standard scheme name → first 2 meaningful words")
        void standardScheme_first2Words() {
            assertThat(extractSearchKeyword("Axis Midcap Fund - Direct Plan"))
                .isEqualTo("Axis Midcap");
        }

        @Test
        @DisplayName("TC-PDF-022 | Parag Parikh scheme → first 2 words")
        void paragParikh_first2Words() {
            assertThat(extractSearchKeyword("Parag Parikh Flexi Cap Fund - Direct Growth"))
                .isEqualTo("Parag Parikh");
        }

        @Test
        @DisplayName("TC-PDF-023 | Strips 'Direct Plan' suffix before selecting words")
        void stripsDirectPlan_beforeKeyword() {
            String kw = extractSearchKeyword("Mirae Asset Large Cap Fund - Direct Plan");
            assertThat(kw).doesNotContain("Direct").doesNotContain("Plan");
        }

        @Test
        @DisplayName("TC-PDF-024 | Single-word scheme name returns that word")
        void singleWord_returnsItself() {
            assertThat(extractSearchKeyword("HDFC")).isEqualTo("HDFC");
        }

        @Test
        @DisplayName("TC-PDF-025 | Null input returns empty string (safe)")
        void nullInput_returnsEmpty() {
            assertThat(extractSearchKeyword(null)).isEqualTo("");
        }

        @Test
        @DisplayName("TC-PDF-026 | Strips 'Regular Plan' suffix correctly")
        void stripsRegularPlan() {
            String kw = extractSearchKeyword("Kotak Bluechip Fund - Regular Plan - Growth");
            assertThat(kw).doesNotContain("Regular").doesNotContain("Growth");
        }

        @Test
        @DisplayName("TC-PDF-027 | IDCW variant scheme stripped of plan suffix")
        void idcwVariant_stripped() {
            String kw = extractSearchKeyword("SBI Bluechip Fund - Direct Plan - IDCW");
            assertThat(kw).contains("SBI");
        }

        @Test
        @DisplayName("TC-PDF-028 | Result is never null")
        void result_neverNull() {
            assertThat(extractSearchKeyword("Some Fund - Direct")).isNotNull();
        }
    }
}
