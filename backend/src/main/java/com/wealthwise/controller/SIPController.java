package com.wealthwise.controller;

import com.wealthwise.service.SIPService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the SIP Intelligence Suite (M13).
 *
 * Base path: /api/sip
 *   GET /api/sip/dashboard  — live SIP overview (active SIPs, outflow, streak, projection)
 *   GET /api/sip/compare    — aggregate SIP vs Lumpsum comparison
 *   GET /api/sip/optimize   — personalized best day-of-month recommendation
 *   GET /api/sip/topup      — SIP step-up projection (with/without 10% annual increase)
 */
@RestController
@RequestMapping("/api/sip")
public class SIPController {

    @Autowired private SIPService sipService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(sipService.getDashboard(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/compare")
    public ResponseEntity<?> compare(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(sipService.compare(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/optimize")
    public ResponseEntity<?> optimize(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(sipService.optimize(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/topup")
    public ResponseEntity<?> topup(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(sipService.calculateTopUp(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
