package com.wealthwise.controller;

import com.wealthwise.service.NavService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * On-demand NAV endpoints.
 * NAV is fetched from mfapi.in and cached — only ever called for
 * schemes users actually add to their portfolio.
 */
@RestController
@RequestMapping("/api/nav")
@CrossOrigin(origins = "*")
public class NavController {

    @Autowired
    private NavService navService;

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
}
