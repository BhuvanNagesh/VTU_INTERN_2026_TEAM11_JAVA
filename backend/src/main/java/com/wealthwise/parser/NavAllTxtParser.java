package com.wealthwise.parser;

import com.wealthwise.model.Scheme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the AMFI NAVAll.txt file.
 *
 * File format:
 * ─────────────────────────────────────────────────────────────────────
 * Open Ended Schemes(Equity Scheme - Large Cap Fund)  ← SEBI category header
 *
 * Aditya Birla Sun Life Mutual Fund                    ← AMC header (no semicolons)
 *
 * Scheme Code;ISIN Div Payout;ISIN Div Reinvestment;Scheme Name;NAV;Date
 * 119551;INF209K01YO3;INF209K01YP0;ABSL Bluechip Fund - Direct;52.34;03-Apr-2026
 * ─────────────────────────────────────────────────────────────────────
 *
 * The category headers are parsed to extract broadCategory + sebiCategory
 * which are stored on Scheme — 100% accurate SEBI classification.
 * Risk is also assigned directly from the SEBI category in this parser.
 */
public class NavAllTxtParser {

    private static final Logger log = LoggerFactory.getLogger(NavAllTxtParser.class);

    // Locale.ENGLISH is REQUIRED — prevents parse failures on non-English OS locales
    private static final DateTimeFormatter NAV_DATE_FMT =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    /**
     * Regex to match category headers like:
     *   "Open Ended Schemes(Equity Scheme - Large Cap Fund)"
     *   "Close Ended Schemes(ELSS)"
     *   "Interval Fund Schemes(Income)"
     */
    private static final Pattern CATEGORY_HEADER = Pattern.compile(
        "^(Open Ended Schemes|Close Ended Schemes|Interval Fund Schemes)\\((.+)\\)$");

    public static class ParseResult {
        public final List<Scheme> schemes = new ArrayList<>();
    }

