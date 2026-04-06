import { useState, useEffect, useCallback } from 'react';
import { motion, animate, useMotionValue, useTransform } from 'framer-motion';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend, BarChart, Bar } from 'recharts';
import { TrendingUp, TrendingDown, RefreshCw, Activity, PieChart as PieIcon, BarChart2, Target, AlertCircle, Wifi } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import './DashboardPage.css';

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

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
  return (num >= 0 ? '+' : '') + num.toFixed(2) + '%';
}

const CATEGORY_COLORS = {
  EQUITY: '#00F298',
  DEBT: '#8C52FF',
  HYBRID: '#00D2FF',
  SOLUTION: '#FFB247',
  OTHER: '#888',
  Other: '#999',
};

const CHART_COLORS = ['#00F298', '#8C52FF', '#00D2FF', '#FF3366', '#FFB247', '#E63946', '#A8DADC', '#457B9D'];

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <p className="ctip-label">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="ctip-value" style={{ color: p.color }}>
          {p.name}: {formatCurrency(p.value)}
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
    </div>
  );
};

// Animated Number Counter
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

export default function DashboardPage() {
  const { getToken, user } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [activeTab, setActiveTab] = useState('growth'); // 'growth' | 'allocation'

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

  // Real growth timeline from API (or fallback to simulated)
  const growthData = (() => {
    if (data?.growthTimeline?.length > 0) {
      // Drop leading zero-invested months (before first purchase), keep rest
      const tl = data.growthTimeline;
      let firstNonZero = tl.findIndex(d => parseFloat(d.invested) > 0);
      if (firstNonZero < 0) firstNonZero = 0;
      return tl.slice(firstNonZero);
    }
    // Simulated fallback
    if (!data?.holdings) return [];
    const invested = parseFloat(data.totalInvested) || 0;
    const current = parseFloat(data.totalCurrentValue) || 0;
    if (!invested) return [];
    const months = ['6m ago', '5m ago', '4m ago', '3m ago', '2m ago', '1m ago', 'Today'];
    return months.map((m, i) => {
      const progress = i / (months.length - 1);
      return {
        month: m,
        invested: invested * (0.4 + 0.6 * progress),
        value: invested * (0.4 + 0.6 * progress) * (1 + (current / invested - 1) * progress),
      };
    });
  })();

  // Category-level pie data (fallback to fund-level)
  const pieData = (() => {
    if (data?.categoryBreakdown?.length > 0) {
      return data.categoryBreakdown
        .filter(c => parseFloat(c.value) > 0)
        .map(c => ({
          name: c.category,
          value: parseFloat(c.value),
          fill: CATEGORY_COLORS[c.category] || CHART_COLORS[0],
        }));
    }
    // Fund-level fallback
    return (data?.holdings || [])
      .filter(h => parseFloat(h.currentValue) > 0 || parseFloat(h.investedAmount) > 0)
      .map((h, i) => ({
        name: (h.schemeName || h.schemeAmfiCode)?.substring(0, 22),
        value: parseFloat(h.currentValue) || parseFloat(h.investedAmount) || 0,
        fill: CHART_COLORS[i % CHART_COLORS.length],
      }));
  })();

  const isUp = data && parseFloat(data.totalGainLoss) >= 0;
  const hasData = data && parseInt(data.transactionCount) > 0;

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
      {/* Header */}
      <div className="dash-header">
        <div>
          <div className="page-tag"><Activity size={12} /> M09 — Returns Engine</div>
          <h1 className="page-title">
            Welcome, <span className="text-gradient">{user?.fullName?.split(' ')[0] || 'Investor'}</span> 👋
          </h1>
          <p className="page-subtitle">Your portfolio performance powered by XIRR calculations</p>
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
          {/* Top KPI Cards */}
          <div className="kpi-grid">
            <motion.div className="kpi-card total-value"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}>
              <div className="kpi-icon-wrap"><TrendingUp size={20} color="#00D09C" /></div>
              <div className="kpi-label">Current Portfolio Value</div>
              {loading ? <div className="kpi-skeleton" /> : (
                <div className="kpi-value"><AnimatedCounter value={data?.totalCurrentValue} /></div>
              )}
              <div className="kpi-sub">As of today's NAV</div>
            </motion.div>

            <motion.div className="kpi-card"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
              <div className="kpi-icon-wrap"><BarChart2 size={20} color="#8C52FF" /></div>
              <div className="kpi-label">Total Invested</div>
              {loading ? <div className="kpi-skeleton" /> : (
                <div className="kpi-value"><AnimatedCounter value={data?.totalInvested} colorClass="purple" /></div>
              )}
              <div className="kpi-sub">{data?.transactionCount || 0} transactions</div>
            </motion.div>

            <motion.div className={`kpi-card ${isUp ? 'positive-card' : 'negative-card'}`}
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
              <div className="kpi-icon-wrap">
                {isUp ? <TrendingUp size={20} color="#00D09C" /> : <TrendingDown size={20} color="#FF4D4D" />}
              </div>
              <div className="kpi-label">Total Gain / Loss</div>
              {loading ? <div className="kpi-skeleton" /> : (
                <div className="kpi-value">
                  <AnimatedCounter value={data?.totalGainLoss} colorClass={isUp ? '' : 'negative'} />
                </div>
              )}
              <div className={`kpi-sub ${isUp ? 'green' : 'red'}`}>
                {formatPct(data?.absoluteReturnPct)} absolute return
              </div>
            </motion.div>

            <motion.div className="kpi-card xirr-card"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
              <div className="kpi-icon-wrap"><Activity size={20} color="#FFB247" /></div>
              <div className="kpi-label">XIRR (Annualized)</div>
              {loading ? <div className="kpi-skeleton" /> : (
                <div className="kpi-value">
                  {data?.xirrPct != null ? (
                    <>
                      <AnimatedCounter value={data.xirrPct} isCurrency={false}
                        colorClass={parseFloat(data.xirrPct) >= 0 ? 'xirr-positive' : 'negative'} />
                      {' '}<span style={{ fontSize: '14px', verticalAlign: 'middle', fontWeight: 600 }}>p.a.</span>
                    </>
                  ) : '—'}
                </div>
              )}
              <div className="kpi-sub">Newton-Raphson XIRR</div>
            </motion.div>
          </div>

          {/* Charts Row */}
          <div className="charts-row">
            {/* Portfolio Growth / Allocation Toggle */}
            <motion.div className="chart-card glassmorphism chart-wide"
              initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.25 }}>
              <div className="chart-card-header">
                <div>
                  <h3 className="chart-title">Portfolio Growth</h3>
                  <span className="chart-subtitle">
                    {data?.growthTimeline?.length > 0 ? 'Real monthly invested vs. estimated value' : 'Invested vs Current Value'}
                  </span>
                </div>
              </div>
              {growthData.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <AreaChart data={growthData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id="investedGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#8C52FF" stopOpacity={0.4} />
                        <stop offset="95%" stopColor="#8C52FF" stopOpacity={0.01} />
                      </linearGradient>
                      <linearGradient id="valueGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#00F298" stopOpacity={0.4} />
                        <stop offset="95%" stopColor="#00F298" stopOpacity={0.01} />
                      </linearGradient>
                    </defs>
                    <XAxis dataKey="month" tick={{ fontSize: 11, fill: '#6B6B7B' }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11, fill: '#6B6B7B' }} axisLine={false} tickLine={false}
                      tickFormatter={v => v >= 100000 ? `₹${(v / 100000).toFixed(1)}L` : v >= 1000 ? `₹${(v / 1000).toFixed(0)}K` : `₹${v}`} />
                    <Tooltip content={<CustomTooltip />} />
                    <Area type="monotone" dataKey="invested" stroke="#8C52FF" strokeWidth={2.5}
                      fill="url(#investedGrad)" name="Invested" />
                    <Area type="monotone" dataKey="value" stroke="#00F298" strokeWidth={2.5}
                      fill="url(#valueGrad)" name="Current Value" />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="chart-empty">Add transactions to see growth chart</div>
              )}
            </motion.div>

            {/* Category Breakdown Pie */}
            <motion.div className="chart-card glassmorphism"
              initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.3 }}>
              <div className="chart-card-header">
                <h3 className="chart-title">Allocation Breakdown</h3>
                <span className="chart-subtitle">
                  {data?.categoryBreakdown?.length > 0 ? 'By asset class' : 'By fund value'}
                </span>
              </div>
              {pieData.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <PieChart>
                    <Pie data={pieData} cx="50%" cy="50%" innerRadius={55} outerRadius={85}
                      paddingAngle={3} dataKey="value">
                      {pieData.map((entry, i) => (
                        <Cell key={i} fill={entry.fill} stroke="transparent" />
                      ))}
                    </Pie>
                    <Tooltip content={<PieTooltip />} />
                    <Legend iconType="circle" iconSize={8}
                      wrapperStyle={{ fontSize: 11, color: '#A0A0B0' }} />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="chart-empty">No allocation data yet</div>
              )}
            </motion.div>
          </div>

          {/* Holdings Table */}
          {data?.holdings?.length > 0 && (
            <motion.div className="holdings-section glassmorphism"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.35 }}>
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
                              {noNav ? '—' : !isNaN(absRet) ? formatPct(absRet) : '—'}
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
    </div>
  );
}
