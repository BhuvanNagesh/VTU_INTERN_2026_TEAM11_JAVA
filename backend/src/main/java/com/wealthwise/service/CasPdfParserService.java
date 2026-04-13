package com.wealthwise.service;

import com.wealthwise.model.*;
import com.wealthwise.parser.NavAllTxtParser;
import com.wealthwise.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.*;

@Service
public class CasPdfParserService {

    private static final Logger log = LoggerFactory.getLogger(CasPdfParserService.class);

    @Autowired private SchemeRepository schemeRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private CasUploadLogRepository logRepo;
    @Autowired private InvestmentLotRepository lotRepo;
    @Autowired private NavService navService;

    // ── Regex Patterns ──────────────────────────────────────────────────────

    // Handles both formats:
    //   "Folio No: 1234567/89 PAN: ..."      (space-separated, original)
    //   "Folio No: 2345678/90 | PAN: ..."    (pipe-separated, CAS_2nd / Priya format)
    private static final Pattern FOLIO_PATTERN = Pattern.compile(
            "Folio\\s*(?:No|Number)[:.\\s]+([\\w/\\-\\s]+?)(?:\\s*[|]\\s*PAN|\\s+PAN|\\s+KYC|\\n|$)",
            Pattern.CASE_INSENSITIVE);

    // Handles:  "ISIN: INF179K01VQ8 AMC: ..."     (no pipe)
    //           "ISIN: INF769K01DM5 | AMC: ..."   (pipe-separated)
    private static final Pattern ISIN_PATTERN = Pattern.compile(
            "ISIN\\s*[:\\-]?\\s*([A-Z]{2}[A-Z0-9]{10})",
            Pattern.CASE_INSENSITIVE);

    // Handles both no-pipe and pipe-separated AMC values
    private static final Pattern AMC_PATTERN = Pattern.compile(
            "AMC\\s*[:\\-]?\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CLOSING_UNITS_PATTERN = Pattern.compile(
            "(?:Closing|Available)\\s+(?:[/]?\\s*Available\\s+)?(?:Unit\\s+)?Balance\\s*[:\\-|]?\\s*([\\d,]+\\.\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_LINE = Pattern.compile(
            "^(\\d{2}-[A-Za-z]{3}-\\d{4})$");

    private static final Pattern AMOUNT_LINE = Pattern.compile(
            "^([\\d,]+\\.\\d{2})$");