    public ParseResult parse(InputStream inputStream) throws IOException {
        ParseResult result    = new ParseResult();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, "UTF-8"));

        String currentAmc = "";
        String currentCategoryRaw = "";
        String currentBroadCategory = "";
        String currentSebiCategory = "";
        String line;
        int lineNo = 0;

        while ((line = reader.readLine()) != null) {
            lineNo++;
            line = line.trim();
            if (line.isEmpty()) continue;

            // ── Check for SEBI category header ───────────────────────────
            Matcher catMatcher = CATEGORY_HEADER.matcher(line);
            if (catMatcher.matches()) {
                currentCategoryRaw = catMatcher.group(2).trim();
                String[] parsed = parseBroadAndSebi(currentCategoryRaw);
                currentBroadCategory = parsed[0];
                currentSebiCategory  = parsed[1];
                continue;
            }

            // AMC header lines contain no semicolons
            if (!line.contains(";")) {
                if (line.toLowerCase().startsWith("scheme code")) continue;
                currentAmc = line;
                continue;
            }

            // Skip the column-header row
            if (line.toLowerCase().startsWith("scheme code")) continue;

            String[] parts = line.split(";", -1);
            if (parts.length < 6) continue;

            try {
                Scheme s = new Scheme();
                s.setAmfiCode(parts[0].trim());
                s.setIsinGrowth(blank(parts[1]));
                s.setIsinIdcw(blank(parts[2]));
                s.setSchemeName(parts[3].trim());
                s.setAmcName(currentAmc);

                // Determine active: NAV must be a valid number
                String navStr = parts[4].trim();
                boolean active = isValidNav(navStr);
                s.setIsActive(active);

                // Set latest NAV
                if (active) {
                    try { s.setLastNav(new BigDecimal(navStr)); } catch (Exception ignored) {}
                }

                // Set date
                if (parts.length >= 6 && !parts[5].trim().isEmpty()) {
                    try {
                        s.setLastNavDate(LocalDate.parse(parts[5].trim(), NAV_DATE_FMT));
                    } catch (Exception ignored) {}
                }

                // SEBI category from section header — 100% accurate
                s.setBroadCategory(currentBroadCategory);
                s.setSebiCategory(currentSebiCategory);

                // Risk level based on SEBI category
                s.setRiskLevel(assignRiskLevel(currentSebiCategory, currentBroadCategory));

                // Enrich name-derived fields (planType, optionType, fundType)
                enrichFromName(s);

                result.schemes.add(s);

            } catch (Exception e) {
                log.warn("Skipping malformed line {}: {}", lineNo, e.getMessage());
            }
        }

        log.info("Parsed " + result.schemes.size() + " schemes from NAVAll.txt");
        return result;
    }

    // ── Parse broad category + SEBI category from header text ────────────

    private String[] parseBroadAndSebi(String raw) {
        String broad;
        String sebi;

        if (raw.startsWith("Equity Scheme")) {
            broad = "EQUITY";
            sebi = raw.contains(" - ") ? raw.substring(raw.indexOf(" - ") + 3).trim() : raw;
        } else if (raw.startsWith("Debt Scheme")) {
            broad = "DEBT";
            sebi = raw.contains(" - ") ? raw.substring(raw.indexOf(" - ") + 3).trim() : raw;
        } else if (raw.startsWith("Hybrid Scheme")) {
            broad = "HYBRID";
            sebi = raw.contains(" - ") ? raw.substring(raw.indexOf(" - ") + 3).trim() : raw;
        } else if (raw.startsWith("Solution Oriented")) {
            broad = "SOLUTION";
            sebi = raw.contains(" - ") ? raw.substring(raw.indexOf(" - ") + 3).trim() : raw;
        } else if (raw.startsWith("Other Scheme")) {
            broad = "OTHER";
            sebi = raw.contains(" - ") ? raw.substring(raw.indexOf(" - ") + 3).trim() : raw;
        } else if (raw.equalsIgnoreCase("ELSS")) {
            broad = "EQUITY";
            sebi = "ELSS";
        } else {
            broad = mapLegacyBroad(raw);
            sebi = raw;
        }

        return new String[]{broad, sebi};
    }

    private String mapLegacyBroad(String raw) {
        String upper = raw.toUpperCase();
        if (upper.contains("GILT"))         return "DEBT";
        if (upper.contains("MONEY MARKET")) return "DEBT";
        if (upper.contains("INCOME"))       return "DEBT";
        if (upper.contains("GROWTH"))       return "EQUITY";
        return "OTHER";
    }

    // ── Risk Level Assignment (1-6 scale) ─────────────────────────────────

    public static int assignRiskLevel(String sebiCategory, String broadCategory) {
        if (sebiCategory == null) return 4;
        String cat = sebiCategory.toLowerCase();

        if (cat.contains("overnight"))       return 1;
        if (cat.contains("liquid"))          return 2;
        if (cat.contains("ultra short"))     return 2;
        if (cat.contains("low duration"))          return 3;
        if (cat.contains("money market"))          return 3;
        if (cat.contains("short duration"))        return 3;
        if (cat.contains("floater"))               return 3;
        if (cat.contains("banking and psu") || cat.contains("banking & psu")) return 3;
        if (cat.contains("medium duration") || cat.contains("medium to long")) return 4;
        if (cat.contains("corporate bond"))        return 4;
        if (cat.contains("dynamic bond"))          return 4;
        if (cat.contains("gilt"))                  return 4;
        if (cat.contains("long duration"))         return 4;
        if (cat.contains("credit risk"))           return 6;
        if (cat.contains("large cap") && !cat.contains("mid")) return 4;
        if (cat.contains("large & mid"))           return 5;
        if (cat.contains("mid cap"))               return 5;
        if (cat.contains("flexi cap"))             return 5;
        if (cat.contains("multi cap"))             return 5;
        if (cat.contains("value") || cat.contains("contra"))   return 5;
        if (cat.contains("dividend yield"))        return 5;
        if (cat.contains("focused"))               return 5;
        if (cat.contains("elss"))                  return 5;
        if (cat.contains("small cap"))             return 6;
        if (cat.contains("sectoral") || cat.contains("thematic")) return 6;
        if (cat.contains("conservative hybrid"))   return 3;
        if (cat.contains("balanced hybrid"))       return 4;
        if (cat.contains("aggressive hybrid"))     return 5;
        if (cat.contains("balanced advantage") || cat.contains("dynamic asset")) return 5;
        if (cat.contains("multi asset"))           return 5;
        if (cat.contains("arbitrage"))             return 3;
        if (cat.contains("equity savings"))        return 4;
        if (cat.contains("retirement") || cat.contains("children")) return 5;
        if (cat.contains("index") || cat.contains("etf") || cat.contains("fof")) return 4;
        if (cat.contains("gold"))                  return 4;

        // Broad category fallback
        if (broadCategory != null) {
            String b = broadCategory.toUpperCase();
            if (b.equals("DEBT"))   return 2;
            if (b.equals("HYBRID")) return 4;
            if (b.equals("EQUITY")) return 5;
        }

        return 4;
    }

    // ── Enrich scheme from parsed name ─────────────────────────────────────

    private void enrichFromName(Scheme s) {
        if (s.getSchemeName() == null) return;
        String name = s.getSchemeName().toUpperCase();

        // Plan Type
        if (name.contains("DIRECT"))
            s.setPlanType("DIRECT");
        else
            s.setPlanType("REGULAR");

        // Option Type
        if (name.contains("IDCW") && name.contains("REINVEST"))
            s.setOptionType("IDCW_REINVESTMENT");
        else if (name.contains("IDCW") || name.contains("DIVIDEND"))
            s.setOptionType("IDCW_PAYOUT");
        else
            s.setOptionType("GROWTH");

        // Fund Type
        if (name.contains("CLOSE ENDED") || name.contains("CLOSE-ENDED"))
            s.setFundType("CLOSE_ENDED");
        else if (name.contains("INTERVAL"))
            s.setFundType("INTERVAL");
        else
            s.setFundType("OPEN_ENDED");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String blank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private boolean isValidNav(String navStr) {
        if (navStr == null || navStr.equalsIgnoreCase("N.A.") || navStr.isBlank())
            return false;
        try {
            new BigDecimal(navStr);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
