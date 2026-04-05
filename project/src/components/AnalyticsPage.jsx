import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Activity, ShieldAlert, Network, RefreshCw, AlertTriangle, Zap, Target, BookOpen, BarChart2, CheckCircle2, ChevronRight } from 'lucide-react';
import { ResponsiveContainer, Radar, RadarChart, PolarGrid, PolarAngleAxis } from 'recharts';
import { useAuth } from '../context/AuthContext';
import './AnalyticsPage.css';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const formatCurrency = (val) => {
  if (val === undefined || val === null) return '₹0.00';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(val);
};

// ─── Risk Survey ──────────────────────────────────────────────────────────────
const SURVEY_QUESTIONS = [
  {
    id: 'q1',
    question: 'If your portfolio dropped 30% in a market crash, what would you do?',
    options: [
      { label: 'Sell everything to stop further losses', value: 'CONSERVATIVE' },
      { label: 'Do nothing and wait for recovery', value: 'MODERATE' },
      { label: 'Buy more — great opportunity!', value: 'AGGRESSIVE' },
    ],
  },
  {
    id: 'q2',
    question: 'What is your primary investment goal?',
    options: [
      { label: 'Protect my money (capital preservation)', value: 'CONSERVATIVE' },
      { label: 'Grow steadily with moderate risk', value: 'MODERATE' },
      { label: 'Maximise long-term wealth, I can handle volatility', value: 'AGGRESSIVE' },
    ],
  },
  {
    id: 'q3',
    question: "What is your investment time horizon?",
    options: [
      { label: 'Less than 3 years (short-term)', value: 'CONSERVATIVE' },
      { label: '3 to 7 years (medium-term)', value: 'MODERATE' },
      { label: 'More than 7 years (long-term)', value: 'AGGRESSIVE' },
    ],
  },
];

function RiskSurveyModal({ onComplete, onClose, token }) {
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState([]);
  const [saving, setSaving] = useState(false);

  const handleAnswer = (value) => {
    const newAnswers = [...answers, value];
    setAnswers(newAnswers);
    if (step < SURVEY_QUESTIONS.length - 1) {
      setStep(s => s + 1);
    } else {
      // Determine final profile
      const counts = { CONSERVATIVE: 0, MODERATE: 0, AGGRESSIVE: 0 };
      newAnswers.forEach(a => { if (counts[a] !== undefined) counts[a]++; });
      const profile = Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
      submitProfile(profile);
    }
  };

  const submitProfile = async (profile) => {
    setSaving(true);
    try {
      await fetch(`${API}/api/analytics/risk-profile`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ riskProfile: profile }),
      });
      onComplete(profile);
    } catch (e) {
      onComplete(profile); // Still show result even on error
    }
  };

  const q = SURVEY_QUESTIONS[step];
  const progress = ((step) / SURVEY_QUESTIONS.length) * 100;

  return (
    <motion.div className="modal-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      onClick={onClose}>
      <motion.div className="survey-modal glassmorphism" initial={{ scale: 0.92, y: 30 }} animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.92, y: 30 }} onClick={e => e.stopPropagation()}>

        {saving ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <RefreshCw size={40} color="#8C52FF" style={{ animation: 'spin 1s linear infinite', margin: '0 auto 16px' }} />
            <p style={{ color: '#E0E0FF' }}>Saving your risk profile...</p>
          </div>
        ) : (
          <>
            <div style={{ marginBottom: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span style={{ fontSize: '12px', color: '#A0A0B0' }}>Question {step + 1} of {SURVEY_QUESTIONS.length}</span>
                <span style={{ fontSize: '12px', color: '#8C52FF' }}>{Math.round(progress)}% done</span>
              </div>
              <div style={{ height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px' }}>
                <motion.div style={{ height: '100%', background: 'linear-gradient(90deg, #8C52FF, #00F298)', borderRadius: '2px' }}
                  animate={{ width: `${progress}%` }} transition={{ duration: 0.3 }} />
              </div>
            </div>

            <h3 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '28px', color: '#fff', lineHeight: 1.4 }}>
              {q.question}
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {q.options.map((opt, i) => (
                <motion.button key={i} className="survey-option" onClick={() => handleAnswer(opt.value)}
                  whileHover={{ x: 4 }} whileTap={{ scale: 0.98 }}>
                  <ChevronRight size={16} style={{ opacity: 0.5, flexShrink: 0 }} />
                  {opt.label}
                </motion.button>
              ))}
            </div>
          </>
        )}
      </motion.div>
    </motion.div>
  );
}

