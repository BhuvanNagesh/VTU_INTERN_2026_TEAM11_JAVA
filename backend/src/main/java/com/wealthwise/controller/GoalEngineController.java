package com.wealthwise.controller;

import com.wealthwise.service.GoalEngineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learn") // Kept as /api/learn so your React fetch works without changes
@RequiredArgsConstructor
public class GoalEngineController {

    private final GoalEngineService goalEngineService;

    // ────────────────────────────────────────────────────────────
    // Combined Analysis Endpoint
    // Returns ALL values normalized to TODAY'S MONEY
    // ────────────────────────────────────────────────────────────
    @PostMapping("/analyse")
    public ResponseEntity<AnalyseResponse> analyse(
            Authentication auth,
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
            @NotNull @Positive                double initialPortfolio,
            @NotNull @PositiveOrZero          double monthlyContribution,
            @NotNull                          double monthlyMean,
            @NotNull @Positive                double monthlyStdDev,
            @NotNull @Min(1) @Max(600)        int months,
            @NotNull @Positive                double targetAmount,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") double annualInflationRate
    ) {}

    public record AnalyseResponse(
            GoalEngineService.MonteCarloResult monteCarlo,
            GoalEngineService.DeterministicResult deterministic,
            GoalEngineService.RequiredSipResult requiredSip
    ) {}
}