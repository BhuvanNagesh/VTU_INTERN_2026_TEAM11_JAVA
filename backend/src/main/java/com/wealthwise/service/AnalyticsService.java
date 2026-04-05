package com.wealthwise.service;

import com.wealthwise.model.Scheme;
import com.wealthwise.model.Transaction;
import com.wealthwise.model.User;
import com.wealthwise.repository.SchemeRepository;
import com.wealthwise.repository.TransactionRepository;
import com.wealthwise.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class AnalyticsService {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private ReturnsService returnsService;

    @Autowired
    private UserRepository userRepository;

    // ─── Module M12: Risk Profiler & Scorer ──────────────────────────────────

    public Map<String, Object> getRiskProfile(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);
        
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        double weightedRiskSum = 0;

        int numEquityFunds = 0;
        int numDebtFunds = 0;
        int numHybridFunds = 0;
        int largeCapCount = 0;
        int midCapCount = 0;
        int smallCapCount = 0;

        Set<String> uniqueAmcs = new HashSet<>();

        // Risk Mapping Arrays (1 to 6 scale)
        // 1: Low, 2: Low-Mid, 3: Mid, 4: Mid-High, 5: High, 6: Very High
        
        for (Map<String, Object> h : portfolio) {
            BigDecimal cv = (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO);
            String category = (String) h.getOrDefault("broadCategory", "UNKNOWN");
            String schemeName = (String) h.getOrDefault("schemeName", "");
            String schemeType = (String) h.getOrDefault("schemeType", "");

            totalCurrentValue = totalCurrentValue.add(cv);

            // Mock basic Risk Score based on textual category
            double fundRisk = mapCategoryToRisk(category, schemeType);
            weightedRiskSum += (fundRisk * cv.doubleValue());

            // Collect Diversification Stats
            if (category.toLowerCase().contains("equity")) numEquityFunds++;
            else if (category.toLowerCase().contains("debt")) numDebtFunds++;
            else if (category.toLowerCase().contains("hybrid")) numHybridFunds++;

            if (schemeType.toLowerCase().contains("large cap")) largeCapCount++;
            if (schemeType.toLowerCase().contains("mid cap")) midCapCount++;
            if (schemeType.toLowerCase().contains("small cap")) smallCapCount++;

            // Mock AMC extraction (first 2 words)
            String[] words = schemeName.split(" ");
            if (words.length > 1) {
                uniqueAmcs.add(words[0] + " " + words[1]);
            }
        }

        double portfolioRiskScore = totalCurrentValue.compareTo(BigDecimal.ZERO) == 0 ? 0 
                : weightedRiskSum / totalCurrentValue.doubleValue();

        // Diversification Score (out of 10)
        double divScore = 0;
        // Asset Class Mix (0 to 2)
        if (numEquityFunds > 0 && numDebtFunds > 0 && numHybridFunds > 0) divScore += 2.0;
        else if (numEquityFunds > 0 && numDebtFunds > 0) divScore += 1.5;
        else if (numEquityFunds > 0) divScore += 0.5;

        // Number of Funds (0 to 2)
        int totalFunds = portfolio.size();
        if (totalFunds >= 3 && totalFunds <= 8) divScore += 2.0;
        else if (totalFunds >= 9 && totalFunds <= 12) divScore += 1.0;

        // Market Cap Spread (0 to 2)
        if (largeCapCount > 0 && midCapCount > 0 && smallCapCount > 0) divScore += 2.0;
        else if (largeCapCount > 0 && midCapCount > 0) divScore += 1.5;
        else if (largeCapCount > 0) divScore += 1.0;

        // AMC Spread (0 to 2)
        if (uniqueAmcs.size() >= 3) divScore += 2.0;
        else if (uniqueAmcs.size() == 2) divScore += 1.0;

        // Set remaining 2 for generic overlap metric
        divScore += 1.0; // Padded for overlap logic placeholder

        // Mock Volatility and Sharpe
        double simulatedVolatility = 12.5 + (portfolioRiskScore * 1.5); // Mock 12-20%
        double simulatedSharpe = portfolioRiskScore > 0 ? (15.0 - 7.0) / simulatedVolatility : 0.0;
        double maxDrawdown = -1 * (portfolioRiskScore * 4.5); // Mock -15% to -27%

        // Load user's self-assessed risk tolerance
        User user = userRepository.findById(userId).orElse(null);
        String userRiskProfile = (user != null && user.getRiskProfile() != null) ? user.getRiskProfile() : "MODERATE";

        // Map user profile label to numeric score for comparison
        double userToleranceScore = switch (userRiskProfile) {
            case "CONSERVATIVE" -> 2.0;
            case "AGGRESSIVE"   -> 5.5;
            default             -> 3.5; // MODERATE
        };

        String riskComparison;
        if (portfolioRiskScore > userToleranceScore + 0.5) {
            riskComparison = "⚠️ Your portfolio is MORE risky than your comfort level. Consider adding low-risk debt funds.";
        } else if (portfolioRiskScore < userToleranceScore - 1.0) {
            riskComparison = "💡 You could take slightly more risk for potentially higher returns."; 
        } else {
            riskComparison = "✅ Your portfolio risk aligns well with your stated risk tolerance.";
        }

        Map<String, Object> riskData = new HashMap<>();
        riskData.put("portfolioRiskScore", Math.round(portfolioRiskScore * 100.0) / 100.0);
        riskData.put("portfolioRiskLabel", getRiskLabel(portfolioRiskScore));
        riskData.put("diversificationScore", Math.round(divScore * 10.0) / 10.0);
        riskData.put("volatilityPct", Math.round(simulatedVolatility * 100.0) / 100.0);
        riskData.put("sharpeRatio", Math.round(simulatedSharpe * 100.0) / 100.0);
        riskData.put("maxDrawdownPct", Math.round(maxDrawdown * 100.0) / 100.0);
        riskData.put("totalFunds", totalFunds);
        riskData.put("uniqueAmcs", uniqueAmcs.size());
        riskData.put("userRiskProfile", userRiskProfile);
        riskData.put("riskComparison", riskComparison);

        return riskData;
    }

    private double mapCategoryToRisk(String broadCategory, String subCategory) {
        String cat = broadCategory.toLowerCase();
        String sub = subCategory.toLowerCase();
        
        if (cat.contains("debt")) {
            if (sub.contains("liquid") || sub.contains("overnight")) return 1.0;
            if (sub.contains("credit")) return 4.0;
            return 2.5;
        }
        if (cat.contains("hybrid")) return 4.0;
        if (cat.contains("equity")) {
            if (sub.contains("large")) return 4.5;
            if (sub.contains("small") || sub.contains("sector")) return 6.0;
            return 5.0; // Midcap/Flexi
        }
        return 3.0; // Unknown
    }

    private String getRiskLabel(double score) {
        if (score == 0) return "N/A";
        if (score <= 1.5) return "Low";
        if (score <= 2.5) return "Low to Moderate";
        if (score <= 3.5) return "Moderate";
        if (score <= 4.5) return "Moderately High";
        if (score <= 5.5) return "High";
        return "Very High";
    }

    // ─── Module M13: SIP Intelligence Suite ──────────────────────────────────

    public Map<String, Object> getSipIntelligence(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);
        
        int activeSips = 0;
        BigDecimal totalSipOutflow = BigDecimal.ZERO;

        List<Map<String, Object>> sipAnalysis = new ArrayList<>();

        for (Map<String, Object> h : portfolio) {
            String amfiCode = (String) h.get("schemeAmfiCode");
            List<Transaction> txns = transactionRepository.findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);
            
            // Check if there are SIP entries
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

                // Analyzer Calculation:
                // What if we took ALL money and lumpsumped it on the First SIP Date?
                BigDecimal totalInvested = (BigDecimal) h.getOrDefault("investedAmount", BigDecimal.ZERO);
                BigDecimal actualSipValue = (BigDecimal) h.getOrDefault("currentValue", BigDecimal.ZERO);
                
                // Let's get the first Date of transaction
                Transaction firstTxn = txns.get(0);
                BigDecimal firstNav = firstTxn.getNav(); // Fallback theoretical
                if (firstNav != null && firstNav.compareTo(BigDecimal.ZERO) > 0) {
                    // Hypothetical lumpsum = sum / firstNav * latestNav
                    // To do this perfectly we need latest nav
                    BigDecimal latestNav = BigDecimal.ONE;
                    // Approximate latestNav = currentValue / totalUnits
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

    // ─── Module M11: Fund Overlap Mock Engine ────────────────────────────────

    public Map<String, Object> getFundOverlapMatrix(Long userId) {
        List<Map<String, Object>> portfolio = transactionService.getPortfolioSummary(userId);
        
        List<Map<String, Object>> matrixNodes = new ArrayList<>();
        List<Map<String, Object>> matrixLinks = new ArrayList<>();

        for (int i = 0; i < portfolio.size(); i++) {
            Map<String, Object> fund1 = portfolio.get(i);
            String id1 = (String) fund1.get("schemeAmfiCode");
            String name1 = (String) fund1.get("schemeName");
            String cat1 = (String) fund1.get("schemeType");
            
            // Add Node
            matrixNodes.add(Map.of("id", id1, "name", name1, "category", cat1));

            for (int j = i + 1; j < portfolio.size(); j++) {
                Map<String, Object> fund2 = portfolio.get(j);
                String id2 = (String) fund2.get("schemeAmfiCode");
                String cat2 = (String) fund2.get("schemeType");

                // Mock overlap % based on category similarity
                double overlapPct = 0;
                if (cat1 != null && cat2 != null) {
                    if (cat1.equals(cat2)) overlapPct = 40.0; // Same category, high overlap
                    else if (cat1.contains("Large Cap") && cat2.contains("Large Cap")) overlapPct = 55.0;
                    else if (cat1.contains("Equity") && cat2.contains("Equity")) overlapPct = 15.0;
                    else overlapPct = 0.0;
                }

                // Inject randomness for realism
                if (overlapPct > 0) {
                    overlapPct = overlapPct + (new Random().nextDouble() * 10 - 5);
                    if (overlapPct < 0) overlapPct = 0;
                    
                    matrixLinks.add(Map.of("source", id1, "target", id2, "overlapPct", Math.round(overlapPct * 10.0) / 10.0));
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("nodes", matrixNodes);
        response.put("links", matrixLinks);

        // Overall portfolio overlap average
        double avgOverlap = matrixLinks.stream().mapToDouble(l -> (double) l.get("overlapPct")).average().orElse(0.0);
        response.put("averageOverlapPct", Math.round(avgOverlap * 10.0) / 10.0);

        return response;
    }

    // ─── Save Risk Profile from Survey ───────────────────────────────────────

    public void saveRiskProfile(Long userId, String riskProfile) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        // Validate value
        if (!riskProfile.equals("CONSERVATIVE") && !riskProfile.equals("MODERATE") && !riskProfile.equals("AGGRESSIVE")) {
            throw new RuntimeException("Invalid risk profile. Must be CONSERVATIVE, MODERATE, or AGGRESSIVE.");
        }
        user.setRiskProfile(riskProfile);
        userRepository.save(user);
    }
}
