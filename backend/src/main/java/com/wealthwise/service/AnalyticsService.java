package com.wealthwise.service;

import com.wealthwise.model.InvestmentLot;
import com.wealthwise.model.Scheme;
import com.wealthwise.model.Transaction;
import com.wealthwise.model.User;
import com.wealthwise.repository.InvestmentLotRepository;
import com.wealthwise.repository.SchemeRepository;
import com.wealthwise.repository.TransactionRepository;
import com.wealthwise.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class AnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final double RISK_FREE_RATE = 0.07;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvestmentLotRepository investmentLotRepository;

    @Autowired
    private NavService navService;

    public Map<String, Object> getRiskProfile(Long userId) {
        List<InvestmentLot> lots = investmentLotRepository.findByUserIdOrderByPurchaseDateAsc(userId);

        if (lots.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("portfolioRiskScore", 0.0);
            empty.put("portfolioRiskLabel", "N/A");
            empty.put("riskAppetite", "N/A");
            empty.put("riskAppetiteDescription", "No portfolio data available.");
            empty.put("diversificationScore", 0.0);
            empty.put("volatilityPct", 0.0);
            empty.put("sharpeRatio", 0.0);
            empty.put("maxDrawdownPct", 0.0);
            empty.put("totalFunds", 0);
            empty.put("uniqueAmcs", 0);
            return empty;
        }

        Map<String, Scheme> schemeMap = buildSchemeMap(lots);
        BigDecimal totalCurrentValue = ZERO;
        double weightedRiskSum = 0.0;
        Set<String> distinctFunds = new HashSet<>();
        Set<String> uniqueAmcs = new HashSet<>();

        for (InvestmentLot lot : lots) {
            if (lot.getUnitsRemaining() == null || lot.getUnitsRemaining().compareTo(ZERO) <= 0) {
                continue;
            }

            Scheme scheme = schemeMap.get(lot.getSchemeAmfiCode());
            if (scheme == null || scheme.getLastNav() == null || scheme.getRiskLevel() == null) {
                continue;
            }

            BigDecimal currentValue = lot.getUnitsRemaining().multiply(scheme.getLastNav());
            totalCurrentValue = totalCurrentValue.add(currentValue);
            weightedRiskSum += currentValue.doubleValue() * scheme.getRiskLevel();
            distinctFunds.add(lot.getSchemeAmfiCode());

            if (scheme.getAmcName() != null && !scheme.getAmcName().isBlank()) {
                uniqueAmcs.add(scheme.getAmcName().trim());
            }
        }

        double portfolioRiskScore = totalCurrentValue.compareTo(ZERO) == 0
            ? 0.0
            : weightedRiskSum / totalCurrentValue.doubleValue();
        portfolioRiskScore = round(portfolioRiskScore);

        List<Double> portfolioValues = calculatePortfolioValues(userId, schemeMap);
        List<Double> returns = calculateReturns(portfolioValues);

        double volatility = calculateVolatility(returns);
        double volatilityPct = round(volatility * 100);
        double sharpeRatio = round(calculateSharpeRatio(returns, volatility));
        double maxDrawdownPct = round(calculateMaxDrawdown(portfolioValues) * 100);
        double diversificationScore = round(calculateDiversificationScore(lots, schemeMap));

        Map<String, Object> riskData = new HashMap<>();
        riskData.put("portfolioRiskScore", portfolioRiskScore);
        riskData.put("portfolioRiskLabel", getRiskLabel(portfolioRiskScore));
        riskData.put("riskAppetite", getRiskAppetite(portfolioRiskScore));
        riskData.put("riskAppetiteDescription", getRiskAppetiteDescription(portfolioRiskScore));
        riskData.put("diversificationScore", diversificationScore);
        riskData.put("volatilityPct", volatilityPct);
        riskData.put("sharpeRatio", sharpeRatio);
        riskData.put("maxDrawdownPct", maxDrawdownPct);
        riskData.put("totalFunds", distinctFunds.size());
        riskData.put("uniqueAmcs", uniqueAmcs.size());

        return riskData;
    }

    private Map<String, Scheme> buildSchemeMap(List<InvestmentLot> lots) {
        Map<String, Scheme> schemeMap = new HashMap<>();
        for (InvestmentLot lot : lots) {
            String amfiCode = lot.getSchemeAmfiCode();
            if (amfiCode == null || schemeMap.containsKey(amfiCode)) {
                continue;
            }
            schemeRepository.findByAmfiCode(amfiCode).ifPresent(scheme -> schemeMap.put(amfiCode, scheme));
        }
        return schemeMap;
    }

    /**
     * Computes a time series of a Time-Weighted Return (TWR) "Price Index"
     * scaled to 100.0, evaluating real market returns at each transaction date.
     * This makes volatility, Sharpe ratio, and max drawdown mathematically accurate
     * and immune to cash flow distortions from SIPs and redemptions.
     */
    private List<Double> calculatePortfolioValues(Long userId, Map<String, Scheme> schemeMap) {
        List<Transaction> transactions = new ArrayList<>(
            transactionRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId)
        );
        transactions.sort(
            Comparator.comparing(Transaction::getTransactionDate)
                .thenComparing(txn -> txn.getCreatedAt() != null ? txn.getCreatedAt() : LocalDateTime.MIN)
        );

        // Pre-fetch full NAV history for every scheme (7-day cached in NavService)
        Map<String, Map<String, BigDecimal>> navHistories = new HashMap<>();
        for (String code : schemeMap.keySet()) {
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
                navHistories.put(code, new HashMap<>());
            }
        }

        Map<String, BigDecimal> holdings = new HashMap<>();
        List<Double> indexValues = new ArrayList<>();
        double index = 100.0;
        
        LocalDate prevDate = null;
        BigDecimal previousValue = ZERO;

        for (Transaction txn : transactions) {
            if (txn.getSchemeAmfiCode() == null || txn.getUnits() == null) continue;

            LocalDate txDate = txn.getTransactionDate();

            // 1. Calculate return on existing holdings before applying the new transaction
            if (prevDate != null && txDate.isAfter(prevDate)) {
                BigDecimal currentValueOfOldHoldings = computePortfolioValueAtDate(holdings, navHistories, schemeMap, txDate);
                if (previousValue.compareTo(ZERO) > 0) {
                    double periodReturn = currentValueOfOldHoldings.subtract(previousValue)
                            .divide(previousValue, 6, RoundingMode.HALF_UP).doubleValue();
                    index = index * (1.0 + periodReturn);
                }
                indexValues.add(index);
                previousValue = currentValueOfOldHoldings; // Update old portfolio baseline to current date
            } else if (prevDate == null) {
                indexValues.add(index);
            }

            // 2. Apply transaction units to holdings
            String type = txn.getTransactionType();
            boolean isRedemptionType = type != null && (
                type.equals("REDEMPTION") || type.equals("SWITCH_OUT")
                || type.equals("STP_OUT") || type.equals("SWP"));

            BigDecimal unitDelta = isRedemptionType ? txn.getUnits().negate() : txn.getUnits();
            holdings.merge(txn.getSchemeAmfiCode(), unitDelta, BigDecimal::add);

            // 3. Re-evaluate full new holdings for the next period baseline
            previousValue = computePortfolioValueAtDate(holdings, navHistories, schemeMap, txDate);
            prevDate = txDate;
        }

        // Add final epoch to capture market moves up to today
        LocalDate today = LocalDate.now();
        if (prevDate != null && today.isAfter(prevDate)) {
            BigDecimal currentValueOfOldHoldings = computePortfolioValueAtDate(holdings, navHistories, schemeMap, today);
            if (previousValue.compareTo(ZERO) > 0) {
                double periodReturn = currentValueOfOldHoldings.subtract(previousValue)
                        .divide(previousValue, 6, RoundingMode.HALF_UP).doubleValue();
                index = index * (1.0 + periodReturn);
            }
            indexValues.add(index);
        }

        return indexValues;
    }

    private BigDecimal computePortfolioValueAtDate(
            Map<String, BigDecimal> holdings,
            Map<String, Map<String, BigDecimal>> navHistories,
            Map<String, Scheme> schemeMap,
            LocalDate date) {
        BigDecimal totalValue = ZERO;
        for (Map.Entry<String, BigDecimal> entry : holdings.entrySet()) {
            if (entry.getValue().compareTo(ZERO) <= 0) continue;
            
            Map<String, BigDecimal> navHistory = navHistories.getOrDefault(entry.getKey(), Collections.emptyMap());
            BigDecimal nav = findNavOnOrBefore(navHistory, date);
            
            // Fallback to scheme's last known NAV if historical data unavailable for that date
            if (nav == null || nav.compareTo(ZERO) <= 0) {
                Scheme scheme = schemeMap.get(entry.getKey());
                if (scheme != null) nav = scheme.getLastNav();
            }
            if (nav != null && nav.compareTo(ZERO) > 0) {
                totalValue = totalValue.add(entry.getValue().multiply(nav));
            }
        }
        return totalValue;
    }

    /**
     * Finds the NAV for a given date (or nearest previous trading day, up to 7 days back).
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

    private List<Double> calculateReturns(List<Double> values) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            double previous = values.get(i - 1);
            double current = values.get(i);
            if (previous > 0) {
                returns.add((current - previous) / previous);
            }
        }
        return returns;
    }

    /**
     * Calculates annualized volatility from a series of per-transaction portfolio return ratios.
     *
     * NOTE: These data points are per-transaction (not monthly), so no standard annualisation
     * multiplier (sqrt(12) for monthly, sqrt(252) for daily) applies cleanly.
     * We compute raw standard deviation of the return series and display it as-is.
     * This is a realistic approximation for a multi-fund portfolio tracked at transaction events.
     *
     * Uses population std-dev (N), not sample std-dev (N-1), since we have the full set.
     */
    private double calculateVolatility(List<Double> returns) {
        if (returns.size() < 2) return 0.0;

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance); // raw std-dev of the return series
    }

    /**
     * Computes total portfolio return from first to last snapshot in the value series.
     * Formula: (lastValue / firstValue) - 1
     * This is used as the portfolio return input for the Sharpe ratio calculation.
     *
     * Note: This is a portfolio-level total return, not time-weighted. For an accurate
     * time-weighted Sharpe ratio, monthly NAV samples would be needed. This is a best
     * approximation given the transaction-frequency data we have.
     */
    private double calculatePortfolioReturn(List<Double> values) {
        if (values.size() < 2) return 0.0;
        double initial = values.get(0);
        double latest  = values.get(values.size() - 1);
        if (initial <= 0) return 0.0;
        return (latest - initial) / initial; // simple total return over the period
    }

    /**
     * Sharpe Ratio using mean excess return per TWR period.
     * Both the mean return and risk-free rate are expressed per-period (not annualized),
     * making them directly comparable.
     *
     * Approximate per-period risk-free rate assumes ~12 transactions/year (monthly SIPs).
     * For a portfolio with fewer transactions the ratio is still directionally correct.
     *
     * Sharpe = (meanReturn - rfPerPeriod) / σ
     */
    private double calculateSharpeRatio(List<Double> returns, double volatility) {
        if (volatility == 0.0 || returns.isEmpty()) return 0.0;
        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        // Approximate per-period risk-free rate: 7% annual / sqrt(12) periods-per-year
        // Using geometric monthly equivalent: (1 + 0.07)^(1/12) - 1 ≈ 0.00565
        double rfPerPeriod = Math.pow(1.0 + RISK_FREE_RATE, 1.0 / 12.0) - 1.0;
        return (meanReturn - rfPerPeriod) / volatility;
    }

    private double calculateMaxDrawdown(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double peak = values.get(0);
        double maxDrawdown = 0.0;

        for (double value : values) {
            if (value > peak) {
                peak = value;
            }
            if (peak > 0 && value > 0) {
                double drawdown = (peak - value) / peak;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return maxDrawdown;
    }

    /**
     * Improved diversification score (out of 10).
     * Component 1 — Fund count (4 pts max):
     *   1 fund=0, 2=1, 3-5=4, 6-8=5 (capped to 4), 9-12=3, >12=2
     * Component 2 — Concentration (6 pts max):
     *   Top fund <30%=6, 30-50%=4, 50-70%=2, >70%=0
     * Max = 10
     */
    private double calculateDiversificationScore(List<InvestmentLot> lots, Map<String, Scheme> schemeMap) {
        Set<String> distinctFunds = new HashSet<>();
        double totalValue = 0.0;
        Map<String, Double> fundValues = new HashMap<>();

        for (InvestmentLot lot : lots) {
            if (lot.getUnitsRemaining() == null || lot.getUnitsRemaining().compareTo(ZERO) <= 0) continue;
            Scheme scheme = schemeMap.get(lot.getSchemeAmfiCode());
            if (scheme == null || scheme.getLastNav() == null) continue;

            distinctFunds.add(lot.getSchemeAmfiCode());
            double val = lot.getUnitsRemaining().multiply(scheme.getLastNav()).doubleValue();
            totalValue += val;
            fundValues.merge(lot.getSchemeAmfiCode(), val, Double::sum);
        }

        int n = distinctFunds.size();
        double countScore;
        if (n <= 1)      countScore = 0;
        else if (n == 2) countScore = 1;
        else if (n <= 5) countScore = 4;
        else if (n <= 8) countScore = 4; // sweet spot
        else if (n <= 12)countScore = 3; // over-diversified starts to hurt
        else             countScore = 2;

        // Take a final snapshot after the loop — totalValue was mutated above so it can't
        // be captured directly in a lambda (Java requires effectively-final captures).
        final double finalTotal = totalValue;

        double concScore = 0;
        if (finalTotal > 0) {
            // Compute max share without a lambda to avoid any capture issues
            double maxFundPct = 0.0;
            for (double v : fundValues.values()) {
                double pct = v / finalTotal;
                if (pct > maxFundPct) maxFundPct = pct;
            }
            if (maxFundPct < 0.30)      concScore = 6;
            else if (maxFundPct < 0.50) concScore = 4;
            else if (maxFundPct < 0.70) concScore = 2;
            // else concScore stays 0 (>70% concentration — poorly diversified)
        }

        return Math.min(10.0, countScore + concScore);
    }

    /**
     * Derives the investor risk appetite category from the SEBI 6-point portfolio risk score.
     * Score range  → Appetite
     *   0.0        → N/A  (no data)
     *   0.1 – 2.0  → Conservative
     *   2.1 – 4.0  → Moderate
     *   4.1 – 6.0  → Aggressive
     */
    private String getRiskAppetite(double score) {
        if (score == 0.0) {
            return "N/A";
        }
        if (score <= 2.0) {
            return "Conservative";
        }
        if (score <= 4.0) {
            return "Moderate";
        }
        return "Aggressive";
    }

    private String getRiskAppetiteDescription(double score) {
        if (score == 0.0) {
            return "No portfolio data available.";
        }
        if (score <= 2.0) {
            return "You prefer capital preservation with minimal risk exposure.";
        }
        if (score <= 4.0) {
            return "You balance growth with measured risk.";
        }
        return "You seek high returns and are comfortable with significant risk.";
    }


    private String getRiskLabel(double score) {
        if (score == 0.0) {
            return "N/A";
        }
        if (score < 1.5) {
            return "Low";
        }
        if (score < 2.5) {
            return "Low to Moderate";
        }
        if (score < 3.5) {
            return "Moderate";
        }
        if (score < 4.5) {
            return "Moderately High";
        }
        if (score < 5.5) {
            return "High";
        }
        return "Very High";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public Map<String, Object> getSipIntelligence(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        int activeSips = 0;
        BigDecimal totalSipOutflow = BigDecimal.ZERO;

        List<Map<String, Object>> sipAnalysis = new ArrayList<>();

        for (Map<String, Object> h : portfolio) {
            String amfiCode = (String) h.get("schemeAmfiCode");
            if (amfiCode == null || amfiCode.isBlank()) continue; // skip incomplete holdings
            List<Transaction> txns = transactionRepository.findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);
            if (txns.isEmpty()) continue;

            boolean hasSip = false;
            BigDecimal latestSipAmount = BigDecimal.ZERO;
            for (Transaction t : txns) {
                if ("PURCHASE_SIP".equals(t.getTransactionType()) && t.getAmount() != null) {
                    hasSip = true;
                    latestSipAmount = t.getAmount();
                }
            }

            if (hasSip) {
                // Check if this SIP is ACTIVE: must have at least one SIP in the last 35 days
                // (35 days accounts for monthly SIPs that may fall on weekends/holidays)
                LocalDate cutoff = LocalDate.now().minusDays(35);
                boolean isRecentlyActive = txns.stream()
                    .anyMatch(t -> "PURCHASE_SIP".equals(t.getTransactionType())
                        && t.getTransactionDate() != null
                        && !t.getTransactionDate().isBefore(cutoff));

                if (isRecentlyActive && latestSipAmount.compareTo(BigDecimal.ZERO) > 0) {
                    activeSips++;
                    totalSipOutflow = totalSipOutflow.add(latestSipAmount);
                }

                BigDecimal totalInvested = (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO);
                BigDecimal actualSipValue = (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO);

                Transaction firstTxn = txns.get(0);
                BigDecimal firstNav = firstTxn.getNav();
                if (firstNav != null && firstNav.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal latestNav = BigDecimal.ONE;
                    BigDecimal totalUnits = (BigDecimal) h.getOrDefault("units", BigDecimal.ONE);
                    if (totalUnits.compareTo(BigDecimal.ZERO) > 0) {
                        latestNav = actualSipValue.divide(totalUnits, 4, RoundingMode.HALF_UP);
                    }

                    /**
                     * Hypothetical Lumpsum comparison:
                     * "What if the investor had put ALL the total SIP capital as a single lumpsum
                     *  on the date of the FIRST SIP installment?"
                     *  → hypotheticalUnits = totalInvested / firstNav
                     *  → hypotheticalValue  = hypotheticalUnits × currentNav
                     *
                     * This is the standard SIP-vs-Lumpsum comparison methodology used in
                     * fintech platforms (AMFI, Groww, etc.). Both strategies use the same
                     * total capital (totalInvested), so the comparison is fair.
                     *
                     * The derived currentNav uses current portfolio value ÷ current units,
                     * which gives the weighted-average NAV the portfolio is worth today.
                     */
                    BigDecimal hypotheticalUnits = totalInvested.divide(firstNav, 4, RoundingMode.HALF_UP);
                    BigDecimal hypotheticalLumpsumValue = hypotheticalUnits.multiply(latestNav);

                    Map<String, Object> analysis = new HashMap<>();
                    analysis.put("fundName", h.get("schemeName"));
                    analysis.put("sipValue", actualSipValue.setScale(2, RoundingMode.HALF_UP));
                    analysis.put("lumpsumValue", hypotheticalLumpsumValue.setScale(2, RoundingMode.HALF_UP));
                    analysis.put("sipAbsReturn",
                        totalInvested.compareTo(BigDecimal.ZERO) > 0
                            ? actualSipValue.subtract(totalInvested)
                                .divide(totalInvested, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    analysis.put("lumpsumAbsReturn",
                        totalInvested.compareTo(BigDecimal.ZERO) > 0
                            ? hypotheticalLumpsumValue.subtract(totalInvested)
                                .divide(totalInvested, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);

                    BigDecimal diff = actualSipValue.subtract(hypotheticalLumpsumValue);
                    analysis.put("difference", diff.setScale(2, RoundingMode.HALF_UP));
                    analysis.put("winner", diff.compareTo(BigDecimal.ZERO) >= 0 ? "SIP Strategy" : "Lumpsum Strategy");

                    sipAnalysis.add(analysis);
                }
            }
        }

        // Compute SIP streak: consecutive months with at least one PURCHASE_SIP across all schemes.
        // Runs independently of sipAnalysis — even if no firstNav data, streak is still valid.
        int sipStreak = 0;
        {
            TreeSet<String> sipMonths = new TreeSet<>();
            for (Map<String, Object> h : portfolio) {
                String amfiCode = (String) h.get("schemeAmfiCode");
                if (amfiCode == null) continue;
                List<Transaction> allTxns = transactionRepository
                    .findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);
                for (Transaction t : allTxns) {
                    if ("PURCHASE_SIP".equals(t.getTransactionType()) && t.getTransactionDate() != null) {
                        sipMonths.add(t.getTransactionDate().getYear() + "-"
                            + String.format("%02d", t.getTransactionDate().getMonthValue()));
                    }
                }
            }
            YearMonth cursor = YearMonth.now();
            while (sipMonths.contains(cursor.getYear() + "-"
                    + String.format("%02d", cursor.getMonthValue()))) {
                sipStreak++;
                cursor = cursor.minusMonths(1);
            }
        }

        Map<String, Object> sipSuite = new HashMap<>();
        sipSuite.put("activeSips", activeSips);
        sipSuite.put("totalSipOutflow", totalSipOutflow);
        sipSuite.put("sipStreak", sipStreak > 0 ? sipStreak + " consecutive month" + (sipStreak > 1 ? "s" : "") : "No SIPs recorded");
        sipSuite.put("analysis", sipAnalysis);

        return sipSuite;
    }


    public Map<String, Object> getFundOverlapMatrix(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        List<Map<String, Object>> matrixNodes = new ArrayList<>();
        List<Map<String, Object>> matrixLinks = new ArrayList<>();

        for (int i = 0; i < portfolio.size(); i++) {
            Map<String, Object> fund1 = portfolio.get(i);
            String id1 = (String) fund1.get("schemeAmfiCode");
            String name1 = (String) fund1.get("schemeName");
            String cat1 = (String) fund1.getOrDefault("broadCategory", fund1.get("schemeType"));

            matrixNodes.add(Map.of(
                "id", id1 != null ? id1 : "UNKNOWN_ID_" + i,
                "name", name1 != null ? name1 : "Unknown",
                "category", cat1 != null ? cat1 : "Unknown"));

            for (int j = i + 1; j < portfolio.size(); j++) {
                Map<String, Object> fund2 = portfolio.get(j);
                String id2 = (String) fund2.get("schemeAmfiCode");
                String cat2 = (String) fund2.getOrDefault("broadCategory", fund2.get("schemeType"));

                double overlapPct = 0;
                if (cat1 != null && cat2 != null) {
                    String c1 = cat1.toLowerCase();
                    String c2 = cat2.toLowerCase();

                    // Determine if each fund is equity-oriented
                    boolean c1Equity = c1.contains("equity") || c1.contains("elss")
                        || c1.contains("large cap") || c1.contains("mid cap") || c1.contains("small cap")
                        || c1.contains("flexi cap") || c1.contains("multi cap") || c1.contains("focused")
                        || c1.contains("thematic") || c1.contains("sectoral")
                        || c1.contains("balanced advantage") || c1.contains("aggressive hybrid");
                    boolean c2Equity = c2.contains("equity") || c2.contains("elss")
                        || c2.contains("large cap") || c2.contains("mid cap") || c2.contains("small cap")
                        || c2.contains("flexi cap") || c2.contains("multi cap") || c2.contains("focused")
                        || c2.contains("thematic") || c2.contains("sectoral")
                        || c2.contains("balanced advantage") || c2.contains("aggressive hybrid");

                    if (!c1Equity || !c2Equity) {
                        // Debt vs equity or debt vs debt — no meaningful equity stock overlap
                        overlapPct = 0;
                    } else if (cat1.equals(cat2)) {
                        // Exact same SEBI category
                        overlapPct = (c1.contains("index") || c1.contains("etf")) ? 90.0 : 55.0;
                    } else if (c1.contains("large cap") && c2.contains("large cap")) {
                        overlapPct = 60.0; // Both hold Nifty 50/100 stocks heavily
                    } else if ((c1.contains("large cap") && c2.contains("flexi cap"))
                            || (c2.contains("large cap") && c1.contains("flexi cap"))) {
                        overlapPct = 45.0; // Flexi cap tends to be large-cap biased
                    } else if ((c1.contains("large cap") && c2.contains("multi cap"))
                            || (c2.contains("large cap") && c1.contains("multi cap"))) {
                        overlapPct = 30.0; // Multi cap mandates 25%+ in large cap
                    } else if ((c1.contains("elss") && c2.contains("large cap"))
                            || (c2.contains("elss") && c1.contains("large cap"))) {
                        overlapPct = 40.0; // ELSS is diversified equity, large-cap tilted
                    } else if ((c1.contains("elss") && c2.contains("flexi cap"))
                            || (c2.contains("elss") && c1.contains("flexi cap"))) {
                        overlapPct = 35.0;
                    } else if ((c1.contains("mid cap") && c2.contains("flexi cap"))
                            || (c2.contains("mid cap") && c1.contains("flexi cap"))) {
                        overlapPct = 20.0;
                    } else if ((c1.contains("mid cap") && c2.contains("multi cap"))
                            || (c2.contains("mid cap") && c1.contains("multi cap"))) {
                        overlapPct = 25.0; // Multi cap mandates 25%+ in mid cap
                    } else if ((c1.contains("small cap") && c2.contains("mid cap"))
                            || (c2.contains("small cap") && c1.contains("mid cap"))) {
                        overlapPct = 15.0; // Different bands, some fringe overlap
                    } else if ((c1.contains("large cap") && (c2.contains("mid cap") || c2.contains("small cap")))
                            || (c2.contains("large cap") && (c1.contains("mid cap") || c1.contains("small cap")))) {
                        overlapPct = 8.0; // Large cap vs mid/small — minimal
                    } else {
                        overlapPct = 12.0; // Generic equity-to-equity fallback
                    }
                }

                // Guard: skip pairs with null ids (Map.of() throws NPE on null values)
                if (id1 == null || id2 == null) continue;

                if (overlapPct > 0) {
                    // Deterministic rule-based estimate by SEBI category — no random noise
                    // Math.round returns long; cast to double explicitly to avoid ClassCastException
                    // when the map value is retrieved and cast later for stream avg.
                    matrixLinks.add(Map.of(
                        "source", id1,
                        "target", id2,
                        "overlapPct", (double) Math.round(overlapPct * 10.0) / 10.0));
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("nodes", matrixNodes);
        response.put("links", matrixLinks);

        double avgOverlap = matrixLinks.stream()
            .mapToDouble(l -> (double) l.get("overlapPct"))
            .average()
            .orElse(0.0);
        // Math.round returns long — cast to double before storing to guarantee consistent type
        response.put("averageOverlapPct", (double) Math.round(avgOverlap * 10.0) / 10.0);

        return response;
    }

    public void saveRiskProfile(Long userId, String riskProfile) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!riskProfile.equals("CONSERVATIVE") && !riskProfile.equals("MODERATE") && !riskProfile.equals("AGGRESSIVE")) {
            throw new RuntimeException("Invalid risk profile. Must be CONSERVATIVE, MODERATE, or AGGRESSIVE.");
        }
        user.setRiskProfile(riskProfile);
        userRepository.save(user);
    }
}
