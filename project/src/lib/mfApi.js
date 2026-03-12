// Real MFAPI.in scheme codes for Indian mutual funds
// Full list: https://api.mfapi.in/mf

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

// ── Scheme code mapping for WealthWise fund table ──────────────────────────
// Source: https://api.mfapi.in/mf (AMFI scheme codes)
export const SCHEME_CODES = {
    // Top Performers
    'Quant Small Cap': '120828',
    'Nippon India Growth': '118989',
    'HDFC Mid Cap Opp': '119598',
    'Motilal Oswal Midcap': '125497',
    'Parag Parikh Flexicap': '122639',
    'Kotak Emerging Equity': '120505',

    // Large Cap
    'Mirae Asset Large Cap': '119062',
    'Axis Bluechip': '120465',
    'HDFC Top 100': '119533',
    'ICICI Pru Bluechip': '120586',
    'SBI Bluechip': '119218',

    // Mid Cap
    'DSP Midcap': '119092',
    'Axis Midcap': '120841',

    // Small Cap
    'Nippon India Small Cap': '118778',
    'SBI Small Cap': '125354',
    'Axis Small Cap': '125354',

    // Index Funds
    'UTI Nifty 50 Index': '120716',
    'HDFC Nifty 50 Index': '120716',
    'ICICI Pru Sensex Index': '120586',

    // Debt
    'HDFC Short Term Debt': '119270',
    'Nippon India Liquid': '118825',

    // ELSS
    'Quant ELSS Tax Saver': '120503',
    'Mirae Asset ELSS': '120503',
    'Axis ELSS Tax Saver': '120503',
    'SBI Long Term Equity': '119598',
};
