package com.wealthwise.service;

import com.wealthwise.model.NavHistory;
import com.wealthwise.model.Scheme;
import com.wealthwise.repository.NavHistoryRepository;
import com.wealthwise.repository.SchemeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * On-demand NAV Service
 * ─────────────────────
 * Fetches NAV data ONLY for schemes users actually interact with.
 * Uses Caffeine cache (Redis-style TTL) to avoid repeat calls:
 *
 *   nav_latest:{amfiCode}      → Latest NAV (24h TTL)
 *   nav_history:{amfiCode}     → Full history JSON from mfapi.in (7d TTL)
 *
 * Do NOT fetch all 45k schemes. Only fetch when user selects a scheme.
 */
@Service
public class NavService {

    private static final Logger log = Logger.getLogger(NavService.class.getName());
    private static final String MFAPI_BASE = "https://api.mfapi.in/mf/";
    private static final DateTimeFormatter MFAPI_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter AMFI_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    @Autowired
    private SchemeRepository schemeRepo;

    @Autowired
    private NavHistoryRepository navHistoryRepo;

    // ─── Latest NAV (24h cache) ───────────────────────────────────────────────

    /**
     * Returns {nav, date} for a scheme's latest NAV.
     * Priority: cache → mfapi.in → scheme_master (fallback)
     */
    @Cacheable(value = "nav_latest", key = "#amfiCode")
    public Map<String, Object> getLatestNav(String amfiCode) {
        log.info("[NAV] Fetching latest NAV for " + amfiCode);
        // Try mfapi.in first (most up-to-date)
        try {
            Map<String, Object> history = fetchFromMfApi(amfiCode);
            if (history != null && history.containsKey("latestNav")) {
                return Map.of(
                    "amfiCode", amfiCode,
                    "nav", history.get("latestNav"),
                    "date", history.get("latestDate"),
                    "source", "mfapi.in"
                );
            }
        } catch (Exception e) {
            log.warning("[NAV] mfapi.in failed for " + amfiCode + ": " + e.getMessage());
        }

        // Fallback to scheme_master
        return schemeRepo.findByAmfiCode(amfiCode).map(s -> {
            Map<String, Object> result = new HashMap<>();
            result.put("amfiCode", amfiCode);
            result.put("nav", s.getLastNav());
            result.put("date", s.getLastNavDate());
            result.put("source", "db_fallback");
            return result;
        }).orElse(Map.of("amfiCode", amfiCode, "nav", null, "date", null, "source", "not_found"));
    }

    /**
     * Force-refresh NAV for a scheme (bypasses cache — updates it).
     * Call this when user adds a new scheme to a transaction.
     */
    @CachePut(value = "nav_latest", key = "#amfiCode")
    public Map<String, Object> refreshLatestNav(String amfiCode) {
        // Same as getLatestNav but @CachePut always runs and updates cache
        log.info("[NAV] Force-refreshing NAV for " + amfiCode);
        try {
            Map<String, Object> history = fetchFromMfApi(amfiCode);
            if (history != null && history.containsKey("latestNav")) {
                // Also update scheme_master in DB
                schemeRepo.findByAmfiCode(amfiCode).ifPresent(s -> {
                    try {
                        Object navObj = history.get("latestNav");
                        if (navObj != null) {
                            s.setLastNav(new BigDecimal(navObj.toString()));
                            Object dateObj = history.get("latestDate");
                            if (dateObj != null) {
                                s.setLastNavDate(LocalDate.parse(dateObj.toString(), MFAPI_DATE));
                            }
                            schemeRepo.save(s);
                        }
                    } catch (Exception e) {
                        log.warning("[NAV] Could not update scheme_master for " + amfiCode);
                    }
                });
                return Map.of(
                    "amfiCode", amfiCode,
                    "nav", history.get("latestNav"),
                    "date", history.get("latestDate"),
                    "source", "mfapi.in"
                );
            }
        } catch (Exception e) {
            log.warning("[NAV] Refresh failed for " + amfiCode + ": " + e.getMessage());
        }
        return getLatestNav(amfiCode);
    }