    private static final Pattern DECIMAL_LINE = Pattern.compile(
            "^([\\d,]+\\.\\d+)$");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);


    static class FolioBlock {
        String folioNumber;
        String isin;
        String schemeName;
        String pdfSchemeName;
        String amcName;
        String amfiCode;
        String broadCategory;
        String sebiCategory;
        Integer riskLevel;
        BigDecimal closingUnits = BigDecimal.ZERO;
        List<TxRow> transactions = new ArrayList<>();
    }

    static class TxRow {
        LocalDate date;
        String    description;
        BigDecimal amount       = BigDecimal.ZERO;
        BigDecimal units        = BigDecimal.ZERO;
        BigDecimal nav          = BigDecimal.ZERO;
        BigDecimal balanceUnits = BigDecimal.ZERO;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public Map<String, Object> parseCas(MultipartFile file, Long userId) throws IOException {
        log.info("[CAS] ===== CAS PDF Upload Started =====");
        log.info("[CAS] File     : {}", file.getOriginalFilename());
        log.info("[CAS] Size     : {} KB", file.getSize() / 1024);
        log.info("[CAS] UserId   : {}", userId);

        CasUploadLog uploadLog = new CasUploadLog();
        uploadLog.setUserId(userId);
        uploadLog.setFileName(file.getOriginalFilename());
        uploadLog.setStatus(CasUploadLog.Status.PROCESSING);
        uploadLog = logRepo.save(uploadLog);
        log.info("[CAS] Upload log created with ID: {}", uploadLog.getId());

        try {
            return processCasTransactionally(file, userId, uploadLog);
        } catch (Exception e) {
            log.error("[CAS] FAILED — {}: {}", e.getClass().getSimpleName(), e.getMessage());
            uploadLog.setStatus(CasUploadLog.Status.FAILED);
            uploadLog.setErrorMessage(e.getMessage());
            logRepo.save(uploadLog);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> processCasTransactionally(
            MultipartFile file, Long userId, CasUploadLog uploadLog) throws IOException {

        log.info("[CAS] Step 1/4 — Extracting text from PDF...");
        byte[] pdfBytes = file.getBytes();
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            log.info("[CAS] PDF pages    : {}", doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(doc);
            log.info("[CAS] Extracted    : {} characters of text", fullText.length());
            // DEBUG: log first 3000 chars so we can see exactly what PDFBox produced
            if (log.isDebugEnabled() || fullText.length() < 5000) {
                log.info("[CAS] === EXTRACTED TEXT PREVIEW (first 3000 chars) ===\n{}",
                    fullText.substring(0, Math.min(3000, fullText.length())));
            }

            log.info("[CAS] Step 2/4 — Splitting into folio blocks...");
            List<FolioBlock> folios = splitIntoFolios(fullText);
            log.info("[CAS] Folios found : {}", folios.size());

            // Log each folio summary before saving
            for (int i = 0; i < folios.size(); i++) {
                FolioBlock fb = folios.get(i);
                log.info("[CAS]   Folio [{}/{}] #{} | Scheme: {} | AMFI: {} | Txns: {}",
                    i + 1, folios.size(),
                    fb.folioNumber,
                    fb.schemeName != null ? fb.schemeName : "(unmatched — " + fb.pdfSchemeName + ")",
                    fb.amfiCode,
                    fb.transactions.size());
            }

            log.info("[CAS] Step 3/4 — Saving transactions to database...");
            int totalTx = 0;
            for (FolioBlock folio : folios) {
                int saved = saveTransactions(folio, userId);
                log.info("[CAS]   Folio #{} → {} transactions saved", folio.folioNumber, saved);
                totalTx += saved;
            }

            log.info("[CAS] Step 4/4 — Finalising upload log...");
            uploadLog.setStatus(CasUploadLog.Status.SUCCESS);
            uploadLog.setTotalFolios(folios.size());
            uploadLog.setTotalTransactions(totalTx);
            logRepo.save(uploadLog);

            log.info("[CAS] ===== Import COMPLETE =====");
            log.info("[CAS] Total folios      : {}", folios.size());
            log.info("[CAS] Total transactions: {}", totalTx);
            log.info("[CAS] Upload log ID     : {}", uploadLog.getId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status",            "SUCCESS");
            result.put("totalFolios",       folios.size());
            result.put("totalTransactions", totalTx);
            result.put("uploadId",          uploadLog.getId());
            return result;
        }
    }

    // ── Private Helpers ─────────────────────────────────────────────────────

    private List<FolioBlock> splitIntoFolios(String fullText) {
        List<FolioBlock> result = new ArrayList<>();
        String[] lines = fullText.split("\\r?\\n");

        FolioBlock current = null;
        StringBuilder blockBuffer = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Only stop at "Portfolio Summary" if we already found at least one folio.
            // The new PDF format places a "Portfolio Summary" HEADING before the folio
            // blocks (as a section label), so we must not stop on first occurrence.
            if (trimmed.equals("Portfolio Summary") && current != null) {
                break;
            }

            Matcher folioMatcher = FOLIO_PATTERN.matcher(trimmed);
            if (folioMatcher.find()) {
                if (current != null && blockBuffer != null) {
                    finishFolioBlock(current, blockBuffer.toString());
                    result.add(current);
                }
                current = new FolioBlock();
                current.folioNumber = folioMatcher.group(1).trim();
                blockBuffer = new StringBuilder();
            }

            if (blockBuffer != null) {
                blockBuffer.append(line).append("\n");
            }
        }

        if (current != null && blockBuffer != null) {
            finishFolioBlock(current, blockBuffer.toString());
            result.add(current);
        }

        return result;
    }

    private void finishFolioBlock(FolioBlock folio, String blockText) {
        String[] lines = blockText.split("\\r?\\n");

        for (String line : lines) {
            String t = line.trim();
            // Extract ISIN
            Matcher m = ISIN_PATTERN.matcher(t);
            if (m.find()) folio.isin = m.group(1).trim();

            // Extract AMC — handle both:
            //   "ISIN: INF... AMC: Name"      (same line, no pipe)
            //   "ISIN: INF... | AMC: Name"    (same line, pipe)
            //   "AMC: Name"                   (own line)
            // Strip any pipe before checking AMC
            String normalized = t.replace("|", " ");
            Matcher a = AMC_PATTERN.matcher(normalized);
            if (a.find()) {
                String amcVal = a.group(1).trim();
                // If AMC appears after ISIN on the same line, drop the ISIN part
                if (folio.amcName == null || folio.amcName.isEmpty()) {
                    folio.amcName = amcVal;
                }
            }
        }

        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 15
                && !t.startsWith("Folio") && !t.startsWith("ISIN")
                && !t.startsWith("Date")  && !t.startsWith("Closing")
                && !t.startsWith("Transaction") && !t.startsWith("Amount")
                && !t.startsWith("Units") && !t.startsWith("NAV")
                && !t.startsWith("Balance")
                && !DATE_LINE.matcher(t).matches()
                && !AMOUNT_LINE.matcher(t).matches()
                && t.matches(".*(?i)(fund|plan|growth|idcw|option).*")) {
                folio.pdfSchemeName = t;
                break;
            }
        }

        // Tier 1: ISIN
        Scheme matched = null;
        if (folio.isin != null) {
            matched = schemeRepo.findByIsinGrowth(folio.isin)
                .or(() -> schemeRepo.findByIsinIdcw(folio.isin))
                .orElse(null);
        }

        // Tier 2: Keyword match — use FIRST meaningful words (AMC + fund type)
        // e.g. "Axis Midcap Fund - Direct Plan" → search "Axis Midcap"
        if (matched == null && folio.pdfSchemeName != null) {
            String keyword = extractSearchKeyword(folio.pdfSchemeName);
            java.util.List<Scheme> candidates =
                schemeRepo.findBySchemeNameContainingIgnoreCaseAndIsActiveTrue(keyword);
            if (!candidates.isEmpty()) {
                matched = candidates.stream()
                    .filter(s -> "DIRECT".equals(s.getPlanType()))
                    .findFirst()
                    .orElse(candidates.get(0));
                if (matched != null)
                    log.info("[CAS] Tier-2 matched '{}' via keyword '{}' -> AMFI {}",
                        folio.pdfSchemeName, keyword, matched.getAmfiCode());
            }
        }

        // Tier 3: mfapi.in scheme search by name — resolves ISIN to real AMFI code
        // Only fires when DB has no ISIN and no keyword match.
        if (matched == null && folio.pdfSchemeName != null) {
            matched = resolveAmfiCodeFromMfApi(folio.pdfSchemeName, folio.isin);
        }

        if (matched != null) {
            folio.schemeName    = matched.getSchemeName();
            folio.amfiCode      = matched.getAmfiCode();
            folio.broadCategory = matched.getBroadCategory();
            folio.sebiCategory  = matched.getSebiCategory();
            folio.riskLevel     = matched.getRiskLevel();
        } else {
            folio.schemeName = folio.pdfSchemeName;
            // Use MVP-grade heuristic — derives broad+sebi from scheme name keywords
            String[] derived = deriveCategory(folio.pdfSchemeName);
            folio.broadCategory = derived[0];
            folio.sebiCategory  = derived[1];
            // Use the full NavAllTxtParser 36-category risk table (superior to old method)
            folio.riskLevel = NavAllTxtParser.assignRiskLevel(folio.sebiCategory, folio.broadCategory);
            
            // Generate deterministic synthetic amfiCode so transactions are NEVER dropped
            // Use ISIN prefix if available, otherwise hash the scheme name
            folio.amfiCode = folio.isin != null 
                ? "WW_ISIN_" + folio.isin 
                : "WW_" + Math.abs((folio.pdfSchemeName != null ? folio.pdfSchemeName : "").hashCode());

            // Pre-save synthetic scheme in scheme_master so ISIN resolution works next time
            Optional<Scheme> existingSynthetic = schemeRepo.findByAmfiCode(folio.amfiCode);
            if (existingSynthetic.isEmpty()) {
                Scheme synthetic = new Scheme();
                synthetic.setAmfiCode(folio.amfiCode);
                synthetic.setSchemeName(folio.schemeName != null ? folio.schemeName : "Unknown Scheme");
                synthetic.setIsinGrowth(folio.isin);
                synthetic.setAmcName(folio.amcName);
                synthetic.setBroadCategory(folio.broadCategory);
                synthetic.setSebiCategory(folio.sebiCategory);
                synthetic.setRiskLevel(folio.riskLevel);
                synthetic.setIsActive(true);
                try {
                    schemeRepo.save(synthetic);
                } catch (Exception e) {
                    log.warn("[CAS] Could not pre-save synthetic scheme {}: {}", folio.amfiCode, e.getMessage());
                }
            } else {
                // If it already exists (e.g. re-upload), still apply latest derived data
                Scheme existing = existingSynthetic.get();
                if (existing.getBroadCategory() == null) existing.setBroadCategory(folio.broadCategory);
                if (existing.getSebiCategory() == null)  existing.setSebiCategory(folio.sebiCategory);
                if (existing.getRiskLevel() == null)      existing.setRiskLevel(folio.riskLevel);
                try { schemeRepo.save(existing); } catch (Exception ignored) {}
            }
        }

        Matcher closingMatcher = CLOSING_UNITS_PATTERN.matcher(blockText);
        if (closingMatcher.find()) {
            folio.closingUnits = parseBigDecimal(closingMatcher.group(1));
        }

        parseMultiLineTransactions(folio, lines);
    }

    private void parseMultiLineTransactions(FolioBlock folio, String[] lines) {
        // Strategy 1: true single-line format (all 6 tokens on one line)
        // Amount may be in parentheses for redemptions: (10,000.00)
        Pattern singleLineTx = Pattern.compile(
            "(\\d{2}-[A-Za-z]{3}-\\d{4})\\s+(.{4,60}?)\\s+(\\(?[\\d,]+\\.\\d{2}\\)?)\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+([\\d.,]+)");

        boolean foundSingleLine = false;
        for (String line : lines) {
            Matcher m = singleLineTx.matcher(line.trim());
            if (m.matches()) {
                foundSingleLine = true;
                try {
                    TxRow row = new TxRow();
                    row.date         = LocalDate.parse(m.group(1), DATE_FMT);
                    row.description  = m.group(2).trim();
                    // Strip parentheses from negative amounts e.g. (10,000.00) → 10,000.00
                    String rawAmount = m.group(3).replace("(", "").replace(")", "");
                    row.amount       = parseBigDecimal(rawAmount);
                    row.units        = parseBigDecimal(m.group(4));
                    row.nav          = parseBigDecimal(m.group(5));
                    row.balanceUnits = parseBigDecimal(m.group(6));
                    folio.transactions.add(row);
                } catch (Exception e) {}
            }
        }
        if (foundSingleLine) return;

        // ── Strategy 2: columnar multi-line (PDFBox column-scrambled output) ──
        // PDFBox sometimes dumps all dates first, then all descriptions, then
        // all amounts in separate blocks.  We collect dated rows with their
        // descriptions, then separately collect the number columns, and zip.
        parseColumnarTransactions(folio, lines);
    }

    /**
     * Handles PDFs where PDFBox renders the transaction table as:
     *   date1          date2          date3     ...
     *   description1   description2   ...
     *   amount1        amount2        ...
     *   units1         units2         ...
     *   nav1           nav2           ...
     *   balance1       balance2       ...
     *
     * OR the classic interleaved multi-line:
     *   date1
     *   description1
     *   amount1  units1  nav1  balance1
     *   date2
     *   ...
     */
    private void parseColumnarTransactions(FolioBlock folio, String[] lines) {
        // Collect ordered dates + matching descriptions from the lines
        List<LocalDate>  dates        = new ArrayList<>();
        List<String>     descriptions = new ArrayList<>();
        List<BigDecimal> amounts      = new ArrayList<>();
        List<BigDecimal> unitsList    = new ArrayList<>();
        List<BigDecimal> navList      = new ArrayList<>();
        List<BigDecimal> balanceList  = new ArrayList<>();

        // Pass 1: collect dates + descriptions (in order)
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (!DATE_LINE.matcher(t).matches()) continue;
            try {
                LocalDate d = LocalDate.parse(t, DATE_FMT);
                // next non-empty line that is NOT a date/number is the description
                String desc = "Unknown";
                for (int j = i + 1; j < lines.length && j <= i + 3; j++) {
                    String s = lines[j].trim();
                    if (s.isEmpty()) continue;
                    if (DATE_LINE.matcher(s).matches()) break;
                    // If the very next non-empty line is a number it means description
                    // column didn't land here — use generic label
                    String cleaned = s.replace(",", "");
                    try { new BigDecimal(cleaned); break; } catch (NumberFormatException ignore) {}
                    desc = s;
                    break;
                }
                dates.add(d);
                descriptions.add(desc);
            } catch (Exception ignored) {}
        }

        if (dates.isEmpty()) return;

        // Pass 2: collect ALL decimal numbers that appear in this folio block
        // (excluding balance-units column header lines and section markers)
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            // Skip known header/label lines
            if (t.equalsIgnoreCase("Date") || t.equalsIgnoreCase("Transaction")
                    || t.equalsIgnoreCase("Amount (Rs.)")
                    || t.equalsIgnoreCase("Units") || t.equalsIgnoreCase("NAV (Rs.)")
                    || t.equalsIgnoreCase("Balance Units") || t.equalsIgnoreCase("Balance")
                    || t.startsWith("Closing Balance") || t.startsWith("Portfolio")
                    || t.startsWith("Folio") || t.startsWith("ISIN") || t.startsWith("AMC")
                    || DATE_LINE.matcher(t).matches()) continue;

            // Is this line a description (non-numeric)?
            String cleaned = t.replace(",", "");
            try {
                BigDecimal val = new BigDecimal(cleaned);
                // Classify into amount / units / nav / balance buckets
                // We store them in a flat list and redistribute by groups of 4
                if (amounts.size() <= unitsList.size()) {
                    amounts.add(val);
                } else if (unitsList.size() < amounts.size() && unitsList.size() <= navList.size()) {
                    unitsList.add(val);
                } else if (navList.size() < unitsList.size() && navList.size() <= balanceList.size()) {
                    navList.add(val);
                } else {
                    balanceList.add(val);
                }
            } catch (NumberFormatException ignore) {}
        }

        // ── Fallback: interleaved multi-line (original strategy 2) ──
        // If the simple bucket fill didn't work cleanly, use the original approach.
        if (amounts.size() != unitsList.size() || amounts.size() != navList.size()
                || amounts.size() != balanceList.size()
                || amounts.isEmpty()) {
            // Re-run original interleaved multi-line parser
            parseInterleavedMultiLine(folio, lines);
            return;
        }

        int txCount = Math.min(dates.size(), amounts.size());
        for (int i = 0; i < txCount; i++) {
            TxRow row = new TxRow();
            row.date         = dates.get(i);
            row.description  = i < descriptions.size() ? descriptions.get(i) : "Unknown";
            row.amount       = amounts.get(i);
            row.units        = unitsList.get(i);
            row.nav          = navList.get(i);
            row.balanceUnits = balanceList.get(i);
            folio.transactions.add(row);
        }
    }

    /**
     * Original interleaved multi-line strategy:
     *   date
     *   description
     *   number number number number
     */
    private void parseInterleavedMultiLine(FolioBlock folio, String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            Matcher dateMatcher = DATE_LINE.matcher(trimmed);
            if (!dateMatcher.matches()) continue;

            try {
                LocalDate txDate = LocalDate.parse(dateMatcher.group(1), DATE_FMT);

                List<String> subsequent = new ArrayList<>();
                for (int j = i + 1; j < lines.length && subsequent.size() < 8; j++) {
                    String s = lines[j].trim();
                    if (s.isEmpty()) continue;
                    if (DATE_LINE.matcher(s).matches()) break;
                    if (s.startsWith("Folio") || s.startsWith("Closing") || s.startsWith("Portfolio")) break;
                    subsequent.add(s);
                }

                if (subsequent.size() < 4) continue;

                String description = "";
                List<BigDecimal> numbers = new ArrayList<>();

                for (String s : subsequent) {
                    String cleaned = s.replace(",", "").trim();
                    if (numbers.isEmpty()) {
                        try {
                            numbers.add(new BigDecimal(cleaned));
                        } catch (NumberFormatException e) {
                            description = description.isEmpty() ? s : description + " " + s;
                        }
                    } else {
                        try {
                            numbers.add(new BigDecimal(cleaned));
                        } catch (NumberFormatException e) {
                            break;
                        }
                    }
                }

                if (numbers.size() < 4) continue;

                TxRow row = new TxRow();
                row.date         = txDate;
                row.description  = description.isEmpty() ? "Unknown" : description;
                row.amount       = numbers.get(0);
                row.units        = numbers.get(1);
                row.nav          = numbers.get(2);
                row.balanceUnits = numbers.get(3);
                folio.transactions.add(row);
            } catch (Exception e) {}
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    int saveTransactions(FolioBlock folio, Long userId) {
        String amfiCode = folio.amfiCode;
        if (amfiCode == null) {
            log.warn("[CAS] Skipping folio #{} — amfiCode is null (scheme not matched)", folio.folioNumber);
            return 0;
        }

        log.debug("[CAS] saveTransactions() folio=#{} amfi={} txns={}",
            folio.folioNumber, amfiCode, folio.transactions.size());

        // Remove old CAS transactions/lots if re-uploading same folio
        List<Transaction> existing = txRepo.findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId, amfiCode)
           .stream().filter(t -> folio.folioNumber.equals(t.getFolioNumber()) && "CAS_IMPORT".equals(t.getSource())).toList();
        if (!existing.isEmpty()) {
            log.info("[CAS] Re-upload detected — removing {} old CAS transactions for folio #{}",
                existing.size(), folio.folioNumber);
            for (Transaction t : existing) {
                lotRepo.deleteByTransactionId(t.getId());
                txRepo.delete(t);
            }
        }

        Scheme scheme = navService.ensureSchemeExists(amfiCode);

        // KEY FIX: if this is a synthetic scheme (no real NAV), populate lastNav
        // from the last transaction's NAV field in the CAS PDF so currentValue != ₹0
        if ((scheme.getLastNav() == null || scheme.getLastNav().compareTo(BigDecimal.ZERO) == 0)
                && !folio.transactions.isEmpty()) {
            // Use the last transaction row's NAV as the best-known NAV
            BigDecimal latestKnownNav = null;
            for (int i = folio.transactions.size() - 1; i >= 0; i--) {
                TxRow r = folio.transactions.get(i);
                if (r.nav != null && r.nav.compareTo(BigDecimal.ZERO) > 0) {
                    latestKnownNav = r.nav;
                    break;
                }
            }
            if (latestKnownNav != null) {
                scheme.setLastNav(latestKnownNav);
                try { schemeRepo.save(scheme); } catch (Exception e) {
                    log.warn("[CAS] Could not update lastNav for {}: {}", amfiCode, e.getMessage());
                }
            }
        }

        int count = 0;
        log.info("[CAS] Saving {} transactions for folio #{} (scheme: {})",
            folio.transactions.size(), folio.folioNumber,
            folio.schemeName != null ? folio.schemeName : amfiCode);
        for (TxRow row : folio.transactions) {
            String type = detectType(row.description);
            log.debug("[CAS]   #{} {} | {} | amt={} units={} nav={}",
                count + 1, row.date, type, row.amount, row.units, row.nav);
            boolean isPurchase = type.startsWith("PURCHASE") || type.startsWith("SWITCH_IN") || type.startsWith("DIVIDEND") || type.startsWith("STP_IN");
            boolean isRedemption = type.startsWith("REDEMPT") || type.startsWith("SWITCH_OUT") || type.startsWith("SWP") || type.startsWith("STP_OUT");

            Transaction tx = new Transaction();
            // Use nanoseconds + sequential count to guarantee uniqueness even for 100+ transactions
            // saved in the same second from a CAS bulk import (second-precision caused UNIQUE violations)
            tx.setTransactionRef("WW"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", System.nanoTime() % 1_000_000L)
                + String.format("%03d", count % 1000));
            tx.setUserId(userId);
            tx.setSchemeAmfiCode(amfiCode);
            tx.setSchemeName(scheme.getSchemeName());
            tx.setFolioNumber(folio.folioNumber);
            tx.setTransactionDate(row.date);
            tx.setTransactionType(type);
            tx.setAmount(row.amount);
            tx.setUnits(row.units);
            tx.setNav(row.nav);
            tx.setSource("CAS_IMPORT");
            tx.setNotes(row.description);
            tx.setCategory(folio.broadCategory != null && !folio.broadCategory.equals("UNKNOWN") ? folio.broadCategory : folio.sebiCategory);
            tx.setRisk(folio.riskLevel);
            
            Transaction saved = txRepo.save(tx);
            
            if (isPurchase) {
                InvestmentLot lot = new InvestmentLot();
                lot.setTransactionId(saved.getId());
                lot.setUserId(userId);
                lot.setSchemeAmfiCode(amfiCode);
                lot.setSchemeName(scheme.getSchemeName());
                lot.setFolioNumber(folio.folioNumber);
                lot.setPurchaseDate(row.date);
                lot.setPurchaseNav(row.nav);
                lot.setPurchaseAmount(row.amount);
                lot.setUnitsOriginal(row.units);
                lot.setUnitsRemaining(row.units);
                lotRepo.save(lot);
            } else if (isRedemption) {
                // Use abs() — some CAS PDFs store redemption units as negative
                consumeLotsFifo(userId, amfiCode, folio.folioNumber, row.units.abs());
            }
            count++;
        }
        if (folio.transactions.size() > 0 && count == 0) {
            log.warn("[CAS] WARNING: {} transactions were parsed but 0 were saved for folio #{}. Check for DB errors above.",
                folio.transactions.size(), folio.folioNumber);
        }
        return count;
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


    private String detectType(String description) {
        if (description == null) {
            log.debug("[CAS] detectType: null description → defaulting to PURCHASE_LUMPSUM");
            return "PURCHASE_LUMPSUM";
        }
        String upper = description.toUpperCase();

        if (upper.contains("SIP") || upper.contains("SYSTEMATIC")) return "PURCHASE_SIP";
        if (upper.contains("SWITCH IN") || upper.contains("SWITCH-IN")) return "SWITCH_IN";
        if (upper.contains("SWITCH OUT") || upper.contains("SWITCH-OUT")) return "SWITCH_OUT";
        if (upper.contains("REDEMPTION") || upper.contains("REDEEM")) return "REDEMPTION";
        if (upper.contains("STP IN") || upper.contains("STP-IN")) return "STP_IN";
        if (upper.contains("STP OUT") || upper.contains("STP-OUT")) return "STP_OUT";
        if (upper.contains("SWP")) return "SWP";
        if (upper.contains("DIVIDEND PAYOUT")) return "DIVIDEND_PAYOUT";
        if (upper.contains("DIVIDEND REINVEST") || upper.contains("IDCW REINVEST")) return "DIVIDEND_REINVEST";

        return "PURCHASE_LUMPSUM";
    }

    private BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Extracts the best 1-2 word search keyword from a PDF scheme name.
     * Uses the FIRST meaningful words (AMC + fund type) rather than the
     * last 2 words which are always generic ("Growth Option", "Direct Plan").
     * e.g. "Axis Midcap Fund - Direct Plan" -> "Axis Midcap"
     *      "Parag Parikh Flexi Cap Fund" -> "Parag Parikh"
     */
    private String extractSearchKeyword(String pdfSchemeName) {
        if (pdfSchemeName == null) return "";
        // Strip trailing plan/option qualifiers
        String cleaned = pdfSchemeName
            .replaceAll("(?i)\\s*-\\s*(Direct|Regular)\\s*(Plan)?.*", "")
            .replaceAll("(?i)\\s*(Fund|Scheme)\\s*$", "")
            .trim();
        String[] parts = cleaned.split("\\s+");
        // Use first 2 words — these are the most specific (AMC name + fund type)
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];   // e.g. "Axis Midcap", "Parag Parikh"
        }
        return cleaned;
    }

    /**
     * Tier 3: When ISIN lookup and DB name search both fail, try mfapi.in's
     * search endpoint to resolve the scheme name to a real AMFI code.
     * On success, updates scheme_master with the real AMFI code + ISIN so
     * future imports match at Tier 1 (ISIN lookup).
     */
    private Scheme resolveAmfiCodeFromMfApi(String schemeName, String isin) {
        try {
            // Build search query: first 3 meaningful words
            String[] parts = schemeName
                .replaceAll("(?i)\\s*-\\s*(Direct|Regular).*", "")
                .split("\\s+");
            String query = String.join("+",
                java.util.Arrays.copyOfRange(parts, 0, Math.min(3, parts.length)));

            String url = "https://api.mfapi.in/mf/search?q=" + query;
            log.info("[CAS] Tier-3 mfapi.in search: {}", url);

            org.springframework.web.client.RestTemplate rt =
                new org.springframework.web.client.RestTemplate();
            rt.getMessageConverters().add(0,
                new org.springframework.http.converter.StringHttpMessageConverter(
                    java.nio.charset.StandardCharsets.UTF_8));

            String json = rt.getForObject(url, String.class);
            if (json == null || json.isBlank() || json.equals("[]")) return null;

            com.fasterxml.jackson.databind.ObjectMapper om =
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode arr = om.readTree(json);

            // Find best match — prefer "Direct" plan
            String bestAmfi = null;
            String bestName = null;
            for (com.fasterxml.jackson.databind.JsonNode node : arr) {
                String code = node.path("schemeCode").asText("");
                String name = node.path("schemeName").asText("");
                if (code.isBlank() || name.isBlank()) continue;
                if (name.toLowerCase().contains("direct")) {
                    bestAmfi = code;
                    bestName = name;
                    break;
                }
                if (bestAmfi == null) { bestAmfi = code; bestName = name; }
            }

            if (bestAmfi == null) return null;

            // Check if real scheme already in DB
            Optional<Scheme> existing = schemeRepo.findByAmfiCode(bestAmfi);
            if (existing.isPresent()) {
                Scheme s = existing.get();
                // Backfill ISIN so future imports skip Tier 3
                if (isin != null && s.getIsinGrowth() == null) {
                    s.setIsinGrowth(isin);
                    try { schemeRepo.save(s); } catch (Exception ignored) {}
                }
                log.info("[CAS] Tier-3 resolved '{}' -> AMFI {} (already in DB)",
                    schemeName, bestAmfi);
                return s;
            }

            // Create minimal scheme entry from mfapi data
            Scheme s = new Scheme();
            s.setAmfiCode(bestAmfi);
            s.setSchemeName(bestName);
            s.setIsinGrowth(isin);
            String[] derived = deriveCategory(bestName);
            s.setBroadCategory(derived[0]);
            s.setSebiCategory(derived[1]);
            s.setRiskLevel(NavAllTxtParser.assignRiskLevel(derived[1], derived[0]));
            s.setIsActive(true);
            try {
                schemeRepo.save(s);
                log.info("[CAS] Tier-3 saved new scheme '{}' with AMFI {} ISIN {}",
                    bestName, bestAmfi, isin);
            } catch (Exception e) {
                log.warn("[CAS] Tier-3 could not save scheme {}: {}", bestAmfi, e.getMessage());
            }
            return s;

        } catch (Exception e) {
            log.warn("[CAS] Tier-3 mfapi search failed for '{}': {}", schemeName, e.getMessage());
            return null;
        }
    }

    /**
     * Derive broad category + SEBI sub-category from scheme name keywords.
     * Used when no DB match found for the scheme.
     */
    public static String[] deriveCategory(String name) {
        if (name == null) return new String[]{"OTHER", "Unknown"};
        String u = name.toUpperCase();

        // ── Debt (all sub-categories incl. FMP / Fixed Term / Interval) ──────
        if (u.contains("OVERNIGHT"))               return new String[]{"DEBT", "Overnight Fund"};
        if (u.contains("LIQUID"))                  return new String[]{"DEBT", "Liquid Fund"};
        if (u.contains("ULTRA SHORT"))             return new String[]{"DEBT", "Ultra Short Duration Fund"};
        if (u.contains("LOW DURATION"))            return new String[]{"DEBT", "Low Duration Fund"};
        if (u.contains("MONEY MARKET"))            return new String[]{"DEBT", "Money Market Fund"};
        if (u.contains("SHORT DURATION"))          return new String[]{"DEBT", "Short Duration Fund"};
        if (u.contains("MEDIUM TO LONG") || u.contains("MEDIUM-TO-LONG"))
                                                   return new String[]{"DEBT", "Medium to Long Duration Fund"};
        if (u.contains("MEDIUM DURATION"))         return new String[]{"DEBT", "Medium Duration Fund"};
        if (u.contains("LONG DURATION"))           return new String[]{"DEBT", "Long Duration Fund"};
        if (u.contains("BANKING AND PSU") || u.contains("BANKING PSU") || u.contains("BANKING & PSU"))
                                                   return new String[]{"DEBT", "Banking and PSU Fund"};
        if (u.contains("CORPORATE BOND"))          return new String[]{"DEBT", "Corporate Bond Fund"};
        if (u.contains("DYNAMIC BOND"))            return new String[]{"DEBT", "Dynamic Bond Fund"};
        if (u.contains("GILT"))                    return new String[]{"DEBT", "Gilt Fund"};
        if (u.contains("CREDIT RISK"))             return new String[]{"DEBT", "Credit Risk Fund"};
        if (u.contains("FLOATER"))                 return new String[]{"DEBT", "Floater Fund"};
        if (u.contains("FIXED TERM") || u.contains("FIXED MATURITY") || u.contains("FMP"))
                                                   return new String[]{"DEBT", "Fixed Maturity Plan"};
        if (u.contains("INTERVAL"))                return new String[]{"DEBT", "Interval Fund"};
        if (u.contains("INCOME"))                  return new String[]{"DEBT", "Income Fund"};

        // ── Hybrid ────────────────────────────────────────────────────────────
        if (u.contains("CONSERVATIVE HYBRID"))    return new String[]{"HYBRID", "Conservative Hybrid Fund"};
        if (u.contains("BALANCED ADVANTAGE") || u.contains("DYNAMIC ASSET"))
                                                   return new String[]{"HYBRID", "Balanced Advantage Fund"};
        if (u.contains("BALANCED HYBRID"))         return new String[]{"HYBRID", "Balanced Hybrid Fund"};
        if (u.contains("AGGRESSIVE HYBRID"))       return new String[]{"HYBRID", "Aggressive Hybrid Fund"};
        if (u.contains("MULTI ASSET"))             return new String[]{"HYBRID", "Multi Asset Allocation Fund"};
        if (u.contains("ARBITRAGE"))               return new String[]{"HYBRID", "Arbitrage Fund"};
        if (u.contains("EQUITY SAVINGS"))          return new String[]{"HYBRID", "Equity Savings Fund"};
        if (u.contains("HYBRID"))                  return new String[]{"HYBRID", "Hybrid Fund"};

        // ── Equity ────────────────────────────────────────────────────────────
        if (u.contains("SMALL CAP") || u.contains("SMALLCAP"))  return new String[]{"EQUITY", "Small Cap Fund"};
        if (u.contains("SECTORAL") || u.contains("THEMATIC"))   return new String[]{"EQUITY", "Sectoral/Thematic Fund"};
        if (u.contains("ELSS") || u.contains("TAX SAVER"))      return new String[]{"EQUITY", "ELSS"};
        if (u.contains("MID CAP") || u.contains("MIDCAP"))      return new String[]{"EQUITY", "Mid Cap Fund"};
        if (u.contains("FLEXI CAP"))                            return new String[]{"EQUITY", "Flexi Cap Fund"};
        if (u.contains("MULTI CAP"))                            return new String[]{"EQUITY", "Multi Cap Fund"};
        if (u.contains("LARGE & MID") || u.contains("LARGE AND MID")) return new String[]{"EQUITY", "Large & Mid Cap Fund"};
        if (u.contains("LARGE CAP") || u.contains("LARGECAP"))  return new String[]{"EQUITY", "Large Cap Fund"};
        if (u.contains("DIVIDEND YIELD"))                       return new String[]{"EQUITY", "Dividend Yield Fund"};
        if (u.contains("FOCUSED"))                              return new String[]{"EQUITY", "Focused Fund"};
        if (u.contains("VALUE") || u.contains("CONTRA"))        return new String[]{"EQUITY", "Value/Contra Fund"};
        if (u.contains("INDEX") || u.contains("NIFTY") || u.contains("SENSEX") || u.contains("ETF"))
                                                                return new String[]{"EQUITY", "Index Fund"};

        // ── Other / Solution ──────────────────────────────────────────────────
        if (u.contains("GOLD"))                    return new String[]{"OTHER", "Gold Fund"};
        if (u.contains("INTERNATIONAL") || u.contains("GLOBAL") || u.contains("OVERSEAS"))
                                                   return new String[]{"OTHER", "International Fund"};
        if (u.contains("FOF") || u.contains("FUND OF FUND"))    return new String[]{"OTHER", "Fund of Funds"};
        if (u.contains("RETIREMENT"))              return new String[]{"SOLUTION", "Retirement Fund"};
        if (u.contains("CHILDREN"))                return new String[]{"SOLUTION", "Children Fund"};

        if (u.contains("EQUITY") || u.contains("FUND")) return new String[]{"EQUITY", "Equity Fund"};
        return new String[]{"OTHER", "Unknown"};
    }
}
