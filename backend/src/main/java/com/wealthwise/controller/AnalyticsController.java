package com.wealthwise.controller;

import com.wealthwise.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired private AnalyticsService analyticsService;

    @GetMapping("/risk")
    public ResponseEntity<?> getRiskProfile(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(analyticsService.getRiskProfile(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sip")
    public ResponseEntity<?> getSipIntelligence(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(analyticsService.getSipIntelligence(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/overlap")
    public ResponseEntity<?> getFundOverlap(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(analyticsService.getFundOverlapMatrix(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/risk-profile")
    public ResponseEntity<?> saveRiskProfile(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            String profile = body.get("riskProfile");
            if (profile == null || profile.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "riskProfile field is required"));
            analyticsService.saveRiskProfile(userId, profile);
            return ResponseEntity.ok(Map.of("message", "Risk profile updated to " + profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
