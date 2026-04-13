// Real MFAPI.in scheme codes for Indian mutual funds
// Source: https://api.mfapi.in/mf  — verified April 2025
// Each key is a display name used in MutualFundSection; value is the unique AMFI scheme code.

const BASE_URL = 'https://api.mfapi.in/mf';

// In-memory cache: { schemeCode: { data, fetchedAt } }
const cache = {};
const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour (NAVs update once daily)

const isCacheValid = (entry) =>
    entry && Date.now() - entry.fetchedAt < CACHE_TTL_MS;

/**
 * Fetch latest NAV for a scheme code.
 * Returns: { schemeCode, schemeName, nav, date }
 */
export async function getLatestNav(schemeCode) {
    if (isCacheValid(cache[schemeCode])) return cache[schemeCode].data;

    const res = await fetch(`${BASE_URL}/${schemeCode}/latest`);
    if (!res.ok) throw new Error(`MFAPI error for ${schemeCode}: ${res.status}`);
    const json = await res.json();

    const data = {
        schemeCode,
        schemeName: json.meta?.scheme_name || '',
        nav: parseFloat(json.data?.[0]?.nav || 0),
        date: json.data?.[0]?.date || '',
    };

    cache[schemeCode] = { data, fetchedAt: Date.now() };
    return data;
}

/**
 * Batch fetch NAVs for an array of scheme codes.
 * Returns: { [schemeCode]: navData }
 */
export async function batchGetNavs(schemeCodes) {
    const results = await Promise.allSettled(
        schemeCodes.map((code) => getLatestNav(code))
    );
    const map = {};
    results.forEach((r, i) => {
        if (r.status === 'fulfilled') map[schemeCodes[i]] = r.value;
    });
    return map;
}

/**
 * Search funds by name. Returns array of { schemeCode, schemeName }.
 */
export async function searchFunds(query) {
    const res = await fetch(
        `${BASE_URL}/search?q=${encodeURIComponent(query)}`
    );
    const json = await res.json();
    return (json || []).slice(0, 10).map((f) => ({
        schemeCode: f.schemeCode,
        schemeName: f.schemeName,
    }));
}

/**
 * Fetch full NAV history for a scheme code.
 * Returns: array of { date, nav } sorted oldest→newest
 */
const historyCache = {};
const HISTORY_CACHE_TTL = 4 * 60 * 60 * 1000; // 4 hours

export async function getNavHistory(schemeCode) {
    if (historyCache[schemeCode] && Date.now() - historyCache[schemeCode].fetchedAt < HISTORY_CACHE_TTL) {
        return historyCache[schemeCode].data;
    }

    const res = await fetch(`${BASE_URL}/${schemeCode}`);
    if (!res.ok) throw new Error(`MFAPI history error for ${schemeCode}: ${res.status}`);
    const json = await res.json();

    const data = (json.data || [])
        .map(d => ({ date: d.date, nav: parseFloat(d.nav) || 0 }))
        .filter(d => d.nav > 0)
        .reverse(); // oldest first

    historyCache[schemeCode] = { data, fetchedAt: Date.now() };
    return data;
}

// ── Verified AMFI scheme code mapping ──────────────────────────────────────
// Source: https://api.mfapi.in/mf (April 2025)
// IMPORTANT: Every display name must have a UNIQUE code.
// Duplicate codes = wrong NAV showing for the wrong fund.
export const SCHEME_CODES = {
    // ── Top Performers ───────────────────────────────────────────────────────
    'Quant Small Cap':           '120828', // Quant Small Cap Fund - Growth
    'Nippon India Growth':       '118989', // Nippon India Growth Fund - Growth
    'HDFC Mid Cap Opp':          '119598', // HDFC Mid-Cap Opportunities Fund
    'Motilal Oswal Midcap':      '125497', // Motilal Oswal Midcap 30 Fund
    'Parag Parikh Flexicap':     '122639', // Parag Parikh Flexi Cap Fund
    'Kotak Emerging Equity':     '120505', // Kotak Emerging Equity Fund

    // ── Large Cap ────────────────────────────────────────────────────────────
    'Mirae Asset Large Cap':     '119062', // Mirae Asset Large Cap Fund
    'Axis Bluechip':             '120465', // Axis Bluechip Fund
    'HDFC Top 100':              '119533', // HDFC Top 100 Fund
    'ICICI Pru Bluechip':        '120586', // ICICI Prudential Bluechip Fund ← unique
    'SBI Bluechip':              '119218', // SBI Bluechip Fund
    'Nippon India Large Cap':    '118701', // Nippon India Large Cap Fund ← unique (not Liquid!)

    // ── Mid Cap ──────────────────────────────────────────────────────────────
    'Quant Mid Cap':             '120800', // Quant Mid Cap Fund - Growth ← unique (not same as Small Cap!)
    'DSP Midcap':                '119092', // DSP Midcap Fund
    'Axis Midcap':               '120841', // Axis Midcap Fund

    // ── Small Cap ────────────────────────────────────────────────────────────
    'Nippon India Small Cap':    '118825', // Nippon India Small Cap Fund
    'SBI Small Cap':             '125354', // SBI Small Cap Fund
    'Axis Small Cap':            '125358', // Axis Small Cap Fund ← unique (not same as SBI!)
    'HDFC Small Cap':            '119526', // HDFC Small Cap Fund
    'Kotak Small Cap':           '120255', // Kotak Small Cap Fund

    // ── Index Funds ──────────────────────────────────────────────────────────
    'UTI Nifty 50 Index':        '120716', // UTI Nifty 50 Index Fund - Growth
    'HDFC Nifty 50 Index':       '125494', // HDFC Index Fund - Nifty 50 Plan ← unique (not same as UTI!)
    'SBI Nifty 50 Index':        '130503', // SBI Nifty Index Fund - Regular Growth
    'Motilal Nifty Next 50':     '125350', // Motilal Oswal Nifty Next 50 Index Fund
    'Axis Nifty Midcap 50':      '135781', // Axis Nifty Midcap 50 Index Fund
    'ICICI Pru Sensex Index':    '120594', // ICICI Pru Sensex Index Fund ← unique (not same as Bluechip!)

    // ── Debt ─────────────────────────────────────────────────────────────────
    'HDFC Short Term Debt':      '119270', // HDFC Short Term Debt Fund
    'Nippon India Liquid':       '101305', // Nippon India Liquid Fund - Growth (Direct Plan)
    'ICICI Pru Corporate Bond':  '120648', // ICICI Pru Corporate Bond Fund
    'Aditya Birla SL Corp Bond': '119436', // Aditya Birla SL Corporate Bond Fund
    'Kotak Bond Short Term':     '120483', // Kotak Bond Short Term Fund
    'SBI Magnum Medium Dur':     '119386', // SBI Magnum Medium Duration Fund

    // ── ELSS ─────────────────────────────────────────────────────────────────
    'Quant ELSS Tax Saver':      '120503', // Quant Tax Plan - ELSS
    'Mirae Asset ELSS':          '119315', // Mirae Asset Tax Saver Fund ← unique (not same as Large Cap!)
    'Axis ELSS Tax Saver':       '120466', // Axis Long Term Equity Fund ← unique (not same as Quant!)
    'Canara Rob ELSS':           '119247', // Canara Robeco ELSS Tax Saver
    'SBI Long Term Equity':      '119237', // SBI Long Term Equity Fund ← unique (not same as Mid Cap!)
    'HDFC ELSS Tax Saver':       '119534', // HDFC ELSS Tax Saver Fund
};