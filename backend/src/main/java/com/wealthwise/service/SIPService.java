package com.wealthwise.service;

import com.wealthwise.dto.SIPComparisonResponse;
import com.wealthwise.dto.SIPDashboardResponse;
import com.wealthwise.dto.TopUpResponse;
import com.wealthwise.model.Transaction;
import com.wealthwise.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SIP Intelligence Service — all calculations are wired to real user data.
 *
 * Module coverage:
 *   M13.1 — SIP Dashboard (getDashboard)
 *   M13.2 — SIP vs Lumpsum aggregate comparison (compare)
 *   M13.3 — SIP Day Optimizer (optimize)
 *   M13.4 — SIP Top-Up / Step-Up Projection (calculateTopUp)
 */
@Service
public class SIPService {

    private static final double ANNUAL_RETURN  = 0.12; // assumed 12% p.a. for projections
    private static final double STEP_UP_PCT    = 10.0; // 10% annual step-up
    private static final int    PROJECTION_YRS = 20;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService    transactionService;

    // ─── M13.1: SIP Dashboard ────────────────────────────────────────────────

    /**
     * Returns a real-time SIP dashboard by reading the user's SIP transactions.
     * Active SIP = at least one PURCHASE_SIP in the last 35 days (covers weekends/holidays).
     */
    public SIPDashboardResponse getDashboard(Long userId) {
        List<Transaction> allTxns =
            transactionRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId);

        // Active SIP = had a SIP installment within the last 60 days.
        // 60 days covers monthly SIPs even with weekends and processing delays.
        LocalDate cutoff = LocalDate.now().minusDays(60);
        Set<String>             activeSipCodes    = new HashSet<>();
        Set<String>             everHadSip        = new HashSet<>();
        Map<String, BigDecimal> latestSipAmounts  = new LinkedHashMap<>();

        for (Transaction t : allTxns) {
            if (!"PURCHASE_SIP".equals(t.getTransactionType())) continue;
            if (t.getSchemeAmfiCode() == null || t.getAmount() == null) continue;

            // putIfAbsent → list is desc-by-date, so first seen = most recent
            latestSipAmounts.putIfAbsent(t.getSchemeAmfiCode(), t.getAmount());
            everHadSip.add(t.getSchemeAmfiCode());

            if (t.getTransactionDate() != null && !t.getTransactionDate().isBefore(cutoff)) {
                activeSipCodes.add(t.getSchemeAmfiCode());
            }
        }

        // If all SIPs are from a historical CAS import (none in last 60 days),
        // show all SIP schemes so the dashboard is still useful.
        if (activeSipCodes.isEmpty() && !everHadSip.isEmpty()) {
            activeSipCodes.addAll(everHadSip);
        }

        int totalActiveSIPs = activeSipCodes.size();

        double monthlyOutflow = activeSipCodes.stream()
            .mapToDouble(code -> {
                BigDecimal amt = latestSipAmounts.get(code);
                return amt != null ? amt.doubleValue() : 0.0;
            })
            .sum();

        // SIP streak — consecutive months (portfolio-wide) with PURCHASE_SIP
        TreeSet<String> sipMonths = new TreeSet<>();
        for (Transaction t : allTxns) {
            if ("PURCHASE_SIP".equals(t.getTransactionType()) && t.getTransactionDate() != null) {
                sipMonths.add(t.getTransactionDate().getYear() + "-"
                    + String.format("%02d", t.getTransactionDate().getMonthValue()));
            }
        }
        int sipStreak = 0;
        // Start from the LATEST month that had a SIP (not the current month) so that the
        // streak is not broken simply because this month's SIP hasn't executed yet.
        // This matches the AnalyticsService.getSipIntelligence() streak calculation.
        String latestSipKey = sipMonths.isEmpty() ? null : sipMonths.last();
        YearMonth cursor = latestSipKey != null
            ? YearMonth.of(Integer.parseInt(latestSipKey.split("-")[0]),
                           Integer.parseInt(latestSipKey.split("-")[1]))
            : YearMonth.now();
        while (sipMonths.contains(cursor.getYear() + "-"
                + String.format("%02d", cursor.getMonthValue()))) {
            sipStreak++;
            cursor = cursor.minusMonths(1);
        }
        String sipStreakStr = sipStreak > 0
            ? sipStreak + " consecutive month" + (sipStreak > 1 ? "s" : "")
            : "No SIPs recorded";

