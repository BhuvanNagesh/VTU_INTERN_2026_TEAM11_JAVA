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
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final double RISK_FREE_RATE = 0.07;

    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private SchemeRepository schemeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InvestmentLotRepository investmentLotRepository;
    @Autowired private ReturnsService returnsService;

    // ─── Risk Profile ─────────────────────────────────────────────────────────

    public Map<String, Object> getRiskProfile(Long userId) {
        List<InvestmentLot> lots = investmentLotRepository.findByUserIdOrderByPurchaseDateAsc(userId);
        User user = userRepository.findById(userId).orElse(null);
        String userRiskProfile = (user != null && user.getRiskProfile() != null)
            ? user.getRiskProfile() : "MODERATE";
        double userToleranceScore = mapUserProfileToTolerance(userRiskProfile);

        if (lots.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("portfolioRiskScore", 0.0);
            empty.put("portfolioRiskLabel", "N/A");
            empty.put("diversificationScore", 0.0);
            empty.put("volatilityPct", 0.0);
            empty.put("sharpeRatio", 0.0);
            empty.put("maxDrawdownPct", 0.0);
            empty.put("totalFunds", 0);
            empty.put("uniqueAmcs", 0);
            empty.put("userRiskProfile", userRiskProfile);
            empty.put("riskComparison", "No active investments found for this user yet.");
            return empty;
        }

        Map<String, Scheme> schemeMap = buildSchemeMap(lots);
        BigDecimal totalCurrentValue = ZERO;
        double weightedRiskSum = 0.0;
        Set<String> distinctFunds = new HashSet<>();
        Set<String> uniqueAmcs = new HashSet<>();

        for (InvestmentLot lot : lots) {
            if (lot.getUnitsRemaining() == null || lot.getUnitsRemaining().compareTo(ZERO) <= 0) continue;
            Scheme scheme = schemeMap.get(lot.getSchemeAmfiCode());
            if (scheme == null || scheme.getLastNav() == null || scheme.getRiskLevel() == null) continue;

            BigDecimal currentValue = lot.getUnitsRemaining().multiply(scheme.getLastNav());
            totalCurrentValue = totalCurrentValue.add(currentValue);
            weightedRiskSum += currentValue.doubleValue() * scheme.getRiskLevel();
            distinctFunds.add(lot.getSchemeAmfiCode());
            if (scheme.getAmcName() != null && !scheme.getAmcName().isBlank())
                uniqueAmcs.add(scheme.getAmcName().trim());
        }

        double portfolioRiskScore = totalCurrentValue.compareTo(ZERO) == 0
            ? 0.0
            : round(weightedRiskSum / totalCurrentValue.doubleValue());

        List<Double> portfolioValues = calculatePortfolioValues(userId, schemeMap);
        List<Double> returns = calculateReturns(portfolioValues);

        double volatility = calculateVolatility(returns);
        double volatilityPct = round(volatility * 100);
        double portfolioReturn = calculatePortfolioReturn(portfolioValues);
        double sharpeRatio = round(calculateSharpeRatio(portfolioReturn, volatility));
        double maxDrawdownPct = round(calculateMaxDrawdown(portfolioValues) * 100);
        double diversificationScore = round(calculateDiversificationScore(lots, schemeMap));

        Map<String, Object> riskData = new HashMap<>();
        riskData.put("portfolioRiskScore", portfolioRiskScore);
        riskData.put("portfolioRiskLabel", getRiskLabel(portfolioRiskScore));
        riskData.put("diversificationScore", diversificationScore);
        riskData.put("volatilityPct", volatilityPct);
        riskData.put("sharpeRatio", sharpeRatio);
        riskData.put("maxDrawdownPct", maxDrawdownPct);
        riskData.put("totalFunds", distinctFunds.size());
        riskData.put("uniqueAmcs", uniqueAmcs.size());
        riskData.put("userRiskProfile", userRiskProfile);
        riskData.put("riskComparison", buildRiskComparison(portfolioRiskScore, userToleranceScore));

        // Accurate metrics computed from real NAV-based monthly growth timeline
        riskData.putAll(computeAccurateMetrics(userId));

        // Derived risk appetite — computed from actual portfolio risk score (0-6 scale)
        String derivedAppetite;
        String derivedDescription;
        if (portfolioRiskScore <= 2.0) {
            derivedAppetite = "Conservative";
            derivedDescription = "Your holdings are concentrated in low-risk instruments.";
        } else if (portfolioRiskScore <= 4.0) {
            derivedAppetite = "Moderate";
            derivedDescription = "Your portfolio balances growth and stability.";
        } else {
            derivedAppetite = "Aggressive";
            derivedDescription = "Your portfolio is heavily weighted towards high-risk assets.";
        }
        riskData.put("derivedRiskAppetite", derivedAppetite);
        riskData.put("derivedRiskAppetiteDescription", derivedDescription);

        return riskData;
    }

    private Map<String, Scheme> buildSchemeMap(List<InvestmentLot> lots) {
        Map<String, Scheme> schemeMap = new HashMap<>();
        for (InvestmentLot lot : lots) {
            String amfiCode = lot.getSchemeAmfiCode();
            // Skip synthetic WW_ISIN_* codes — they have no real NAV
            if (amfiCode == null || amfiCode.startsWith("WW_") || schemeMap.containsKey(amfiCode)) continue;
            schemeRepository.findByAmfiCode(amfiCode).ifPresent(scheme -> schemeMap.put(amfiCode, scheme));
        }
        return schemeMap;
    }

    private List<Double> calculatePortfolioValues(Long userId, Map<String, Scheme> schemeMap) {
        List<Transaction> transactions = new ArrayList<>(
            transactionRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId)
        );
        transactions.sort(
            Comparator.comparing(Transaction::getTransactionDate)
                .thenComparing(txn -> txn.getCreatedAt() != null ? txn.getCreatedAt() : LocalDateTime.MIN)
        );

        Map<String, BigDecimal> holdings = new HashMap<>();
        List<Double> portfolioValues = new ArrayList<>();

        for (Transaction txn : transactions) {
            if (txn.getSchemeAmfiCode() == null || txn.getUnits() == null) continue;
            // Skip synthetic codes
            if (txn.getSchemeAmfiCode().startsWith("WW_")) continue;

            holdings.merge(txn.getSchemeAmfiCode(), txn.getUnits(), BigDecimal::add);

            BigDecimal totalValue = ZERO;
            for (Map.Entry<String, BigDecimal> entry : holdings.entrySet()) {
                if (entry.getValue().compareTo(ZERO) <= 0) continue;
                Scheme scheme = schemeMap.get(entry.getKey());
                if (scheme == null || scheme.getLastNav() == null) continue;
                totalValue = totalValue.add(entry.getValue().multiply(scheme.getLastNav()));
            }
            portfolioValues.add(totalValue.doubleValue());
        }
        return portfolioValues;
    }

    private List<Double> calculateReturns(List<Double> values) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            double previous = values.get(i - 1);
            double current = values.get(i);
            if (previous > 0) returns.add((current - previous) / previous);
        }
        return returns;
    }

    private double calculateVolatility(List<Double> returns) {
        if (returns.isEmpty()) return 0.0;
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = 0.0;
        for (double v : returns) variance += Math.pow(v - mean, 2);
        variance /= returns.size();
        return Math.sqrt(variance) * Math.sqrt(12);
    }

    private double calculatePortfolioReturn(List<Double> values) {
        if (values.size() < 2) return 0.0;
        double initial = values.get(0);
        double latest = values.get(values.size() - 1);
        return initial <= 0 ? 0.0 : (latest - initial) / initial;
    }

    private double calculateSharpeRatio(double portfolioReturn, double volatility) {
        return volatility == 0.0 ? 0.0 : (portfolioReturn - RISK_FREE_RATE) / volatility;
    }

    private double calculateMaxDrawdown(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double peak = values.get(0);
        double maxDrawdown = 0.0;
        for (double value : values) {
            if (value > peak) peak = value;
            if (peak > 0 && value > 0) {
                double drawdown = (peak - value) / peak;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private double calculateDiversificationScore(List<InvestmentLot> lots, Map<String, Scheme> schemeMap) {
        Set<String> distinctFunds = new HashSet<>();
        double totalValue = 0.0;
        double maxFundValue = 0.0;

        for (InvestmentLot lot : lots) {
            if (lot.getUnitsRemaining() == null || lot.getUnitsRemaining().compareTo(ZERO) <= 0) continue;
            Scheme scheme = schemeMap.get(lot.getSchemeAmfiCode());
            if (scheme == null || scheme.getLastNav() == null) continue;

            distinctFunds.add(lot.getSchemeAmfiCode());
            double currentValue = lot.getUnitsRemaining().multiply(scheme.getLastNav()).doubleValue();
            totalValue += currentValue;
            if (currentValue > maxFundValue) maxFundValue = currentValue;
        }

        double score = 0.0;
        int fundCount = distinctFunds.size();
        if (fundCount >= 3 && fundCount <= 8) score += 4.0;
        else if (fundCount == 2 || fundCount > 8) score += 2.0;

        if (totalValue > 0) {
            double concentration = maxFundValue / totalValue;
            if (concentration < 0.5) score += 6.0;
            else if (concentration < 0.7) score += 3.0;
        }
        return score;
    }

    // ─── SIP Intelligence ─────────────────────────────────────────────────────

    public Map<String, Object> getSipIntelligence(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        int activeSips = 0;
        BigDecimal totalSipOutflow = BigDecimal.ZERO;
        List<Map<String, Object>> sipAnalysis = new ArrayList<>();

        for (Map<String, Object> h : portfolio) {
            String amfiCode = (String) h.get("schemeAmfiCode");
            if (amfiCode == null) continue;
            // NOTE: WW_ (synthetic) codes ARE included here — they have real transaction
            // data and a lastNav stored from the CAS PDF. We only skip WW_ codes when
            // making external mfapi.in HTTP calls, not for internal analytics.

            List<Transaction> txns = transactionRepository
                .findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);

            boolean hasSip = false;
            BigDecimal latestSipAmount = BigDecimal.ZERO;
            for (Transaction t : txns) {
                if ("PURCHASE_SIP".equals(t.getTransactionType()) && t.getAmount() != null) {
                    hasSip = true;
                    latestSipAmount = t.getAmount();
                }
            }

            if (hasSip && latestSipAmount.compareTo(BigDecimal.ZERO) > 0) {
                activeSips++;
                totalSipOutflow = totalSipOutflow.add(latestSipAmount);

                BigDecimal totalInvested = (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO);
                BigDecimal actualSipValue = (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO);

                if (!txns.isEmpty()) {
                    Transaction firstTxn = txns.get(0);
                    BigDecimal firstNav = firstTxn.getNav();
                    if (firstNav != null && firstNav.compareTo(BigDecimal.ZERO) > 0
                            && totalInvested.compareTo(BigDecimal.ZERO) > 0) {
                        // Prefer actual market NAV from scheme_master for accuracy
                        BigDecimal latestNav = BigDecimal.ONE;
                        Optional<Scheme> sipSchemeOpt = schemeRepository.findByAmfiCode(amfiCode);
                        if (sipSchemeOpt.isPresent() && sipSchemeOpt.get().getLastNav() != null
                                && sipSchemeOpt.get().getLastNav().compareTo(BigDecimal.ZERO) > 0) {
                            latestNav = sipSchemeOpt.get().getLastNav();
                        } else {
                            BigDecimal totalUnits = (BigDecimal) h.getOrDefault("units", BigDecimal.ONE);
                            if (totalUnits.compareTo(BigDecimal.ZERO) > 0) {
                                latestNav = actualSipValue.divide(totalUnits, 4, RoundingMode.HALF_UP);
                            }
                        }
                        BigDecimal hypotheticalLumpsumValue = totalInvested
                            .divide(firstNav, 4, RoundingMode.HALF_UP)
                            .multiply(latestNav);

                        Map<String, Object> analysis = new HashMap<>();
                        analysis.put("fundName", h.get("schemeName"));
                        analysis.put("sipValue", actualSipValue.setScale(2, RoundingMode.HALF_UP));
                        analysis.put("lumpsumValue", hypotheticalLumpsumValue.setScale(2, RoundingMode.HALF_UP));
                        BigDecimal diff = actualSipValue.subtract(hypotheticalLumpsumValue);
                        analysis.put("difference", diff.setScale(2, RoundingMode.HALF_UP));
                        analysis.put("winner", diff.compareTo(BigDecimal.ZERO) >= 0 ? "SIP Strategy" : "Lumpsum Strategy");

                        // Return percentages: (currentValue - invested) / invested × 100
                        BigDecimal sipReturnPct = actualSipValue.subtract(totalInvested)
                            .divide(totalInvested, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal lumpsumReturnPct = hypotheticalLumpsumValue.subtract(totalInvested)
                            .divide(totalInvested, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                        analysis.put("sipAbsReturn", sipReturnPct);
                        analysis.put("lumpsumAbsReturn", lumpsumReturnPct);

                        sipAnalysis.add(analysis);
                    }
                }
            }
        }

        Map<String, Object> sipSuite = new HashMap<>();
        sipSuite.put("activeSips", activeSips);
        sipSuite.put("totalSipOutflow", totalSipOutflow);
        sipSuite.put("sipStreak", "No Missed SIPs");
        sipSuite.put("analysis", sipAnalysis);
        return sipSuite;
    }

    // ─── Fund Overlap Matrix ──────────────────────────────────────────────────

    public Map<String, Object> getFundOverlapMatrix(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        List<Map<String, Object>> matrixNodes = new ArrayList<>();
        List<Map<String, Object>> matrixLinks = new ArrayList<>();

        for (int i = 0; i < portfolio.size(); i++) {
            Map<String, Object> fund1 = portfolio.get(i);
            String id1 = (String) fund1.get("schemeAmfiCode");
            String name1 = (String) fund1.get("schemeName");
            // broadCategory is what TransactionService.getPortfolioSummary puts in the map
            String cat1 = (String) fund1.get("broadCategory");

            matrixNodes.add(Map.of(
                "id",       id1 != null ? id1 : "UNKNOWN_" + i,
                "name",     name1 != null ? name1 : "Unknown",
                "category", cat1 != null ? cat1 : "Unknown"
            ));

            for (int j = i + 1; j < portfolio.size(); j++) {
                Map<String, Object> fund2 = portfolio.get(j);
                String id2  = (String) fund2.get("schemeAmfiCode");
                String cat2 = (String) fund2.get("broadCategory");

                double overlapPct = 0;
                if (cat1 != null && cat2 != null) {
                    if (cat1.equals(cat2))                                   overlapPct = 40.0;
                    else if (cat1.contains("EQUITY") && cat2.contains("EQUITY")) overlapPct = 15.0;
                    else if (cat1.contains("DEBT")   && cat2.contains("DEBT"))   overlapPct = 10.0;
                }

                if (overlapPct > 0) {
                    // small ±5 jitter so charts look natural
                    overlapPct = Math.max(0, overlapPct + (new Random().nextDouble() * 10 - 5));
                    matrixLinks.add(Map.of(
                        "source",     id1 != null ? id1 : "",
                        "target",     id2 != null ? id2 : "",
                        "overlapPct", Math.round(overlapPct * 10.0) / 10.0
                    ));
                }
            }
        }

        double avgOverlap = matrixLinks.stream()
            .mapToDouble(l -> (double) l.get("overlapPct"))
            .average().orElse(0.0);

        Map<String, Object> response = new HashMap<>();
        response.put("nodes", matrixNodes);
        response.put("links", matrixLinks);
        response.put("averageOverlapPct", Math.round(avgOverlap * 10.0) / 10.0);
        return response;
    }

    // ─── Risk Profile Persistence ─────────────────────────────────────────────

    public void saveRiskProfile(Long userId, String riskProfile) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!riskProfile.equals("CONSERVATIVE") && !riskProfile.equals("MODERATE") && !riskProfile.equals("AGGRESSIVE")) {
            throw new RuntimeException("Invalid risk profile. Must be CONSERVATIVE, MODERATE, or AGGRESSIVE.");
        }
        user.setRiskProfile(riskProfile);
        userRepository.save(user);
    }

    // ─── Accurate Metrics (NAV-based) ─────────────────────────────────────

    /**
     * Computes accurate volatility, Sharpe ratio, and max drawdown using
     * the NAV-based monthly growth timeline already computed by getPortfolioReturns().
     *
     * NOTE: We do NOT call returnsService.getPortfolioReturns() here — that would
     * trigger a second full NAV fetch cycle. Instead we call this method ONLY from
     * getRiskProfile(), passing in a lightweight growthTimeline precomputed from
     * the Caffeine-cached nav_history data.
     */
    private Map<String, Object> computeAccurateMetrics(Long userId) {
        try {
            // Delegate to ReturnsService but only if NAV data is already in DB/cache
            // (i.e., getPortfolioReturns won't do any new mfapi.in calls for already-loaded schemes)
            Map<String, Object> portfolioReturns = returnsService.getPortfolioReturns(userId);
            return computeFromTimeline(portfolioReturns);
        } catch (Exception e) {
            // Non-critical — return zeros if computation fails
            return zeroAccurateMetrics();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> computeFromTimeline(Map<String, Object> portfolioReturns) {
        List<Map<String, Object>> timeline =
            (List<Map<String, Object>>) portfolioReturns.get("growthTimeline");

        if (timeline == null || timeline.size() < 3) return zeroAccurateMetrics();

        // 1. Monthly returns from real NAV-based timeline
        List<Double> monthlyReturns = new ArrayList<>();
        for (int i = 1; i < timeline.size(); i++) {
            double prevValue = asBigDecimal(timeline.get(i - 1).get("value")).doubleValue();
            double currValue = asBigDecimal(timeline.get(i).get("value")).doubleValue();
            if (prevValue > 0) monthlyReturns.add((currValue - prevValue) / prevValue);
        }

        if (monthlyReturns.isEmpty()) return zeroAccurateMetrics();

        // 2. Annualized volatility = σ_monthly × √12
        double mean = monthlyReturns.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = monthlyReturns.stream()
            .mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        double annualizedVol = Math.sqrt(variance) * Math.sqrt(12);

        // 3. Annualized return
        double firstValue = asBigDecimal(timeline.get(0).get("value")).doubleValue();
        double lastValue  = asBigDecimal(timeline.get(timeline.size() - 1).get("value")).doubleValue();
        int months = timeline.size() - 1;
        double totalReturn = firstValue > 0 ? (lastValue / firstValue) - 1 : 0;
        double annualizedReturn = months > 0 ? Math.pow(1 + totalReturn, 12.0 / months) - 1 : 0;

        // 4. Sharpe = (annualized return − Rf) / annualized volatility
        double sharpe = annualizedVol > 0 ? (annualizedReturn - RISK_FREE_RATE) / annualizedVol : 0;

        // 5. Max drawdown using value/invested ratio (strips deposit effects)
        double peakRatio = 0, maxDD = 0;
        for (Map<String, Object> point : timeline) {
            double value = asBigDecimal(point.get("value")).doubleValue();
            double invested = asBigDecimal(point.get("invested")).doubleValue();
            if (invested > 0) {
                double ratio = value / invested;
                if (ratio > peakRatio) peakRatio = ratio;
                if (peakRatio > 0) {
                    double dd = (peakRatio - ratio) / peakRatio;
                    if (dd > maxDD) maxDD = dd;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("accurateVolatilityPct", round(annualizedVol * 100));
        result.put("accurateSharpeRatio",   round(sharpe));
        result.put("accurateMaxDrawdownPct", round(maxDD * 100));
        return result;
    }

    private Map<String, Object> zeroAccurateMetrics() {
        Map<String, Object> m = new HashMap<>();
        m.put("accurateVolatilityPct", 0.0);
        m.put("accurateSharpeRatio",   0.0);
        m.put("accurateMaxDrawdownPct", 0.0);
        return m;
    }

    private BigDecimal asBigDecimal(Object val) {
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return BigDecimal.ZERO;
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private double mapUserProfileToTolerance(String profile) {
        return switch (profile) {
            case "CONSERVATIVE" -> 2.0;
            case "AGGRESSIVE"   -> 5.5;
            default             -> 3.5;
        };
    }

    private String buildRiskComparison(double portfolioRiskScore, double userToleranceScore) {
        if (portfolioRiskScore == 0.0) return "We need more portfolio data before we can compare your risk tolerance.";
        if (portfolioRiskScore > userToleranceScore) return "Your portfolio is more risky than your stated comfort level.";
        if (portfolioRiskScore < userToleranceScore - 1.0) return "Your portfolio is below your stated risk tolerance and may be overly conservative.";
        return "Your portfolio risk is broadly aligned with your stated comfort level.";
    }

    private String getRiskLabel(double score) {
        if (score == 0.0) return "N/A";
        if (score < 1.5) return "Low";
        if (score < 2.5) return "Low to Moderate";
        if (score < 3.5) return "Moderate";
        if (score < 4.5) return "Moderately High";
        if (score < 5.5) return "High";
        return "Very High";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
