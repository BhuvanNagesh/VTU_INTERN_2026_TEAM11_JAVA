package com.wealthwise.service;

import com.wealthwise.model.FundHolding;
import com.wealthwise.model.InvestmentLot;
import com.wealthwise.model.Scheme;
import com.wealthwise.model.Transaction;
import com.wealthwise.model.User;
import com.wealthwise.repository.FundHoldingRepository;
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
    @Autowired private FundHoldingRepository fundHoldingRepository;
    @Autowired private FundHoldingsIngestionService fundHoldingsIngestionService;

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

    // ─── Fund Overlap Matrix (Real Stock-Level Analysis) ─────────────────────

    public Map<String, Object> getFundOverlapMatrix(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        // Filter valid, non-synthetic equity/hybrid funds only
        List<Map<String, Object>> validFunds = new ArrayList<>();
        for (Map<String, Object> fund : portfolio) {
            String code = (String) fund.get("schemeAmfiCode");
            if (code == null || code.startsWith("WW_")) continue;
            BigDecimal cv = (BigDecimal) fund.getOrDefault("currentValue", BigDecimal.ZERO);
            if (cv == null || cv.compareTo(BigDecimal.ZERO) <= 0) continue;
            validFunds.add(fund);
        }

        // Pre-load holdings for all valid funds (on-demand ingestion)
        Map<String, List<FundHolding>> holdingsMap = new HashMap<>();
        for (Map<String, Object> fund : validFunds) {
            String code = (String) fund.get("schemeAmfiCode");
            try {
                fundHoldingsIngestionService.ensureHoldingsExist(code);
                List<FundHolding> h = fundHoldingRepository.findBySchemeAmfiCode(code);
                if (!h.isEmpty()) holdingsMap.put(code, h);
            } catch (Exception e) {
                // Non-critical — fall back to category estimation
            }
        }

        List<Map<String, Object>> matrixNodes = new ArrayList<>();
        List<Map<String, Object>> matrixLinks = new ArrayList<>();

        for (int i = 0; i < validFunds.size(); i++) {
            Map<String, Object> fund1 = validFunds.get(i);
            String id1   = (String) fund1.get("schemeAmfiCode");
            String name1 = (String) fund1.get("schemeName");
            String cat1  = (String) fund1.get("broadCategory");

            Map<String, Object> node1 = new HashMap<>();
            node1.put("id",       id1 != null ? id1 : "UNKNOWN_" + i);
            node1.put("name",     name1 != null ? name1 : "Unknown");
            node1.put("category", cat1 != null ? cat1 : "Unknown");
            node1.put("hasRealHoldings", holdingsMap.containsKey(id1));
            matrixNodes.add(node1);

            for (int j = i + 1; j < validFunds.size(); j++) {
                Map<String, Object> fund2 = validFunds.get(j);
                String id2   = (String) fund2.get("schemeAmfiCode");
                String name2 = (String) fund2.get("schemeName");
                String cat2  = (String) fund2.get("broadCategory");

                List<FundHolding> h1 = holdingsMap.get(id1);
                List<FundHolding> h2 = holdingsMap.get(id2);

                double overlapPct         = 0.0;
                double weightedOverlapPct = 0.0;
                List<String> commonStocks  = new ArrayList<>();
                boolean isRealOverlap      = false;

                if (h1 != null && !h1.isEmpty() && h2 != null && !h2.isEmpty()) {
                    // ── REAL STOCK-LEVEL OVERLAP (Arya's algorithm) ─────────────
                    isRealOverlap = true;
                    Map<String, Double> weightsA = new LinkedHashMap<>();
                    Map<String, Double> weightsB = new LinkedHashMap<>();
                    for (FundHolding h : h1) weightsA.put(normalise(h.getStockName()), h.getWeightPct() != null ? h.getWeightPct() : 1.0);
                    for (FundHolding h : h2) weightsB.put(normalise(h.getStockName()), h.getWeightPct() != null ? h.getWeightPct() : 1.0);

                    Set<String> setA = weightsA.keySet();
                    Set<String> setB = weightsB.keySet();
                    Set<String> intersection = new HashSet<>(setA);
                    intersection.retainAll(setB);

                    // Stock count overlap (Arya's formula)
                    if (!setA.isEmpty() && !setB.isEmpty()) {
                        overlapPct = (double) intersection.size() / Math.min(setA.size(), setB.size()) * 100.0;
                    }

                    // Weighted overlap: Σ min(wA, wB) for common stocks
                    double wSum = 0.0;
                    for (String stock : intersection) {
                        wSum += Math.min(
                            weightsA.getOrDefault(stock, 0.0),
                            weightsB.getOrDefault(stock, 0.0)
                        );
                    }
                    weightedOverlapPct = wSum;

                    // Collect human-readable stock names for the common set
                    Map<String, String> nameMapA = new HashMap<>();
                    for (FundHolding h : h1) nameMapA.put(normalise(h.getStockName()), h.getStockName());
                    for (String key : intersection) {
                        commonStocks.add(nameMapA.getOrDefault(key, key));
                    }
                    commonStocks.sort(String::compareTo);
                } else {
                    // ── FALLBACK: Category-similarity estimation ─────────────────
                    isRealOverlap = false;
                    if (cat1 != null && cat2 != null) {
                        if (cat1.equals(cat2))                                         overlapPct = 38.0;
                        else if (cat1.contains("EQUITY") && cat2.contains("EQUITY"))   overlapPct = 15.0;
                        else if (cat1.contains("HYBRID") && cat2.contains("EQUITY"))   overlapPct = 12.0;
                        else if (cat1.contains("DEBT")   && cat2.contains("DEBT"))     overlapPct = 10.0;
                    }
                    weightedOverlapPct = overlapPct * 0.7; // approximate
                }

                overlapPct         = round(overlapPct);
                weightedOverlapPct = round(weightedOverlapPct);

                if (overlapPct > 0 || isRealOverlap) {
                    // Risk level from Arya's thresholds
                    String riskLevel = overlapPct > 60 ? "HIGH" : overlapPct > 30 ? "MODERATE" : "LOW";

                    // Actionable insight (enhanced from Arya's idea)
                    String insight = buildOverlapInsight(overlapPct, weightedOverlapPct, commonStocks.size(),
                                                         name1, name2, isRealOverlap);

                    Map<String, Object> link = new HashMap<>();
                    link.put("source",             id1 != null ? id1 : "");
                    link.put("target",             id2 != null ? id2 : "");
                    link.put("sourceName",         name1);
                    link.put("targetName",         name2);
                    link.put("overlapPct",         overlapPct);
                    link.put("weightedOverlapPct", weightedOverlapPct);
                    link.put("riskLevel",          riskLevel);
                    link.put("insight",            insight);
                    link.put("commonStocks",       commonStocks);
                    link.put("isRealOverlap",      isRealOverlap);
                    // Keep legacy field name for frontend compat
                    link.put("categorySimilarityPct", overlapPct);
                    matrixLinks.add(link);
                }
            }
        }

        // Average overlap
        double avgOverlap = matrixLinks.stream()
            .mapToDouble(l -> (double) l.get("overlapPct"))
            .average().orElse(0.0);
        double avgWeighted = matrixLinks.stream()
            .mapToDouble(l -> (double) l.get("weightedOverlapPct"))
            .average().orElse(0.0);

        // ── High Overlap Stocks (portfolio-level, Arya's idea) ─────────────────
        List<Map<String, Object>> highOverlapStocks = buildHighOverlapStocks(holdingsMap);

        // ── Consolidation Suggestions ─────────────────────────────────────────
        List<Map<String, Object>> consolidationSuggestions = buildConsolidationSuggestions(matrixLinks);

        // ── Portfolio Diversification Insight ────────────────────────────────
        String portfolioInsight = buildPortfolioInsight(matrixLinks, highOverlapStocks, validFunds.size());

        // Disclaimer if any fallback was used
        boolean hasEstimates = matrixLinks.stream().anyMatch(l -> !(boolean)l.getOrDefault("isRealOverlap", true));
        String disclaimer = hasEstimates
            ? "Some funds use category-based overlap estimation. Holdings data is auto-generated from SEBI-mandated index allocation rules."
            : null;

        Map<String, Object> response = new HashMap<>();
        response.put("nodes",                         matrixNodes);
        response.put("links",                         matrixLinks);
        response.put("averageOverlapPct",             round(avgOverlap));
        response.put("averageWeightedOverlapPct",     round(avgWeighted));
        response.put("averageCategorySimilarityPct",  round(avgOverlap)); // legacy compat
        response.put("highOverlapStocks",             highOverlapStocks);
        response.put("consolidationSuggestions",      consolidationSuggestions);
        response.put("portfolioDiversificationInsight", portfolioInsight);
        if (disclaimer != null) response.put("disclaimer", disclaimer);
        return response;
    }

    // ─── Overlap intelligence helpers ─────────────────────────────────────────

    private String normalise(String name) {
        if (name == null) return "";
        return name.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private String buildOverlapInsight(double overlapPct, double weightedPct,
                                       int commonCount, String nameA, String nameB,
                                       boolean isReal) {
        String fundA = shortenFundName(nameA);
        String fundB = shortenFundName(nameB);
        if (overlapPct > 60) {
            return String.format(
                "🔴 High overlap (%d common stocks) — %s & %s share over 60%% holdings. " +
                "Consider consolidating into one fund to reduce redundancy.",
                commonCount, fundA, fundB);
        } else if (overlapPct > 40) {
            return String.format(
                "🟡 Significant overlap — %s & %s share %d stocks (%.0f%%). " +
                "Review if both funds are necessary in your portfolio.",
                fundA, fundB, commonCount, overlapPct);
        } else if (overlapPct > 20) {
            return String.format(
                "🟡 Moderate overlap — %s & %s share %d common stocks. " +
                "This is within acceptable diversification range.",
                fundA, fundB, commonCount);
        } else {
            return String.format(
                "✅ Good diversification — %s & %s have minimal overlap (%.0f%%). " +
                "These funds complement each other well.",
                fundA, fundB, overlapPct);
        }
    }

    private String shortenFundName(String name) {
        if (name == null) return "Fund";
        String[] words = name.split(" ");
        return words.length >= 2 ? words[0] + " " + words[1] : name;
    }

    private List<Map<String, Object>> buildHighOverlapStocks(Map<String, List<FundHolding>> holdingsMap) {
        if (holdingsMap.size() < 2) return Collections.emptyList();

        // Count how many funds each stock appears in
        Map<String, Map<String, Object>> stockData = new LinkedHashMap<>();
        for (Map.Entry<String, List<FundHolding>> entry : holdingsMap.entrySet()) {
            String code = entry.getKey();
            for (FundHolding h : entry.getValue()) {
                String norm = normalise(h.getStockName());
                Map<String, Object> info = stockData.computeIfAbsent(norm, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("stockName", h.getStockName());
                    m.put("sector",    h.getSector());
                    m.put("funds",     new ArrayList<String>());
                    m.put("fundCount", 0);
                    return m;
                });
                @SuppressWarnings("unchecked")
                List<String> funds = (List<String>) info.get("funds");
                funds.add(code);
                info.put("fundCount", funds.size());
            }
        }

        // Filter to stocks in >1 fund and sort by count
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> info : stockData.values()) {
            int count = (int) info.get("fundCount");
            if (count > 1) result.add(info);
        }
        result.sort((a, b) -> Integer.compare((int)b.get("fundCount"), (int)a.get("fundCount")));

        // Cap at top 20 for UI performance
        return result.size() > 20 ? result.subList(0, 20) : result;
    }

    private List<Map<String, Object>> buildConsolidationSuggestions(
            List<Map<String, Object>> matrixLinks) {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Map<String, Object> link : matrixLinks) {
            double overlapPct = (double) link.get("overlapPct");
            if (overlapPct >= 50) {
                String severity = overlapPct >= 70 ? "HIGH" : "MODERATE";
                String sName = (String) link.get("sourceName");
                String tName = (String) link.get("targetName");
                @SuppressWarnings("unchecked")
                int commonCount = ((List<String>) link.getOrDefault("commonStocks", Collections.emptyList())).size();

                String message;
                if (overlapPct >= 70) {
                    message = String.format(
                        "%s and %s share %.0f%% of their holdings (%d common stocks). " +
                        "They are essentially duplicates — redeeming one and adding to the other would reduce cost and improve tracking.",
                        shortenFundName(sName), shortenFundName(tName), overlapPct, commonCount);
                } else {
                    message = String.format(
                        "%s and %s have significant overlap (%.0f%%). " +
                        "Consider whether both funds serve a distinct purpose in your strategy.",
                        shortenFundName(sName), shortenFundName(tName), overlapPct);
                }

                Map<String, Object> s = new HashMap<>();
                s.put("message",   message);
                s.put("severity",  severity);
                s.put("fundA",     sName);
                s.put("fundB",     tName);
                s.put("overlapPct", overlapPct);
                suggestions.add(s);
            }
        }

        // Sort by overlap % descending
        suggestions.sort((a, b) -> Double.compare((double)b.get("overlapPct"), (double)a.get("overlapPct")));
        return suggestions;
    }

    private String buildPortfolioInsight(List<Map<String, Object>> links,
                                         List<Map<String, Object>> highOverlapStocks,
                                         int totalFunds) {
        if (links.isEmpty()) {
            return totalFunds < 2
                ? "Add more funds to see overlap analysis."
                : "No significant overlap detected. Your funds appear well-diversified!";
        }
        long highRiskPairs = links.stream()
            .filter(l -> "HIGH".equals(l.get("riskLevel"))).count();
        double avgOverlap = links.stream()
            .mapToDouble(l -> (double)l.get("overlapPct")).average().orElse(0);
        int hotStocks = highOverlapStocks.size();

        if (highRiskPairs > 0) {
            return String.format(
                "⚠️ %d fund pair%s with HIGH overlap detected. %d stock%s appear in multiple funds. " +
                "Average overlap: %.0f%%. Review consolidation suggestions below.",
                highRiskPairs, highRiskPairs > 1 ? "s" : "",
                hotStocks, hotStocks != 1 ? "s" : "", avgOverlap);
        } else if (avgOverlap > 25) {
            return String.format(
                "Your portfolio has moderate overlap (avg %.0f%%). %d stock%s appear in multiple funds. " +
                "Consider reviewing fund allocation for better diversification.",
                avgOverlap, hotStocks, hotStocks != 1 ? "s" : "");
        } else {
            return String.format(
                "✅ Your %d-fund portfolio is well diversified with low average overlap (%.0f%%). " +
                "%d stock%s appear in more than one fund.",
                totalFunds, avgOverlap, hotStocks, hotStocks != 1 ? "s" : "");
        }
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
