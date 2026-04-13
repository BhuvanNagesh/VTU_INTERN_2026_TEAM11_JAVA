package com.wealthwise.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GoalEngineService {

    // ════════════════════════════════════════════════════════════
    // 🔥 Helper: Convert future nominal money → today's real money
    // ════════════════════════════════════════════════════════════
    private double toReal(double nominalFutureValue, double annualInflationRate, int months) {
        double monthlyInflation = annualInflationRate / 12;
        return nominalFutureValue / Math.pow(1 + monthlyInflation, months);
    }

    // ════════════════════════════════════════════════════════════
    // F16.2 — Monte Carlo Simulation
    // ════════════════════════════════════════════════════════════
    public MonteCarloResult runMonteCarlo(
            double initialPortfolio,
            double monthlyContribution,
            double monthlyMean,
            double monthlyStdDev,
            int months,
            double targetAmount,
            double annualInflationRate) {

        double monthlyInflation = annualInflationRate / 12;
        double inflationAdjustedTarget = targetAmount * Math.pow(1 + monthlyInflation, months);

        int simulations = 10_000;
        List<Double> finalValues = new ArrayList<>(simulations);
        Random random = new Random();

        for (int sim = 0; sim < simulations; sim++) {
            double portfolio = initialPortfolio;

            for (int month = 0; month < months; month++) {
                double r = monthlyMean + monthlyStdDev * random.nextGaussian();

                // Floor limits to prevent extreme negative math
                double floor = monthlyStdDev > 0.07 ? -0.30
                        : monthlyStdDev > 0.03 ? -0.20
                        : -0.10;

                r = Math.max(r, floor);
                portfolio = portfolio * (1 + r) + monthlyContribution;
                portfolio = Math.max(portfolio, 0);
            }
            finalValues.add(portfolio);
        }

        Collections.sort(finalValues);

        double p10Nominal = percentile(finalValues, 10);
        double p50Nominal = percentile(finalValues, 50);
        double p90Nominal = percentile(finalValues, 90);

        // 🔥 Convert output percentiles to TODAY's money
        double p10Real = toReal(p10Nominal, annualInflationRate, months);
        double p50Real = toReal(p50Nominal, annualInflationRate, months);
        double p90Real = toReal(p90Nominal, annualInflationRate, months);

        // Probability evaluates against the future target (apples to apples in future)
        long successCount = finalValues.stream()
                .filter(v -> v >= inflationAdjustedTarget)
                .count();

        double probability = (successCount * 100.0) / simulations;

        return new MonteCarloResult(
                Math.round(p10Real),
                Math.round(p50Real),
                Math.round(p90Real),
                Math.round(probability * 10.0) / 10.0
        );
    }

    // ════════════════════════════════════════════════════════════
    // F16.1 — Deterministic Projection
    // ════════════════════════════════════════════════════════════
    public DeterministicResult runDeterministicProjection(
            double initialPortfolio,
            double monthlyContribution,
            double monthlyMean,
            int months,
            double targetAmount, // Today's money target
            double annualInflationRate) {

        double r = monthlyMean;
        int n = months;

        // 1. Calculate future nominal values
        double fvCorpusNominal = initialPortfolio * Math.pow(1 + r, n);
        double fvSipNominal = monthlyContribution * ((Math.pow(1 + r, n) - 1) / r) * (1 + r);

        // 2. Deflate everything to today's money
        double fvCorpusReal = toReal(fvCorpusNominal, annualInflationRate, n);
        double fvSipReal = toReal(fvSipNominal, annualInflationRate, n);
        double totalReal = fvCorpusReal + fvSipReal;

        // 3. Compare apples to apples (Real Target vs Real Projection)
        double gapReal = targetAmount - totalReal;

        // --- Scenarios ---

        // Scenario A: Return is only 10% pa
        double lowerMean = 0.10 / 12;
        double projLowerNominal = initialPortfolio * Math.pow(1 + lowerMean, n)
                + monthlyContribution * ((Math.pow(1 + lowerMean, n) - 1) / lowerMean) * (1 + lowerMean);
        double projLowerReal = toReal(projLowerNominal, annualInflationRate, n);

        // Scenario B: Missed 6 SIPs
        double fvSipMissedNominal = monthlyContribution * ((Math.pow(1 + r, n - 6) - 1) / r) * (1 + r);
        double projMissedNominal = fvCorpusNominal + fvSipMissedNominal;
        double projMissedReal = toReal(projMissedNominal, annualInflationRate, n);

        // Scenario C: Inflation is 2% higher than expected
        double highInflationRate = annualInflationRate + 0.02;
        double totalNominal = fvCorpusNominal + fvSipNominal;
        // Same nominal output, but severely degraded purchasing power in today's money
        double projHighInfReal = toReal(totalNominal, highInflationRate, n);

        List<SensitivityRow> sensitivity = List.of(
                new SensitivityRow("Return = 10% pa", Math.round(projLowerReal), Math.round(targetAmount - projLowerReal)),
                new SensitivityRow("6 SIPs missed", Math.round(projMissedReal), Math.round(targetAmount - projMissedReal)),
                new SensitivityRow("Inflation at " + Math.round(highInflationRate * 100) + "%", Math.round(projHighInfReal), Math.round(targetAmount - projHighInfReal))
        );

        return new DeterministicResult(
                Math.round(fvCorpusReal),
                Math.round(fvSipReal),
                Math.round(totalReal),
                Math.round(gapReal),
                gapReal <= 0,
                sensitivity
        );
    }

    // ════════════════════════════════════════════════════════════
    // F16.3 — Required SIP Calculator
    // ════════════════════════════════════════════════════════════
    public RequiredSipResult runRequiredSipCalculator(
            double initialPortfolio,
            double monthlyContribution,
            double monthlyMean,
            int months,
            double targetAmount, // Today's money target
            double annualInflationRate) {

        double monthlyInflation = annualInflationRate / 12;
        // The nominal target at the primary deadline
        double inflationAdjustedTarget = targetAmount * Math.pow(1 + monthlyInflation, months);

        double r = monthlyMean;
        int n = months;

        // SIP needed is a nominal out-of-pocket cash value you pay every month
        double sipNeeded = requiredSip(initialPortfolio, r, n, inflationAdjustedTarget);
        double sipGap = sipNeeded - monthlyContribution;

        // Lump Sum is discounted back to today using portfolio return rate,
        // meaning "how much cash do I need to put in my portfolio TODAY to bridge the gap"
        double fvSipCurrent = monthlyContribution * ((Math.pow(1 + r, n) - 1) / r) * (1 + r);
        double remainingNeedFuture = inflationAdjustedTarget - (initialPortfolio * Math.pow(1 + r, n) + fvSipCurrent);
        double lumpSumToday = Math.max(remainingNeedFuture / Math.pow(1 + r, n), 0);

        return new RequiredSipResult(
                Math.round(sipNeeded),
                Math.round(monthlyContribution),
                Math.round(sipGap),
                Math.round(lumpSumToday),
                extraMonthsNeeded(initialPortfolio, monthlyContribution, r, targetAmount, annualInflationRate, n),
                sipGap <= 0
        );
    }

    // ════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════
    private double requiredSip(double corpus, double r, int n, double targetNominal) {
        double fvCorpus = corpus * Math.pow(1 + r, n);
        double remaining = targetNominal - fvCorpus;
        double annuityFactor = ((Math.pow(1 + r, n) - 1) / r) * (1 + r);
        return remaining / annuityFactor;
    }

    private int extraMonthsNeeded(double corpus, double sip, double r,
                                  double targetAmountToday, double annualInflationRate, int currentMonths) {

        double monthlyInflation = annualInflationRate / 12;

        for (int extra = 1; extra <= 600; extra++) {
            int n = currentMonths + extra;

            // 🔥 CRITICAL FIX: The target keeps growing with inflation for every extra month!
            double movingTargetNominal = targetAmountToday * Math.pow(1 + monthlyInflation, n);

            double fvC = corpus * Math.pow(1 + r, n);
            double fvS = sip * ((Math.pow(1 + r, n) - 1) / r) * (1 + r);

            if (fvC + fvS >= movingTargetNominal) return extra;
        }
        return -1;
    }

    private double percentile(List<Double> sorted, int pct) {
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    // ════════════════════════════════════════════════════════════
    // Records
    // ════════════════════════════════════════════════════════════
    public record MonteCarloResult(
            double pessimistic,
            double likely,
            double optimistic,
            double probability
    ) {}

    public record DeterministicResult(
            long fvCorpus,
            long fvSip,
            long totalProjected,
            long gap,
            boolean onTrack,
            List<SensitivityRow> sensitivity
    ) {}

    public record SensitivityRow(String scenario, long projected, long gap) {}

    public record RequiredSipResult(
            long requiredSip,
            long currentSip,
            long sipGap,
            long lumpSumToday,
            int extraMonths,
            boolean currentSipEnough
    ) {}
}