// ─── Main Analytics Page ──────────────────────────────────────────────────────
export default function AnalyticsPage() {
  const { getToken } = useAuth();
  const [activeTab, setActiveTab] = useState('risk');
  const [data, setData] = useState({ risk: null, sip: null, overlap: null });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showSurvey, setShowSurvey] = useState(false);

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
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { fetchAnalytics(); }, [fetchAnalytics]);

  const handleSurveyComplete = (profile) => {
    setShowSurvey(false);
    fetchAnalytics(); // refresh with new profile
  };

  // ─── Risk Tab ───────────────────────────────────────────────────────────────
  const renderRiskTab = () => {
    if (!data.risk) return null;
    const { portfolioRiskScore, portfolioRiskLabel, diversificationScore, volatilityPct, sharpeRatio,
            maxDrawdownPct, totalFunds, uniqueAmcs, userRiskProfile, riskComparison } = data.risk;

    const profileColors = { CONSERVATIVE: '#00D09C', MODERATE: '#FFB247', AGGRESSIVE: '#FF4D4D' };
    const profileColor = profileColors[userRiskProfile] || '#8C52FF';

    let riskBadgeClass = 'risk-Moderate';
    if (portfolioRiskLabel?.includes('Low')) riskBadgeClass = 'risk-Low';
    if (portfolioRiskLabel?.includes('High')) riskBadgeClass = 'risk-High';

    const radarData = [
      { subject: 'Diversification', A: Math.min(100, (diversificationScore || 0) * 10) },
      { subject: 'Volatility Ctrl', A: Math.max(0, 100 - (volatilityPct || 0) * 3) },
      { subject: 'Sharpe (Risk-Adj)', A: Math.min(100, (sharpeRatio || 0) * 50) },
      { subject: 'Drawdown Safety', A: Math.max(0, 100 + (maxDrawdownPct || 0)) },
    ];

    return (
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
        {/* Risk Comparison Banner */}
        {riskComparison && (
          <div style={{ padding: '14px 18px', background: 'rgba(140,82,255,0.1)', border: '1px solid rgba(140,82,255,0.25)',
            borderRadius: '12px', marginBottom: '20px', fontSize: '14px', color: '#E0E0FF', lineHeight: 1.5 }}>
            {riskComparison}
          </div>
        )}

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

          {/* User's Risk Tolerance */}
          <div className="intel-card glassmorphism">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 className="card-title" style={{ marginBottom: 0 }}><Target size={16} color={profileColor} /> Your Risk Appetite</h3>
              <motion.button onClick={() => setShowSurvey(true)} whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}
                style={{ fontSize: '11px', padding: '4px 10px', borderRadius: '6px', border: `1px solid ${profileColor}40`,
                  background: `${profileColor}15`, color: profileColor, cursor: 'pointer', fontWeight: 600 }}>
                Retake Survey
              </motion.button>
            </div>
            <div className="risk-score-display">
              <span className="risk-number" style={{ fontSize: '36px', color: profileColor }}>{userRiskProfile}</span>
              <p style={{ fontSize: '12px', color: '#A0A0B0', marginTop: '8px', textAlign: 'center' }}>
                {userRiskProfile === 'CONSERVATIVE' && 'You prefer capital safety over high returns.'}
                {userRiskProfile === 'MODERATE' && 'You balance growth with measured risk.'}
                {userRiskProfile === 'AGGRESSIVE' && 'You seek maximum long-term growth.'}
              </p>
            </div>
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
            <h3 className="card-title">Advanced Metrics</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1, justifyContent: 'center' }}>
              {[
                { label: 'Annualized Volatility', value: `${volatilityPct}%`, color: '#fff' },
                { label: 'Sharpe Ratio', value: sharpeRatio, color: sharpeRatio >= 1 ? '#00D09C' : '#FFB247',
                  note: sharpeRatio >= 1.5 ? 'Excellent' : sharpeRatio >= 1 ? 'Good' : sharpeRatio >= 0.5 ? 'Adequate' : 'Poor' },
                { label: 'Max Drawdown', value: `${maxDrawdownPct}%`, color: '#FF4D4D' },
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
                <thead><tr><th>Fund</th><th>SIP Value (actual)</th><th>Lumpsum Value (hypothetical)</th><th>Difference</th><th>Winner</th></tr></thead>
                <tbody>
                  {analysis.map((a, i) => (
                    <tr key={i}>
                      <td style={{ fontWeight: 600, maxWidth: '200px', wordBreak: 'break-word' }}>{a.fundName}</td>
                      <td>{formatCurrency(a.sipValue)}</td>
                      <td>{formatCurrency(a.lumpsumValue)}</td>
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
      </motion.div>
    );
  };

  // ─── Overlap Tab ─────────────────────────────────────────────────────────────
  const renderOverlapTab = () => {
    if (!data.overlap) return null;
    const { links, nodes, averageOverlapPct } = data.overlap;
    const overlapColor = averageOverlapPct > 35 ? '#FF4D4D' : averageOverlapPct > 20 ? '#FFB247' : '#00D09C';
    return (
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
        <div className="intel-card glassmorphism" style={{ marginBottom: '24px' }}>
          <div style={{ display: 'flex', gap: '24px', alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <h3 className="card-title"><Network size={16} color="#00F298" /> Portfolio Overlap Analysis (MVP)</h3>
              <p style={{ fontSize: '13px', color: '#A0A0B0', lineHeight: 1.6 }}>
                Fund overlap occurs when multiple funds hold the same underlying stocks, reducing true diversification. 
                Overlap is estimated via SEBI category matching — funds in the same category typically share <strong>40-55% of their top holdings</strong>.
              </p>
            </div>
            <div style={{ textAlign: 'center', padding: '20px', background: 'rgba(0,0,0,0.2)', borderRadius: '12px', minWidth: '100px' }}>
              <div style={{ fontSize: '36px', fontWeight: 800, color: overlapColor }}>{averageOverlapPct}%</div>
              <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#A0A0B0', marginTop: '4px' }}>Avg Overlap</div>
              <div style={{ fontSize: '11px', color: overlapColor, marginTop: '4px', fontWeight: 600 }}>
                {averageOverlapPct > 35 ? '⚠️ High' : averageOverlapPct > 20 ? '⚡ Moderate' : '✅ Low'}
              </div>
            </div>
          </div>
        </div>
        <div className="intel-card glassmorphism">
          <h3 className="card-title">Pairwise Overlap Matrix</h3>
          {links?.length > 0 ? (
            <table className="sip-comparison-table">
              <thead><tr><th>Fund A</th><th>Fund B</th><th>Estimated Overlap</th><th>Risk</th></tr></thead>
              <tbody>
                {links.sort((a, b) => b.overlapPct - a.overlapPct).map((link, i) => {
                  const nameA = nodes?.find(n => n.id === link.source)?.name || link.source;
                  const nameB = nodes?.find(n => n.id === link.target)?.name || link.target;
                  const col = link.overlapPct > 35 ? '#FF4D4D' : link.overlapPct > 20 ? '#FFB247' : '#00D09C';
                  return (
                    <tr key={i}>
                      <td style={{ fontSize: '13px', maxWidth: '200px' }}>{nameA}</td>
                      <td style={{ fontSize: '13px', maxWidth: '200px' }}>{nameB}</td>
                      <td style={{ fontWeight: 700, color: col }}>{link.overlapPct}%</td>
                      <td style={{ color: col, fontWeight: 600 }}>
                        {link.overlapPct > 35 ? 'High' : link.overlapPct > 20 ? 'Moderate' : 'Low'}
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
      </div>

      {/* Survey Modal */}
      <AnimatePresence>
        {showSurvey && (
          <RiskSurveyModal
            onComplete={handleSurveyComplete}
            onClose={() => setShowSurvey(false)}
            token={getToken()}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