        // Future value of current monthly SIP at ANNUAL_RETURN over PROJECTION_YRS
        double projectedAmount = futureValue(monthlyOutflow, ANNUAL_RETURN, PROJECTION_YRS);

        // Next step-up: April 1st of next year (typical financial-year step-up date)
        String nextStepUpDate = LocalDate.now().plusYears(1).withMonth(4).withDayOfMonth(1).toString();

        String alert;
        if (totalActiveSIPs == 0) {
            alert = "No active SIPs detected. Consider starting a SIP today!";
        } else if (sipStreak >= 12) {
            alert = "🎉 Excellent! " + sipStreak + " months of disciplined investing!";
        } else if (sipStreak >= 6) {
            alert = "✅ Good going! Keep your SIPs consistent.";
        } else {
            alert = "💡 All SIPs on track. Keep investing every month!";
        }

        SIPDashboardResponse resp = new SIPDashboardResponse();
        resp.setTotalActiveSIPs(totalActiveSIPs);
        resp.setMonthlyOutflow(round(monthlyOutflow));
        resp.setNextStepUpDate(nextStepUpDate);
        resp.setProjectedAmount(round(projectedAmount));
        resp.setSipStreak(sipStreakStr);
        resp.setAlert(alert);
        return resp;
    }

    // ─── M13.2: Aggregate SIP vs Lumpsum Comparison ──────────────────────────

    public SIPComparisonResponse compare(Long userId) {
        List<String> schemes = transactionRepository.findDistinctSchemesByUserId(userId);
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        BigDecimal totalSipCurrentValue     = BigDecimal.ZERO;
        BigDecimal totalHypotheticalLumpsum = BigDecimal.ZERO;

        for (String amfiCode : schemes) {
            List<Transaction> txns = transactionRepository
                .findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);

            boolean hasSip = txns.stream()
                .anyMatch(t -> "PURCHASE_SIP".equals(t.getTransactionType()));
            if (!hasSip) continue;

            Map<String, Object> holding = portfolio.stream()
                .filter(h -> amfiCode.equals(h.get("schemeAmfiCode")))
                .findFirst().orElse(null);
            if (holding == null) continue;

            BigDecimal totalInvested = asBD(holding.getOrDefault("investedAmount", BigDecimal.ZERO));
            BigDecimal currentValue  = asBD(holding.getOrDefault("currentValue",  BigDecimal.ZERO));
            BigDecimal totalUnits    = asBD(holding.getOrDefault("units",         BigDecimal.ZERO));
            if (totalInvested.compareTo(BigDecimal.ZERO) == 0) continue;

            totalSipCurrentValue = totalSipCurrentValue.add(currentValue);

            Transaction first    = txns.get(0);
            BigDecimal  firstNav = first.getNav();
            if (firstNav != null && firstNav.compareTo(BigDecimal.ZERO) > 0
                    && totalUnits.compareTo(BigDecimal.ZERO) > 0) {
                // Prefer the scheme's actual current NAV over value÷units (which gives a
                // weighted-average lot NAV, not the real market price).
                BigDecimal latestNav = asBD(holding.getOrDefault("currentNav", BigDecimal.ZERO));
                if (latestNav.compareTo(BigDecimal.ZERO) <= 0) {
                    // Fallback: derive from value / units only when scheme NAV is unavailable
                    latestNav = currentValue.divide(totalUnits, 4, RoundingMode.HALF_UP);
                }
                BigDecimal hypUnits  = totalInvested.divide(firstNav, 4, RoundingMode.HALF_UP);
                totalHypotheticalLumpsum = totalHypotheticalLumpsum.add(hypUnits.multiply(latestNav));
            }
        }

        double sipVal  = totalSipCurrentValue.setScale(2, RoundingMode.HALF_UP).doubleValue();
        double lumpVal = totalHypotheticalLumpsum.setScale(2, RoundingMode.HALF_UP).doubleValue();
        String winner  = sipVal >= lumpVal ? "SIP" : "Lumpsum";
        double diff    = round(Math.abs(sipVal - lumpVal));

        return new SIPComparisonResponse(sipVal, lumpVal, winner, diff);
    }

    // ─── M13.3: SIP Day Optimizer ────────────────────────────────────────────

    /**
     * Analyzes the user's actual SIP transaction history to find which day-of-month
     * historically returned the most units per rupee invested (i.e. lowest average NAV).
     *
     * Falls back to general market guidance if insufficient data (< 3 SIP transactions).
     */
    public Map<String, Object> optimize(Long userId) {
        List<Transaction> allTxns =
            transactionRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId);

        // Group SIP transactions by day-of-month, collect NAVs for each day
        Map<Integer, List<BigDecimal>> navsByDay = new TreeMap<>();
        for (Transaction t : allTxns) {
            if (!"PURCHASE_SIP".equals(t.getTransactionType())) continue;
            if (t.getNav() == null || t.getNav().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (t.getTransactionDate() == null) continue;
            int day = t.getTransactionDate().getDayOfMonth();
            navsByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(t.getNav());
        }

        if (navsByDay.size() < 2) {
            // Not enough variation to give a personalized answer — fall back to general guidance
            return Map.of(
                "personalized",  false,
                "bestDays",      List.of(1, 5, 7),
                "avoid",         "Last 3 business days of each month",
                "tip",           "Based on historical Indian equity market patterns: SIP on 1st, " +
                                 "5th, or 7th of month often captures marginal post-settlement dips. " +
                                 "Track more SIPs to get a personalized recommendation.",
                "dataSuffix",    "General guidance (need ≥2 different SIP days for personal analysis)"
            );
        }

        // Find day with lowest average NAV
        int    bestDay    = -1;
        double bestAvgNav = Double.MAX_VALUE;
        Map<Integer, Double> avgNavPerDay = new LinkedHashMap<>();

        for (Map.Entry<Integer, List<BigDecimal>> entry : navsByDay.entrySet()) {
            double avg = entry.getValue().stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(Double.MAX_VALUE);
            avgNavPerDay.put(entry.getKey(), Math.round(avg * 100.0) / 100.0);
            if (avg < bestAvgNav) {
                bestAvgNav = avg;
                bestDay    = entry.getKey();
            }
        }

        return Map.of(
            "personalized",   true,
            "bestDay",        bestDay,
            "bestDays",       List.of(bestDay),
            "avgNavPerDay",   avgNavPerDay,
            "tip",            "Based on your actual SIP history, day " + bestDay +
                              " of the month has given you the lowest average NAV (₹" +
                              String.format("%.2f", bestAvgNav) + "), meaning more units per rupee.",
            "dataSuffix",     "Personalized — based on " +
                              navsByDay.values().stream().mapToInt(List::size).sum() + " SIP transactions"
        );
    }

    // ─── M13.4: SIP Top-Up / Step-Up Projection ──────────────────────────────

    public TopUpResponse calculateTopUp(Long userId) {
        SIPDashboardResponse dashboard = getDashboard(userId);
        double monthlySIP = dashboard.getMonthlyOutflow();
        if (monthlySIP <= 0) {
            monthlySIP = 5000.0;
        }

        double monthlyRate = ANNUAL_RETURN / 12;
        int    months      = PROJECTION_YRS * 12;

        double withoutTopUp = futureValue(monthlySIP, ANNUAL_RETURN, PROJECTION_YRS);

        double withTopUp   = 0;
        double currentSIP  = monthlySIP;
        for (int year = 1; year <= PROJECTION_YRS; year++) {
            for (int m = 0; m < 12; m++) {
                withTopUp = (withTopUp + currentSIP) * (1 + monthlyRate);
            }
            currentSIP *= (1 + STEP_UP_PCT / 100.0);
        }

        double difference = withTopUp - withoutTopUp;

        return new TopUpResponse(
            round(monthlySIP),
            round(withoutTopUp),
            round(withTopUp),
            round(difference),
            STEP_UP_PCT,
            PROJECTION_YRS
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double futureValue(double monthlyPayment, double annualRate, int years) {
        if (monthlyPayment <= 0) return 0;
        double r = annualRate / 12;
        int    n = years * 12;
        return monthlyPayment * (Math.pow(1 + r, n) - 1) / r * (1 + r);
    }

    private BigDecimal asBD(Object val) {
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number)     return BigDecimal.valueOf(((Number) val).doubleValue());
        return BigDecimal.ZERO;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
