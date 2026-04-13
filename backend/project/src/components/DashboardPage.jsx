import { useState, useEffect, useCallback, useMemo } from 'react';
import { motion, AnimatePresence, animate, useMotionValue, useTransform } from 'framer-motion';
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, BarChart, Bar, LineChart, Line, CartesianGrid,
  Legend, ReferenceLine
} from 'recharts';
import {
  TrendingUp, TrendingDown, RefreshCw, Activity, Target, AlertCircle, Wifi,
  Settings, X, BarChart2, HelpCircle
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { getNavHistory } from '../lib/mfApi';
import './DashboardPage.css';

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════
function formatINR(val) {
  if (val === null || val === undefined) return '—';
  const num = parseFloat(val);
  if (isNaN(num)) return '—';
  return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}

function formatCurrency(val) {
  if (val === null || val === undefined) return '—';
  const num = parseFloat(val);
  if (isNaN(num)) return '—';
  if (Math.abs(num) >= 10000000) return '₹' + (num / 10000000).toFixed(2) + ' Cr';
  if (Math.abs(num) >= 100000) return '₹' + (num / 100000).toFixed(2) + ' L';
  if (Math.abs(num) >= 1000) return '₹' + (num / 1000).toFixed(1) + 'K';
  return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPct(val) {
  if (val === null || val === undefined) return '—';
  const num = parseFloat(val);
  if (isNaN(num)) return '—';
  return num.toFixed(2) + '%';
}

function formatPctSigned(val) {
  if (val === null || val === undefined) return '—';
  const num = parseFloat(val);
  if (isNaN(num)) return '—';
  return (num >= 0 ? '+' : '') + num.toFixed(2) + '%';
}

const MONTH_MAP = {
  JAN: 0, FEB: 1, MAR: 2, APR: 3, MAY: 4, JUN: 5,
  JUL: 6, AUG: 7, SEP: 8, SEPT: 8, OCT: 9, NOV: 10, DEC: 11,
};

function parseDate(str) {
  if (!str) return null;
  // Handle DD-MM-YYYY
  if (/^\d{2}-\d{2}-\d{4}$/.test(str)) {
    const [dd, mm, yyyy] = str.split('-');
    return new Date(yyyy, mm - 1, dd);
  }
  // Handle backend format: "APR '23", "MAY '23", "JAN '26" etc.
  // Accept both straight apostrophe (') and unicode smart quotes (\u2018\u2019)
  const tickMatch = str.match(/^([A-Za-z]+)\s*['\u2018\u2019](\d{2})$/);
  if (tickMatch) {
    const mon = MONTH_MAP[tickMatch[1].toUpperCase()];
    const year = 2000 + parseInt(tickMatch[2], 10);
    if (mon !== undefined) return new Date(year, mon, 1);
  }
  const d = new Date(str);
  return isNaN(d) ? null : d;
}

function shortMonth(dateStr) {
  if (!dateStr) return '';
  const d = parseDate(dateStr);
  if (!d || isNaN(d)) return dateStr;
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const yr = String(d.getFullYear()).slice(-2);
  return `${months[d.getMonth()]} '${yr}`;
}

const CHART_COLORS = ['#00D09C', '#8C52FF', '#00D2FF', '#FF3366', '#FFB247', '#E63946', '#A8DADC', '#457B9D', '#F4A261', '#264653'];
const CATEGORY_COLORS = {
  EQUITY: '#00D09C', Equity: '#00D09C',
  DEBT: '#FFB247', Debt: '#FFB247',
  HYBRID: '#4D8AF0', Hybrid: '#4D8AF0',
  SOLUTION: '#8C52FF', Solution: '#8C52FF',
  OTHER: '#888', Other: '#888',
};

// ═══════════════════════════════════════════════════════════════
//  ANIMATED COUNTER
// ═══════════════════════════════════════════════════════════════
function AnimatedCounter({ value, isCurrency = true, colorClass = '' }) {
  const count = useMotionValue(0);
  const rounded = useTransform(count, (latest) =>
    isCurrency ? formatCurrency(latest) : parseFloat(latest).toFixed(2) + '%'
  );
  useEffect(() => {
    const numValue = parseFloat(value) || 0;
    const animation = animate(count, numValue, { duration: 1.2, ease: 'easeOut' });
    return animation.stop;
  }, [value, count]);
  return <motion.span className={colorClass}>{rounded}</motion.span>;
}

// ═══════════════════════════════════════════════════════════════
//  INFO TOOLTIP (chart "?" icon)
// ═══════════════════════════════════════════════════════════════
const CHART_INFO = {
  portfolioGrowth: {
    title: 'Portfolio Growth',
    desc: 'Tracks the total market value of your entire portfolio over time, computed from actual historical NAV data. The Y-axis shows your combined holdings value (units × NAV per fund) at each month-end. Rises reflect market appreciation; steps up reflect new investments.',
    metric: 'Value = Σ(Units held × Historical NAV on month-end) across all funds',
  },
  investedVsCurrent: {
    title: 'Invested vs Current Value',
    desc: 'Compares the total amount you deposited (invested) against the current market value of your portfolio month by month. The gap between the two lines represents your profit or loss.',
    metric: 'Green = Current Value, Purple = Capital Deployed',
  },
  fundAllocation: {
    title: 'Fund Allocation',
    desc: 'Shows how your total portfolio value is distributed across individual mutual fund schemes. Helps identify concentration risk — if one fund dominates, your portfolio may be under-diversified.',
    metric: 'Fund Weight = (Fund Value / Total Portfolio Value) × 100',
  },
  assetCategory: {
    title: 'Asset Category Allocation',
    desc: 'Breaks down your portfolio into broad asset classes — Equity, Debt, and Hybrid. A well-diversified portfolio typically has exposure across multiple categories based on your risk profile.',
    metric: 'Category Weight = (Category Value / Total Value) × 100',
  },
  fundPerformance: {
    title: 'Fund Performance Comparison',
    desc: 'Compares absolute returns across all your fund holdings as a bar chart. Green bars indicate positive returns, red bars indicate losses. Helps you spot underperformers.',
    metric: 'Absolute Return % = ((Current Value − Invested) / Invested) × 100',
  },
  monthlyInvestments: {
    title: 'Monthly Investments',
    desc: 'Shows the net new money you added to your portfolio each month (SIPs, lump sums, top-ups). Calculated as the difference in total invested amount between consecutive months.',
    metric: 'Monthly Flow = Invested(Month N) − Invested(Month N−1)',
  },
  navTrend: {
    title: 'Funds NAV Trend',
    desc: 'Plots the daily Net Asset Value (NAV) history of your top fund holdings over time. Data is fetched live from the public MFAPI database. Helps you compare how different funds have performed historically.',
    metric: 'NAV = Net Asset Value per unit (fetched from api.mfapi.in)',
  },
  cagrTrend: {
    title: 'Cumulative Return %',
    desc: 'Shows how much your total invested capital has grown (or shrunk) over time as a simple percentage. Because your portfolio has multiple SIP/lumpsum deposits at different dates, CAGR cannot be meaningfully applied — absolute return % is the correct and transparent metric here. Green line = profit territory; red = loss.',
    metric: 'Cumulative Return % = ((Portfolio Value − Capital Invested) / Capital Invested) × 100',
  },
  rollingReturns: {
    title: 'Rolling Returns (3-Month)',
    desc: 'Shows the 3-month momentum of your portfolio by comparing the return ratio at each point against the ratio 3 months prior. A positive bar means your portfolio performed well in that 3-month window; negative means it declined. This strips out the distortion of new deposits by working on ratios.',
    metric: 'Rolling = ((value[t]/invested[t] − value[t−3]/invested[t−3]) / value[t−3]/invested[t−3]) × 100',
  },
  drawdownTrend: {
    title: 'Drawdown Trend',
    desc: 'Measures how far your portfolio value has fallen from its highest point (peak) at any given time. A drawdown of −10% means the portfolio is 10% below its all-time high. Useful for understanding risk.',
    metric: 'Drawdown % = ((Current Value − Peak Value) / Peak Value) × 100',
  },
};

function InfoTooltip({ chartId }) {
  const [open, setOpen] = useState(false);
  const info = CHART_INFO[chartId];
  if (!info) return null;

  return (
    <span className="info-tooltip-wrap"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onClick={() => setOpen(o => !o)}
    >
      <span className="info-tooltip-icon">?</span>
      <AnimatePresence>
        {open && (
          <motion.div className="info-tooltip-popup"
            initial={{ opacity: 0, y: 6, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 6, scale: 0.95 }}
            transition={{ duration: 0.15 }}
          >
            <div className="info-tooltip-title">{info.title}</div>
            <div className="info-tooltip-desc">{info.desc}</div>
            <div className="info-tooltip-metric">
              <span className="info-tooltip-metric-label">Metric:</span> {info.metric}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </span>
  );
}

// ═══════════════════════════════════════════════════════════════
//  TOOLTIPS
// ═══════════════════════════════════════════════════════════════
const CustomTooltip = ({ active, payload, label, formatter }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <p className="ctip-label">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="ctip-value" style={{ color: p.color || p.stroke }}>
          {p.name}: {formatter ? formatter(p.value) : formatCurrency(p.value)}
        </p>
      ))}
    </div>
  );
};

const PieTooltip = ({ active, payload }) => {
  if (!active || !payload?.length) return null;
  const d = payload[0];
  return (
    <div className="chart-tooltip">
      <p className="ctip-label">{d.name}</p>
      <p className="ctip-value" style={{ color: d.payload.fill }}>{formatCurrency(d.value)}</p>
      {d.payload.pct != null && <p className="ctip-value">{formatPct(d.payload.pct)}</p>}
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════
//  TIME RANGE FILTER
// ═══════════════════════════════════════════════════════════════
const RANGES = ['1M', '6M', '1Y', 'ALL'];

function RangePills({ value, onChange }) {
  return (
    <div className="range-pills">
      {RANGES.map(r => (
        <button key={r} className={`range-pill ${value === r ? 'active' : ''}`} onClick={() => onChange(r)}>{r}</button>
      ))}
    </div>
  );
}

function filterByRange(data, range, dateKey = 'month') {
  if (range === 'ALL' || !data?.length) return data;
  const now = new Date();
  const months = range === '1M' ? 1 : range === '6M' ? 6 : 12;
  const cutoff = new Date(now.getFullYear(), now.getMonth() - months, 1);
  const filtered = data.filter(d => {
    const dt = parseDate(d[dateKey]);
    return dt && dt >= cutoff;
  });
  // If filtering produces nothing, return all data so charts don't vanish
  return filtered.length > 0 ? filtered : data;
}

// ═══════════════════════════════════════════════════════════════
//  CHART DEFINITIONS
// ═══════════════════════════════════════════════════════════════
const ALL_CHARTS = [
  { id: 'portfolioGrowth', label: 'Portfolio Growth' },
  { id: 'investedVsCurrent', label: 'Invested vs Current Value' },
  { id: 'fundAllocation', label: 'Fund Allocation' },
  { id: 'assetCategory', label: 'Asset Category Allocation' },
  { id: 'fundPerformance', label: 'Fund Performance Comparison' },
  { id: 'monthlyInvestments', label: 'Monthly Investments' },
  { id: 'navTrend', label: 'Funds NAV Trend' },
  { id: 'cagrTrend', label: 'Cumulative Return %' },
  { id: 'rollingReturns', label: 'Rolling Returns' },
  { id: 'drawdownTrend', label: 'Drawdown Trend' },
];

const DEFAULT_VISIBLE = [
  'portfolioGrowth', 'investedVsCurrent', 'fundAllocation', 'assetCategory',
  'fundPerformance', 'monthlyInvestments',
];

function loadVisibleCharts() {
  try {
    const stored = localStorage.getItem('ww_dashboard_charts');
    if (stored) return JSON.parse(stored);
  } catch { }
  return DEFAULT_VISIBLE;
}

function saveVisibleCharts(ids) {
  localStorage.setItem('ww_dashboard_charts', JSON.stringify(ids));
}

// ═══════════════════════════════════════════════════════════════
//  CONFIGURE MODAL
// ═══════════════════════════════════════════════════════════════
function ConfigureModal({ visible, onClose, visibleCharts, setVisibleCharts }) {
  if (!visible) return null;

  const toggle = (id) => {
    setVisibleCharts(prev => {
      const next = prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id];
      saveVisibleCharts(next);
      return next;
    });
  };

  return (
    <motion.div className="config-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      onClick={onClose}>
      <motion.div className="config-modal" initial={{ scale: 0.92, y: 30 }} animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.92, y: 30 }} onClick={e => e.stopPropagation()}>
        <button className="config-modal-close" onClick={onClose}>×</button>
        <p className="config-modal-tag">Chart Layout</p>
        <h2>Configure Dashboard</h2>
        <p className="config-modal-desc">Choose which charts appear on the dashboard. Changes are saved when you close this panel.</p>
        <div className="config-grid">
          {ALL_CHARTS.map(c => (
            <label key={c.id} className={`config-item ${visibleCharts.includes(c.id) ? 'active' : ''}`}>
              <input type="checkbox" checked={visibleCharts.includes(c.id)} onChange={() => toggle(c.id)} />
              <span className="config-item-label">{c.label}</span>
            </label>
          ))}
        </div>
        <p className="config-count">{visibleCharts.length} charts visible</p>
      </motion.div>
    </motion.div>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: PORTFOLIO GROWTH
// ═══════════════════════════════════════════════════════════════
function PortfolioGrowthChart({ growthData }) {
  const [range, setRange] = useState('ALL');

  const chartData = useMemo(() => {
    const filtered = filterByRange(growthData, range);
    if (!filtered?.length) return [];
    return filtered.map(d => ({ ...d, label: shortMonth(d.month) }));
  }, [growthData, range]);

  if (!chartData.length) return <div className="chart-empty">Add transactions to see growth chart</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Portfolio Growth <InfoTooltip chartId="portfolioGrowth" /></h3>
          <span className="chart-subtitle">Portfolio value progression across the selected horizon</span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={240}>
        <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="valueGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#00F298" stopOpacity={0.4} />
              <stop offset="95%" stopColor="#00F298" stopOpacity={0.01} />
            </linearGradient>
          </defs>
          <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 11, fill: '#6B6B7B' }} axisLine={false} tickLine={false} tickFormatter={v => formatCurrency(v)} />
          <Tooltip content={<CustomTooltip />} />
          <Area type="monotone" dataKey="value" stroke="#00F298" strokeWidth={2} fill="url(#valueGrad)" name="Current Value" />
        </AreaChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: INVESTED VS CURRENT VALUE
// ═══════════════════════════════════════════════════════════════
function InvestedVsCurrentChart({ growthData }) {
  const [range, setRange] = useState('ALL');
  const filtered = useMemo(() => {
    const f = filterByRange(growthData, range);
    return (f || []).map(d => ({ ...d, label: shortMonth(d.month) }));
  }, [growthData, range]);

  if (!filtered.length) return <div className="chart-empty">No growth data available</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Invested vs Current Value <InfoTooltip chartId="investedVsCurrent" /></h3>
          <span className="chart-subtitle">Capital deployed versus current market value</span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={filtered} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="invGrad2" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#8C52FF" stopOpacity={0.25} />
              <stop offset="95%" stopColor="#8C52FF" stopOpacity={0.01} />
            </linearGradient>
            <linearGradient id="valGrad2" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#00D09C" stopOpacity={0.25} />
              <stop offset="95%" stopColor="#00D09C" stopOpacity={0.01} />
            </linearGradient>
          </defs>
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} tickFormatter={formatCurrency} />
          <Tooltip content={<CustomTooltip />} />
          <Area type="monotone" dataKey="invested" stroke="#8C52FF" strokeWidth={2} fill="url(#invGrad2)" name="Invested" />
          <Area type="monotone" dataKey="value" stroke="#00D09C" strokeWidth={2} fill="url(#valGrad2)" name="Current Value" />
        </AreaChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: FUND ALLOCATION (DONUT)
// ═══════════════════════════════════════════════════════════════
function FundAllocationChart({ holdings }) {
  const pieData = useMemo(() => {
    if (!holdings?.length) return [];
    const total = holdings.reduce((s, h) => s + (parseFloat(h.currentValue) || parseFloat(h.investedAmount) || 0), 0);
    return holdings
      .filter(h => parseFloat(h.currentValue) > 0 || parseFloat(h.investedAmount) > 0)
      .map((h, i) => {
        const val = parseFloat(h.currentValue) || parseFloat(h.investedAmount) || 0;
        return {
          name: (h.schemeName || h.schemeAmfiCode || 'Fund').substring(0, 28),
          value: val,
          pct: total > 0 ? (val / total) * 100 : 0,
          fill: CHART_COLORS[i % CHART_COLORS.length],
        };
      })
      .sort((a, b) => b.value - a.value);
  }, [holdings]);

  if (!pieData.length) return <div className="chart-empty">No fund allocation data</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Fund Allocation <InfoTooltip chartId="fundAllocation" /></h3>
          <span className="chart-subtitle">Current fund-wise value distribution</span>
        </div>
      </div>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie data={pieData} cx="50%" cy="50%" innerRadius={55} outerRadius={85} paddingAngle={3} dataKey="value">
            {pieData.map((e, i) => <Cell key={i} fill={e.fill} stroke="transparent" />)}
          </Pie>
          <Tooltip content={<PieTooltip />} />
        </PieChart>
      </ResponsiveContainer>
      <div className="chart-legend-custom">
        {pieData.slice(0, 6).map((d, i) => (
          <div key={i} className="chart-legend-item">
            <div className="chart-legend-left">
              <span className="chart-legend-dot" style={{ backgroundColor: d.fill }} />
              <span className="chart-legend-name">{d.name}</span>
            </div>
            <span className="chart-legend-pct">{formatPct(d.pct)}</span>
          </div>
        ))}
      </div>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: ASSET CATEGORY ALLOCATION (DONUT)
// ═══════════════════════════════════════════════════════════════
function AssetCategoryChart({ categoryBreakdown, holdings }) {
  const pieData = useMemo(() => {
    if (categoryBreakdown?.length > 0) {
      const total = categoryBreakdown.reduce((s, c) => s + parseFloat(c.value || 0), 0);
      return categoryBreakdown.filter(c => parseFloat(c.value) > 0).map(c => ({
        name: c.category,
        value: parseFloat(c.value),
        pct: total > 0 ? (parseFloat(c.value) / total) * 100 : 0,
        fill: CATEGORY_COLORS[c.category] || CHART_COLORS[0],
      }));
    }
    // Fallback: group holdings by broadCategory
    if (!holdings?.length) return [];
    const groups = {};
    holdings.forEach(h => {
      const cat = h.broadCategory || 'Other';
      const val = parseFloat(h.currentValue) || parseFloat(h.investedAmount) || 0;
      groups[cat] = (groups[cat] || 0) + val;
    });
    const total = Object.values(groups).reduce((s, v) => s + v, 0);
    return Object.entries(groups).map(([cat, val]) => ({
      name: cat,
      value: val,
      pct: total > 0 ? (val / total) * 100 : 0,
      fill: CATEGORY_COLORS[cat] || CHART_COLORS[3],
    }));
  }, [categoryBreakdown, holdings]);

  if (!pieData.length) return <div className="chart-empty">No category data available</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Asset Category Allocation <InfoTooltip chartId="assetCategory" /></h3>
          <span className="chart-subtitle">Equity, debt, and hybrid exposure</span>
        </div>
      </div>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie data={pieData} cx="50%" cy="50%" innerRadius={55} outerRadius={85} paddingAngle={3} dataKey="value">
            {pieData.map((e, i) => <Cell key={i} fill={e.fill} stroke="transparent" />)}
          </Pie>
          <Tooltip content={<PieTooltip />} />
        </PieChart>
      </ResponsiveContainer>
      <div className="chart-legend-custom">
        {pieData.map((d, i) => (
          <div key={i} className="chart-legend-item">
            <div className="chart-legend-left">
              <span className="chart-legend-dot" style={{ backgroundColor: d.fill }} />
              <span className="chart-legend-name">{d.name}</span>
            </div>
            <span className="chart-legend-pct">{formatPct(d.pct)}</span>
          </div>
        ))}
      </div>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: FUND PERFORMANCE COMPARISON (BAR)
// ═══════════════════════════════════════════════════════════════
function FundPerformanceChart({ holdings }) {
  const [range, setRange] = useState('1Y');

  const barData = useMemo(() => {
    if (!holdings?.length) return [];
    return holdings
      .filter(h => h.absoluteReturnPct != null && !isNaN(parseFloat(h.absoluteReturnPct)))
      // Exclude dead/merged funds (NAV=0, -100% return) that destroy the scale
      .filter(h => parseFloat(h.currentValue) > 0 && parseFloat(h.absoluteReturnPct) > -99)
      .map(h => ({
        name: (h.schemeName || h.schemeAmfiCode || 'Fund').substring(0, 20),
        return: parseFloat(h.absoluteReturnPct),
      }))
      .sort((a, b) => b.return - a.return);
  }, [holdings]);

  if (!barData.length) return <div className="chart-empty">No performance data available</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Fund Performance Comparison <InfoTooltip chartId="fundPerformance" /></h3>
          <span className="chart-subtitle">Trailing returns by fund</span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={barData} margin={{ top: 10, right: 10, left: 0, bottom: 40 }}>
          <XAxis dataKey="name" tick={{ fontSize: 9, fill: '#6B6B7B', angle: -30, textAnchor: 'end' }} axisLine={false} tickLine={false} interval={0} height={60} />
          <YAxis tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} tickFormatter={v => v.toFixed(0) + '%'} />
          <Tooltip content={<CustomTooltip formatter={v => v.toFixed(2) + '%'} />} />
          <ReferenceLine y={0} stroke="rgba(255,255,255,0.1)" />
          <Bar dataKey="return" name="Return %" radius={[4, 4, 0, 0]}>
            {barData.map((entry, i) => (
              <Cell key={i} fill={entry.return >= 0 ? '#00D09C' : '#FF3366'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: MONTHLY INVESTMENTS (BAR)
// ═══════════════════════════════════════════════════════════════
function MonthlyInvestmentsChart({ growthData }) {
  const [range, setRange] = useState('ALL');

  const barData = useMemo(() => {
    if (!growthData?.length) return [];
    const filtered = filterByRange(growthData, range);
    if (!filtered?.length) return [];
    const result = [];
    for (let i = 0; i < filtered.length; i++) {
      const inv = parseFloat(filtered[i].invested) || 0;
      const prevInv = i > 0 ? (parseFloat(filtered[i - 1].invested) || 0) : 0;
      const monthlyFlow = i === 0 ? inv : Math.max(0, inv - prevInv);
      result.push({ label: shortMonth(filtered[i].month), amount: monthlyFlow });
    }
    return result;
  }, [growthData, range]);

  if (!barData.length) return <div className="chart-empty">No investment flow data</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Monthly Investments <InfoTooltip chartId="monthlyInvestments" /></h3>
          <span className="chart-subtitle">SIP and top-up flow by month</span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={barData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} tickFormatter={formatCurrency} />
          <Tooltip content={<CustomTooltip />} />
          <Bar dataKey="amount" name="Investment" fill="#FFB247" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: FUNDS NAV TREND (MULTI-LINE)
// ═══════════════════════════════════════════════════════════════
function FundsNavTrendChart({ holdings }) {
  const [range, setRange] = useState('ALL');
  const [navData, setNavData] = useState(null);
  const [navLoading, setNavLoading] = useState(false);

  useEffect(() => {
    if (!holdings?.length) return;
    const topFunds = holdings
      .filter(h => h.schemeAmfiCode && (parseFloat(h.currentValue) > 0 || parseFloat(h.investedAmount) > 0))
      .slice(0, 5);

    if (!topFunds.length) return;

    setNavLoading(true);
    Promise.allSettled(
      topFunds.map(async (h) => {
        try {
          const history = await getNavHistory(h.schemeAmfiCode);
          return { code: h.schemeAmfiCode, name: (h.schemeName || h.schemeAmfiCode).substring(0, 25), history };
        } catch {
          return null;
        }
      })
    ).then(results => {
      const valid = results.filter(r => r.status === 'fulfilled' && r.value?.history?.length).map(r => r.value);
      setNavData(valid);
      setNavLoading(false);
    });
  }, [holdings]);

  const chartData = useMemo(() => {
    if (!navData?.length) return [];
    // Build unified timeline: sample every ~30 points
    const allDates = new Set();
    navData.forEach(f => f.history.forEach(d => allDates.add(d.date)));
    const sortedDates = [...allDates].sort((a, b) => parseDate(a) - parseDate(b));

    // Filter by range
    let filtered = sortedDates;
    if (range !== 'ALL') {
      const now = new Date();
      const months = range === '1M' ? 1 : range === '6M' ? 6 : 12;
      const cutoff = new Date(now.getFullYear(), now.getMonth() - months, 1);
      filtered = sortedDates.filter(d => parseDate(d) >= cutoff);
    }

    // Sample to ~60 points max for perf
    const step = Math.max(1, Math.floor(filtered.length / 60));
    const sampled = filtered.filter((_, i) => i % step === 0 || i === filtered.length - 1);

    return sampled.map(date => {
      const point = { date: shortMonth(date) };
      navData.forEach(fund => {
        const match = fund.history.find(d => d.date === date);
        point[fund.code] = match ? match.nav : null;
      });
      return point;
    });
  }, [navData, range]);

  if (navLoading) return <div className="chart-empty">Loading NAV history...</div>;
  if (!navData?.length || !chartData.length) return <div className="chart-empty">No NAV trend data available</div>;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Funds NAV Trend <InfoTooltip chartId="navTrend" /></h3>
          <span className="chart-subtitle">Multi-fund NAV progression over time</span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
          <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis domain={['auto', 'auto']} tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <Tooltip content={<CustomTooltip formatter={v => v?.toFixed(2)} />} />
          {navData.map((fund, i) => (
            <Line key={fund.code} type="monotone" dataKey={fund.code} name={fund.name}
              stroke={CHART_COLORS[i % CHART_COLORS.length]} strokeWidth={1.5} dot={false} connectNulls />
          ))}
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 11, color: '#A0A0B0', marginTop: 8 }} />
        </LineChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: CUMULATIVE RETURN % OVER TIME (replaces incorrect CAGR trend)
//  NOTE: CAGR requires a single investment at t=0 which is not the case
//  for SIP portfolios. We display absolute return % = (value-invested)/invested×100
//  which is factually accurate for any number of transactions.
// ═══════════════════════════════════════════════════════════════
function AbsoluteReturnChart({ growthData }) {
  const [range, setRange] = useState('ALL');

  const chartData = useMemo(() => {
    if (!growthData?.length || growthData.length < 2) return [];
    const filtered = filterByRange(growthData, range);
    if (!filtered?.length || filtered.length < 2) return [];

    return filtered
      .map(d => {
        const val = parseFloat(d.value) || 0;
        const inv = parseFloat(d.invested) || 1;
        // Absolute return % = (current market value − cumulative invested) / invested × 100
        // This is always mathematically correct regardless of SIP/lumpsum mix
        const absReturn = ((val - inv) / inv) * 100;
        return {
          label: shortMonth(d.month),
          absReturn: isFinite(absReturn) ? parseFloat(absReturn.toFixed(2)) : 0,
          value: val,
          invested: inv,
        };
      })
      .filter(Boolean);
  }, [growthData, range]);

  if (!chartData.length) return <div className="chart-empty">Insufficient data for return trend</div>;

  const latestReturn = chartData[chartData.length - 1]?.absReturn ?? 0;
  const isPositive = latestReturn >= 0;

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">
            Cumulative Return %
            <InfoTooltip chartId="cagrTrend" />
          </h3>
          <span className="chart-subtitle" style={{ color: isPositive ? '#44D7B6' : '#FF4D4D' }}>
            {isPositive ? '+' : ''}{latestReturn.toFixed(2)}% total return on invested capital
          </span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false}
            tickFormatter={v => v.toFixed(1) + '%'} />
          <Tooltip content={<CustomTooltip formatter={v => v.toFixed(2) + '%'} />} />
          <ReferenceLine y={0} stroke="rgba(255,255,255,0.1)" strokeDasharray="4 2" />
          <Line
            type="monotone" dataKey="absReturn" name="Return %"
            stroke={isPositive ? '#44D7B6' : '#FF4D4D'}
            strokeWidth={2} dot={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </>
  );
}



// ═══════════════════════════════════════════════════════════════
//  CHART: ROLLING RETURNS (LINE)
// ═══════════════════════════════════════════════════════════════
function RollingReturnsChart({ growthData }) {
  const [range, setRange] = useState('ALL');

  const chartData = useMemo(() => {
    if (!growthData?.length) return [];
    const filtered = filterByRange(growthData, range);
    if (!filtered?.length || filtered.length < 4) return [];

    const window = 3; // 3-month rolling
    const result = [];
    for (let i = window; i < filtered.length; i++) {
      // Use value/invested ratio to strip out new SIP deposit effects
      const prevInv = parseFloat(filtered[i - window].invested) || 1;
      const prevVal = parseFloat(filtered[i - window].value) || 0;
      const currInv = parseFloat(filtered[i].invested) || 1;
      const currVal = parseFloat(filtered[i].value) || 0;
      const prevRatio = prevVal / prevInv;
      const currRatio = currVal / currInv;
      const rolling = prevRatio > 0 ? ((currRatio - prevRatio) / prevRatio) * 100 : 0;
      result.push({ label: shortMonth(filtered[i].month), rolling });
    }
    return result;
  }, [growthData, range]);

  if (!chartData.length) return (
    <div className="chart-empty" style={{ flexDirection: 'column', gap: 12 }}>
      <span>Not enough data for 3-month rolling returns in this range</span>
      {range !== 'ALL' && (
        <button
          onClick={() => setRange('ALL')}
          style={{
            marginTop: 4, padding: '6px 18px',
            background: 'rgba(140,82,255,0.15)',
            border: '1px solid rgba(140,82,255,0.4)',
            borderRadius: 20, color: '#8C52FF',
            fontSize: 12, cursor: 'pointer', fontWeight: 600,
          }}
        >
          ← View All Time
        </button>
      )}
    </div>
  );

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Rolling Returns <InfoTooltip chartId="rollingReturns" /></h3>
          <span className="chart-subtitle">Trend of 3-month rolling return snapshots</span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} tickFormatter={v => v.toFixed(1) + '%'} />
          <Tooltip content={<CustomTooltip formatter={v => v.toFixed(2) + '%'} />} />
          <ReferenceLine y={0} stroke="rgba(255,255,255,0.1)" />
          <Line type="monotone" dataKey="rolling" name="Rolling Return %" stroke="#8C52FF" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CHART: DRAWDOWN TREND
// ═══════════════════════════════════════════════════════════════
function DrawdownTrendChart({ growthData, holdings }) {
  const [range, setRange] = useState('ALL');
  const [navData, setNavData] = useState(null);
  const [navLoading, setNavLoading] = useState(false);

  // Fetch real NAV history for portfolio-level drawdown
  useEffect(() => {
    if (!holdings?.length) return;
    const funds = holdings
      .filter(h => h.schemeAmfiCode && !h.schemeAmfiCode.startsWith('WW_') && (parseFloat(h.currentValue) > 0 || parseFloat(h.investedAmount) > 0))
      .slice(0, 8);

    if (!funds.length) return;

    setNavLoading(true);
    Promise.allSettled(
      funds.map(async (h) => {
        try {
          const history = await getNavHistory(h.schemeAmfiCode);
          return { code: h.schemeAmfiCode, units: parseFloat(h.units) || 0, history };
        } catch { return null; }
      })
    ).then(results => {
      const valid = results.filter(r => r.status === 'fulfilled' && r.value?.history?.length).map(r => r.value);
      setNavData(valid);
      setNavLoading(false);
    });
  }, [holdings]);

  const chartData = useMemo(() => {
    // If we have real NAV history, compute portfolio-level drawdown
    if (navData?.length > 0) {
      const allDates = new Set();
      navData.forEach(f => f.history.forEach(d => allDates.add(d.date)));
      const sortedDates = [...allDates].sort((a, b) => parseDate(a) - parseDate(b));

      let dates = sortedDates;
      if (range !== 'ALL') {
        const now = new Date();
        const months = range === '1M' ? 1 : range === '6M' ? 6 : 12;
        const cutoff = new Date(now.getFullYear(), now.getMonth() - months, 1);
        const filtered = sortedDates.filter(d => parseDate(d) >= cutoff);
        if (filtered.length > 0) dates = filtered;
      }

      // Sample to ~80 points for performance
      const step = Math.max(1, Math.floor(dates.length / 80));
      const sampled = dates.filter((_, i) => i % step === 0 || i === dates.length - 1);

      // Build lookup for fast NAV access
      const navLookups = navData.map(f => {
        const map = new Map();
        f.history.forEach(d => map.set(d.date, d.nav));
        return { code: f.code, units: f.units, map };
      });

      let peak = 0;
      const lastNav = {};
      const points = [];

      for (const date of sampled) {
        let portfolioValue = 0;
        for (const fund of navLookups) {
          const nav = fund.map.get(date);
          if (nav) lastNav[fund.code] = nav;
          portfolioValue += fund.units * (nav || lastNav[fund.code] || 0);
        }
        if (portfolioValue > 0) {
          if (portfolioValue > peak) peak = portfolioValue;
          const dd = peak > 0 ? ((portfolioValue - peak) / peak) * 100 : 0;
          points.push({ label: shortMonth(date), drawdown: dd });
        }
      }
      return points;
    }

    // Fallback: use growthData
    if (!growthData?.length) return [];
    const filtered = filterByRange(growthData, range);
    if (!filtered?.length) return [];

    let peak = 0;
    return filtered.map(d => {
      const val = parseFloat(d.value) || 0;
      if (val > peak) peak = val;
      const dd = peak > 0 ? ((val - peak) / peak) * 100 : 0;
      return { label: shortMonth(d.month), drawdown: dd };
    });
  }, [navData, growthData, range]);

  if (navLoading) return <div className="chart-empty">Calculating drawdown from live NAV data…</div>;
  if (!chartData.length) return <div className="chart-empty">Insufficient data for drawdown analysis</div>;

  const maxDD = Math.min(...chartData.map(d => d.drawdown));

  return (
    <>
      <div className="chart-card-header">
        <div>
          <h3 className="chart-title">Drawdown Trend <InfoTooltip chartId="drawdownTrend" /></h3>
          <span className="chart-subtitle">
            Depth of decline from the rolling peak
            {maxDD < 0 && (
              <span style={{ marginLeft: 8, color: '#FF3366', fontWeight: 700, fontSize: 11 }}>
                Max: {maxDD.toFixed(2)}%
              </span>
            )}
          </span>
        </div>
        <RangePills value={range} onChange={setRange} />
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="ddGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#FF3366" stopOpacity={0.3} />
              <stop offset="95%" stopColor="#FF3366" stopOpacity={0.01} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
          <YAxis domain={['auto', 0]} tick={{ fontSize: 10, fill: '#6B6B7B' }} axisLine={false} tickLine={false} tickFormatter={v => v.toFixed(1) + '%'} />
          <Tooltip content={<CustomTooltip formatter={v => v.toFixed(2) + '%'} />} />
          <ReferenceLine y={0} stroke="rgba(255,255,255,0.15)" />
          <Area type="monotone" dataKey="drawdown" name="Drawdown %" stroke="#FF3366" strokeWidth={2} fill="url(#ddGrad)" />
        </AreaChart>
      </ResponsiveContainer>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
//  MAIN DASHBOARD PAGE
// ═══════════════════════════════════════════════════════════════
export default function DashboardPage() {
  const { getToken, user } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [configOpen, setConfigOpen] = useState(false);
  const [visibleCharts, setVisibleCharts] = useState(loadVisibleCharts);

  const fetchPortfolio = useCallback(async () => {
    setRefreshing(true);
    setError('');
    try {
      const res = await fetch(`${API}/api/returns/portfolio`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      if (!res.ok) {
        const json = await res.json().catch(() => ({}));
        throw new Error(json.error || `Server error ${res.status}`);
      }
      const json = await res.json();
      setData(json);
    } catch (e) {
      setError(e.message || 'Failed to load portfolio data');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [getToken]);

  useEffect(() => { fetchPortfolio(); }, [fetchPortfolio]);

  // Prepare growth data
  const growthData = useMemo(() => {
    if (data?.growthTimeline?.length > 0) {
      const tl = data.growthTimeline;
      let firstNonZero = tl.findIndex(d => parseFloat(d.invested) > 0);
      if (firstNonZero < 0) firstNonZero = 0;
      return tl.slice(firstNonZero);
    }
    // Return empty — charts will show their "Add transactions" empty state.
    // We never synthesise fake data points.
    return [];
  }, [data]);

  const isUp = data && parseFloat(data.totalGainLoss) >= 0;
  const hasData = data && parseInt(data.transactionCount) > 0;

  const isChartVisible = (id) => visibleCharts.includes(id);

  // ─── ERROR STATE ───
  if (error && !data) {
    return (
      <div className="dashboard-page">
        <div className="dash-error-full">
          <AlertCircle size={48} color="#FF4D4D" />
          <h2>Could not load portfolio</h2>
          <p>{error}</p>
          <motion.button className="refresh-btn" onClick={fetchPortfolio}
            whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
            <RefreshCw size={14} /> Try Again
          </motion.button>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      {/* ─── HEADER ─── */}
      <div className="dash-header">
        <div>
          <div className="page-tag"><Activity size={12} /> Mutual Fund Intelligence</div>
          <h1 className="page-title">Portfolio Dashboard</h1>
          <p className="page-subtitle">Track growth, allocations, SIP cadence, and fund-level quality across your portfolio.</p>
        </div>
        <motion.button className="refresh-btn" onClick={fetchPortfolio} disabled={refreshing}
          whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
          <RefreshCw size={14} className={refreshing ? 'spin' : ''} /> Refresh
        </motion.button>
      </div>

      {error && data && (
        <div className="dash-error"><AlertCircle size={14} /> {error}</div>
      )}

      {!hasData && !loading ? (
        /* ─── EMPTY STATE ─── */
        <div className="dash-empty">
          <motion.div className="empty-card"
            initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.5, type: 'spring' }}>
            <motion.div className="empty-icon"
              animate={{ y: [0, -10, 0] }} transition={{ repeat: Infinity, duration: 4, ease: 'easeInOut' }}>
              <Target size={54} color="#00F298" />
            </motion.div>
            <h2>No Investments Yet</h2>
            <p>Start securely logging your first transactions to dynamically generate your portfolio analytics and wealth projections.</p>
            <motion.button className="btn-goto-txns" onClick={() => navigate('/transactions')}
              whileHover={{ scale: 1.05, boxShadow: '0 0 20px rgba(0, 242, 152, 0.4)' }}
              whileTap={{ scale: 0.95 }}>
              + Add First Transaction
            </motion.button>
          </motion.div>
        </div>
      ) : (
        <>
          {/* ─── PORTFOLIO OVERVIEW HERO ─── */}
          <motion.div className="portfolio-overview"
            initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}>
            <p className="po-label">Portfolio Overview</p>
            {loading ? <div className="kpi-skeleton" style={{ height: 42, maxWidth: 260 }} /> : (
              <div className="po-value">{formatINR(data?.totalCurrentValue)}</div>
            )}
            <div className="po-delta-row">
              <span className="po-delta-label">Net {isUp ? 'profit' : 'loss'}</span>
              {loading ? <div className="kpi-skeleton" style={{ height: 20, width: 100 }} /> : (
                <>
                  <span className={`po-delta ${isUp ? 'positive' : 'negative'}`}>
                    {isUp ? '▲' : '▼'} {formatINR(Math.abs(parseFloat(data?.totalGainLoss) || 0))}
                  </span>
                  <span className={`po-delta-pct ${isUp ? 'positive' : 'negative'}`}>
                    {isUp ? '▲' : '▼'} {formatPct(Math.abs(parseFloat(data?.absoluteReturnPct) || 0))}
                  </span>
                </>
              )}
            </div>
            <p className="po-note">Marked to market as of the latest NAV cycle</p>

            <div className="po-stats">
              <div className="po-stat">
                <span className="po-stat-label">Total Invested</span>
                {loading ? <div className="kpi-skeleton" style={{ height: 24 }} /> : (
                  <span className="po-stat-value">{formatINR(data?.totalInvested)}</span>
                )}
              </div>
              <div className="po-stat">
                <span className="po-stat-label">XIRR</span>
                {loading ? <div className="kpi-skeleton" style={{ height: 24 }} /> : (
                  <span className="po-stat-value">{data?.xirrPct != null ? formatPct(data.xirrPct) : '—'}</span>
                )}
              </div>
              <div className="po-stat">
                <span className="po-stat-label">{isUp ? <TrendingUp size={12} style={{ marginRight: 4, verticalAlign: -2 }} /> : <TrendingDown size={12} style={{ marginRight: 4, verticalAlign: -2 }} />}Total Gain / Loss</span>
                {loading ? <div className="kpi-skeleton" style={{ height: 24 }} /> : (
                  <>
                    <span className={`po-stat-value ${isUp ? 'green' : 'red'}`}>
                      {isUp ? '+' : '-'}{formatINR(Math.abs(parseFloat(data?.totalGainLoss) || 0))}
                    </span>
                    <span className={`po-stat-sub ${isUp ? 'green' : 'red'}`}>
                      {formatPctSigned(parseFloat(data?.absoluteReturnPct) || 0)} absolute return
                    </span>
                  </>
                )}
              </div>
            </div>
          </motion.div>

          {/* ─── CONFIGURE BUTTON ─── */}
          <div className="configure-row">
            <button className="configure-btn" onClick={() => setConfigOpen(true)}>
              <Settings size={14} /> Configure
            </button>
          </div>

          {/* ─── CHARTS GRID ─── */}
          <div className="charts-grid">
            {isChartVisible('portfolioGrowth') && (
              <motion.div className="chart-card full-width"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
                <PortfolioGrowthChart growthData={growthData} />
              </motion.div>
            )}

            {isChartVisible('fundAllocation') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
                <FundAllocationChart holdings={data?.holdings} />
              </motion.div>
            )}

            {isChartVisible('investedVsCurrent') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
                <InvestedVsCurrentChart growthData={growthData} />
              </motion.div>
            )}

            {isChartVisible('assetCategory') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
                <AssetCategoryChart categoryBreakdown={data?.categoryBreakdown} holdings={data?.holdings} />
              </motion.div>
            )}

            {isChartVisible('fundPerformance') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
                <FundPerformanceChart holdings={data?.holdings} />
              </motion.div>
            )}

            {isChartVisible('monthlyInvestments') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}>
                <MonthlyInvestmentsChart growthData={growthData} />
              </motion.div>
            )}

            {isChartVisible('navTrend') && (
              <motion.div className="chart-card full-width"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}>
                <FundsNavTrendChart holdings={data?.holdings} />
              </motion.div>
            )}

            {isChartVisible('cagrTrend') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}>
                <AbsoluteReturnChart growthData={growthData} />
              </motion.div>
            )}


            {isChartVisible('rollingReturns') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}>
                <RollingReturnsChart growthData={growthData} />
              </motion.div>
            )}

            {isChartVisible('drawdownTrend') && (
              <motion.div className="chart-card"
                initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.35 }}>
                <DrawdownTrendChart growthData={growthData} holdings={data?.holdings} />
              </motion.div>
            )}
          </div>

          {/* ─── HOLDINGS TABLE ─── */}
          {data?.holdings?.length > 0 && (
            <motion.div className="holdings-section glassmorphism"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}>
              <div className="holdings-header">
                <h3 className="chart-title">Fund-wise Returns <span className="badge-m09">M09</span></h3>
                <span className="chart-subtitle">XIRR + Absolute Return per holding</span>
              </div>
              <div className="holdings-table-wrap">
                <table className="holdings-table">
                  <thead>
                    <tr>
                      <th>Fund</th>
                      <th>Category</th>
                      <th>Invested</th>
                      <th>Units</th>
                      <th>Current Value</th>
                      <th>Gain / Loss</th>
                      <th>Abs. Return</th>
                      <th>NAV Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.holdings
                      .filter(h => parseFloat(h.units) > 0 || parseFloat(h.currentValue) > 0 || parseFloat(h.investedAmount) > 0)
                      .map((h, i) => {
                        const gain = parseFloat(h.gainLoss) || 0;
                        const absRet = parseFloat(h.absoluteReturnPct);
                        const currentVal = parseFloat(h.currentValue) || 0;
                        const noNav = currentVal === 0 && parseFloat(h.units) > 0;
                        return (
                          <motion.tr key={i}
                            initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                            transition={{ delay: 0.04 * i }}>
                            <td>
                              <div className="h-fund-name">
                                {h.schemeName || h.schemeAmfiCode}
                                {h.schemeName?.toLowerCase().includes('direct') && (
                                  <span className="plan-badge direct">DIRECT</span>
                                )}
                                {h.schemeName?.toLowerCase().includes('regular') && (
                                  <span className="plan-badge regular">REGULAR</span>
                                )}
                              </div>
                              <div className="h-folio">{h.folioNumber}</div>
                            </td>
                            <td>
                              <span className={`cat-badge ${h.broadCategory?.toLowerCase()}`}>
                                {h.broadCategory || '—'}
                              </span>
                            </td>
                            <td className="h-num">{formatCurrency(h.investedAmount)}</td>
                            <td className="h-num units-col">{parseFloat(h.units)?.toFixed(3) || '—'}</td>
                            <td className="h-num">
                              {noNav ? (
                                <span className="nav-pending-badge" title="NAV not yet synced from AMFI. Run POST /api/schemes/seed to refresh.">
                                  NAV Pending
                                </span>
                              ) : formatCurrency(currentVal)}
                            </td>
                            <td className={`h-num ${gain >= 0 ? 'green' : 'red'}`}>
                              {noNav ? '—' : (gain >= 0 ? '+' : '') + formatCurrency(gain)}
                            </td>
                            <td className={`h-num bold ${!isNaN(absRet) && absRet >= 0 ? 'green' : 'red'}`}>
                              {noNav ? '—' : !isNaN(absRet) ? formatPctSigned(absRet) : '—'}
                            </td>
                            <td className="h-date">{h.lastNavDate || (noNav ? <span className="nav-pending">Pending</span> : '—')}</td>
                          </motion.tr>
                        );
                      })}
                  </tbody>
                </table>
              </div>
              {data.holdings.some(h => parseFloat(h.units) > 0 && parseFloat(h.currentValue) === 0) && (
                <div className="nav-sync-hint">
                  <Wifi size={13} />
                  <span>Some funds show "NAV Pending" because their AMFI codes weren't in our database. Run <code>POST /api/schemes/seed</code> to sync NAV data, then re-upload your CAS PDF.</span>
                </div>
              )}
            </motion.div>
          )}
        </>
      )}

      {/* ─── CONFIGURE MODAL ─── */}
      <AnimatePresence>
        {configOpen && (
          <ConfigureModal
            visible={configOpen}
            onClose={() => setConfigOpen(false)}
            visibleCharts={visibleCharts}
            setVisibleCharts={setVisibleCharts}
          />
        )}
      </AnimatePresence>
    </div>
  );
}