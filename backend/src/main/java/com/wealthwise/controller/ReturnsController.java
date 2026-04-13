package com.wealthwise.controller;

import com.wealthwise.service.ReturnsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnsController {

    @Autowired private ReturnsService returnsService;

    /**
     * Portfolio-level returns: total invested, current value, XIRR, absolute return, growthTimeline
     * GET /api/returns/portfolio
     */
    @GetMapping("/portfolio")
    public ResponseEntity<?> portfolio(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(returnsService.getPortfolioReturns(userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Per-scheme returns
     * GET /api/returns/scheme/{amfiCode}
     */
    @GetMapping("/scheme/{amfiCode}")
    public ResponseEntity<?> byScheme(
            HttpServletRequest request,
            @PathVariable String amfiCode) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            Map<String, Object> result = returnsService.getSchemeReturns(userId, amfiCode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
