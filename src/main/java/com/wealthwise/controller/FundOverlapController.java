package com.wealthwise.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.wealthwise.service.FundOverlapService;

@RestController
@RequestMapping("/api")
public class FundOverlapController {

    private final FundOverlapService service;

    // ✅ Constructor Injection (BEST PRACTICE)
    public FundOverlapController(FundOverlapService service) {
        this.service = service;
    }

    // ✅ 11.2 SINGLE OVERLAP
    @GetMapping("/overlap")
    public Map<String, Object> getOverlap(
            @RequestParam String fundA,
            @RequestParam String fundB
    ) {
        return service.calculateOverlap(fundA, fundB);
    }

    // ✅ 11.2 MATRIX
    @GetMapping("/overlap/matrix")
    public List<Map<String, Object>> getOverlapMatrix() {
        return service.calculateOverlapMatrix();
    }

    // ✅ 11.3 BASIC PORTFOLIO
    @GetMapping("/overlap/portfolio")
    public Map<String, Object> getPortfolioAnalysis() {
        return service.calculatePortfolioAnalysis();
    }

    // ✅ 11.3 ADVANCED PORTFOLIO
    @GetMapping("/overlap/portfolio/advanced")
    public Map<String, Object> getAdvancedPortfolio() {
        return service.analyzePortfolioAdvanced();
    }

    // ✅ 11.4 CONSOLIDATION
    @GetMapping("/overlap/consolidation")
    public Map<String, Object> getConsolidation() {
        return service.getConsolidationSuggestion();
    }
}