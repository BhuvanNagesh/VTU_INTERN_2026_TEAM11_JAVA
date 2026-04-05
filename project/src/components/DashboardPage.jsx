import { useState, useEffect, useCallback } from 'react';
import { motion, animate, useMotionValue, useTransform } from 'framer-motion';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend } from 'recharts';
import { TrendingUp, TrendingDown, RefreshCw, Activity, PieChart as PieIcon, BarChart2, Target } from 'lucide-react';
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
  return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPct(val) {
  if (val === null || val === undefined) return '—';
  const num = parseFloat(val);
  if (isNaN(num)) return '—';
  return (num >= 0 ? '+' : '') + num.toFixed(2) + '%';
}

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

// Animated Number Counter Hook
function AnimatedCounter({ value, isCurrency = true, colorClass = '' }) {
  const count = useMotionValue(0);
  const rounded = useTransform(count, (latest) => 
    isCurrency ? formatCurrency(latest) : parseFloat(latest).toFixed(2) + '%'
  );
  
  useEffect(() => {
    const numValue = parseFloat(value) || 0;
    const animation = animate(count, numValue, { duration: 1.2, ease: "easeOut" });
    return animation.stop;
  }, [value, count]);

  return <motion.span className={colorClass}>{rounded}</motion.span>;
}

