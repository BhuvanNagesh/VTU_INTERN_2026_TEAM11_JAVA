package com.wealthwise.controller;

import com.wealthwise.model.Scheme;
import com.wealthwise.service.SchemeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/schemes")
@CrossOrigin(origins = "*")
public class SchemeController {

    @Autowired
    private SchemeService schemeService;

    /**
     * Live scheme search with optional category + planType filters
     * GET /api/schemes/search?q=axis+bluechip&category=EQUITY&planType=DIRECT&page=0&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String planType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<Scheme> results = schemeService.search(q, category, planType, page, size);
            return ResponseEntity.ok(Map.of(
                "content", results.getContent(),
                "totalElements", results.getTotalElements(),
                "totalPages", results.getTotalPages(),
                "page", results.getNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get scheme by AMFI code
     * GET /api/schemes/{amfiCode}
     */
    @GetMapping("/{amfiCode}")
    public ResponseEntity<?> getByCode(@PathVariable String amfiCode) {
        Optional<Scheme> scheme = schemeService.findByAmfiCode(amfiCode);
        return scheme.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get latest NAV for a scheme
     * GET /api/schemes/nav/{amfiCode}
     */
    @GetMapping("/nav/{amfiCode}")
    public ResponseEntity<?> getNav(@PathVariable String amfiCode) {
        return schemeService.findByAmfiCode(amfiCode)
            .map(s -> ResponseEntity.ok(Map.of(
                "amfiCode", s.getAmfiCode(),
                "schemeName", s.getSchemeName(),
                "nav", s.getLastNav() != null ? s.getLastNav() : "N/A",
                "navDate", s.getLastNavDate() != null ? s.getLastNavDate() : "N/A"
            )))
            .orElse(ResponseEntity.notFound().build());
    }



    /**
     * Count active schemes in DB
     * GET /api/schemes/count
     */
    @GetMapping("/count")
    public ResponseEntity<?> count() {
        return ResponseEntity.ok(Map.of("activeSchemes", schemeService.countActive()));
    }

    /**
     * Trigger a fresh seed from AMFI NAVAll.txt.
     * This updates broadCategory, sebiCategory, and riskLevel for ALL schemes.
     * POST /api/schemes/seed
     */
    @PostMapping("/seed")
    public ResponseEntity<?> seed() {
        try {
            Map<String, Object> result = schemeService.seedFromNavAll();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Seed failed";
            return ResponseEntity.internalServerError().body(Map.of("error", errorMsg));
        }
    }
}
