package com.wealthwise.controller;

import com.wealthwise.model.Transaction;
import com.wealthwise.service.CasPdfParserService;
import com.wealthwise.service.TransactionService;
import com.wealthwise.service.TransactionService.TransactionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired private TransactionService transactionService;
    @Autowired private CasPdfParserService casPdfParserService;

    // ─── Record Transaction ───────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> record(
            HttpServletRequest request,
            @RequestBody TransactionRequest req) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            Transaction txn = transactionService.recordTransaction(req, userId);
            return ResponseEntity.ok(txn);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Bulk SIP Generator ───────────────────────────────────────────────────

    @PostMapping("/bulk-sip")
    public ResponseEntity<?> bulkSip(
            HttpServletRequest request,
            @RequestBody TransactionService.BulkSipRequest req) {
        try {
            // Cap bulk SIP range to 120 months (10 years) to prevent abuse
            if (req.getStartDate() != null && req.getEndDate() != null) {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(
                    req.getStartDate(), req.getEndDate());
                if (months > 120) {
                    return ResponseEntity.badRequest().body(
                        Map.of("error", "Bulk SIP range cannot exceed 120 months (10 years). Requested: " + months + " months."));
                }
            }
            Long userId = (Long) request.getAttribute("userId");
            List<Transaction> txns = transactionService.bulkCreateSip(req, userId);
            return ResponseEntity.ok(Map.of(
                "message", "Successfully generated " + txns.size() + " SIP transactions",
                "transactions", txns));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── CAS Upload ───────────────────────────────────────────────────

    @PostMapping("/upload-cas")
    public ResponseEntity<?> uploadCas(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid PDF file is required"));
            }
            Map<String, Object> result = casPdfParserService.parseCas(file, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── List Transactions ────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            List<Transaction> txns = transactionService.getTransactionsByUser(userId);
            return ResponseEntity.ok(txns);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Get Single Transaction ───────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            HttpServletRequest request,
            @PathVariable Long id) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            Optional<Transaction> txn = transactionService.getById(id, userId);
            return txn.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Reverse Transaction ──────────────────────────────────────────────────

    @PostMapping("/{id}/reverse")
    public ResponseEntity<?> reverse(
            HttpServletRequest request,
            @PathVariable Long id) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            Transaction reversal = transactionService.createReversal(id, userId);
            return ResponseEntity.ok(reversal);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Portfolio Summary ────────────────────────────────────────────────────

    @GetMapping("/portfolio-summary")
    public ResponseEntity<?> portfolioSummary(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            List<Map<String, Object>> summary = transactionService.getPortfolioSummary(userId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── By Scheme ────────────────────────────────────────────────────────────

    @GetMapping("/by-scheme/{amfiCode}")
    public ResponseEntity<?> byScheme(
            HttpServletRequest request,
            @PathVariable String amfiCode) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(transactionService.getByScheme(userId, amfiCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