function ReturnCard({ label, value, sub, up, loading }) {
  return (
    <div className={`return-card ${up === true ? 'positive' : up === false ? 'negative' : ''}`}>
      <span className="rc-label">{label}</span>
      {loading ? <div className="rc-skeleton" /> : (
        <>
          <span className="rc-value">{value}</span>
          {sub && <span className="rc-sub">{sub}</span>}
        </>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const { getToken, user } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  const fetchPortfolio = useCallback(async () => {
    setRefreshing(true);
    try {
      const res = await fetch(`${API}/api/returns/portfolio`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error || 'Failed to load');
      setData(json);
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [getToken]);

  useEffect(() => { fetchPortfolio(); }, [fetchPortfolio]);

  // Build pie chart data from holdings
  const pieData = data?.holdings?.filter(h => parseFloat(h.currentValue) > 0).map((h, i) => ({
    name: (h.schemeName || h.schemeAmfiCode)?.substring(0, 28),
    value: parseFloat(h.currentValue) || 0,
    color: CHART_COLORS[i % CHART_COLORS.length],
  })) || [];

  // Build simulated portfolio growth data from transactions
  const growthData = (() => {
    if (!data?.holdings) return [];
    // Simple projection: show invested vs current for display
    const invested = parseFloat(data.totalInvested) || 0;
    const current = parseFloat(data.totalCurrentValue) || 0;
    if (!invested) return [];
    // Generate 6-month simulated curve (linear interpolation for display)
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

  const isUp = data && parseFloat(data.totalGainLoss) >= 0;
  const hasData = data && parseInt(data.transactionCount) > 0;

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

      {error && (
        <div className="dash-error">{error}</div>
      )}

      {!hasData && !loading ? (
        <div className="dash-empty">
          <motion.div className="empty-card" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.5, type: 'spring' }}>
            <motion.div className="empty-icon" animate={{ y: [0, -10, 0] }} transition={{ repeat: Infinity, duration: 4, ease: "easeInOut" }}>
              <Target size={54} color="#00F298" />
            </motion.div>
            <h2>No Investments Yet</h2>
            <p>Start securely logging your first transactions to dynamically generate your portfolio analytics and wealth projections.</p>
            <motion.button className="btn-goto-txns" onClick={() => navigate('/transactions')}
              whileHover={{ scale: 1.05, boxShadow: '0 0 20px rgba(0, 242, 152, 0.4)' }} whileTap={{ scale: 0.95 }}>
              + Add First Transaction
            </motion.button>
          </motion.div>
        </div>
      ) : (
        <>
          {/* Top KPI Cards */}
          <div className="kpi-grid">
            <motion.div className="kpi-card total-value" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}>
              <div className="kpi-icon-wrap"><TrendingUp size={20} color="#00D09C" /></div>
              <div className="kpi-label">Current Portfolio Value</div>
              {loading ? <div className="kpi-skeleton" /> : (
                <div className="kpi-value"><AnimatedCounter value={data?.totalCurrentValue} /></div>
              )}
              <div className="kpi-sub">As of today's NAV</div>
            </motion.div>

            <motion.div className="kpi-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
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

            <motion.div className="kpi-card xirr-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
              <div className="kpi-icon-wrap"><Activity size={20} color="#FFB247" /></div>
              <div className="kpi-label">XIRR (Annualized)</div>
              {loading ? <div className="kpi-skeleton" /> : (
                <div className="kpi-value">
                  {data?.xirrPct ? (
                    <><AnimatedCounter value={data.xirrPct} isCurrency={false} colorClass={parseFloat(data.xirrPct) >= 0 ? 'xirr-positive' : 'negative'} /> <span style={{ fontSize: '14px', verticalAlign: 'middle', fontWeight: 600 }}>p.a.</span></>
                  ) : '—'}
                </div>
              )}
              <div className="kpi-sub">Newton-Raphson XIRR</div>
            </motion.div>
          </div>

          {/* Charts Row */}
          <div className="charts-row">
            {/* Portfolio Growth Chart */}
            <motion.div className="chart-card glassmorphism" initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.25 }}>
              <div className="chart-card-header">
                <h3 className="chart-title">Portfolio Growth</h3>
                <span className="chart-subtitle">Invested vs Current Value</span>
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
                      tickFormatter={v => v >= 100000 ? `₹${(v/100000).toFixed(1)}L` : `₹${(v/1000).toFixed(0)}K`} />
                    <Tooltip content={<CustomTooltip />} />
                    <Area type="monotone" dataKey="invested" stroke="#8C52FF" strokeWidth={3} fill="url(#investedGrad)" name="Invested" />
                    <Area type="monotone" dataKey="value" stroke="#00F298" strokeWidth={3} fill="url(#valueGrad)" name="Current Value" />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="chart-empty">Add transactions to see growth chart</div>
              )}
            </motion.div>

            {/* Portfolio Allocation Pie */}
            <motion.div className="chart-card glassmorphism" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.3 }}>
              <div className="chart-card-header">
                <h3 className="chart-title">Allocation Breakdown</h3>
                <span className="chart-subtitle">By fund current value</span>
              </div>
              {pieData.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <PieChart>
                    <Pie data={pieData} cx="50%" cy="50%" innerRadius={55} outerRadius={90}
                      paddingAngle={3} dataKey="value">
                      {pieData.map((entry, i) => (
                        <Cell key={i} fill={entry.color} stroke="transparent" />
                      ))}
                    </Pie>
                    <Tooltip formatter={(v) => formatCurrency(v)} />
                    <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 11, color: '#A0A0B0' }} />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="chart-empty">No holdings with current value yet</div>
              )}
            </motion.div>
          </div>

          {/* Holdings Table */}
          {data?.holdings?.length > 0 && (
            <motion.div className="holdings-section glassmorphism" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.35 }}>
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
                      <th>Current Value</th>
                      <th>Gain / Loss</th>
                      <th>Abs. Return</th>
                      <th>NAV Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.holdings.filter(h => parseFloat(h.units) > 0 || parseFloat(h.currentValue) > 0).map((h, i) => {
                      const gain = parseFloat(h.gainLoss) || 0;
                      const absRet = parseFloat(h.absoluteReturnPct);
                      return (
                        <motion.tr key={i} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.05 * i }}>
                          <td>
                            <div className="h-fund-name">
                              {h.schemeName || h.schemeAmfiCode}
                              {h.schemeName?.toLowerCase().includes("direct") && <span className="cat-badge p-direct" style={{marginLeft: '8px', fontSize: '10px', background: 'rgba(0,242,152,0.1)', color: '#00F298'}}>DIRECT</span>}
                              {h.schemeName?.toLowerCase().includes("regular") && <span className="cat-badge p-regular" style={{marginLeft: '8px', fontSize: '10px', background: 'rgba(255,178,71,0.1)', color: '#FFB247'}}>REGULAR</span>}
                            </div>
                            <div className="h-folio">{h.folioNumber}</div>
                          </td>
                          <td>
                            <span className={`cat-badge ${h.broadCategory?.toLowerCase()}`}>{h.broadCategory || '—'}</span>
                          </td>
                          <td className="h-num">{formatCurrency(h.investedAmount)}</td>
                          <td className="h-num">{formatCurrency(h.currentValue)}</td>
                          <td className={`h-num ${gain >= 0 ? 'green' : 'red'}`}>
                            {gain >= 0 ? '+' : ''}{formatCurrency(gain)}
                          </td>
                          <td className={`h-num bold ${absRet >= 0 ? 'green' : 'red'}`}>
                            {!isNaN(absRet) ? formatPct(absRet) : '—'}
                          </td>
                          <td className="h-date">{h.lastNavDate || '—'}</td>
                        </motion.tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </motion.div>
          )}
        </>
      )}
    </div>
  );
}
