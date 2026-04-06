package com.wealthwise.service;

import com.wealthwise.model.Scheme;
import com.wealthwise.parser.NavAllTxtParser;
import com.wealthwise.repository.SchemeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

/**
 * SchemeService — manages scheme data from AMFI NAVAll.txt.
 *
 * Uses NavAllTxtParser (ported from MVP) which reads SEBI category headers
 * from NAVAll.txt and correctly sets broadCategory, sebiCategory, riskLevel
 * on every scheme — no manual classification needed.
 */
@Service
public class SchemeService {

    private static final Logger log = Logger.getLogger(SchemeService.class.getName());
    private static final String AMFI_URL = "https://www.amfiindia.com/spages/NAVAll.txt";

    @Autowired
    private SchemeRepository schemeRepository;

    // ─── Search ──────────────────────────────────────────────────────────────

    public Page<Scheme> search(String query, String category, String planType, int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("schemeName").ascending());
        if (query == null || query.isBlank()) {
            return schemeRepository.findAll(pr);
        }
        if ((category == null || category.isBlank()) && (planType == null || planType.isBlank())) {
            return schemeRepository.searchActive(query.trim(), pr);
        }
        return schemeRepository.searchFiltered(
            query.trim(),
            (category == null || category.isBlank()) ? null : category.toUpperCase(),
            (planType == null || planType.isBlank()) ? null : planType.toUpperCase(),
            pr
        );
    }

    public Optional<Scheme> findByAmfiCode(String amfiCode) {
        return schemeRepository.findByAmfiCode(amfiCode);
    }

    public long countActive() {
        return schemeRepository.countByIsActiveTrue();
    }

    // ─── Seeding ──────────────────────────────────────────────────────────────

    /**
     * Seeds scheme_master from AMFI NAVAll.txt.
     * Uses NavAllTxtParser which correctly parses SEBI category headers,
     * so every scheme gets accurate broadCategory, sebiCategory, riskLevel.
     * 
     * Called: on startup (if empty) + daily midnight cron.
     */
    @Transactional
    public Map<String, Object> seedFromNavAll() {
        log.info("[Seed] Starting NAVAll.txt seeding from AMFI...");
        int inserted = 0, updated = 0, skipped = 0;

        try {
            URL url = new URL(AMFI_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "WealthWise/1.0");

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("AMFI returned HTTP " + conn.getResponseCode());
            }

            NavAllTxtParser parser = new NavAllTxtParser();
            NavAllTxtParser.ParseResult result;
            try (InputStream is = conn.getInputStream()) {
                result = parser.parse(is);
            }

            for (Scheme incoming : result.schemes) {
                try {
                    Optional<Scheme> existing = schemeRepository.findByAmfiCode(incoming.getAmfiCode());
                    if (existing.isPresent()) {
                        Scheme s = existing.get();
                        // Update mutable fields — critically category + risk
                        s.setSchemeName(incoming.getSchemeName());
                        s.setAmcName(incoming.getAmcName());
                        s.setIsActive(incoming.getIsActive());
                        s.setLastNav(incoming.getLastNav());
                        s.setLastNavDate(incoming.getLastNavDate());
                        s.setIsinGrowth(incoming.getIsinGrowth());
                        s.setIsinIdcw(incoming.getIsinIdcw());
                        s.setPlanType(incoming.getPlanType());
                        s.setOptionType(incoming.getOptionType());
                        s.setFundType(incoming.getFundType());
                        // Set category + risk (the key improvement)
                        if (incoming.getBroadCategory() != null)
                            s.setBroadCategory(incoming.getBroadCategory());
                        if (incoming.getSebiCategory() != null)
                            s.setSebiCategory(incoming.getSebiCategory());
                        if (incoming.getRiskLevel() != null)
                            s.setRiskLevel(incoming.getRiskLevel());
                        schemeRepository.save(s);
                        updated++;
                    } else {
                        schemeRepository.save(incoming);
                        inserted++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.warning("[Seed] Skipped " + incoming.getAmfiCode() + ": " + e.getMessage());
                }
            }

            String msg = "Seed complete: " + inserted + " inserted, " + updated + " updated, " + skipped + " skipped";
            log.info("[Seed] " + msg);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("inserted", inserted);
            response.put("updated", updated);
            response.put("skipped", skipped);
            response.put("total", result.schemes.size());
            return response;

        } catch (Exception e) {
            log.severe("[Seed] Failed: " + e.getMessage());
            throw new RuntimeException("Seeding failed: " + e.getMessage(), e);
        }
    }

    /**
     * Daily midnight re-sync — updates NAV + active flags
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledSync() {
        log.info("[Seed] Scheduled NAVAll.txt sync started");
        try {
            seedFromNavAll();
        } catch (Exception e) {
            log.warning("[Seed] Scheduled sync failed: " + e.getMessage());
        }
    }
}
