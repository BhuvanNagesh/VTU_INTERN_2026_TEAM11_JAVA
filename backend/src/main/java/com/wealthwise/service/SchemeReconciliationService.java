package com.wealthwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealthwise.model.Scheme;
import com.wealthwise.parser.NavAllTxtParser;
import com.wealthwise.repository.InvestmentLotRepository;
import com.wealthwise.repository.SchemeRepository;
import com.wealthwise.repository.TransactionRepository;
import com.wealthwise.service.CasPdfParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reconciliation Service — fixes existing WW_ISIN_* and WW_* synthetic scheme codes
 * by resolving them to real AMFI codes via mfapi.in name search.
 *
 * Call POST /api/nav/reconcile-synthetic-schemes to trigger.
 *
 * For each synthetic scheme code:
 *   1. Extract the ISIN if present (WW_ISIN_INF846K01EW2 → INF846K01EW2)
 *   2. Look up the scheme name via the stored scheme_master entry
 *   3. Search mfapi.in by name to find the real AMFI code
 *   4. Bulk-update transactions + investment_lots + scheme_master atomically
 *   5. Delete the now-redundant synthetic scheme_master row
 *
 * This is idempotent — safe to call multiple times; already-real codes are skipped.
 */
@Service
public class SchemeReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(SchemeReconciliationService.class);

    @Autowired private SchemeRepository      schemeRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private InvestmentLotRepository lotRepo;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate rt;

    public SchemeReconciliationService() {
        this.rt = new RestTemplate();
        this.rt.getMessageConverters().add(0,
            new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Finds every WW_ISIN_ / WW_ scheme in scheme_master and tries to resolve it.
     * Returns a result map per synthetic code: resolved / unresolved / skipped.
     */
    @Transactional
    public Map<String, Object> reconcileAll() {
        List<Scheme> synthetics = schemeRepo.findAll().stream()
            .filter(s -> s.getAmfiCode() != null && s.getAmfiCode().startsWith("WW_"))
            .toList();

        log.info("[RECONCILE] Found {} synthetic scheme(s) to process", synthetics.size());

        int resolved = 0, unresolved = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (Scheme synthetic : synthetics) {
            Map<String, Object> result = reconcileOne(synthetic);
            details.add(result);
            if (Boolean.TRUE.equals(result.get("resolved"))) resolved++;
            else unresolved++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSynthetic",  synthetics.size());
        summary.put("resolved",        resolved);
        summary.put("unresolved",      unresolved);
        summary.put("details",         details);
        return summary;
    }

    // ── Per-scheme reconciliation ─────────────────────────────────────────────

    private Map<String, Object> reconcileOne(Scheme synthetic) {
        String syntheticCode = synthetic.getAmfiCode();
        String schemeName    = synthetic.getSchemeName();
        String isin          = synthetic.getIsinGrowth();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("syntheticCode", syntheticCode);
        detail.put("schemeName",    schemeName);
        detail.put("isin",          isin);

        // ── Step 1: Try to resolve via mfapi search ───────────────────────────
        String realAmfi = null;
        String realName = null;

        // Strategy A: search by ISIN extracted from synthetic code
        // WW_ISIN_INF846K01EW2 → try "INF846K01EW2" as query
        if (syntheticCode.startsWith("WW_ISIN_")) {
            String extractedIsin = syntheticCode.substring("WW_ISIN_".length());
            String[] resolved = searchMfApi(extractedIsin);
            if (resolved != null) { realAmfi = resolved[0]; realName = resolved[1]; }
        }

        // Strategy B: search by scheme name (first 3 words, prefer Direct plan)
        if (realAmfi == null && schemeName != null && !schemeName.isBlank()) {
            String[] resolved = searchMfApi(buildQuery(schemeName));
            if (resolved != null) { realAmfi = resolved[0]; realName = resolved[1]; }
        }

        if (realAmfi == null) {
            log.warn("[RECONCILE] Could not resolve '{}' ({})", syntheticCode, schemeName);
            detail.put("resolved", false);
            detail.put("reason",   "mfapi.in returned no matches");
            return detail;
        }

        // ── Step 2: Check if we'd be replacing with a code that already exists ─
        Optional<Scheme> existingReal = schemeRepo.findByAmfiCode(realAmfi);
        if (existingReal.isPresent() && !existingReal.get().getAmfiCode().equals(syntheticCode)) {
            // Real scheme already in DB — just remap transactions to it
            Scheme real = existingReal.get();
            if (isin != null && real.getIsinGrowth() == null) {
                real.setIsinGrowth(isin);
                schemeRepo.save(real);
            }
            int txUpdated  = txRepo.bulkUpdateSchemeAmfiCode(syntheticCode, realAmfi, real.getSchemeName());
            int lotUpdated = lotRepo.bulkUpdateSchemeAmfiCode(syntheticCode, realAmfi, real.getSchemeName());
            schemeRepo.delete(synthetic); // remove redundant synthetic entry
            log.info("[RECONCILE] {} → {} (real already in DB) | tx={} lots={}", syntheticCode, realAmfi, txUpdated, lotUpdated);
            detail.put("resolved",   true);
            detail.put("newAmfi",    realAmfi);
            detail.put("newName",    real.getSchemeName());
            detail.put("txUpdated",  txUpdated);
            detail.put("lotUpdated", lotUpdated);
            detail.put("source",     "existing_db");
            return detail;
        }

        // ── Step 3: Update the synthetic scheme row to be the real one ────────
        String oldName = synthetic.getSchemeName();
        synthetic.setAmfiCode(realAmfi);
        synthetic.setSchemeName(realName);
        if (isin != null) synthetic.setIsinGrowth(isin);
        String[] derived = CasPdfParserService.deriveCategory(realName);
        if (synthetic.getBroadCategory() == null) synthetic.setBroadCategory(derived[0]);
        if (synthetic.getSebiCategory()  == null) synthetic.setSebiCategory(derived[1]);
        if (synthetic.getRiskLevel()     == null)
            synthetic.setRiskLevel(NavAllTxtParser.assignRiskLevel(derived[1], derived[0]));
        schemeRepo.save(synthetic);

        // ── Step 4: Bulk-update transactions + lots ───────────────────────────
        int txUpdated  = txRepo.bulkUpdateSchemeAmfiCode(syntheticCode, realAmfi, realName);
        int lotUpdated = lotRepo.bulkUpdateSchemeAmfiCode(syntheticCode, realAmfi, realName);

        log.info("[RECONCILE] {} → {} | name: '{}' → '{}' | tx={} lots={}",
            syntheticCode, realAmfi, oldName, realName, txUpdated, lotUpdated);

        detail.put("resolved",   true);
        detail.put("newAmfi",    realAmfi);
        detail.put("newName",    realName);
        detail.put("txUpdated",  txUpdated);
        detail.put("lotUpdated", lotUpdated);
        detail.put("source",     "mfapi_search");
        return detail;
    }

    // ── mfapi.in search helpers ───────────────────────────────────────────────

    /**
     * Calls mfapi.in search with the given query string.
     * Returns [amfiCode, schemeName] of the best match (Direct plan preferred),
     * or null if nothing found.
     */
    private String[] searchMfApi(String query) {
        try {
            String url = "https://api.mfapi.in/mf/search?q=" + query.replace(" ", "+");
            log.debug("[RECONCILE] mfapi search: {}", url);
            String json = rt.getForObject(url, String.class);
            if (json == null || json.isBlank() || json.equals("[]")) return null;

            JsonNode arr = om.readTree(json);
            String bestCode = null, bestName = null;

            for (JsonNode node : arr) {
                String code = node.path("schemeCode").asText("");
                String name = node.path("schemeName").asText("");
                if (code.isBlank() || name.isBlank()) continue;
                if (name.toLowerCase().contains("direct")) {
                    // Prefer Growth over IDCW
                    if (bestCode == null || name.toLowerCase().contains("growth")) {
                        bestCode = code;
                        bestName = name;
                    }
                    if (name.toLowerCase().contains("growth")) break; // best possible
                } else if (bestCode == null) {
                    bestCode = code;
                    bestName = name;
                }
            }
            return bestCode != null ? new String[]{bestCode, bestName} : null;

        } catch (Exception e) {
            log.warn("[RECONCILE] mfapi search failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }

    /** Builds a search query from the first 3 words of a scheme name. */
    private String buildQuery(String schemeName) {
        String cleaned = schemeName
            .replaceAll("(?i)\\s*-\\s*(Direct|Regular).*", "")
            .trim();
        String[] parts = cleaned.split("\\s+");
        return String.join(" ", Arrays.copyOfRange(parts, 0, Math.min(3, parts.length)));
    }
}
