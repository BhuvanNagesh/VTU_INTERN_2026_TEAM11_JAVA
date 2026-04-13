package com.wealthwise.controller;

import com.wealthwise.model.Transaction;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.security.JwtService;
import com.wealthwise.service.CasPdfParserService;
import com.wealthwise.service.TransactionService;
import com.wealthwise.service.TransactionService.TransactionRequest;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    // ─── Record Transaction ───────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> record(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TransactionRequest request) {
        try {
            Long userId = extractUserId(authHeader);
            Transaction txn = transactionService.recordTransaction(request, userId);
            return ResponseEntity.ok(txn);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Bulk SIP Generator ───────────────────────────────────────────────────

    @PostMapping("/bulk-sip")
    public ResponseEntity<?> bulkSip(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TransactionService.BulkSipRequest request) {
        try {
            // Cap bulk SIP range to 120 months (10 years) to prevent abuse
            if (request.getStartDate() != null && request.getEndDate() != null) {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(
                    request.getStartDate(), request.getEndDate());
                if (months > 120) {
                    return ResponseEntity.badRequest().body(
                        Map.of("error", "Bulk SIP range cannot exceed 120 months (10 years). Requested: " + months + " months."));
                }
            }
            Long userId = extractUserId(authHeader);
            List<Transaction> txns = transactionService.bulkCreateSip(request, userId);
            return ResponseEntity.ok(Map.of("message", "Successfully generated " + txns.size() + " SIP transactions", "transactions", txns));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── CAS Upload ───────────────────────────────────────────────────

    @PostMapping("/upload-cas")
    public ResponseEntity<?> uploadCas(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {
        try {
            Long userId = extractUserId(authHeader);
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
    public ResponseEntity<?> list(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = extractUserId(authHeader);
            List<Transaction> txns = transactionService.getTransactionsByUser(userId);
            return ResponseEntity.ok(txns);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Get Single Transaction ───────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(authHeader);
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
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(authHeader);
            Transaction reversal = transactionService.createReversal(id, userId);
            return ResponseEntity.ok(reversal);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Portfolio Summary ────────────────────────────────────────────────────

    @GetMapping("/portfolio-summary")
    public ResponseEntity<?> portfolioSummary(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = extractUserId(authHeader);
            List<Map<String, Object>> summary = transactionService.getPortfolioSummary(userId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── By Scheme ────────────────────────────────────────────────────────────

    @GetMapping("/by-scheme/{amfiCode}")
    public ResponseEntity<?> byScheme(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String amfiCode) {
        try {
            Long userId = extractUserId(authHeader);
            return ResponseEntity.ok(transactionService.getByScheme(userId, amfiCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    /**
     * Extracts userId from Bearer JWT. Throws 401-mapped exception on invalid/expired token.
     * JwtException (invalid signature, expired, malformed) is caught here so it returns
     * HTTP 401 instead of leaking a 500 with internal class names.
     */
    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new SecurityException("Missing or invalid Authorization header");
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            return userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("User not found"))
                .getId();
        } catch (JwtException e) {
            throw new SecurityException("Token invalid or expired — please log in again");
        }
    }
}
