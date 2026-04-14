package com.wealthwise.controller;

import com.wealthwise.service.GoalEngineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learn")
public class GoalEngineController {

    @Autowired
    private GoalEngineService goalEngineService;

    // ────────────────────────────────────────────────────────────
    // Combined Analysis Endpoint
    // Returns ALL values normalized to TODAY'S MONEY
    // ────────────────────────────────────────────────────────────
    @PostMapping("/analyse")
    public ResponseEntity<AnalyseResponse> analyse(
            @RequestBody @Valid AnalyseRequest req) {

        GoalEngineService.MonteCarloResult mc = goalEngineService.runMonteCarlo(
                req.initialPortfolio(),
                req.monthlyContribution(),
                req.monthlyMean(),
                req.monthlyStdDev(),
                req.months(),
                req.targetAmount(),
                req.annualInflationRate()
        );

        GoalEngineService.DeterministicResult det = goalEngineService.runDeterministicProjection(
                req.initialPortfolio(),
                req.monthlyContribution(),
                req.monthlyMean(),
                req.months(),
                req.targetAmount(),
                req.annualInflationRate()
        );

        GoalEngineService.RequiredSipResult sip = goalEngineService.runRequiredSipCalculator(
                req.initialPortfolio(),
                req.monthlyContribution(),
                req.monthlyMean(),
                req.months(),
                req.targetAmount(),
                req.annualInflationRate()
        );

        return ResponseEntity.ok(new AnalyseResponse(mc, det, sip));
    }

    public record AnalyseRequest(
            @Positive                                    double initialPortfolio,
            @PositiveOrZero                              double monthlyContribution,
                                                         double monthlyMean,
            @Positive                                    double monthlyStdDev,
            @Min(1) @Max(600)                            int months,
            @Positive                                    double targetAmount,
            @DecimalMin("0.0") @DecimalMax("1.0")        double annualInflationRate
    ) {}

    public record AnalyseResponse(
            GoalEngineService.MonteCarloResult monteCarlo,
            GoalEngineService.DeterministicResult deterministic,
            GoalEngineService.RequiredSipResult requiredSip
    ) {}
}