    // ─── Historical NAV for a specific date ──────────────────────────────────

    /**
     * Returns the full historical NAV list for a scheme.
     *
     * Strategy (DB-first write-through cache):
     *   1. If we have records in nav_history DB table for this scheme → serve from DB
     *      and asynchronously refresh if data is older than 7 days.
     *   2. If no DB data → fetch from mfapi.in, persist ALL entries to DB, then return.
     *
     * This means historical NAV data survives server restarts and is not
     * dependent on the in-memory Caffeine cache being warm.
     *
     * Returns: list of {"date": "dd-MM-yyyy", "nav": "NNN.NNNN"} maps
     */
    @Cacheable(value = "nav_history", key = "#amfiCode")
    public List<Map<String, String>> getHistoricalNavs(String amfiCode) {
        // ── Step 1: Try DB first ──────────────────────────────────────────────
        if (navHistoryRepo.existsByAmfiCode(amfiCode)) {
            List<NavHistory> dbRows = navHistoryRepo.findByAmfiCodeOrderByNavDateDesc(amfiCode);
            if (!dbRows.isEmpty()) {
                // Check if the most recent stored date is within 7 days (fresh enough)
                LocalDate latestStored = dbRows.get(0).getNavDate();
                boolean isStale = latestStored.isBefore(LocalDate.now().minusDays(7));

                if (!isStale) {
                    log.info("[NAV] Serving history from DB for " + amfiCode
                        + " (" + dbRows.size() + " records, latest: " + latestStored + ")");
                    return convertDbRowsToApiFormat(dbRows);
                }
                // Data is stale — fall through to API fetch to refresh
                log.info("[NAV] DB data stale for " + amfiCode + ", refreshing from mfapi.in");
            }
        }

        // ── Step 2: Fetch from mfapi.in ──────────────────────────────────────
        log.info("[NAV] Fetching full history from mfapi.in for " + amfiCode);
        try {
            Map<String, Object> apiData = fetchFromMfApi(amfiCode);
            if (apiData != null && apiData.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> data = (List<Map<String, String>>) apiData.get("data");
                // Persist to DB asynchronously (best-effort, non-blocking)
                persistNavHistoryToDB(amfiCode, data);
                return data;
            }
        } catch (Exception e) {
            log.warning("[NAV] History fetch failed for " + amfiCode + ": " + e.getMessage());
            // Fall through to DB fallback even if stale
            if (navHistoryRepo.existsByAmfiCode(amfiCode)) {
                List<NavHistory> dbRows = navHistoryRepo.findByAmfiCodeOrderByNavDateDesc(amfiCode);
                if (!dbRows.isEmpty()) {
                    log.warning("[NAV] Using stale DB data for " + amfiCode + " as fallback");
                    return convertDbRowsToApiFormat(dbRows);
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Converts DB NavHistory rows to the {date, nav} map format used throughout the backend.
     * mfapi.in returns dates as dd-MM-yyyy, so we format the same way.
     */
    private List<Map<String, String>> convertDbRowsToApiFormat(List<NavHistory> rows) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        List<Map<String, String>> result = new ArrayList<>();
        for (NavHistory row : rows) {
            Map<String, String> entry = new HashMap<>();
            entry.put("date", row.getNavDate().format(fmt));
            entry.put("nav", row.getNavValue().toPlainString());
            result.add(entry);
        }
        return result;
    }

    /**
     * Persists NAV history entries to the DB using upsert-by-ignore.
     * Uses a simple loop with existsByAmfiCodeAndNavDate check to avoid duplicates.
     * Runs in the same transaction context; skips entries that already exist.
     */
    @Transactional
    public void persistNavHistoryToDB(String amfiCode, List<Map<String, String>> data) {
        if (data == null || data.isEmpty()) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        int saved = 0;
        for (Map<String, String> entry : data) {
            String dateStr = entry.getOrDefault("date", "");
            String navStr  = entry.getOrDefault("nav", "");
            if (dateStr.isEmpty() || navStr.isEmpty()) continue;
            try {
                LocalDate navDate = LocalDate.parse(dateStr, fmt);
                BigDecimal navVal = new BigDecimal(navStr);
                // Skip if already stored (upsert-on-conflict-ignore)
                if (!navHistoryRepo.findByAmfiCodeAndNavDate(amfiCode, navDate).isPresent()) {
                    navHistoryRepo.save(new NavHistory(amfiCode, navDate, navVal));
                    saved++;
                }
            } catch (Exception e) {
                // Skip malformed entries silently
            }
        }
        if (saved > 0) {
            log.info("[NAV] Persisted " + saved + " new NAV records to DB for " + amfiCode);
        }
    }

    /**
     * Get NAV for a specific date string (dd-MM-yyyy or dd-MMM-yyyy).
     * Searches cached history. Returns null if not found.
     */
    public BigDecimal getNavForDate(String amfiCode, String dateStr) {
        List<Map<String, String>> history = getHistoricalNavs(amfiCode);
        // Normalize date to dd-MM-yyyy (mfapi format)
        String lookupDate = normalizeDateString(dateStr);
        for (Map<String, String> entry : history) {
            String entryDate = entry.getOrDefault("date", "");
            String entryNav = entry.getOrDefault("nav", "");
            if (entryDate.equals(lookupDate) || entryDate.equals(dateStr)) {
                try { return new BigDecimal(entryNav); } catch (Exception e) { return null; }
            }
        }
        // Not found on that exact date (holiday/weekend) — find nearest previous business day
        return findNearestNav(history, lookupDate);
    }

    // ─── On-demand scheme registration ───────────────────────────────────────

    /**
     * Called when a user first adds a scheme to a transaction.
     * If the scheme doesn't exist in scheme_master yet, creates a minimal record
     * and triggers a NAV fetch. This avoids needing the full seed.
     */
    public Scheme ensureSchemeExists(String amfiCode) {
        // Check if already in DB
        Optional<Scheme> existing = schemeRepo.findByAmfiCode(amfiCode);
        if (existing.isPresent()) {
            Scheme s = existing.get();
            // Re-enrich from mfapi.in if category is missing or still OTHER
            boolean needsEnrich = s.getBroadCategory() == null
                || s.getBroadCategory().isBlank()
                || "OTHER".equalsIgnoreCase(s.getBroadCategory())
                || "UNKNOWN".equalsIgnoreCase(s.getBroadCategory());
            if (needsEnrich) {
                try {
                    Map<String, Object> apiData = fetchFromMfApi(amfiCode);
                    if (apiData != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> meta = (Map<String, String>) apiData.get("meta");
                        if (meta != null) {
                            String sebiCat = meta.getOrDefault("scheme_category", s.getSebiCategory());
                            String type    = meta.getOrDefault("scheme_type", "");
                            String broad   = inferBroadCategory(type, sebiCat);
                            if (!"OTHER".equalsIgnoreCase(broad)) {
                                s.setSebiCategory(sebiCat);
                                s.setBroadCategory(broad);
                                if (s.getSchemeName() == null || s.getSchemeName().startsWith("Scheme "))
                                    s.setSchemeName(meta.getOrDefault("scheme_name", s.getSchemeName()));
                                schemeRepo.save(s);
                                log.info("[NAV] Re-enriched " + amfiCode + " → " + broad + " / " + sebiCat);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warning("[NAV] Re-enrich failed for " + amfiCode + ": " + e.getMessage());
                }
            }
            return s;
        }

        // Not in DB — fetch from mfapi.in and create
        try {
            Map<String, Object> apiData = fetchFromMfApi(amfiCode);
            if (apiData != null) {
                Scheme s = new Scheme();
                s.setAmfiCode(amfiCode);
                @SuppressWarnings("unchecked")
                Map<String, String> meta = (Map<String, String>) apiData.get("meta");
                if (meta != null) {
                    s.setSchemeName(meta.getOrDefault("scheme_name", "Scheme " + amfiCode));
                    s.setAmcName(meta.getOrDefault("fund_house", "Unknown AMC"));
                    s.setSebiCategory(meta.getOrDefault("scheme_category", null));
                    String type = meta.getOrDefault("scheme_type", "");
                    s.setBroadCategory(inferBroadCategory(type, s.getSebiCategory()));
                    s.setFundType("OPEN_ENDED");
                    s.setPlanType(inferPlanType(s.getSchemeName()));
                    s.setOptionType(inferOptionType(s.getSchemeName()));
                }
                if (apiData.containsKey("latestNav")) {
                    try { s.setLastNav(new BigDecimal(apiData.get("latestNav").toString())); } catch (Exception ignored) {}
                }
                if (apiData.containsKey("latestDate")) {
                    try { s.setLastNavDate(LocalDate.parse(apiData.get("latestDate").toString(), MFAPI_DATE)); } catch (Exception ignored) {}
                }
                s.setIsActive(true);
                return schemeRepo.save(s);
            }
        } catch (Exception e) {
            log.warning("[NAV] ensureSchemeExists failed for " + amfiCode + ": " + e.getMessage());
        }
        // Minimal fallback
        Scheme s = new Scheme();
        s.setAmfiCode(amfiCode);
        s.setSchemeName("Scheme " + amfiCode);
        s.setIsActive(true);
        return schemeRepo.save(s);
    }

    // ─── mfapi.in HTTP fetcher ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchFromMfApi(String amfiCode) throws Exception {
        String urlStr = MFAPI_BASE + amfiCode;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "WealthWise/1.0");

        if (conn.getResponseCode() != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        String json = sb.toString();
        // Simple JSON parsing without Jackson (to avoid dependency issues)
        Map<String, Object> result = new HashMap<>();

        // Extract meta block
        if (json.contains("\"meta\"")) {
            Map<String, String> meta = new HashMap<>();
            extractJsonString(json, "scheme_name", meta);
            extractJsonString(json, "fund_house", meta);
            extractJsonString(json, "scheme_type", meta);
            extractJsonString(json, "scheme_category", meta);
            result.put("meta", meta);
        }

        // Extract latest NAV (first item in data array)
        int dataIdx = json.indexOf("\"data\"");
        if (dataIdx >= 0) {
            // Parse all data entries
            List<Map<String, String>> dataList = new ArrayList<>();
            int pos = json.indexOf('[', dataIdx);
            int end = json.lastIndexOf(']');
            if (pos >= 0 && end > pos) {
                String dataSection = json.substring(pos + 1, end);
                String[] entries = dataSection.split("\\},\\s*\\{");
                for (String entry : entries) {
                    Map<String, String> row = new HashMap<>();
                    extractJsonStringInto(entry, "date", row);
                    extractJsonStringInto(entry, "nav", row);
                    if (!row.isEmpty()) dataList.add(row);
                }
            }
            result.put("data", dataList);
            if (!dataList.isEmpty()) {
                result.put("latestNav", dataList.get(0).getOrDefault("nav", null));
                result.put("latestDate", dataList.get(0).getOrDefault("date", null));
            }
        }

        return result;
    }

    private void extractJsonString(String json, String key, Map<String, String> target) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end > start) target.put(key, json.substring(start, end));
    }

    private void extractJsonStringInto(String json, String key, Map<String, String> target) {
        extractJsonString("{" + json + "}", key, target);
    }

    private BigDecimal findNearestNav(List<Map<String, String>> history, String targetDate) {
        // History is newest-first; find the entry just after the target date
        for (Map<String, String> entry : history) {
            String d = entry.getOrDefault("date", "");
            if (d.compareTo(targetDate) <= 0) { // d is on or before target
                try { return new BigDecimal(entry.getOrDefault("nav", "")); } catch (Exception e) { return null; }
            }
        }
        return null;
    }

    private String normalizeDateString(String dateStr) {
        if (dateStr == null) return "";
        dateStr = dateStr.trim();
        // Convert yyyy-MM-dd to dd-MM-yyyy (mfapi format)
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] p = dateStr.split("-");
            return p[2] + "-" + p[1] + "-" + p[0];
        }
        // Convert dd-MMM-yyyy to dd-MM-yyyy
        if (dateStr.matches("\\d{2}-[A-Za-z]+-\\d{4}")) {
            try {
                LocalDate d = LocalDate.parse(dateStr, AMFI_DATE);
                return d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception e) { return dateStr; }
        }
        return dateStr;
    }

    private String inferBroadCategory(String type, String category) {
        if (type == null && category == null) return "OTHER";
        String combined = ((type != null ? type : "") + " " + (category != null ? category : "")).toUpperCase();

        // ── Equity ──────────────────────────────────────────────────────────
        if (combined.contains("EQUITY") || combined.contains("ELSS")
                || combined.contains("LARGE CAP") || combined.contains("MID CAP")
                || combined.contains("SMALL CAP") || combined.contains("MULTI CAP")
                || combined.contains("FLEXI CAP") || combined.contains("FOCUSED")
                || combined.contains("VALUE") || combined.contains("CONTRA")
                || combined.contains("DIVIDEND YIELD") || combined.contains("SECTORAL")
                || combined.contains("THEMATIC") || combined.contains("INDEX FUND")
                || combined.contains("ETF"))                          return "EQUITY";

        // ── Debt — includes Fixed Term / FMP / Interval / Close Ended ───────
        if (combined.contains("DEBT") || combined.contains("LIQUID")
                || combined.contains("MONEY MARKET") || combined.contains("OVERNIGHT")
                || combined.contains("ULTRA SHORT") || combined.contains("LOW DURATION")
                || combined.contains("SHORT DURATION") || combined.contains("MEDIUM DURATION")
                || combined.contains("MEDIUM TO LONG") || combined.contains("LONG DURATION")
                || combined.contains("DYNAMIC BOND") || combined.contains("CORPORATE BOND")
                || combined.contains("CREDIT RISK") || combined.contains("BANKING AND PSU")
                || combined.contains("BANKING & PSU") || combined.contains("GILT")
                || combined.contains("FIXED TERM") || combined.contains("FMP")
                || combined.contains("FIXED MATURITY") || combined.contains("INTERVAL")
                || combined.contains("CLOSE ENDED") || combined.contains("FLOATER")
                || combined.contains("INCOME"))                        return "DEBT";

        // ── Hybrid ──────────────────────────────────────────────────────────
        if (combined.contains("HYBRID") || combined.contains("BALANCED")
                || combined.contains("ARBITRAGE") || combined.contains("EQUITY SAVINGS")
                || combined.contains("MULTI ASSET"))                   return "HYBRID";

        // ── Solution ────────────────────────────────────────────────────────
        if (combined.contains("SOLUTION") || combined.contains("CHILDREN")
                || combined.contains("RETIRE"))                        return "SOLUTION";

        return "OTHER";
    }

    private String inferPlanType(String name) {
        if (name == null) return "UNKNOWN";
        if (name.toUpperCase().contains("DIRECT")) return "DIRECT";
        if (name.toUpperCase().contains("REGULAR")) return "REGULAR";
        return "UNKNOWN";
    }

    private String inferOptionType(String name) {
        if (name == null) return "UNKNOWN";
        String u = name.toUpperCase();
        if (u.contains("IDCW REINVEST") || u.contains("DIVIDEND REINVEST")) return "IDCW_REINVESTMENT";
        if (u.contains("IDCW") || u.contains("DIVIDEND") || u.contains("PAYOUT")) return "IDCW_PAYOUT";
        if (u.contains("GROWTH")) return "GROWTH";
        return "UNKNOWN";
    }
}
