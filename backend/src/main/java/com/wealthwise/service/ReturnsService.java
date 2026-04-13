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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReturnsService {

    private static final Logger log = LoggerFactory.getLogger(ReturnsService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final double XIRR_TOLERANCE = 1e-7;
    private static final int XIRR_MAX_ITERATIONS = 1000;

    @Autowired private TransactionRepository transactionRepo;
    @Autowired private InvestmentLotRepository lotRepo;
    @Autowired private SchemeRepository schemeRepo;
    @Autowired private TransactionService transactionService;
    @Autowired private NavService navService;

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
                // DIVIDEND_REINVEST excluded: it's an internal NAV event, not investor cash outflow.
                // Including it would overstate cash deployed and understate XIRR.
                flows.add(new CashFlow(t.getTransactionDate(), t.getAmount().negate()));
            } else if (isRedemption(t) || "DIVIDEND_PAYOUT".equals(type)) {
                flows.add(new CashFlow(t.getTransactionDate(), t.getAmount()));
            }
            // DIVIDEND_REINVEST, REVERSAL, STP_IN/OUT are handled separately in lot tracking
        }
        if (currentValue != null && currentValue.compareTo(BigDecimal.ZERO) > 0) {
            flows.add(new CashFlow(LocalDate.now(), currentValue));
        }
        flows.sort(Comparator.comparing(cf -> cf.date));
        return flows;
    }

    /**
     * Builds an accurate monthly growth timeline from actual transaction history.
     * For each month:
     *   invested = cumulative amount put in up to that month (exact)
     *   value    = Σ(units_held × actual_NAV_on_month_end) across all schemes
     *
     * NAV history is fetched via NavService (7-day cached) — no estimates or ratios.
     * Falls back to invested amount only when NAV data is genuinely unavailable.
     */
    private List<Map<String, Object>> buildGrowthTimeline(
            List<Transaction> txns, List<Map<String, Object>> holdings) {
        if (txns.isEmpty()) return List.of();

        // ── 1. Collect all unique scheme codes ─────────────────────────────────
        Set<String> schemeCodes = new HashSet<>();
        for (Transaction t : txns) {
            if (t.getSchemeAmfiCode() != null) schemeCodes.add(t.getSchemeAmfiCode());
        }

        // ── 2. Pre-fetch full NAV histories (7-day cache in NavService) ─────────
        Map<String, Map<String, BigDecimal>> navHistories = new HashMap<>();
        for (String code : schemeCodes) {
            try {
                List<Map<String, String>> history = navService.getHistoricalNavs(code);
                Map<String, BigDecimal> navMap = new HashMap<>();
                for (Map<String, String> entry : history) {
                    String d = entry.getOrDefault("date", "");
                    String n = entry.getOrDefault("nav", "");
                    if (!d.isEmpty() && !n.isEmpty()) {
                        try { navMap.put(d, new BigDecimal(n)); } catch (Exception ignored) {}
                    }
                }
                navHistories.put(code, navMap);
            } catch (Exception e) {
                log.warn("[TIMELINE] NAV fetch failed for {}: {}", code, e.getMessage());
                navHistories.put(code, new HashMap<>());
            }
        }

        // ── 3. Sort transactions chronologically ───────────────────────────────
        List<Transaction> sorted = new ArrayList<>(txns);
        sorted.sort(Comparator.comparing(Transaction::getTransactionDate));

        LocalDate first = sorted.get(0).getTransactionDate();
        LocalDate now = LocalDate.now();
        LocalDate maxStart = now.minusMonths(59).withDayOfMonth(1);
        LocalDate windowStart = first.withDayOfMonth(1).isBefore(maxStart)
            ? maxStart : first.withDayOfMonth(1);

        // ── 4. Build unit balances and invested amount for months before window ─
        Map<String, BigDecimal> unitsHeld = new HashMap<>();
        BigDecimal cumulativeInvested = BigDecimal.ZERO;
        int txPointer = 0;

        for (; txPointer < sorted.size(); txPointer++) {
            Transaction t = sorted.get(txPointer);
            if (!t.getTransactionDate().isBefore(windowStart)) break;
            applyTransactionToUnits(t, unitsHeld);
            if (isPurchase(t) && t.getAmount() != null)
                cumulativeInvested = cumulativeInvested.add(t.getAmount());
        }

        // ── 5. Walk month by month ─────────────────────────────────────────────
        List<Map<String, Object>> timeline = new ArrayList<>();
        LocalDate cursor = windowStart;
        // Track the last known real portfolio value to carry forward when NAV data is missing.
        // Using invested-amount as fallback creates a false flat-then-spike chart.
        BigDecimal lastKnownPortfolioValue = BigDecimal.ZERO;

        while (!cursor.isAfter(now)) {
            LocalDate nextMonth = cursor.plusMonths(1);

            // Apply all transactions falling in this month
            while (txPointer < sorted.size()
                    && sorted.get(txPointer).getTransactionDate().isBefore(nextMonth)) {
                Transaction t = sorted.get(txPointer);
                applyTransactionToUnits(t, unitsHeld);
                if (isPurchase(t) && t.getAmount() != null)
                    cumulativeInvested = cumulativeInvested.add(t.getAmount());
                txPointer++;
            }

            // Compute real portfolio value at end of this month using actual NAVs
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            BigDecimal portfolioValue = computePortfolioValue(unitsHeld, navHistories, monthEnd);

            if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                // Real NAV data available — use it and update carry-forward
                lastKnownPortfolioValue = portfolioValue;
            } else if (lastKnownPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                // NAV missing for this month — carry forward the last real value
                // This is far more accurate than substituting invested amount,
                // which falsely shows portfolio stagnating and then spiking.
                portfolioValue = lastKnownPortfolioValue;
            } else if (cumulativeInvested.compareTo(BigDecimal.ZERO) > 0) {
                // Very first month and no NAV at all — use invested as bootstrap only
                portfolioValue = cumulativeInvested;
            }

            // Only emit data points where something has actually been invested
            if (cumulativeInvested.compareTo(BigDecimal.ZERO) > 0) {
                String label = cursor.getMonth().name().substring(0, 3) + " '"
                    + String.valueOf(cursor.getYear()).substring(2);
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("month", label);
                point.put("invested", cumulativeInvested.setScale(2, RoundingMode.HALF_UP));
                point.put("value", portfolioValue.setScale(2, RoundingMode.HALF_UP));
                timeline.add(point);
            }

            cursor = nextMonth;
        }
        return timeline;
    }

    /** Applies a transaction's unit change to the running unit balance map. */
    private void applyTransactionToUnits(Transaction t, Map<String, BigDecimal> unitsHeld) {
        if (t.getUnits() == null || t.getSchemeAmfiCode() == null) return;
        if (isPurchase(t)) {
            unitsHeld.merge(t.getSchemeAmfiCode(), t.getUnits(), BigDecimal::add);
        } else if (isRedemption(t)) {
            BigDecimal current = unitsHeld.getOrDefault(t.getSchemeAmfiCode(), BigDecimal.ZERO);
            unitsHeld.put(t.getSchemeAmfiCode(), current.subtract(t.getUnits()).max(BigDecimal.ZERO));
        }
    }

    /** Computes Σ(units × actual_NAV) across all holdings using real historical NAV. */
    private BigDecimal computePortfolioValue(
            Map<String, BigDecimal> unitsHeld,
            Map<String, Map<String, BigDecimal>> navHistories,
            LocalDate date) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : unitsHeld.entrySet()) {
            BigDecimal units = entry.getValue();
            if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal nav = findNavOnOrBefore(navHistories.get(entry.getKey()), date);
            if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0)
                total = total.add(units.multiply(nav));
        }
        return total;
    }

    /**
     * Finds the NAV for a given date (or nearest previous trading day up to 7 days back).
     * mfapi.in stores dates in dd-MM-yyyy format.
     */
    private BigDecimal findNavOnOrBefore(Map<String, BigDecimal> navMap, LocalDate date) {
        if (navMap == null || navMap.isEmpty()) return null;
        for (int i = 0; i <= 7; i++) {
            LocalDate d = date.minusDays(i);
            String key = String.format("%02d-%02d-%04d",
                d.getDayOfMonth(), d.getMonthValue(), d.getYear());
            BigDecimal nav = navMap.get(key);
            if (nav != null) return nav;
        }
        return null;
    }

    private boolean isPurchase(Transaction t) {
        String type = t.getTransactionType();
        // DIVIDEND_REINVEST deliberately excluded — it's an internal NAV event,
        // not a cash outflow from the investor. It should not appear in XIRR cash flows.
        return type != null && (type.equals("PURCHASE_LUMPSUM") || type.equals("PURCHASE_SIP")
            || type.equals("SWITCH_IN") || type.equals("STP_IN"));
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
