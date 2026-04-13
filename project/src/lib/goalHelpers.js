// ── Goal type definitions ─────────────────────────────────────────────────
export const GOAL_TYPES = [
  { type: 'HOUSE',     icon: '🏠', label: 'House Purchase',  inflationRate: 0.07,  suggestedYears: 10, defaultPriority: 'MEDIUM', expectedReturn: 0.12 },
  { type: 'EDUCATION', icon: '🎓', label: 'Child Education', inflationRate: 0.11,  suggestedYears: 15, defaultPriority: 'HIGH',   expectedReturn: 0.12 },
  { type: 'MARRIAGE',  icon: '💍', label: 'Marriage',        inflationRate: 0.08,  suggestedYears: 20, defaultPriority: 'HIGH',   expectedReturn: 0.12 },
  { type: 'RETIREMENT',icon: '🏖️', label: 'Retirement',     inflationRate: 0.06,  suggestedYears: 25, defaultPriority: 'HIGH',   expectedReturn: 0.11 },
  { type: 'CAR',       icon: '🚗', label: 'Car Purchase',    inflationRate: 0.06,  suggestedYears: 5,  defaultPriority: 'MEDIUM', expectedReturn: 0.10 },
  { type: 'VACATION',  icon: '✈️', label: 'Vacation',        inflationRate: 0.06,  suggestedYears: 2,  defaultPriority: 'LOW',    expectedReturn: 0.08 },
  { type: 'EMERGENCY', icon: '🏥', label: 'Emergency Fund',  inflationRate: 0.06,  suggestedYears: 1,  defaultPriority: 'HIGH',   expectedReturn: 0.07 },
  { type: 'CUSTOM',    icon: '💰', label: 'Custom Goal',     inflationRate: 0.06,  suggestedYears: 5,  defaultPriority: 'MEDIUM', expectedReturn: 0.12 },
];

// ── Asset allocation by time horizon ──────────────────────────────────────
export function getAllocationSuggestion(years) {
  if (years > 10) return { equity: 80, debt: 20, label: 'Aggressive Growth' };
  if (years > 5)  return { equity: 60, debt: 40, label: 'Balanced Growth' };
  if (years > 3)  return { equity: 40, debt: 60, label: 'Conservative Growth' };
  if (years > 1)  return { equity: 20, debt: 80, label: 'Capital Preservation' };
  return           { equity: 0,  debt: 100, label: 'Liquid / Debt Only' };
}

// ── Compound future value: PV × (1 + rate)^years ─────────────────────────
export function futureValue(pv, rate, years) {
  if (!pv || !years || years <= 0) return pv || 0;
  return pv * Math.pow(1 + rate, years);
}

// ── Years between today and a date string ────────────────────────────────
export function yearsUntil(dateStr) {
  if (!dateStr) return 0;
  const ms = new Date(dateStr) - new Date();
  return Math.max(ms / (365.25 * 24 * 3600 * 1000), 0);
}

// ── Recommended SIP: FV × r / ((1+r)^n − 1) ─────────────────────────────
export function recommendedSIP(fv, annualRate, years) {
  const r = annualRate / 12;
  const n = Math.round(years * 12);
  if (n <= 0 || r <= 0) return fv || 0;
  return (fv * r) / (Math.pow(1 + r, n) - 1);
}

// ── Currency formatter ───────────────────────────────────────────────────
export function formatINR(val) {
  if (val === null || val === undefined) return '—';
  const num = parseFloat(val);
  if (isNaN(num)) return '—';
  if (Math.abs(num) >= 10000000) return '₹' + (num / 10000000).toFixed(2) + ' Cr';
  if (Math.abs(num) >= 100000)   return '₹' + (num / 100000).toFixed(2) + ' L';
  if (Math.abs(num) >= 1000)     return '₹' + (num / 1000).toFixed(1) + 'K';
  return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}
