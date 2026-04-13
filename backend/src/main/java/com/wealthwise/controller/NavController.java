package com.wealthwise.controller;

import com.wealthwise.service.NavService;
import com.wealthwise.service.SchemeReconciliationService;
import com.wealthwise.repository.SchemeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * On-demand NAV endpoints.
 * NAV is fetched from mfapi.in and cached — only ever called for
 * schemes users actually add to their portfolio.
 */
@RestController
@RequestMapping("/api/nav")
public class NavController {

    @Autowired private NavService navService;
    @Autowired private SchemeReconciliationService reconciliationService;
    @Autowired private SchemeRepository schemeRepo;

    /**
     * Get latest NAV for a scheme (24h Caffeine cache)
     * GET /api/nav/latest/{amfiCode}
     */
    @GetMapping("/latest/{amfiCode}")
    public ResponseEntity<?> latestNav(@PathVariable String amfiCode) {
        try {
            Map<String, Object> nav = navService.getLatestNav(amfiCode);
            return ResponseEntity.ok(nav);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Force-refresh NAV (bypasses cache, fetches fresh from mfapi.in)
     * POST /api/nav/refresh/{amfiCode}
     * Called when user first adds a scheme to a transaction.
     */
    @PostMapping("/refresh/{amfiCode}")
    public ResponseEntity<?> refreshNav(@PathVariable String amfiCode) {
        try {
            Map<String, Object> nav = navService.refreshLatestNav(amfiCode);
            return ResponseEntity.ok(nav);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get NAV for a specific date (uses 7d-cached history from mfapi.in)
     * GET /api/nav/{amfiCode}/date/{dateStr}
     * dateStr format: yyyy-MM-dd or dd-MM-yyyy
     */
    @GetMapping("/{amfiCode}/date/{dateStr}")
    public ResponseEntity<?> navForDate(
            @PathVariable String amfiCode,
            @PathVariable String dateStr) {
        try {
            BigDecimal nav = navService.getNavForDate(amfiCode, dateStr);
            if (nav == null) {
                return ResponseEntity.ok(Map.of(
                    "amfiCode", amfiCode,
                    "date", dateStr,
                    "nav", (Object) null,
                    "message", "NAV not available for this date (possible holiday). Use nearest available NAV."
                ));
            }
            return ResponseEntity.ok(Map.of("amfiCode", amfiCode, "date", dateStr, "nav", nav));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get full historical NAV list for a scheme (7d cache)
     * GET /api/nav/{amfiCode}/history
     */
    @GetMapping("/{amfiCode}/history")
    public ResponseEntity<?> history(@PathVariable String amfiCode) {
        try {
            return ResponseEntity.ok(navService.getHistoricalNavs(amfiCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Preview all synthetic WW_ISIN_ / WW_ scheme codes currently in the DB.
     * GET /api/nav/synthetic-schemes
     * Use this BEFORE running the reconciliation to see what will be fixed.
     */
    @GetMapping("/synthetic-schemes")
    public ResponseEntity<?> listSyntheticSchemes() {
        List<Map<String, String>> synthetics = schemeRepo.findAll().stream()
            .filter(s -> s.getAmfiCode() != null && s.getAmfiCode().startsWith("WW_"))
            .map(s -> Map.of(
                "syntheticCode", s.getAmfiCode(),
                "schemeName",    s.getSchemeName() != null ? s.getSchemeName() : "",
                "isin",          s.getIsinGrowth() != null ? s.getIsinGrowth() : ""
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
            "count",    synthetics.size(),
            "schemes",  synthetics
        ));
    }

    /**
     * Reconcile all synthetic WW_ISIN_ / WW_ codes in the DB to real AMFI codes.
     * POST /api/nav/reconcile-synthetic-schemes
     *
     * This calls mfapi.in for each synthetic scheme, finds the real AMFI code,
     * and bulk-updates transactions + investment_lots + scheme_master atomically.
     * Safe to call multiple times (idempotent).
     */
    @PostMapping("/reconcile-synthetic-schemes")
    public ResponseEntity<?> reconcileSyntheticSchemes() {
        try {
            Map<String, Object> result = reconciliationService.reconcileAll();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
