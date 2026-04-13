package com.wealthwise.service;

import com.wealthwise.model.Transaction;
import com.wealthwise.repository.InvestmentLotRepository;
import com.wealthwise.repository.SchemeRepository;
import com.wealthwise.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

@Service
public class ReturnsService {

    private static final Logger log = Logger.getLogger(ReturnsService.class.getName());
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final double XIRR_TOLERANCE = 1e-7;
    private static final int XIRR_MAX_ITERATIONS = 1000;

    @Autowired private TransactionRepository transactionRepo;
    @Autowired private InvestmentLotRepository lotRepo;
    @Autowired private SchemeRepository schemeRepo;
    @Autowired private TransactionService transactionService;

    // ─── Absolute Return ─────────────────────────────────────────────────────

    /**
     * ((currentValue - investedValue) / investedValue) × 100
     */
    public BigDecimal absoluteReturn(BigDecimal invested, BigDecimal current) {
        if (invested == null || invested.compareTo(BigDecimal.ZERO) == 0) return null;
        if (current == null) current = BigDecimal.ZERO;
        return current.subtract(invested)
            .divide(invested, 8, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP);
    }

    // ─── CAGR ────────────────────────────────────────────────────────────────

    /**
     * (finalValue / initialValue)^(1/years) - 1
     * Valid only for single lumpsum; returns null if duration < 30 days.
     */
    public BigDecimal cagr(BigDecimal initial, BigDecimal finalVal, long days) {
        if (days < 30) return null;
        if (initial == null || initial.compareTo(BigDecimal.ZERO) <= 0) return null;
        if (finalVal == null || finalVal.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.valueOf(-100);

        double years = days / 365.25;
        double ratio = finalVal.divide(initial, 10, RoundingMode.HALF_UP).doubleValue();
        double result = Math.pow(ratio, 1.0 / years) - 1.0;

        return BigDecimal.valueOf(result * 100).setScale(2, RoundingMode.HALF_UP);
    }

    // ─── XIRR ────────────────────────────────────────────────────────────────

    /**
     * Newton-Raphson method with bisection fallback.
     * cashFlows: (date, amount) where purchases < 0, redemptions/current value > 0
     */
    public BigDecimal xirr(List<CashFlow> cashFlows) {
        if (cashFlows == null || cashFlows.size() < 2) return null;

        boolean hasNeg = cashFlows.stream().anyMatch(cf -> cf.amount.compareTo(BigDecimal.ZERO) < 0);
        boolean hasPos = cashFlows.stream().anyMatch(cf -> cf.amount.compareTo(BigDecimal.ZERO) > 0);
        if (!hasNeg || !hasPos) return null;

        LocalDate baseDate = cashFlows.get(0).date;

        // Convert to double arrays for performance
        double[] amounts = cashFlows.stream().mapToDouble(cf -> cf.amount.doubleValue()).toArray();
        double[] years = cashFlows.stream()
            .mapToDouble(cf -> ChronoUnit.DAYS.between(baseDate, cf.date) / 365.25)
            .toArray();

        // Try Newton-Raphson first
        Double result = newtonRaphson(amounts, years, 0.1);
        if (result == null || Double.isNaN(result) || Double.isInfinite(result)) {
            // Fallback to bisection
            result = bisection(amounts, years, -0.999, 10.0);
        }

        if (result == null) return null;

        return BigDecimal.valueOf(result * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private Double newtonRaphson(double[] amounts, double[] years, double guess) {
        double rate = guess;
        for (int i = 0; i < XIRR_MAX_ITERATIONS; i++) {
            double npv = npv(amounts, years, rate);
            double deriv = npvDerivative(amounts, years, rate);
            if (Math.abs(deriv) < 1e-10) {
                rate += 0.0001; // perturb and retry
                continue;
            }
            double newRate = rate - npv / deriv;
            if (Math.abs(newRate - rate) < XIRR_TOLERANCE) return newRate;
            rate = newRate;
            if (rate < -0.999 || rate > 100) break; // diverged
        }
        return null;
    }

    private Double bisection(double[] amounts, double[] years, double lo, double hi) {
        double npvLo = npv(amounts, years, lo);
        double npvHi = npv(amounts, years, hi);
        if (npvLo * npvHi > 0) return null; // no root in range

        for (int i = 0; i < XIRR_MAX_ITERATIONS; i++) {
            double mid = (lo + hi) / 2;
            double npvMid = npv(amounts, years, mid);
            if (Math.abs(npvMid) < XIRR_TOLERANCE || (hi - lo) / 2 < XIRR_TOLERANCE) return mid;
            if (npvLo * npvMid < 0) { hi = mid; npvHi = npvMid; }
            else { lo = mid; npvLo = npvMid; }
        }
        return (lo + hi) / 2;
    }

    private double npv(double[] amounts, double[] years, double rate) {
        double sum = 0;
        for (int i = 0; i < amounts.length; i++) {
            sum += amounts[i] / Math.pow(1 + rate, years[i]);
        }
        return sum;
    }

    private double npvDerivative(double[] amounts, double[] years, double rate) {
        double sum = 0;
        for (int i = 0; i < amounts.length; i++) {
            sum -= years[i] * amounts[i] / Math.pow(1 + rate, years[i] + 1);
        }
        return sum;
    }

    // ─── Portfolio Returns ────────────────────────────────────────────────────

    public Map<String, Object> getPortfolioReturns(Long userId) {
        List<Transaction> txns = transactionRepo
            .findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId);
        List<Map<String, Object>> holdings = transactionService.getPortfolioSummary(userId);

        // Enrich holdings with sebiCategory + planType from scheme_master
        for (Map<String, Object> h : holdings) {
            String code = (String) h.get("schemeAmfiCode");
            if (code != null) {
                schemeRepo.findByAmfiCode(code).ifPresent(s -> {
                    if (h.get("schemeType") == null && s.getSebiCategory() != null)
                        h.put("schemeType", s.getSebiCategory());
                    if (h.get("broadCategory") == null && s.getBroadCategory() != null)
                        h.put("broadCategory", s.getBroadCategory());
                    if (s.getRiskLevel() != null)
                        h.put("riskLevel", s.getRiskLevel());
                });
            }
        }

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;

        for (Map<String, Object> h : holdings) {
            BigDecimal inv = (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO);
            BigDecimal cur = (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO);
            totalInvested = totalInvested.add(inv);
            totalCurrent = totalCurrent.add(cur);
        }

        BigDecimal absRet = absoluteReturn(totalInvested, totalCurrent);

        // Build XIRR cash flows from all transactions
        List<CashFlow> cashFlows = buildCashFlows(txns, totalCurrent);
        BigDecimal xirrVal = null;
        if (cashFlows.size() >= 2) xirrVal = xirr(cashFlows);

        // ── Real growth timeline (monthly snapshots from actual tx dates) ──────
        List<Map<String, Object>> growthTimeline = buildGrowthTimeline(txns, holdings);

        // ── Category-level allocation for pie chart ───────────────────────────
        Map<String, BigDecimal> categoryAlloc = new LinkedHashMap<>();
        for (Map<String, Object> h : holdings) {
            String cat = (String) h.getOrDefault("broadCategory", "Other");
            if (cat == null || cat.isBlank() || cat.equalsIgnoreCase("UNKNOWN"))
                cat = "Other";
            BigDecimal cur = (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO);
            if (cur == null) cur = BigDecimal.ZERO;
            // Fall back to invested if current is zero (synthetic NAV)
            if (cur.compareTo(BigDecimal.ZERO) == 0) {
                cur = (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO);
                if (cur == null) cur = BigDecimal.ZERO;
            }
            categoryAlloc.merge(cat, cur, BigDecimal::add);
        }
        List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : categoryAlloc.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("category", e.getKey());
            entry.put("value", e.getValue().setScale(2, RoundingMode.HALF_UP));
            categoryBreakdown.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalInvested", totalInvested.setScale(2, RoundingMode.HALF_UP));
        result.put("totalCurrentValue", totalCurrent.setScale(2, RoundingMode.HALF_UP));
        result.put("totalGainLoss", totalCurrent.subtract(totalInvested).setScale(2, RoundingMode.HALF_UP));
        result.put("absoluteReturnPct", absRet);
        result.put("xirrPct", xirrVal);
        result.put("holdings", holdings);
        result.put("transactionCount", txns.size());
        result.put("growthTimeline", growthTimeline);
        result.put("categoryBreakdown", categoryBreakdown);

        return result;
    }

    public Map<String, Object> getSchemeReturns(Long userId, String amfiCode) {
        List<Transaction> txns = transactionRepo
            .findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);
        if (txns.isEmpty()) return Map.of("error", "No transactions found");

        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);
        BigDecimal currentValue = portfolio.stream()
            .filter(h -> amfiCode.equals(h.get("schemeAmfiCode")))
            .map(h -> (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invested = portfolio.stream()
            .filter(h -> amfiCode.equals(h.get("schemeAmfiCode")))
            .map(h -> (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal absRet = absoluteReturn(invested, currentValue);
        List<CashFlow> cashFlows = buildCashFlows(txns, currentValue);
        BigDecimal xirrVal = cashFlows.size() >= 2 ? xirr(cashFlows) : null;

        // CAGR (only if single lumpsum type scenario)
        BigDecimal cagrVal = null;
        if (txns.size() == 1 && isPurchase(txns.get(0))) {
            long days = ChronoUnit.DAYS.between(txns.get(0).getTransactionDate(), LocalDate.now());
            cagrVal = cagr(invested, currentValue, days);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemeAmfiCode", amfiCode);
        result.put("investedAmount", invested.setScale(2, RoundingMode.HALF_UP));
        result.put("currentValue", currentValue.setScale(2, RoundingMode.HALF_UP));
        result.put("gainLoss", currentValue.subtract(invested).setScale(2, RoundingMode.HALF_UP));
        result.put("absoluteReturnPct", absRet);
        result.put("xirrPct", xirrVal);
        result.put("cagrPct", cagrVal);
        result.put("transactionCount", txns.size());
        return result;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<CashFlow> buildCashFlows(List<Transaction> txns, BigDecimal currentValue) {
        List<CashFlow> flows = new ArrayList<>();
        for (Transaction t : txns) {
            if (t.getAmount() == null) continue;
            String type = t.getTransactionType();
            if (type == null) continue;

            if (isPurchase(t)) {
                flows.add(new CashFlow(t.getTransactionDate(), t.getAmount().negate()));
            } else if (isRedemption(t) || "DIVIDEND_PAYOUT".equals(type)) {
                flows.add(new CashFlow(t.getTransactionDate(), t.getAmount()));
            }
        }
        if (currentValue != null && currentValue.compareTo(BigDecimal.ZERO) > 0) {
            flows.add(new CashFlow(LocalDate.now(), currentValue));
        }
        flows.sort(Comparator.comparing(cf -> cf.date));
        return flows;
    }

    /**
     * Builds a real monthly growth timeline from actual transaction dates.
     * Returns list of {month, invested, value} where:
     *   invested = cumulative amount put in up to that month
     *   value    = estimated current value (invested * current/totalInvested ratio)
     * This replaces the fake simulated curve in the frontend.
     */
    private List<Map<String, Object>> buildGrowthTimeline(
            List<Transaction> txns, List<Map<String, Object>> holdings) {
        if (txns.isEmpty()) return List.of();

        // Compute total invested and return ratio
        BigDecimal totalInvested = holdings.stream()
            .map(h -> (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCurrent = holdings.stream()
            .map(h -> (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // If current value is still zero (all NAVs missing), use invested as proxy
        if (totalCurrent.compareTo(BigDecimal.ZERO) == 0) totalCurrent = totalInvested;

        double returnRatio = totalInvested.compareTo(BigDecimal.ZERO) > 0
            ? totalCurrent.divide(totalInvested, 8, RoundingMode.HALF_UP).doubleValue()
            : 1.0;

        // Collect monthly invested totals starting from first transaction date
        List<Transaction> sorted = new ArrayList<>(txns);
        sorted.sort(Comparator.comparing(Transaction::getTransactionDate));

        LocalDate first = sorted.get(0).getTransactionDate();
        LocalDate now = LocalDate.now();
        LocalDate cursor = first.withDayOfMonth(1);

        // Show from first transaction date, capped at 60 months for UI legibility
        LocalDate maxStart = now.minusMonths(59).withDayOfMonth(1);
        LocalDate start = cursor.isBefore(maxStart) ? maxStart : cursor;

        Map<String, BigDecimal> monthlyInvested = new LinkedHashMap<>();
        for (Transaction t : sorted) {
            if (!isPurchase(t) || t.getAmount() == null) continue;
            String key = t.getTransactionDate().getYear() + "-"
                + String.format("%02d", t.getTransactionDate().getMonthValue());
            monthlyInvested.merge(key, t.getAmount(), BigDecimal::add);
        }

        // Pre-sum all investments that occurred BEFORE the window start (carried forward)
        BigDecimal cumulative = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : monthlyInvested.entrySet()) {
            // Parse the key "yyyy-MM" and compare to window start
            try {
                String[] parts = e.getKey().split("-");
                LocalDate monthDate = LocalDate.of(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), 1);
                if (monthDate.isBefore(start)) {
                    cumulative = cumulative.add(e.getValue());
                }
            } catch (Exception ignored) {}
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        cursor = start;
        while (!cursor.isAfter(now)) {
            String key = cursor.getYear() + "-" + String.format("%02d", cursor.getMonthValue());
            BigDecimal added = monthlyInvested.getOrDefault(key, BigDecimal.ZERO);
            cumulative = cumulative.add(added);

            Map<String, Object> point = new LinkedHashMap<>();
            String label = cursor.getMonth().name().substring(0, 3) + " '"
                + String.valueOf(cursor.getYear()).substring(2);
            point.put("month", label);
            point.put("invested", cumulative.setScale(2, RoundingMode.HALF_UP));
            // Value grows proportionally with portfolio return ratio
            double valueDouble = cumulative.doubleValue() * returnRatio;
            point.put("value", BigDecimal.valueOf(valueDouble).setScale(2, RoundingMode.HALF_UP));
            timeline.add(point);
            cursor = cursor.plusMonths(1);
        }
        return timeline;
    }

    private boolean isPurchase(Transaction t) {
        String type = t.getTransactionType();
        return type != null && (type.equals("PURCHASE_LUMPSUM") || type.equals("PURCHASE_SIP")
            || type.equals("SWITCH_IN") || type.equals("STP_IN") || type.equals("DIVIDEND_REINVEST"));
    }

    private boolean isRedemption(Transaction t) {
        String type = t.getTransactionType();
        return type != null && (type.equals("REDEMPTION") || type.equals("SWITCH_OUT")
            || type.equals("STP_OUT") || type.equals("SWP"));
    }

    // ─── CashFlow DTO ────────────────────────────────────────────────────────

    public static class CashFlow {
        public final LocalDate date;
        public final BigDecimal amount;

        public CashFlow(LocalDate date, BigDecimal amount) {
            this.date = date;
            this.amount = amount;
        }
    }
}
