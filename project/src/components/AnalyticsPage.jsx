import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Activity, ShieldAlert, Network, RefreshCw, AlertTriangle, Zap, Target, BookOpen, BarChart2, CheckCircle2 } from 'lucide-react';
import { ResponsiveContainer, Radar, RadarChart, PolarGrid, PolarAngleAxis } from 'recharts';
import { useAuth } from '../context/AuthContext';
import './AnalyticsPage.css';

import { API_BASE as API } from '../lib/config';

const formatCurrency = (val) => {
  if (val === undefined || val === null) return '₹0.00';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(val);
};

// ─── Main Analytics Page ──────────────────────────────────────────────────────
export default function AnalyticsPage() {
  const { getToken } = useAuth();
  const [activeTab, setActiveTab] = useState('risk');
  const [data, setData] = useState({ risk: null, sip: null, overlap: null });
  const [sipExtra, setSipExtra] = useState({ topup: null, optimize: null });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [accurateMode, setAccurateMode] = useState(false);

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const token = getToken();
      const [resRisk, resSip, resOverlap] = await Promise.all([
        fetch(`${API}/api/analytics/risk`, { headers: { Authorization: `Bearer ${token}` } }),
        fetch(`${API}/api/analytics/sip`, { headers: { Authorization: `Bearer ${token}` } }),
        fetch(`${API}/api/analytics/overlap`, { headers: { Authorization: `Bearer ${token}` } })
      ]);
      if (!resRisk.ok) throw new Error('Failed to load Risk Profile');
      const [risk, sip, overlap] = await Promise.all([resRisk.json(), resSip.json(), resOverlap.json()]);
      setData({ risk, sip, overlap });
      // Non-critical: fetch SIP extras (top-up calculator + optimizer)
      try {
        const [resTopup, resOptimize] = await Promise.all([
          fetch(`${API}/api/sip/topup`,    { headers: { Authorization: `Bearer ${token}` } }),
          fetch(`${API}/api/sip/optimize`, { headers: { Authorization: `Bearer ${token}` } })
        ]);
        if (resTopup.ok && resOptimize.ok) {
          const [topup, optimize] = await Promise.all([resTopup.json(), resOptimize.json()]);
          setSipExtra({ topup, optimize });
        }
      } catch (_) { /* non-critical extras — silently ignore */ }
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { fetchAnalytics(); }, [fetchAnalytics]);

  // ─── Risk Tab ───────────────────────────────────────────────────────────────
  const renderRiskTab = () => {
    if (!data.risk) return null;
    const { portfolioRiskScore, portfolioRiskLabel, diversificationScore, volatilityPct, sharpeRatio,
            maxDrawdownPct, totalFunds, uniqueAmcs, derivedRiskAppetite, derivedRiskAppetiteDescription,
            riskComparison } = data.risk;

    const appetiteColors = { Conservative: '#00D09C', Moderate: '#FFB247', Aggressive: '#FF4D4D' };
    const appetiteColor = appetiteColors[derivedRiskAppetite] || '#8C52FF';
    const riskAppetiteDescription = derivedRiskAppetiteDescription || '';

    let riskBadgeClass = 'risk-Moderate';
    if (portfolioRiskLabel?.includes('Low')) riskBadgeClass = 'risk-Low';
    if (portfolioRiskLabel?.includes('High')) riskBadgeClass = 'risk-High';

    /**
     * Radar chart data — all axes scaled 0-100 using Indian MF benchmarks.
     *
     * Volatility (σ): typical Indian equity MF σ range = 8-25%
     *   ≤8% → 100, 8-15% → 70, 15-22% → 40, >22% → 10
     * Sharpe: anything above 1.0 is excellent for Indian MFs
     *   <0 → 0, 0-0.5 → 30, 0.5-1.0 → 60, 1-1.5 → 80, >1.5 → 100
     * Drawdown safety: drawdown is negative %
     *   0% drawdown → 100, -10% → 70, -20% → 40, -30% → 10
     */
    const volatilityScore = (() => {
      const v = volatilityPct || 0;
      if (v <= 8)  return 100;
      if (v <= 15) return 70;
      if (v <= 22) return 40;
      return 10;
    })();
    const sharpeScore = (() => {
      const s = sharpeRatio || 0;
      if (s < 0)    return 0;
      if (s < 0.5)  return 30;
      if (s < 1.0)  return 60;
      if (s < 1.5)  return 80;
      return 100;
    })();
    const drawdownScore = (() => {
      const d = maxDrawdownPct || 0; // d is positive % (e.g. 5.23 = 5.23% peak-to-trough decline)
      if (d <= 0)   return 100; // no drawdown — perfect
      if (d <= 5)   return 85;  // very minor dip
      if (d <= 10)  return 70;  // normal equity drawdown
      if (d <= 20)  return 40;  // significant correction
      if (d <= 30)  return 10;  // bear market territory
      return 5;                 // severe crash
    })();

    const radarData = [
      { subject: 'Diversification', A: Math.min(100, (diversificationScore || 0) * 10) },
      { subject: 'Volatility Ctrl', A: volatilityScore },
      { subject: 'Sharpe (Risk-Adj)', A: sharpeScore },
      { subject: 'Drawdown Safety', A: drawdownScore },
    ];

    return (
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>

        <div className="intel-grid">
          {/* Portfolio Risk Card */}
          <div className="intel-card glassmorphism">
            <h3 className="card-title"><ShieldAlert size={16} color="#FFB247" /> Portfolio Risk Score</h3>
            <div className="risk-score-display">
              <span className="risk-number">{portfolioRiskScore}/6</span>
              <span className={`risk-badge ${riskBadgeClass}`}>{portfolioRiskLabel}</span>
            </div>
            <p style={{ marginTop: '16px', fontSize: '12px', color: '#A0A0B0', textAlign: 'center' }}>
              Weighted average based on SEBI 6-point Riskometer.
            </p>
          </div>

          {/* Your Risk Appetite — derived from portfolio risk score */}
          <div className="intel-card glassmorphism">
            <div style={{ marginBottom: '12px' }}>
              <h3 className="card-title" style={{ marginBottom: 0 }}><Target size={16} color={appetiteColor} /> Your Risk Appetite</h3>
            </div>
            <div style={{ textAlign: 'center', marginBottom: '12px' }}>
              <span className="risk-number" style={{ fontSize: '34px', color: appetiteColor }}>
                {derivedRiskAppetite || '—'}
              </span>
            </div>
            {/* Spectrum indicator */}
            <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginBottom: '14px' }}>
              {[['Conservative','#00D09C'],['Moderate','#FFB247'],['Aggressive','#FF4D4D']].map(([label, col]) => (
                <div key={label} style={{
                  width: '10px', height: '10px', borderRadius: '50%',
                  background: derivedRiskAppetite === label ? col : 'rgba(255,255,255,0.12)',
                  boxShadow: derivedRiskAppetite === label ? `0 0 8px ${col}` : 'none',
                  transition: 'all 0.3s'
                }} title={label} />
              ))}
            </div>
            <p style={{ fontSize: '11px', color: '#A0A0B0', textAlign: 'center', margin: 0 }}>
              {riskAppetiteDescription}
            </p>
          </div>

          {/* Diversification */}
          <div className="intel-card glassmorphism">
            <h3 className="card-title"><BarChart2 size={16} color="#8C52FF" /> Diversification Score</h3>
            <div className="risk-score-display">
              <span className="risk-number" style={{ color: diversificationScore >= 7 ? '#00D09C' : diversificationScore >= 4 ? '#FFB247' : '#FF4D4D' }}>
                {diversificationScore}/10
              </span>
              <span style={{ marginTop: '8px', fontSize: '13px', color: '#E0E0FF', textAlign: 'center' }}>
                {totalFunds} fund{totalFunds !== 1 ? 's' : ''} · {uniqueAmcs} AMC{uniqueAmcs !== 1 ? 's' : ''}
              </span>
            </div>
          </div>
        </div>

        {/* Advanced Metrics + Radar */}
        <div className="intel-grid" style={{ gridTemplateColumns: '1fr 1.6fr' }}>
          <div className="intel-card glassmorphism">
            <h3 className="card-title">
              Advanced Metrics
              <button
                className={`metrics-toggle ${accurateMode ? 'active' : ''}`}
                onClick={() => setAccurateMode(m => !m)}
                title={accurateMode ? 'Showing: Precise (NAV-based monthly returns)' : 'Showing: Quick (transaction-based snapshot)'}
              >
                <span className="metrics-toggle-dot" />
                <span className="metrics-toggle-label">{accurateMode ? 'Precise' : 'Quick'}</span>
              </button>
            </h3>
            {(() => {
              const dVol = accurateMode ? (data.risk.accurateVolatilityPct ?? volatilityPct) : volatilityPct;
              const dSharpe = accurateMode ? (data.risk.accurateSharpeRatio ?? sharpeRatio) : sharpeRatio;
              const dDD = accurateMode ? (data.risk.accurateMaxDrawdownPct ?? maxDrawdownPct) : maxDrawdownPct;
              return (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1, justifyContent: 'center' }}>
                  {[
                    { label: 'Annualised Volatility (σ)', value: `${dVol}%`, color: '#fff',
                      note: accurateMode ? 'Monthly return σ × √12 from real NAV history' : 'Transaction-snapshot σ × √12 (approximate)' },
                    { label: 'Sharpe Ratio', value: dSharpe, color: dSharpe >= 1 ? '#00D09C' : '#FFB247',
                      note: dSharpe >= 1.5 ? 'Excellent' : dSharpe >= 1 ? 'Good' : dSharpe >= 0.5 ? 'Adequate' : 'Poor' },
                    { label: 'Max Drawdown', value: `${dDD}%`, color: '#FF4D4D',
                      note: accurateMode ? 'Ratio-based peak-to-trough (deposit-adjusted)' : 'Peak-to-trough decline in portfolio value' },
                  ].map(({ label, value, color, note }) => (
                    <div key={label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ color: '#A0A0B0', fontSize: '13px' }}>{label}</span>
                      <div style={{ textAlign: 'right' }}>
                        <span style={{ fontWeight: 700, color }}>{value}</span>
                        {note && <div style={{ fontSize: '10px', color, opacity: 0.7 }}>{note}</div>}
                      </div>
                    </div>
                  ))}
                </div>
              );
            })()}
          </div>

          <div className="intel-card glassmorphism" style={{ height: '300px' }}>
            <h3 className="card-title">Risk Web Profile</h3>
            <ResponsiveContainer width="100%" height="90%">
              <RadarChart cx="50%" cy="50%" outerRadius="80%" data={radarData}>
                <PolarGrid stroke="rgba(255,255,255,0.08)" />
                <PolarAngleAxis dataKey="subject" tick={{ fill: '#A0A0B0', fontSize: 11 }} />
                <Radar name="Portfolio" dataKey="A" stroke="#8C52FF" fill="#8C52FF" fillOpacity={0.3} />
              </RadarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </motion.div>
    );
  };

  // ─── SIP Tab ────────────────────────────────────────────────────────────────
  const renderSipTab = () => {
    if (!data.sip) return null;
    const { activeSips, totalSipOutflow, sipStreak, analysis } = data.sip;
    return (
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
        <div className="intel-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
          {[
            { icon: <Zap size={30} color="#00D09C" />, value: activeSips, label: 'Active SIPs' },
            { icon: <RefreshCw size={30} color="#8C52FF" />, value: formatCurrency(totalSipOutflow), label: 'Monthly Outflow' },
            { icon: <Activity size={30} color="#FFB247" />, value: sipStreak, label: 'SIP Streak', small: true },
          ].map(({ icon, value, label, small }) => (
            <div key={label} className="intel-card glassmorphism" style={{ alignItems: 'center', justifyContent: 'center', textAlign: 'center', gap: '12px' }}>
              {icon}
              <div style={{ fontSize: small ? '18px' : '32px', fontWeight: 800 }}>{value}</div>
              <div style={{ fontSize: '12px', color: '#A0A0B0', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{label}</div>
            </div>
          ))}
        </div>
        <div className="intel-card glassmorphism">
          <h3 className="card-title"><BookOpen size={16} color="#8C52FF" /> SIP vs Lumpsum Analyzer</h3>
          <p style={{ fontSize: '13px', color: '#A0A0B0', marginBottom: '20px', lineHeight: 1.6 }}>
            Mathematical backtest: if you had invested the <strong>total SIP corpus</strong> as a lumpsum on the <strong>first SIP date</strong>, would it have performed better or worse than Rupee Cost Averaging?
          </p>
          {analysis?.length > 0 ? (
            <div style={{ overflowX: 'auto' }}>
              <table className="sip-comparison-table">
                <thead><tr><th>Fund</th><th>SIP Actual Value</th><th>SIP Return %</th><th>Lumpsum Value (hypothetical)</th><th>Lumpsum Return %</th><th>Difference</th><th>Winner</th></tr></thead>
                <tbody>
                  {analysis.map((a, i) => (
                    <tr key={i}>
                      <td style={{ fontWeight: 600, maxWidth: '200px', wordBreak: 'break-word' }}>{a.fundName}</td>
                      <td>{formatCurrency(a.sipValue)}</td>
                      <td style={{ color: (a.sipAbsReturn ?? 0) >= 0 ? '#00D09C' : '#FF4D4D', fontWeight: 600 }}>
                        {a.sipAbsReturn != null ? ((a.sipAbsReturn >= 0 ? '+' : '') + parseFloat(a.sipAbsReturn).toFixed(2) + '%') : '—'}
                      </td>
                      <td>{formatCurrency(a.lumpsumValue)}</td>
                      <td style={{ color: (a.lumpsumAbsReturn ?? 0) >= 0 ? '#00D09C' : '#FF4D4D', fontWeight: 600 }}>
                        {a.lumpsumAbsReturn != null ? ((a.lumpsumAbsReturn >= 0 ? '+' : '') + parseFloat(a.lumpsumAbsReturn).toFixed(2) + '%') : '—'}
                      </td>
                      <td style={{ color: a.difference >= 0 ? '#00D09C' : '#FF4D4D', fontWeight: 700 }}>
                        {a.difference >= 0 ? '+' : ''}{formatCurrency(a.difference)}
                      </td>
                      <td className={a.winner?.includes('SIP') ? 'sip-winner' : 'lump-winner'}>{a.winner}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px', color: '#A0A0B0' }}>
              No active SIP history found. Add SIP transactions to see this analysis.
            </div>
          )}
        </div>

        {/* ── Top-Up Power Calculator ── */}
        {sipExtra.topup && (
          <div className="intel-card glassmorphism">
            <h3 className="card-title">
              <Zap size={16} color="#FFB247" /> Top-Up Power Calculator
            </h3>
            <p style={{ fontSize: '13px', color: '#A0A0B0', marginBottom: '20px', lineHeight: 1.6 }}>
              Based on your <strong style={{ color: '#E0E0FF' }}>{formatCurrency(sipExtra.topup.monthlyAmount)}/month</strong> SIP
              over <strong style={{ color: '#E0E0FF' }}>{sipExtra.topup.years} years</strong> at 12% p.a. —
              see how a <strong style={{ color: '#FFB247' }}>{sipExtra.topup.stepUpPct}% annual step-up</strong> changes your final corpus.
            </p>
            <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginBottom: '24px' }}>
              {[
                { label: 'Without Step-Up', value: formatCurrency(sipExtra.topup.withoutTopUp), color: '#A0A0B0', bg: 'rgba(160,160,176,0.08)' },
                { label: `With ${sipExtra.topup.stepUpPct}% Annual Step-Up`, value: formatCurrency(sipExtra.topup.withTopUp), color: '#00D09C', bg: 'rgba(0,208,156,0.08)' },
                { label: 'Extra Wealth Potential', value: formatCurrency(sipExtra.topup.difference), color: '#FFB247', bg: 'rgba(255,178,71,0.08)' },
              ].map(({ label, value, color, bg }) => (
                <div key={label} style={{ flex: 1, minWidth: '180px', padding: '16px', borderRadius: '12px', background: bg, border: `1px solid ${color}22` }}>
                  <div style={{ fontSize: '11px', color: '#A0A0B0', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '8px' }}>{label}</div>
                  <div style={{ fontSize: '22px', fontWeight: 800, color }}>{value}</div>
                </div>
              ))}
            </div>
            {(() => {
              const max = sipExtra.topup.withTopUp;
              const pctFlat = max > 0 ? (sipExtra.topup.withoutTopUp / max) * 100 : 0;
              return (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {[{ label: 'Flat SIP', pct: pctFlat, color: '#A0A0B0' },
                    { label: 'Step-Up SIP', pct: 100, color: '#00D09C' }].map(({ label, pct, color }) => (
                    <div key={label}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', fontSize: '12px', color: '#A0A0B0' }}>
                        <span>{label}</span><span style={{ color }}>{pct.toFixed(0)}%</span>
                      </div>
                      <div style={{ height: '8px', borderRadius: '4px', background: 'rgba(255,255,255,0.06)', overflow: 'hidden' }}>
                        <div style={{ height: '100%', width: `${pct}%`, borderRadius: '4px', background: color, transition: 'width 1s ease' }} />
                      </div>
                    </div>
                  ))}
                </div>
              );
            })()}
            <p style={{ marginTop: '16px', fontSize: '11px', color: '#606080', lineHeight: 1.5 }}>
              Step-up SIP: each April, your monthly investment automatically increases by {sipExtra.topup.stepUpPct}%,
              compounding wealth far beyond a flat SIP at the same initial amount.
            </p>
          </div>
        )}

        {/* ── SIP Day Optimizer ── */}
        {sipExtra.optimize && (
          <div className="intel-card glassmorphism">
            <h3 className="card-title">
              <CheckCircle2 size={16} color="#00D09C" /> SIP Day Optimizer
            </h3>
            <div style={{ display: 'flex', gap: '16px', alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '13px', color: '#C0C0D0', lineHeight: 1.7 }}>{sipExtra.optimize.tip}</p>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '160px' }}>
                <div style={{ fontSize: '11px', color: '#A0A0B0', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Recommended Days</div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {sipExtra.optimize.bestDays?.map(d => (
                    <span key={d} style={{ width: '36px', height: '36px', borderRadius: '50%', background: 'rgba(0,208,156,0.15)', border: '1px solid rgba(0,208,156,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, color: '#00D09C', fontSize: '14px' }}>{d}</span>
                  ))}
                </div>
                <div style={{ marginTop: '4px', fontSize: '11px', color: '#FF4D4D' }}>
                  ⚠️ Avoid: {sipExtra.optimize.avoid}
                </div>
              </div>
            </div>
          </div>
        )}
      </motion.div>
    );
  };

  // ─── Overlap Tab ─────────────────────────────────────────────────────────────
  const renderOverlapTab = () => {
    if (!data.overlap) return null;
    const { links, nodes, averageCategorySimilarityPct, disclaimer } = data.overlap;
    const avgPct = averageCategorySimilarityPct ?? 0;
    const overlapColor = avgPct > 35 ? '#FF4D4D' : avgPct > 20 ? '#FFB247' : '#00D09C';
    return (
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
        <div className="intel-card glassmorphism" style={{ marginBottom: '24px' }}>
          <div style={{ display: 'flex', gap: '24px', alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <h3 className="card-title"><Network size={16} color="#00F298" /> Portfolio Category Similarity</h3>
              <p style={{ fontSize: '13px', color: '#A0A0B0', lineHeight: 1.6 }}>
                Measures how similar your funds are by SEBI category. Funds in the same category often share top holdings.
              </p>
              {disclaimer && (
                <p style={{ fontSize: '11px', color: '#FFB247', marginTop: '8px', padding: '8px 12px',
                  background: 'rgba(255,178,71,0.08)', borderRadius: '8px', border: '1px solid rgba(255,178,71,0.2)' }}>
                  ⚠️ {disclaimer}
                </p>
              )}
            </div>
            <div style={{ textAlign: 'center', padding: '20px', background: 'rgba(0,0,0,0.2)', borderRadius: '12px', minWidth: '100px' }}>
              <div style={{ fontSize: '36px', fontWeight: 800, color: overlapColor }}>{avgPct}%</div>
              <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#A0A0B0', marginTop: '4px' }}>Avg Similarity</div>
              <div style={{ fontSize: '11px', color: overlapColor, marginTop: '4px', fontWeight: 600 }}>
                {avgPct > 35 ? '⚠️ High' : avgPct > 20 ? '⚡ Moderate' : '✅ Low'}
              </div>
            </div>
          </div>
        </div>
        <div className="intel-card glassmorphism">
          <h3 className="card-title">Pairwise Category Similarity Matrix</h3>
          {links?.length > 0 ? (
            <table className="sip-comparison-table">
              <thead><tr><th>Fund A</th><th>Fund B</th><th>Category Similarity</th><th>Level</th></tr></thead>
              <tbody>
                {links.sort((a, b) => b.categorySimilarityPct - a.categorySimilarityPct).map((link, i) => {
                  const nameA = nodes?.find(n => n.id === link.source)?.name || link.source;
                  const nameB = nodes?.find(n => n.id === link.target)?.name || link.target;
                  const pct = link.categorySimilarityPct;
                  const col = pct > 35 ? '#FF4D4D' : pct > 20 ? '#FFB247' : '#00D09C';
                  return (
                    <tr key={i}>
                      <td style={{ fontSize: '13px', maxWidth: '200px' }}>{nameA}</td>
                      <td style={{ fontSize: '13px', maxWidth: '200px' }}>{nameB}</td>
                      <td style={{ fontWeight: 700, color: col }}>{pct}%</td>
                      <td style={{ color: col, fontWeight: 600 }}>
                        {pct > 35 ? 'High' : pct > 20 ? 'Moderate' : 'Low'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px', color: '#A0A0B0' }}>
              No overlapping funds detected. Add more equity funds to see overlap analysis.
            </div>
          )}
        </div>
      </motion.div>
    );
  };


  return (
    <div className="analytics-page">
      <div className="analytics-container">
        <div className="analytics-header">
          <h1><Activity size={30} color="#8C52FF" /> Intelligence & Analytics</h1>
          <p className="analytics-subtitle">Advanced SEBI-compliant metrics — Risk Profiler, SIP Engine, Fund Overlap (M11, M12, M13)</p>
        </div>

        <div className="analytics-tabs">
          <button className={`analytics-tab ${activeTab === 'risk' ? 'active' : ''}`} onClick={() => setActiveTab('risk')}>
            <ShieldAlert size={15} /> Risk Profiler
          </button>
          <button className={`analytics-tab ${activeTab === 'sip' ? 'active' : ''}`} onClick={() => setActiveTab('sip')}>
            <BarChart2 size={15} /> SIP Intelligence
          </button>
          <button className={`analytics-tab ${activeTab === 'overlap' ? 'active' : ''}`} onClick={() => setActiveTab('overlap')}>
            <Network size={15} /> Fund Overlap
          </button>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '100px 0' }}>
            <div style={{ width: 40, height: 40, border: '3px solid rgba(140,82,255,0.2)', borderTopColor: '#8C52FF', 
              borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }} />
            <p style={{ color: '#A0A0B0' }}>Loading your analytics...</p>
          </div>
        ) : error ? (
          <div className="intel-card glassmorphism" style={{ textAlign: 'center', color: '#FF4D4D', padding: '40px' }}>
            <AlertTriangle size={32} style={{ margin: '0 auto 12px' }} />
            <p>{error}</p>
            <button onClick={fetchAnalytics} style={{ marginTop: '12px', padding: '8px 16px', background: 'rgba(255,77,77,0.1)', border: '1px solid rgba(255,77,77,0.3)', color: '#FF4D4D', borderRadius: '8px', cursor: 'pointer' }}>Retry</button>
          </div>
        ) : (
          <AnimatePresence mode="wait">
            {activeTab === 'risk' && renderRiskTab()}
            {activeTab === 'sip' && renderSipTab()}
            {activeTab === 'overlap' && renderOverlapTab()}
          </AnimatePresence>
        )}

        {/* SEBI Mandatory Risk Disclosure */}
        <div style={{
          marginTop: 32, padding: '12px 16px',
          background: 'rgba(255,193,7,0.06)',
          border: '1px solid rgba(255,193,7,0.18)',
          borderRadius: 10,
          fontSize: 11,
          color: '#A0A0B0',
          lineHeight: 1.6
        }}>
          <span style={{ color: '#FFD700', fontWeight: 700, marginRight: 6 }}>⚠ Regulatory Disclosure:</span>
          Mutual fund investments are subject to market risks. Past performance does not guarantee future results.
          Risk scores, Sharpe ratios, volatility, and SIP projections shown here are calculated from historical data and assumed
          return rates — actual returns may vary significantly. This platform is for informational and portfolio-tracking purposes only
          and does not constitute investment advice. Please read all scheme-related documents carefully before investing.
        </div>

      </div>

    </div>
  );
}
