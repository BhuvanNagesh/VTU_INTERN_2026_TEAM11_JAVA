package com.wealthwise.service;

import com.wealthwise.model.InvestmentLot;
import com.wealthwise.model.Scheme;
import com.wealthwise.model.Transaction;
import com.wealthwise.parser.NavAllTxtParser;
import com.wealthwise.repository.InvestmentLotRepository;
import com.wealthwise.repository.SchemeRepository;
import com.wealthwise.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

@Service
public class TransactionService {

    private static final Logger log = Logger.getLogger(TransactionService.class.getName());

    // Stamp duty rate for mutual fund purchases (0.005%)
    private static final BigDecimal STAMP_DUTY_RATE = new BigDecimal("0.00005");

    @Autowired private TransactionRepository transactionRepo;
    @Autowired private InvestmentLotRepository lotRepo;
    @Autowired private SchemeRepository schemeRepo;
    @Autowired private NavService navService;

    // ─── Record Transaction ───────────────────────────────────────────────────

    @Transactional
    public Transaction recordTransaction(TransactionRequest req, Long userId) {
        // 1. Ensure scheme exists (creates on-demand from mfapi.in if not seeded)
        Scheme scheme = navService.ensureSchemeExists(req.getSchemeAmfiCode());

        // 2. Resolve NAV — try on-demand cache first (mfapi.in), then scheme DB
        BigDecimal nav = req.getNav();
        if (nav == null || nav.compareTo(BigDecimal.ZERO) <= 0) {
            // Try to get NAV for the specific transaction date from cache
            if (req.getTransactionDate() != null) {
                nav = navService.getNavForDate(req.getSchemeAmfiCode(), req.getTransactionDate().toString());
            }
            // Fall back to latest NAV from cache
            if (nav == null || nav.compareTo(BigDecimal.ZERO) <= 0) {
                Object latestNav = navService.getLatestNav(req.getSchemeAmfiCode()).get("nav");
                if (latestNav != null) {
                    try { nav = new BigDecimal(latestNav.toString()); } catch (Exception ignored) {}
                }
            }
        }
        if (nav == null || nav.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("NAV not available for scheme " + req.getSchemeAmfiCode()
                + " on " + req.getTransactionDate() + ". Please enter NAV manually.");
        }

        // 3. Calculate units if not provided
        BigDecimal units = req.getUnits();
        BigDecimal amount = req.getAmount();

        boolean isPurchase = isPurchaseType(req.getTransactionType());
        boolean isRedemption = isRedemptionType(req.getTransactionType());

        if (isPurchase) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Amount is required for purchase transactions");
            }
            if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
                // Calculate units from amount and NAV, applying stamp duty
                BigDecimal stampDutyAmount = amount.multiply(STAMP_DUTY_RATE).setScale(4, RoundingMode.HALF_UP);
                BigDecimal effectiveAmount = amount.subtract(stampDutyAmount);
                units = effectiveAmount.divide(nav, 6, RoundingMode.HALF_UP);
                req.setStampDuty(stampDutyAmount);
            }
        } else if (isRedemption) {
            if (units == null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                units = amount.divide(nav, 6, RoundingMode.HALF_UP);
            } else if (units != null && amount == null) {
                amount = units.multiply(nav).setScale(4, RoundingMode.HALF_UP);
                req.setAmount(amount);
            }
            validateRedemption(userId, req.getSchemeAmfiCode(), req.getFolioNumber(), units);
        }

        // 4. Build folio number
        String folio = req.getFolioNumber();
        if (folio == null || folio.isBlank()) {
            folio = autoFolio(userId, req.getSchemeAmfiCode());
        }

        // 5. Create transaction
        Transaction txn = new Transaction();
        txn.setTransactionRef(generateRef());
        txn.setUserId(userId);
        txn.setSchemeAmfiCode(req.getSchemeAmfiCode());
        txn.setSchemeName(scheme.getSchemeName());
        txn.setFolioNumber(folio);
        txn.setTransactionType(req.getTransactionType());
        txn.setTransactionDate(req.getTransactionDate());
        txn.setAmount(amount);
        txn.setUnits(units);
        txn.setNav(nav);
        txn.setStampDuty(req.getStampDuty());
        txn.setSource("MANUAL");
        txn.setNotes(req.getNotes());
        String category = scheme.getBroadCategory() != null ? scheme.getBroadCategory() : scheme.getSebiCategory();
        Integer risk = scheme.getRiskLevel();

        boolean badCategory = category == null || category.trim().isEmpty()
            || "UNKNOWN".equalsIgnoreCase(category)
            || "OTHER".equalsIgnoreCase(category);

        if (badCategory || risk == null) {
            String[] derived = CasPdfParserService.deriveCategory(scheme.getSchemeName());
            if (badCategory) {
                // Use derived broad category if it's more specific than OTHER
                String derivedBroad = derived[0];
                if (!"OTHER".equalsIgnoreCase(derivedBroad)) {
                    category = derivedBroad;
                } else if (category == null || category.trim().isEmpty()) {
                    category = derived[1]; // fall back to sebi sub-category label
                }
                // else leave the existing non-null/non-blank category as-is
            }
            if (risk == null) {
                risk = NavAllTxtParser.assignRiskLevel(derived[1], derived[0]);
            }
        }

        txn.setCategory(category);
        txn.setRisk(risk);

        Transaction saved = transactionRepo.save(txn);

        // 6. Create/update investment lots for FIFO tracking
        if (isPurchase) {
            createLot(saved, scheme, folio, units, nav, amount);
        } else if (isRedemption) {
            consumeLotsFifo(userId, req.getSchemeAmfiCode(), folio, units);
        }

        return saved;
    }

    // ─── Bulk SIP Auto-Generator ─────────────────────────────────────────────

    @Transactional
    public List<Transaction> bulkCreateSip(BulkSipRequest req, Long userId) {
        List<Transaction> created = new ArrayList<>();
        Scheme scheme = navService.ensureSchemeExists(req.getSchemeAmfiCode());
        
        String folio = req.getFolioNumber();
        if (folio == null || folio.isBlank()) {
            folio = autoFolio(userId, req.getSchemeAmfiCode());
        }

        LocalDate current = req.getStartDate();
        LocalDate limit = req.getEndDate() != null ? req.getEndDate() : LocalDate.now();
        BigDecimal currentAmount = req.getAmount();

        // Ensure NAV history is cached securely before looping
        navService.getHistoricalNavs(req.getSchemeAmfiCode());

        while (!current.isAfter(limit)) {
            // Find proper NAV safely
            BigDecimal nav = navService.getNavForDate(req.getSchemeAmfiCode(), current.toString());
            
            if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0) {
                // Calculate units with stamp duty deduction
                BigDecimal stampDutyAmount = currentAmount.multiply(STAMP_DUTY_RATE).setScale(4, RoundingMode.HALF_UP);
                BigDecimal effectiveAmount = currentAmount.subtract(stampDutyAmount);
                BigDecimal units = effectiveAmount.divide(nav, 6, RoundingMode.HALF_UP);

                Transaction txn = new Transaction();
                txn.setTransactionRef(generateRef());
                txn.setUserId(userId);
                txn.setSchemeAmfiCode(req.getSchemeAmfiCode());
                txn.setSchemeName(scheme.getSchemeName());
                txn.setFolioNumber(folio);
                txn.setTransactionType("PURCHASE_SIP");
                txn.setTransactionDate(current);
                txn.setAmount(currentAmount);
                txn.setUnits(units);
                txn.setNav(nav);
                txn.setStampDuty(stampDutyAmount);
                txn.setSource("MANUAL_SIP_BULK");
                txn.setNotes("Auto-generated SIP transaction");
                String bCategory = scheme.getBroadCategory() != null ? scheme.getBroadCategory() : scheme.getSebiCategory();
                Integer bRisk = scheme.getRiskLevel();

                if (bCategory == null || bCategory.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(bCategory) || bRisk == null) {
                    String[] derived = CasPdfParserService.deriveCategory(scheme.getSchemeName());
                    if (bCategory == null || bCategory.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(bCategory)) {
                        bCategory = (derived[0] != null && !derived[0].equals("OTHER")) ? derived[0] : derived[1];
                    }
                    if (bRisk == null) {
                        bRisk = NavAllTxtParser.assignRiskLevel(derived[1], derived[0]);
                    }
                }

                txn.setCategory(bCategory);
                txn.setRisk(bRisk);

                Transaction saved = transactionRepo.save(txn);
                createLot(saved, scheme, folio, units, nav, currentAmount);
                created.add(saved);
            } else {
                log.warning("[SIP GENERATOR] Skipping " + current + " for " + req.getSchemeAmfiCode() + " - NAV missing.");
            }

            // Step forward exactly 1 month
            current = current.plusMonths(1);
            
            // Handle Annual Step-Up
            if (req.getAnnualStepUpPct() != null && current.getMonthValue() == req.getStartDate().getMonthValue()) {
                BigDecimal multiplier = BigDecimal.ONE.add(req.getAnnualStepUpPct().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                currentAmount = currentAmount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            }
        }
        
        return created;
    }

    // ─── Reversal ────────────────────────────────────────────────────────────

    @Transactional
    public Transaction createReversal(Long originalTxnId, Long userId) {
        Transaction original = transactionRepo.findById(originalTxnId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + originalTxnId));

        if (!original.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        Transaction reversal = new Transaction();
        reversal.setTransactionRef(generateRef());
        reversal.setUserId(userId);
        reversal.setSchemeAmfiCode(original.getSchemeAmfiCode());
        reversal.setSchemeName(original.getSchemeName());
        reversal.setFolioNumber(original.getFolioNumber());
        reversal.setTransactionType("REVERSAL");
        reversal.setTransactionDate(LocalDate.now());
        reversal.setAmount(original.getAmount() != null ? original.getAmount().negate() : null);
        reversal.setUnits(original.getUnits() != null ? original.getUnits().negate() : null);
        reversal.setNav(original.getNav());
        reversal.setReversalOf(originalTxnId);
        reversal.setSource("MANUAL");
        reversal.setNotes("Reversal of " + original.getTransactionRef());

        return transactionRepo.save(reversal);
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public List<Transaction> getTransactionsByUser(Long userId) {
        return transactionRepo.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId);
    }

    public Optional<Transaction> getById(Long id, Long userId) {
        Optional<Transaction> txn = transactionRepo.findById(id);
        return txn.filter(t -> t.getUserId().equals(userId));
    }

    public List<Transaction> getByScheme(Long userId, String amfiCode) {
        return transactionRepo.findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode);
    }

    /**
     * Returns aggregated portfolio summary: scheme → {invested, units, currentValue, ...}
     */
    public List<Map<String, Object>> getPortfolioSummary(Long userId) {
        List<InvestmentLot> lots = lotRepo.findByUserIdOrderByPurchaseDateAsc(userId);
        Map<String, Map<String, Object>> byScheme = new LinkedHashMap<>();

        for (InvestmentLot lot : lots) {
            String key = lot.getSchemeAmfiCode() + "|" + lot.getFolioNumber();
            Map<String, Object> entry = byScheme.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("schemeAmfiCode", lot.getSchemeAmfiCode());
                m.put("schemeName", lot.getSchemeName());
                m.put("folioNumber", lot.getFolioNumber());
                m.put("investedAmount", BigDecimal.ZERO);
                m.put("units", BigDecimal.ZERO);
                m.put("currentNav", BigDecimal.ZERO);
                m.put("currentValue", BigDecimal.ZERO);
                return m;
            });

            BigDecimal remaining = lot.getUnitsRemaining();
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                entry.put("investedAmount",
                    ((BigDecimal) entry.get("investedAmount"))
                        .add(lot.getPurchaseAmount().multiply(remaining)
                            .divide(lot.getUnitsOriginal(), 4, RoundingMode.HALF_UP)));
                entry.put("units", ((BigDecimal) entry.get("units")).add(remaining));
            }
        }

        // Enrich with current NAV
        List<Map<String, Object>> result = new ArrayList<>(byScheme.values());
        for (Map<String, Object> entry : result) {
            String code = (String) entry.get("schemeAmfiCode");
            schemeRepo.findByAmfiCode(code).ifPresent(s -> {
                BigDecimal latestNav = s.getLastNav();
                if (latestNav != null) {
                    entry.put("currentNav", latestNav);
                    BigDecimal units = (BigDecimal) entry.get("units");
                    entry.put("currentValue", units.multiply(latestNav).setScale(4, RoundingMode.HALF_UP));
                    entry.put("lastNavDate", s.getLastNavDate());
                }
                entry.put("broadCategory", s.getBroadCategory());
                entry.put("planType", s.getPlanType());
            });

            BigDecimal invested = (BigDecimal) entry.get("investedAmount");
            BigDecimal current = (BigDecimal) entry.get("currentValue");
            if (invested.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal absReturn = current.subtract(invested)
                    .divide(invested, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
                entry.put("absoluteReturnPct", absReturn);
                entry.put("gainLoss", current.subtract(invested).setScale(4, RoundingMode.HALF_UP));
            }
        }

        return result;
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private boolean isPurchaseType(String type) {
        return type != null && (type.equals("PURCHASE_LUMPSUM") || type.equals("PURCHASE_SIP")
            || type.equals("SWITCH_IN") || type.equals("STP_IN") || type.equals("DIVIDEND_REINVEST"));
    }

    private boolean isRedemptionType(String type) {
        return type != null && (type.equals("REDEMPTION") || type.equals("SWITCH_OUT")
            || type.equals("STP_OUT") || type.equals("SWP"));
    }

    private void validateRedemption(Long userId, String amfiCode, String folio, BigDecimal units) {
        if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal available = BigDecimal.ZERO;
        List<InvestmentLot> lots = folio != null
            ? lotRepo.findByUserIdAndSchemeAmfiCodeAndFolioNumberOrderByPurchaseDateAsc(userId, amfiCode, folio)
            : lotRepo.findByUserIdAndSchemeAmfiCodeOrderByPurchaseDateAsc(userId, amfiCode);
        for (InvestmentLot lot : lots) available = available.add(lot.getUnitsRemaining());
        if (units.compareTo(available) > 0) {
            throw new RuntimeException("Insufficient units. Available: " + available.toPlainString()
                + ", Requested: " + units.toPlainString());
        }
    }

    private void createLot(Transaction txn, Scheme scheme, String folio,
                           BigDecimal units, BigDecimal nav, BigDecimal amount) {
        InvestmentLot lot = new InvestmentLot();
        lot.setTransactionId(txn.getId());
        lot.setUserId(txn.getUserId());
        lot.setSchemeAmfiCode(txn.getSchemeAmfiCode());
        lot.setSchemeName(scheme.getSchemeName());
        lot.setFolioNumber(folio);
        lot.setPurchaseDate(txn.getTransactionDate());
        lot.setPurchaseNav(nav);
        lot.setPurchaseAmount(amount != null ? amount : BigDecimal.ZERO);
        lot.setUnitsOriginal(units);
        lot.setUnitsRemaining(units);

        // Check if ELSS (3-year lock-in)
        boolean isElss = scheme.getSchemeName() != null
            && scheme.getSchemeName().toUpperCase().contains("ELSS");
        lot.setIsElss(isElss);
        if (isElss) lot.setElssLockUntil(txn.getTransactionDate().plusYears(3));

        lotRepo.save(lot);
    }

    private void consumeLotsFifo(Long userId, String amfiCode, String folio, BigDecimal unitsToConsume) {
        List<InvestmentLot> lots = folio != null
            ? lotRepo.findByUserIdAndSchemeAmfiCodeAndFolioNumberOrderByPurchaseDateAsc(userId, amfiCode, folio)
            : lotRepo.findByUserIdAndSchemeAmfiCodeOrderByPurchaseDateAsc(userId, amfiCode);

        BigDecimal remaining = unitsToConsume;
        for (InvestmentLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = lot.getUnitsRemaining();
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal consume = remaining.min(available);
            lot.setUnitsRemaining(available.subtract(consume));
            lotRepo.save(lot);
            remaining = remaining.subtract(consume);
        }
    }

    private String autoFolio(Long userId, String amfiCode) {
        // Reuse existing folio for same user+scheme, else generate new
        return transactionRepo
            .findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode)
            .stream().map(Transaction::getFolioNumber)
            .filter(f -> f != null && !f.isBlank())
            .findFirst()
            .orElse("WW" + userId + amfiCode);
    }

    /**
     * Generates a unique transaction reference using UUID to prevent collisions
     * under concurrent requests (Math.random() + timestamp is not collision-safe).
     */
    private String generateRef() {
        return "WW" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    // ─── Inner DTO ───────────────────────────────────────────────────────────

    public static class TransactionRequest {
        private String schemeAmfiCode;
        private String folioNumber;
        private String transactionType;
        private LocalDate transactionDate;
        private BigDecimal amount;
        private BigDecimal units;
        private BigDecimal nav;
        private BigDecimal stampDuty;
        private String notes;

        public String getSchemeAmfiCode() { return schemeAmfiCode; }
        public void setSchemeAmfiCode(String s) { this.schemeAmfiCode = s; }
        public String getFolioNumber() { return folioNumber; }
        public void setFolioNumber(String s) { this.folioNumber = s; }
        public String getTransactionType() { return transactionType; }
        public void setTransactionType(String s) { this.transactionType = s; }
        public LocalDate getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDate d) { this.transactionDate = d; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal a) { this.amount = a; }
        public BigDecimal getUnits() { return units; }
        public void setUnits(BigDecimal u) { this.units = u; }
        public BigDecimal getNav() { return nav; }
        public void setNav(BigDecimal n) { this.nav = n; }
        public BigDecimal getStampDuty() { return stampDuty; }
        public void setStampDuty(BigDecimal s) { this.stampDuty = s; }
        public String getNotes() { return notes; }
        public void setNotes(String n) { this.notes = n; }
    }

    public static class BulkSipRequest {
        private String schemeAmfiCode;
        private String folioNumber;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal amount;
        private BigDecimal annualStepUpPct;

        public String getSchemeAmfiCode() { return schemeAmfiCode; }
        public void setSchemeAmfiCode(String s) { this.schemeAmfiCode = s; }
        public String getFolioNumber() { return folioNumber; }
        public void setFolioNumber(String s) { this.folioNumber = s; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate d) { this.startDate = d; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate d) { this.endDate = d; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal a) { this.amount = a; }
        public BigDecimal getAnnualStepUpPct() { return annualStepUpPct; }
        public void setAnnualStepUpPct(BigDecimal a) { this.annualStepUpPct = a; }
    }
}
