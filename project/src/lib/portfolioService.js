import { supabase } from './supabaseClient';
import { getLatestNav } from './mfApi';

const PURCHASE_TYPES = new Set([
  'PURCHASE_LUMPSUM',
  'PURCHASE_SIP',
  'SWITCH_IN',
  'STP_IN',
  'DIVIDEND_REINVEST',
]);

const REDEMPTION_TYPES = new Set([
  'REDEMPTION',
  'SWITCH_OUT',
  'STP_OUT',
  'SWP',
]);

const STAMP_DUTY_RATE = 0.00005;

const toNumber = (value, fallback = 0) => {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const round = (value, digits = 2) => Number(toNumber(value).toFixed(digits));

const makeTransactionRef = () =>
  `WW${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}${Math.floor(
    Math.random() * 10000
  )
    .toString()
    .padStart(4, '0')}`;

const normalizeProfile = (row) => {
  if (!row) return null;
  return {
    id: row.id,
    authUserId: row.auth_user_id,
    fullName: row.full_name ?? '',
    email: row.email ?? '',
    phone: row.phone ?? '',
    currency: row.currency ?? 'INR',
    panCard: row.pan_card ?? '',
    riskProfile: row.risk_profile ?? 'MODERATE',
    createdAt: row.created_at ?? null,
  };
};

function deriveCategory(schemeName = '') {
  const name = schemeName.toLowerCase();

  if (name.includes('liquid') || name.includes('overnight')) {
    return { broadCategory: 'DEBT', sebiCategory: 'Liquid Fund', riskLevel: 1 };
  }
  if (name.includes('debt') || name.includes('bond') || name.includes('gilt') || name.includes('income')) {
    return { broadCategory: 'DEBT', sebiCategory: 'Debt Fund', riskLevel: 2 };
  }
  if (name.includes('hybrid') || name.includes('balanced') || name.includes('aggressive hybrid')) {
    return { broadCategory: 'HYBRID', sebiCategory: 'Hybrid Fund', riskLevel: 4 };
  }
  if (name.includes('elss') || name.includes('tax saver')) {
    return { broadCategory: 'EQUITY', sebiCategory: 'ELSS', riskLevel: 5 };
  }
  if (name.includes('small cap') || name.includes('sectoral') || name.includes('thematic')) {
    return { broadCategory: 'EQUITY', sebiCategory: 'Small Cap Fund', riskLevel: 6 };
  }
  if (name.includes('mid cap')) {
    return { broadCategory: 'EQUITY', sebiCategory: 'Mid Cap Fund', riskLevel: 5 };
  }
  if (name.includes('large cap') || name.includes('bluechip')) {
    return { broadCategory: 'EQUITY', sebiCategory: 'Large Cap Fund', riskLevel: 4 };
  }
  if (name.includes('index') || name.includes('nifty') || name.includes('sensex') || name.includes('flexi')) {
    return { broadCategory: 'EQUITY', sebiCategory: 'Equity Fund', riskLevel: 5 };
  }

  return { broadCategory: 'OTHER', sebiCategory: 'Other', riskLevel: 3 };
}

function addMonths(dateString, months) {
  const date = new Date(`${dateString}T00:00:00`);
  date.setMonth(date.getMonth() + months);
  return date.toISOString().slice(0, 10);
}

async function fetchHistoricalNavs(amfiCode) {
  const response = await fetch(`https://api.mfapi.in/mf/${amfiCode}`);
  if (!response.ok) {
    throw new Error(`Could not fetch NAV history for ${amfiCode}`);
  }

  const json = await response.json();
  const rows = Array.isArray(json.data) ? json.data : [];
  const navs = rows
    .map((row) => {
      const [day, month, year] = String(row.date ?? '').split('-');
      const isoDate = `${year}-${month}-${day}`;
      return { date: isoDate, nav: toNumber(row.nav) };
    })
    .filter((row) => row.date && row.nav > 0)
    .sort((a, b) => a.date.localeCompare(b.date));

  return {
    schemeName: json.meta?.scheme_name ?? '',
    navs,
  };
}

function getNavForDate(navs, targetDate) {
  let fallback = null;
  for (const row of navs) {
    if (row.date <= targetDate) fallback = row;
    if (row.date === targetDate) return row;
    if (row.date > targetDate) break;
  }
  return fallback;
}

async function ensureScheme(amfiCode, schemeNameHint = '') {
  const { data: existing, error } = await supabase
    .from('scheme_master')
    .select('*')
    .eq('amfi_code', amfiCode)
    .maybeSingle();

  if (error) throw error;

  let latestNav = null;
  let latestDate = null;
  let schemeName = existing?.scheme_name ?? schemeNameHint;

  try {
    const latest = await getLatestNav(amfiCode);
    latestNav = latest.nav;
    latestDate = latest.date;
    schemeName = latest.schemeName || schemeName;
  } catch {
    latestNav = existing?.last_nav ?? null;
    latestDate = existing?.last_nav_date ?? null;
  }

  const derived = deriveCategory(schemeName);
  const payload = {
    amfi_code: String(amfiCode),
    scheme_name: schemeName || existing?.scheme_name || `Scheme ${amfiCode}`,
    broad_category: existing?.broad_category ?? derived.broadCategory,
    sebi_category: existing?.sebi_category ?? derived.sebiCategory,
    risk_level: existing?.risk_level ?? derived.riskLevel,
    plan_type: existing?.plan_type ?? (schemeName.toLowerCase().includes('direct') ? 'DIRECT' : 'REGULAR'),
    last_nav: latestNav ?? existing?.last_nav ?? null,
    last_nav_date: latestDate ?? existing?.last_nav_date ?? null,
    is_active: true,
  };

  const { data: saved, error: upsertError } = await supabase
    .from('scheme_master')
    .upsert(payload, { onConflict: 'amfi_code' })
    .select()
    .single();

  if (upsertError) throw upsertError;
  return saved;
}

async function createInvestmentLot(transaction, schemeRow, amount, units, folioNumber) {
  const schemeName = transaction.scheme_name || schemeRow?.scheme_name || `Scheme ${transaction.scheme_amfi_code}`;
  const isElss = schemeName.toUpperCase().includes('ELSS');
  const lockUntil = isElss ? addMonths(transaction.transaction_date, 36) : null;

  const { error } = await supabase.from('investment_lots').insert({
    transaction_id: transaction.id,
    user_id: transaction.user_id,
    scheme_amfi_code: transaction.scheme_amfi_code,
    scheme_name: schemeName,
    folio_number: folioNumber,
    purchase_date: transaction.transaction_date,
    purchase_nav: round(transaction.nav, 4),
    purchase_amount: round(amount, 4),
    units_original: round(units, 6),
    units_remaining: round(units, 6),
    is_elss: isElss,
    elss_lock_until: lockUntil,
  });

  if (error) throw error;
}

async function consumeLotsFifo(userId, amfiCode, folioNumber, unitsToConsume) {
  if (unitsToConsume <= 0) return;

  let query = supabase
    .from('investment_lots')
    .select('*')
    .eq('user_id', userId)
    .eq('scheme_amfi_code', amfiCode)
    .order('purchase_date', { ascending: true })
    .order('created_at', { ascending: true });

  if (folioNumber) query = query.eq('folio_number', folioNumber);

  const { data: lots, error } = await query;
  if (error) throw error;

  let remaining = unitsToConsume;
  for (const lot of lots ?? []) {
    if (remaining <= 0) break;
    const available = toNumber(lot.units_remaining);
    if (available <= 0) continue;

    const consume = Math.min(available, remaining);
    const nextRemaining = round(available - consume, 6);
    remaining = round(remaining - consume, 6);

    const { error: updateError } = await supabase
      .from('investment_lots')
      .update({ units_remaining: nextRemaining })
      .eq('id', lot.id);

    if (updateError) throw updateError;
  }

  if (remaining > 0) {
    throw new Error(`Insufficient units. Missing ${remaining.toFixed(6)} units.`);
  }
}

function buildEffectiveTransactions(transactions) {
  const reversedIds = new Set(
    (transactions ?? [])
      .filter((txn) => txn.transaction_type === 'REVERSAL' && txn.reversal_of != null)
      .map((txn) => txn.reversal_of)
  );

  return (transactions ?? []).filter(
    (txn) => txn.transaction_type !== 'REVERSAL' && !reversedIds.has(txn.id)
  );
}

function buildHoldingsFromTransactions(transactions, schemesByCode) {
  const effective = buildEffectiveTransactions(transactions).sort((a, b) => {
    const left = `${a.transaction_date}|${a.created_at ?? ''}|${a.id}`;
    const right = `${b.transaction_date}|${b.created_at ?? ''}|${b.id}`;
    return left.localeCompare(right);
  });

  const lots = [];

  for (const txn of effective) {
    const units = toNumber(txn.units);
    const amount = toNumber(txn.amount);

    if (PURCHASE_TYPES.has(txn.transaction_type)) {
      lots.push({
        schemeAmfiCode: txn.scheme_amfi_code,
        schemeName: txn.scheme_name,
        folioNumber: txn.folio_number,
        purchaseAmount: amount,
        unitsOriginal: units,
        unitsRemaining: units,
        transactionDate: txn.transaction_date,
      });
      continue;
    }

    if (REDEMPTION_TYPES.has(txn.transaction_type)) {
      let remaining = units;
      const relevantLots = lots.filter(
        (lot) =>
          lot.schemeAmfiCode === txn.scheme_amfi_code &&
          (!txn.folio_number || lot.folioNumber === txn.folio_number)
      );

      for (const lot of relevantLots) {
        if (remaining <= 0) break;
        const consume = Math.min(lot.unitsRemaining, remaining);
        lot.unitsRemaining = round(lot.unitsRemaining - consume, 6);
        remaining = round(remaining - consume, 6);
      }
    }
  }

  const grouped = new Map();

  for (const lot of lots) {
    if (lot.unitsRemaining <= 0) continue;
    const key = `${lot.schemeAmfiCode}|${lot.folioNumber ?? ''}`;
    const scheme = schemesByCode.get(lot.schemeAmfiCode);
    const currentNav = toNumber(scheme?.last_nav);
    const investedContribution =
      lot.unitsOriginal > 0 ? (lot.purchaseAmount * lot.unitsRemaining) / lot.unitsOriginal : 0;

    if (!grouped.has(key)) {
      grouped.set(key, {
        schemeAmfiCode: lot.schemeAmfiCode,
        schemeName: lot.schemeName,
        folioNumber: lot.folioNumber,
        investedAmount: 0,
        units: 0,
        currentNav,
        currentValue: 0,
        gainLoss: 0,
        absoluteReturnPct: null,
        broadCategory: scheme?.broad_category ?? 'Other',
        schemeType: scheme?.sebi_category ?? '',
        riskLevel: scheme?.risk_level ?? null,
        planType: scheme?.plan_type ?? '',
        lastNavDate: scheme?.last_nav_date ?? null,
      });
    }

    const entry = grouped.get(key);
    entry.investedAmount += investedContribution;
    entry.units += lot.unitsRemaining;
  }

  return Array.from(grouped.values()).map((holding) => {
    holding.investedAmount = round(holding.investedAmount, 2);
    holding.units = round(holding.units, 6);
    holding.currentValue = round(holding.units * toNumber(holding.currentNav), 2);
    holding.gainLoss = round(holding.currentValue - holding.investedAmount, 2);
    holding.absoluteReturnPct =
      holding.investedAmount > 0
        ? round(((holding.currentValue - holding.investedAmount) / holding.investedAmount) * 100, 2)
        : null;
    return holding;
  });
}

function absoluteReturn(invested, current) {
  if (!invested) return null;
  return round(((current - invested) / invested) * 100, 2);
}

function xnpv(rate, cashFlows) {
  const baseDate = new Date(`${cashFlows[0].date}T00:00:00`);
  return cashFlows.reduce((sum, flow) => {
    const flowDate = new Date(`${flow.date}T00:00:00`);
    const years = (flowDate - baseDate) / (1000 * 60 * 60 * 24 * 365.25);
    return sum + flow.amount / (1 + rate) ** years;
  }, 0);
}

function xirr(cashFlows) {
  if (cashFlows.length < 2) return null;
  const hasPositive = cashFlows.some((flow) => flow.amount > 0);
  const hasNegative = cashFlows.some((flow) => flow.amount < 0);
  if (!hasPositive || !hasNegative) return null;

  let low = -0.9999;
  let high = 10;
  let guess = 0.1;

  for (let i = 0; i < 100; i += 1) {
    const value = xnpv(guess, cashFlows);
    const delta = 0.000001;
    const derivative = (xnpv(guess + delta, cashFlows) - value) / delta;
    if (Math.abs(derivative) < 1e-10) break;
    const next = guess - value / derivative;
    if (!Number.isFinite(next) || next <= low || next >= high) break;
    if (Math.abs(next - guess) < 1e-7) return round(next * 100, 2);
    guess = next;
  }

  let npvLow = xnpv(low, cashFlows);
  let npvHigh = xnpv(high, cashFlows);
  if (npvLow * npvHigh > 0) return null;

  for (let i = 0; i < 200; i += 1) {
    const mid = (low + high) / 2;
    const npvMid = xnpv(mid, cashFlows);
    if (Math.abs(npvMid) < 1e-7) return round(mid * 100, 2);
    if (npvLow * npvMid < 0) {
      high = mid;
      npvHigh = npvMid;
    } else {
      low = mid;
      npvLow = npvMid;
    }
  }

  return round(((low + high) / 2) * 100, 2);
}

function buildGrowthTimeline(effectiveTransactions, holdings) {
  if (!effectiveTransactions.length) return [];

  const purchases = effectiveTransactions
    .filter((txn) => PURCHASE_TYPES.has(txn.transaction_type))
    .sort((a, b) => a.transaction_date.localeCompare(b.transaction_date));

  if (!purchases.length) return [];

  const totalInvested = holdings.reduce((sum, holding) => sum + holding.investedAmount, 0);
  const totalCurrent = holdings.reduce((sum, holding) => sum + holding.currentValue, 0);
  const ratio = totalInvested > 0 ? totalCurrent / totalInvested : 1;

  const monthly = new Map();
  for (const txn of purchases) {
    const key = txn.transaction_date.slice(0, 7);
    monthly.set(key, (monthly.get(key) ?? 0) + toNumber(txn.amount));
  }

  const firstMonth = `${purchases[0].transaction_date.slice(0, 7)}-01`;
  const now = new Date();
  let cursor = new Date(`${firstMonth}T00:00:00`);
  let cumulative = 0;
  const timeline = [];

  while (cursor <= now) {
    const key = cursor.toISOString().slice(0, 7);
    cumulative += monthly.get(key) ?? 0;
    timeline.push({
      month: cursor.toLocaleString('en-US', { month: 'short', year: '2-digit' }).replace(' ', " '"),
      invested: round(cumulative, 2),
      value: round(cumulative * ratio, 2),
    });
    cursor.setMonth(cursor.getMonth() + 1);
  }

  return timeline;
}

function buildRiskAnalytics(holdings, userRiskProfile = 'MODERATE') {
  const totalCurrentValue = holdings.reduce((sum, holding) => sum + holding.currentValue, 0);
  let weightedRiskSum = 0;
  let equityCount = 0;
  let debtCount = 0;
  let hybridCount = 0;
  let largeCapCount = 0;
  let midCapCount = 0;
  let smallCapCount = 0;
  const uniqueAmcs = new Set();

  for (const holding of holdings) {
    const category = String(holding.broadCategory ?? 'OTHER').toLowerCase();
    const schemeType = String(holding.schemeType ?? '').toLowerCase();
    const schemeName = String(holding.schemeName ?? '');
    const fundRisk = toNumber(holding.riskLevel) || 3;

    weightedRiskSum += fundRisk * holding.currentValue;
    if (category.includes('equity')) equityCount += 1;
    if (category.includes('debt')) debtCount += 1;
    if (category.includes('hybrid')) hybridCount += 1;
    if (schemeType.includes('large')) largeCapCount += 1;
    if (schemeType.includes('mid')) midCapCount += 1;
    if (schemeType.includes('small')) smallCapCount += 1;

    const words = schemeName.split(' ').filter(Boolean);
    if (words.length >= 2) uniqueAmcs.add(`${words[0]} ${words[1]}`);
  }

  const portfolioRiskScore = totalCurrentValue > 0 ? weightedRiskSum / totalCurrentValue : 0;
  let diversificationScore = 1;
  if (equityCount && debtCount && hybridCount) diversificationScore += 2;
  else if (equityCount && debtCount) diversificationScore += 1.5;
  else if (equityCount) diversificationScore += 0.5;
  if (holdings.length >= 3 && holdings.length <= 8) diversificationScore += 2;
  else if (holdings.length >= 9) diversificationScore += 1;
  if (largeCapCount && midCapCount && smallCapCount) diversificationScore += 2;
  else if (largeCapCount && midCapCount) diversificationScore += 1.5;
  else if (largeCapCount) diversificationScore += 1;
  if (uniqueAmcs.size >= 3) diversificationScore += 2;
  else if (uniqueAmcs.size === 2) diversificationScore += 1;

  const volatilityPct = round(12.5 + portfolioRiskScore * 1.5, 2);
  const sharpeRatio = volatilityPct ? round((15 - 7) / volatilityPct, 2) : 0;
  const maxDrawdownPct = round(-1 * portfolioRiskScore * 4.5, 2);

  const toleranceScore =
    userRiskProfile === 'CONSERVATIVE' ? 2 : userRiskProfile === 'AGGRESSIVE' ? 5.5 : 3.5;

  let riskComparison = 'Your portfolio risk aligns well with your stated risk tolerance.';
  if (portfolioRiskScore > toleranceScore + 0.5) {
    riskComparison =
      'Your portfolio is more risky than your comfort level. Consider adding low-risk debt funds.';
  } else if (portfolioRiskScore < toleranceScore - 1) {
    riskComparison = 'You could take slightly more risk for potentially higher returns.';
  }

  const portfolioRiskLabel =
    portfolioRiskScore === 0
      ? 'N/A'
      : portfolioRiskScore <= 1.5
        ? 'Low'
        : portfolioRiskScore <= 2.5
          ? 'Low to Moderate'
          : portfolioRiskScore <= 3.5
            ? 'Moderate'
            : portfolioRiskScore <= 4.5
              ? 'Moderately High'
              : portfolioRiskScore <= 5.5
                ? 'High'
                : 'Very High';

  return {
    portfolioRiskScore: round(portfolioRiskScore, 2),
    portfolioRiskLabel,
    diversificationScore: round(diversificationScore, 1),
    volatilityPct,
    sharpeRatio,
    maxDrawdownPct,
    totalFunds: holdings.length,
    uniqueAmcs: uniqueAmcs.size,
    userRiskProfile,
    riskComparison,
  };
}

function buildSipAnalytics(effectiveTransactions, holdings) {
  const byScheme = new Map();
  for (const txn of effectiveTransactions) {
    const key = txn.scheme_amfi_code;
    if (!byScheme.has(key)) byScheme.set(key, []);
    byScheme.get(key).push(txn);
  }

  let activeSips = 0;
  let totalSipOutflow = 0;
  const analysis = [];

  for (const holding of holdings) {
    const txns = (byScheme.get(holding.schemeAmfiCode) ?? []).sort((a, b) =>
      a.transaction_date.localeCompare(b.transaction_date)
    );
    const sipTxns = txns.filter((txn) => txn.transaction_type === 'PURCHASE_SIP');
    if (!sipTxns.length) continue;

    activeSips += 1;
    totalSipOutflow += toNumber(sipTxns[sipTxns.length - 1].amount);

    const totalInvested = holding.investedAmount;
    const actualSipValue = holding.currentValue;
    const firstNav = toNumber(sipTxns[0].nav);
    const latestNav = holding.units > 0 ? actualSipValue / holding.units : 0;
    const lumpsumValue = firstNav > 0 ? (totalInvested / firstNav) * latestNav : 0;
    const difference = actualSipValue - lumpsumValue;

    analysis.push({
      fundName: holding.schemeName,
      sipValue: round(actualSipValue, 2),
      lumpsumValue: round(lumpsumValue, 2),
      difference: round(difference, 2),
      winner: difference >= 0 ? 'SIP Strategy' : 'Lumpsum Strategy',
    });
  }

  return {
    activeSips,
    totalSipOutflow: round(totalSipOutflow, 2),
    sipStreak: activeSips ? 'No Missed SIPs' : 'No Active SIPs',
    analysis,
  };
}

function buildOverlapAnalytics(holdings) {
  const nodes = holdings.map((holding) => ({
    id: holding.schemeAmfiCode,
    name: holding.schemeName,
    category: holding.schemeType || 'Unknown',
  }));

  const links = [];

  for (let i = 0; i < holdings.length; i += 1) {
    for (let j = i + 1; j < holdings.length; j += 1) {
      const first = holdings[i];
      const second = holdings[j];
      const typeA = String(first.schemeType ?? '');
      const typeB = String(second.schemeType ?? '');
      let overlapPct = 0;

      if (typeA && typeB && typeA === typeB) overlapPct = 40;
      else if (typeA.includes('Large Cap') && typeB.includes('Large Cap')) overlapPct = 55;
      else if (String(first.broadCategory).includes('EQUITY') && String(second.broadCategory).includes('EQUITY')) {
        overlapPct = 15;
      }

      if (overlapPct > 0) {
        links.push({
          source: first.schemeAmfiCode,
          target: second.schemeAmfiCode,
          overlapPct: round(overlapPct, 1),
        });
      }
    }
  }

  const averageOverlapPct =
    links.length > 0
      ? round(links.reduce((sum, link) => sum + link.overlapPct, 0) / links.length, 1)
      : 0;

  return { nodes, links, averageOverlapPct };
}

export async function getProfileByAuthUserId(authUserId) {
  const { data, error } = await supabase
    .from('users')
    .select('*')
    .eq('auth_user_id', authUserId)
    .maybeSingle();

  if (error) throw error;
  return normalizeProfile(data);
}

export async function ensureUserProfile(sessionUser, profileInput = {}) {
  const existing = await getProfileByAuthUserId(sessionUser.id);
  if (existing) return existing;

  const payload = {
    auth_user_id: sessionUser.id,
    full_name: profileInput.fullName || sessionUser.user_metadata?.full_name || sessionUser.email?.split('@')[0] || 'Investor',
    email: sessionUser.email,
    phone: profileInput.phone || null,
    currency: profileInput.currency || 'INR',
    pan_card: profileInput.panCard || null,
    risk_profile: profileInput.riskProfile || 'MODERATE',
  };

  const { data, error } = await supabase.from('users').insert(payload).select().single();
  if (error) throw error;
  return normalizeProfile(data);
}

export async function updateUserProfile(profileId, updates) {
  const payload = {
    full_name: updates.fullName,
    phone: updates.phone,
    pan_card: updates.panCard,
    currency: updates.currency,
  };

  const { data, error } = await supabase
    .from('users')
    .update(payload)
    .eq('id', profileId)
    .select()
    .single();

  if (error) throw error;
  return normalizeProfile(data);
}

export async function updateRiskProfile(profileId, riskProfile) {
  const { error } = await supabase
    .from('users')
    .update({ risk_profile: riskProfile })
    .eq('id', profileId);

  if (error) throw error;
}

export async function getTransactionsByUser(userId) {
  const { data, error } = await supabase
    .from('transactions')
    .select('*')
    .eq('user_id', userId)
    .order('transaction_date', { ascending: false })
    .order('created_at', { ascending: false });

  if (error) throw error;
  return data ?? [];
}

export async function createManualTransaction(userId, input) {
  const scheme = await ensureScheme(input.schemeAmfiCode, input.schemeName);

  let nav = toNumber(input.nav);
  if (!nav) {
    const latest = await getLatestNav(input.schemeAmfiCode);
    nav = toNumber(latest.nav);
  }
  if (!nav) throw new Error('NAV not available for this scheme.');

  const isPurchase = PURCHASE_TYPES.has(input.transactionType);
  const isRedemption = REDEMPTION_TYPES.has(input.transactionType);
  let amount = toNumber(input.amount, null);
  let units = toNumber(input.units, null);
  let stampDuty = null;

  if (isPurchase) {
    if (!amount || amount <= 0) throw new Error('Amount is required for purchase transactions.');
    if (!units || units <= 0) {
      stampDuty = round(amount * STAMP_DUTY_RATE, 4);
      units = round((amount - stampDuty) / nav, 6);
    }
  }

  if (isRedemption) {
    if ((!units || units <= 0) && amount > 0) units = round(amount / nav, 6);
    if ((!amount || amount <= 0) && units > 0) amount = round(units * nav, 4);
    if (!units || units <= 0) throw new Error('Units or amount is required for redemption.');
  }

  const existingTransactions = await getTransactionsByUser(userId);
  const folioNumber =
    input.folioNumber ||
    existingTransactions.find((txn) => txn.scheme_amfi_code === input.schemeAmfiCode)?.folio_number ||
    `WW${userId}${input.schemeAmfiCode}`;

  const derived = deriveCategory(scheme.scheme_name);

  const { data: saved, error } = await supabase
    .from('transactions')
    .insert({
      transaction_ref: makeTransactionRef(),
      user_id: userId,
      folio_number: folioNumber,
      scheme_amfi_code: input.schemeAmfiCode,
      scheme_name: scheme.scheme_name,
      transaction_type: input.transactionType,
      transaction_date: input.transactionDate,
      amount: amount == null ? null : round(amount, 4),
      units: units == null ? null : round(units, 6),
      nav: round(nav, 4),
      stamp_duty: stampDuty,
      source: 'MANUAL',
      notes: input.notes || null,
      category: scheme.broad_category || derived.broadCategory,
      risk: scheme.risk_level || derived.riskLevel,
    })
    .select()
    .single();

  if (error) throw error;

  if (isPurchase) {
    await createInvestmentLot(saved, scheme, amount, units, folioNumber);
  }

  if (isRedemption) {
    await consumeLotsFifo(userId, input.schemeAmfiCode, folioNumber, units);
  }

  return saved;
}

export async function createBulkSipTransactions(userId, input) {
  const { schemeName, navs } = await fetchHistoricalNavs(input.schemeAmfiCode);
  const scheme = await ensureScheme(input.schemeAmfiCode, input.schemeName || schemeName);
  const transactions = await getTransactionsByUser(userId);
  const folioNumber =
    input.folioNumber ||
    transactions.find((txn) => txn.scheme_amfi_code === input.schemeAmfiCode)?.folio_number ||
    `WW${userId}${input.schemeAmfiCode}`;

  const created = [];
  let amount = toNumber(input.amount);
  let currentDate = input.startDate;
  const endDate = input.endDate;
  const annualStepUpPct = toNumber(input.annualStepUpPct);

  while (currentDate <= endDate) {
    const navRow = getNavForDate(navs, currentDate);
    if (navRow?.nav) {
      const stampDuty = round(amount * STAMP_DUTY_RATE, 4);
      const units = round((amount - stampDuty) / navRow.nav, 6);
      const { data: saved, error } = await supabase
        .from('transactions')
        .insert({
          transaction_ref: makeTransactionRef(),
          user_id: userId,
          folio_number: folioNumber,
          scheme_amfi_code: input.schemeAmfiCode,
          scheme_name: scheme.scheme_name,
          transaction_type: 'PURCHASE_SIP',
          transaction_date: currentDate,
          amount: round(amount, 4),
          units,
          nav: round(navRow.nav, 4),
          stamp_duty: stampDuty,
          source: 'MANUAL_SIP_BULK',
          notes: 'Auto-generated SIP transaction',
          category: scheme.broad_category,
          risk: scheme.risk_level,
        })
        .select()
        .single();

      if (error) throw error;
      await createInvestmentLot(saved, scheme, amount, units, folioNumber);
      created.push(saved);
    }

    const nextDate = addMonths(currentDate, 1);
    if (annualStepUpPct > 0 && nextDate.slice(5, 7) === input.startDate.slice(5, 7)) {
      amount = round(amount * (1 + annualStepUpPct / 100), 2);
    }
    currentDate = nextDate;
  }

  return created;
}

export async function createReversalTransaction(userId, originalId) {
  const { data: original, error } = await supabase
    .from('transactions')
    .select('*')
    .eq('id', originalId)
    .eq('user_id', userId)
    .single();

  if (error) throw error;

  const { data: saved, error: insertError } = await supabase
    .from('transactions')
    .insert({
      transaction_ref: makeTransactionRef(),
      user_id: userId,
      folio_number: original.folio_number,
      scheme_amfi_code: original.scheme_amfi_code,
      scheme_name: original.scheme_name,
      transaction_type: 'REVERSAL',
      transaction_date: new Date().toISOString().slice(0, 10),
      amount: original.amount == null ? null : round(-1 * toNumber(original.amount), 4),
      units: original.units == null ? null : round(-1 * toNumber(original.units), 6),
      nav: original.nav,
      reversal_of: original.id,
      source: 'MANUAL',
      notes: `Reversal of ${original.transaction_ref}`,
      category: original.category,
      risk: original.risk,
    })
    .select()
    .single();

  if (insertError) throw insertError;
  return saved;
}

export async function getPortfolioReturns(userId) {
  const transactions = await getTransactionsByUser(userId);
  const effectiveTransactions = buildEffectiveTransactions(transactions);
  const schemeCodes = [...new Set(effectiveTransactions.map((txn) => txn.scheme_amfi_code).filter(Boolean))];

  let schemesByCode = new Map();
  if (schemeCodes.length) {
    const { data: schemes, error } = await supabase
      .from('scheme_master')
      .select('*')
      .in('amfi_code', schemeCodes);

    if (error) throw error;
    schemesByCode = new Map((schemes ?? []).map((scheme) => [scheme.amfi_code, scheme]));
  }

  const holdings = buildHoldingsFromTransactions(transactions, schemesByCode);
  const totalInvested = round(holdings.reduce((sum, holding) => sum + holding.investedAmount, 0), 2);
  const totalCurrentValue = round(holdings.reduce((sum, holding) => sum + holding.currentValue, 0), 2);
  const totalGainLoss = round(totalCurrentValue - totalInvested, 2);

  const cashFlows = [];
  for (const txn of effectiveTransactions) {
    if (PURCHASE_TYPES.has(txn.transaction_type)) {
      cashFlows.push({ date: txn.transaction_date, amount: -1 * toNumber(txn.amount) });
    } else if (REDEMPTION_TYPES.has(txn.transaction_type) || txn.transaction_type === 'DIVIDEND_PAYOUT') {
      cashFlows.push({ date: txn.transaction_date, amount: toNumber(txn.amount) });
    }
  }
  if (totalCurrentValue > 0) {
    cashFlows.push({ date: new Date().toISOString().slice(0, 10), amount: totalCurrentValue });
  }
  cashFlows.sort((a, b) => a.date.localeCompare(b.date));

  const categoryMap = new Map();
  for (const holding of holdings) {
    const key = holding.broadCategory || 'Other';
    const base = holding.currentValue || holding.investedAmount || 0;
    categoryMap.set(key, (categoryMap.get(key) ?? 0) + base);
  }

  return {
    totalInvested,
    totalCurrentValue,
    totalGainLoss,
    absoluteReturnPct: absoluteReturn(totalInvested, totalCurrentValue),
    xirrPct: xirr(cashFlows),
    holdings,
    transactionCount: transactions.length,
    growthTimeline: buildGrowthTimeline(effectiveTransactions, holdings),
    categoryBreakdown: Array.from(categoryMap.entries()).map(([category, value]) => ({
      category,
      value: round(value, 2),
    })),
  };
}

export async function getAnalyticsSnapshot(userId, userRiskProfile) {
  const portfolio = await getPortfolioReturns(userId);
  const effectiveTransactions = buildEffectiveTransactions(await getTransactionsByUser(userId));

  return {
    risk: buildRiskAnalytics(portfolio.holdings, userRiskProfile),
    sip: buildSipAnalytics(effectiveTransactions, portfolio.holdings),
    overlap: buildOverlapAnalytics(portfolio.holdings),
  };
}
