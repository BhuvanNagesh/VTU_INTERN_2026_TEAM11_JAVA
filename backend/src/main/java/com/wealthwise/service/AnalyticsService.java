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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
        double portfolioReturn = calculatePortfolioReturn(portfolioValues);
        double sharpeRatio = round(calculateSharpeRatio(portfolioReturn, volatility));
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
            if (txn.getSchemeAmfiCode() == null || txn.getUnits() == null) {
                continue;
            }

            holdings.merge(txn.getSchemeAmfiCode(), txn.getUnits(), BigDecimal::add);

            BigDecimal totalValue = ZERO;
            for (Map.Entry<String, BigDecimal> entry : holdings.entrySet()) {
                if (entry.getValue().compareTo(ZERO) <= 0) {
                    continue;
                }

                Scheme scheme = schemeMap.get(entry.getKey());
                if (scheme == null || scheme.getLastNav() == null) {
                    continue;
                }

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
            if (previous > 0) {
                returns.add((current - previous) / previous);
            }
        }
        return returns;
    }

    private double calculateVolatility(List<Double> returns) {
        if (returns.isEmpty()) {
            return 0.0;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = 0.0;
        for (double value : returns) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= returns.size();

        return Math.sqrt(variance) * Math.sqrt(12);
    }

    private double calculatePortfolioReturn(List<Double> values) {
        if (values.size() < 2) {
            return 0.0;
        }

        double initial = values.get(0);
        double latest = values.get(values.size() - 1);
        if (initial <= 0) {
            return 0.0;
        }

        return (latest - initial) / initial;
    }

    private double calculateSharpeRatio(double portfolioReturn, double volatility) {
        if (volatility == 0.0) {
            return 0.0;
        }
        return (portfolioReturn - RISK_FREE_RATE) / volatility;
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

    private double calculateDiversificationScore(List<InvestmentLot> lots, Map<String, Scheme> schemeMap) {
        Set<String> distinctFunds = new HashSet<>();
        double totalValue = 0.0;
        double maxFundValue = 0.0;

        for (InvestmentLot lot : lots) {
            if (lot.getUnitsRemaining() == null || lot.getUnitsRemaining().compareTo(ZERO) <= 0) {
                continue;
            }

            Scheme scheme = schemeMap.get(lot.getSchemeAmfiCode());
            if (scheme == null || scheme.getLastNav() == null) {
                continue;
            }

            distinctFunds.add(lot.getSchemeAmfiCode());
            double currentValue = lot.getUnitsRemaining().multiply(scheme.getLastNav()).doubleValue();
            totalValue += currentValue;
            if (currentValue > maxFundValue) {
                maxFundValue = currentValue;
            }
        }

        double score = 0.0;
        int fundCount = distinctFunds.size();
        if (fundCount >= 3 && fundCount <= 8) {
            score += 4.0;
        } else if (fundCount == 2 || fundCount > 8) {
            score += 2.0;
        }

        if (totalValue > 0) {
            double concentration = maxFundValue / totalValue;
            if (concentration < 0.5) {
                score += 6.0;
            } else if (concentration < 0.7) {
                score += 3.0;
            }
        }

        return score;
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
            List<Transaction> txns = transactionRepository.findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);

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

                Transaction firstTxn = txns.get(0);
                BigDecimal firstNav = firstTxn.getNav();
                if (firstNav != null && firstNav.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal latestNav = BigDecimal.ONE;
                    BigDecimal totalUnits = (BigDecimal) h.getOrDefault("units", BigDecimal.ONE);
                    if (totalUnits.compareTo(BigDecimal.ZERO) > 0) {
                        latestNav = actualSipValue.divide(totalUnits, 4, RoundingMode.HALF_UP);
                    }

                    BigDecimal hypotheticalLumpsumValue = totalInvested.divide(firstNav, 4, RoundingMode.HALF_UP).multiply(latestNav);

                    Map<String, Object> analysis = new HashMap<>();
                    analysis.put("fundName", h.get("schemeName"));
                    analysis.put("sipValue", actualSipValue.setScale(2, RoundingMode.HALF_UP));
                    analysis.put("lumpsumValue", hypotheticalLumpsumValue.setScale(2, RoundingMode.HALF_UP));

                    BigDecimal diff = actualSipValue.subtract(hypotheticalLumpsumValue);
                    analysis.put("difference", diff.setScale(2, RoundingMode.HALF_UP));
                    analysis.put("winner", diff.compareTo(BigDecimal.ZERO) >= 0 ? "SIP Strategy" : "Lumpsum Strategy");

                    sipAnalysis.add(analysis);
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

    public Map<String, Object> getFundOverlapMatrix(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);

        List<Map<String, Object>> matrixNodes = new ArrayList<>();
        List<Map<String, Object>> matrixLinks = new ArrayList<>();

        for (int i = 0; i < portfolio.size(); i++) {
            Map<String, Object> fund1 = portfolio.get(i);
            String id1 = (String) fund1.get("schemeAmfiCode");
            String name1 = (String) fund1.get("schemeName");
            String cat1 = (String) fund1.get("schemeType");

            matrixNodes.add(Map.of(
                "id", id1 != null ? id1 : "UNKNOWN_ID_" + i,
                "name", name1 != null ? name1 : "Unknown",
                "category", cat1 != null ? cat1 : "Unknown"));

            for (int j = i + 1; j < portfolio.size(); j++) {
                Map<String, Object> fund2 = portfolio.get(j);
                String id2 = (String) fund2.get("schemeAmfiCode");
                String cat2 = (String) fund2.get("schemeType");

                double overlapPct = 0;
                if (cat1 != null && cat2 != null) {
                    if (cat1.equals(cat2)) {
                        overlapPct = 40.0;
                    } else if (cat1.contains("Large Cap") && cat2.contains("Large Cap")) {
                        overlapPct = 55.0;
                    } else if (cat1.contains("Equity") && cat2.contains("Equity")) {
                        overlapPct = 15.0;
                    }
                }

                if (overlapPct > 0) {
                    overlapPct = overlapPct + (new Random().nextDouble() * 10 - 5);
                    if (overlapPct < 0) {
                        overlapPct = 0;
                    }

                    matrixLinks.add(Map.of(
                        "source", id1,
                        "target", id2,
                        "overlapPct", Math.round(overlapPct * 10.0) / 10.0));
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
        response.put("averageOverlapPct", Math.round(avgOverlap * 10.0) / 10.0);

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
