import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Sparkles, AlertTriangle } from 'lucide-react';
import { formatINR } from '../lib/goalHelpers';
import { API_BASE } from '../lib/config';

// ── Theme-Aligned Probability Ring ───────────────────────────
function ProbabilityRing({ probability }) {
  const radius = 40;
  const stroke = 7;
  const circumference = 2 * Math.PI * radius;
  const filled = (probability / 100) * circumference;
  const color = probability >= 60 ? "#00D09C" : probability >= 40 ? "#FFB247" : "#FF4D4D";
  
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "1.25rem", marginTop: "1rem" }}>
      <svg width="100" height="100" style={{ flexShrink: 0 }}>
        <circle cx="50" cy="50" r={radius} fill="none" stroke="var(--input-border, rgba(128,128,128,0.2))" strokeWidth={stroke} />
        <circle cx="50" cy="50" r={radius} fill="none"
          stroke={color} strokeWidth={stroke} strokeDasharray={`${filled} ${circumference}`}
          strokeLinecap="round" transform="rotate(-90 50 50)"
        />
        <text x="50" y="50" textAnchor="middle" dominantBaseline="middle"
          fill={color} fontSize="15" fontWeight="700">
          {probability}%
        </text>
      </svg>
      <div>
        <div style={{ fontWeight: 700, fontSize: "1rem", color, marginBottom: "0.25rem" }}>
          {probability >= 60 ? "You're likely on track 🎉" : probability >= 40 ? "There's a reasonable chance" : "This goal needs attention"}
        </div>
        <p className="gw-hint">
          Based on 10,000 possible market scenarios, there is a <strong style={{ color }}>{probability}% chance</strong> your investments reach your goal.
        </p>
      </div>
    </div>
  );
}

// ── Theme-Aligned Outcome Range Bar ───────────────────────────
function OutcomeRangeBar({ pessimistic, likely, optimistic, target }) {
  if ([pessimistic, likely, optimistic, target].some((v) => !isFinite(v))) return null;

  const max  = Math.max(optimistic * 1.1, target * 1.1);
  const pPct = (pessimistic / max) * 100;
  const lPct = (likely      / max) * 100;
  const oPct = (optimistic  / max) * 100;
  const tPct = Math.min((target   / max) * 100, 99);

  return (
    <div style={{ marginTop: "1.5rem" }}>
      <p className="gw-label" style={{ marginBottom: "2.5rem" }}>Range of possible outcomes</p>
      <div style={{ position: "relative", height: "2.5rem", marginBottom: "0.5rem" }}>
        {[
          { pct: pPct, color: "#FF4D4D", label: "Worst case",  value: formatINR(pessimistic) },
          { pct: lPct, color: "var(--accent-purple, #8C52FF)", label: "Most likely", value: formatINR(likely) },
          { pct: oPct, color: "#00D09C", label: "Best case",   value: formatINR(optimistic) },
        ].map(({ pct, color, label, value }) => (
          <div key={label} style={{ position: "absolute", left: `${pct}%`, transform: "translateX(-50%)", textAlign: "center", whiteSpace: "nowrap" }}>
            <div style={{ fontSize: "10px", color, fontWeight: 700, textTransform: "uppercase" }}>{label}</div>
            <div style={{ fontSize: "12px", color: "var(--text-primary)", fontWeight: 600 }}>{value}</div>
          </div>
        ))}
      </div>

      <div style={{ position: "relative", height: "1.4rem", marginBottom: "0.25rem" }}>
        <div style={{ position: "absolute", left: `${tPct}%`, transform: "translateX(-50%)", whiteSpace: "nowrap", fontSize: "11px", color: "var(--text-tertiary)", fontWeight: 600 }}>
          Your goal {formatINR(target)}
        </div>
      </div>

      <div style={{ position: "relative", height: "36px", borderRadius: "99px", background: "var(--input-border, rgba(128,128,128,0.2))" }}>
        <div style={{ position: "absolute", left: `${pPct}%`, width: `${oPct - pPct}%`, height: "100%", background: "linear-gradient(90deg, rgba(255,77,77,0.3), rgba(140,82,255,0.4), rgba(0,208,156,0.3))", borderRadius: "99px" }} />
        <div style={{ position: "absolute", left: `${tPct}%`, top: 0, height: "100%", width: "2px", background: "var(--text-secondary)", borderRadius: "2px" }} />
        {[
          { pct: pPct, color: "#FF4D4D" },
          { pct: lPct, color: "var(--accent-purple, #8C52FF)" },
          { pct: oPct, color: "#00D09C" },
        ].map(({ pct, color }, i) => (
          <div key={i} style={{ position: "absolute", left: `${pct}%`, top: "50%", transform: "translate(-50%, -50%)", zIndex: 2, width: "14px", height: "14px", borderRadius: "50%", background: color, border: "2px solid var(--bg, #0A0A0F)", boxShadow: "0 1px 4px rgba(0,0,0,0.5)" }} />
        ))}
      </div>
    </div>
  );
}

// ── Main Modal Component ────────────────────────────────────────
export default function GoalAnalysisModal({ goal, currentValue, onClose }) {
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchAnalysis();
  }, [goal.id]);

  const fetchAnalysis = async () => {
    setLoading(true); setError(null);
    try {
      const payload = {
        initialPortfolio: Number(currentValue) || 0.01,
        monthlyContribution: Number(goal.monthly_sip_allocated) || 0,
        monthlyMean: Number(goal.expected_return_rate) / 12,
        monthlyStdDev: 0.045, 
        months: Math.max(Math.round(Number(goal.years_remaining) * 12), 1),
        targetAmount: Number(goal.target_amount_today),
        annualInflationRate: Number(goal.inflation_rate)
      };

      // Use the Spring Boot backend via API_BASE with JWT auth
      const token = localStorage.getItem('ww_token');
      const headers = { "Content-Type": "application/json" };
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const response = await fetch(`${API_BASE}/api/learn/analyse`, {
        method: "POST",
        headers,
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`Server error: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();
      setAnalysis(data);
    } catch (err) {
      setError(err.message || "Failed to analyze goal.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatePresence>
      <motion.div className="gw-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
        <motion.div className="gw-modal glassmorphism" style={{ maxWidth: 600, maxHeight: '85vh' }}
          initial={{ opacity: 0, y: 40, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 40 }}
          onClick={e => e.stopPropagation()}
        >
          {/* Header */}
          <div className="gw-header">
            <div>
              <h2 className="gw-title">Goal Analysis</h2>
              <span className="gw-step-label">{goal.goal_icon} {goal.goal_name}</span>
            </div>
            <button className="gw-close" onClick={onClose}><X size={18} /></button>
          </div>

          {/* Body */}
          <div className="gw-body" style={{ paddingBottom: '30px' }}>
            {loading ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '40px 0', gap: '16px' }}>
                <div style={{ width: 30, height: 30, border: '3px solid rgba(0,208,156,0.2)', borderTopColor: '#00D09C', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
                <p className="gw-hint">Running 10,000 market simulations...</p>
              </div>
            ) : error ? (
              <div className="gw-info-banner danger"><AlertTriangle size={14} /> {error}</div>
            ) : analysis ? (
              <div style={{ display: "flex", flexDirection: "column", gap: "32px" }}>
                
                {/* Section 1: Probability & Range */}
                <div>
                  <h3 className="gw-step-title" style={{ marginBottom: "8px" }}>Will I reach my goal?</h3>
                  <p className="gw-hint">Your target is <strong>{formatINR(goal.target_amount_today)}</strong> in today's money.</p>
                  
                  <ProbabilityRing probability={analysis.monteCarlo.probability} />
                  
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10px", marginTop: "20px" }}>
                    {[
                      { label: "Bad Markets",  value: analysis.monteCarlo.pessimistic, color: "#FF4D4D" },
                      { label: "Expected",     value: analysis.monteCarlo.likely,      color: "var(--accent-purple, #8C52FF)" },
                      { label: "Good Markets", value: analysis.monteCarlo.optimistic,  color: "#00D09C" },
                    ].map((card) => (
                      <div key={card.label} className="gc-stat" style={{ padding: "12px 8px", border: `1px solid ${card.color}40` }}>
                        <div className="gc-stat-label" style={{ color: card.color }}>{card.label}</div>
                        <div className="gc-stat-value" style={{ color: "var(--text-primary)" }}>{formatINR(card.value)}</div>
                      </div>
                    ))}
                  </div>

                  <OutcomeRangeBar
                    pessimistic={analysis.monteCarlo.pessimistic} likely={analysis.monteCarlo.likely}
                    optimistic={analysis.monteCarlo.optimistic} target={goal.target_amount_today}
                  />
                </div>

                {/* Section 2: Deterministic Baseline */}
                <div>
                  <h3 className="gw-step-title" style={{ marginBottom: "12px" }}>Expected Baseline</h3>
                  <div className="gw-review-table" style={{ marginBottom: "12px" }}>
                    <div className="gw-review-row"><span>Growth on current savings</span><span style={{ color: "var(--text-primary)" }}>{formatINR(analysis.deterministic.fvCorpus)}</span></div>
                    <div className="gw-review-row"><span>Growth from SIPs</span><span style={{ color: "var(--text-primary)" }}>{formatINR(analysis.deterministic.fvSip)}</span></div>
                    <div className="gw-review-row"><span>Total Projected (Real terms)</span><span style={{ color: "var(--text-primary)" }}>{formatINR(analysis.deterministic.totalProjected)}</span></div>
                  </div>

                  {analysis.deterministic.onTrack ? (
                    <div className="gw-info-banner success"><Sparkles size={14} /> ✅ You're on track! You have {formatINR(Math.abs(analysis.deterministic.gap))} buffer.</div>
                  ) : (
                    <div className="gw-info-banner danger"><AlertTriangle size={14} /> ❌ You are currently {formatINR(Math.abs(analysis.deterministic.gap))} short.</div>
                  )}
                </div>

                {/* Section 3: Risk / Sensitivity */}
                <div>
                  <h3 className="gw-step-title" style={{ marginBottom: "12px" }}>What could go wrong?</h3>
                  <div className="gw-review-table">
                    <div className="gw-review-row" style={{ background: 'var(--input-bg)', fontWeight: 700 }}>
                      <span style={{ color: "var(--text-primary)" }}>Scenario</span><span style={{ color: "var(--text-primary)" }}>Shortfall</span>
                    </div>
                    {analysis.deterministic.sensitivity.map((row, i) => (
                      <div key={i} className="gw-review-row">
                        <span>{row.scenario}</span>
                        <span style={{ color: row.gap <= 0 ? '#00D09C' : '#FF4D4D' }}>
                          {row.gap <= 0 ? "On track" : formatINR(Math.abs(row.gap))}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Section 4: Fixes */}
                {!analysis.requiredSip.currentSipEnough && (
                  <div>
                    <h3 className="gw-step-title" style={{ marginBottom: "12px" }}>How to close the gap</h3>
                    <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                      
                      <div style={{ display: "flex", alignItems: "center", gap: "12px", padding: "14px", borderRadius: "14px", background: "var(--input-bg)", border: "1px solid var(--input-border)" }}>
                        <div style={{ width: "28px", height: "28px", borderRadius: "50%", background: "rgba(140,82,255,0.15)", color: "var(--accent-purple, #8C52FF)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: "12px" }}>1</div>
                        <div>
                          <div style={{ fontWeight: 600, color: "var(--text-primary)", fontSize: "13px" }}>Increase SIP by <span style={{ color: "var(--accent-purple, #8C52FF)" }}>{formatINR(analysis.requiredSip.sipGap)}/mo</span></div>
                          <div className="gw-hint" style={{ marginTop: "2px" }}>From {formatINR(analysis.requiredSip.currentSip)} → {formatINR(analysis.requiredSip.requiredSip)}</div>
                        </div>
                      </div>

                      <div style={{ display: "flex", alignItems: "center", gap: "12px", padding: "14px", borderRadius: "14px", background: "var(--input-bg)", border: "1px solid var(--input-border)" }}>
                        <div style={{ width: "28px", height: "28px", borderRadius: "50%", background: "rgba(140,82,255,0.15)", color: "var(--accent-purple, #8C52FF)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: "12px" }}>2</div>
                        <div>
                          <div style={{ fontWeight: 600, color: "var(--text-primary)", fontSize: "13px" }}>One-time top-up of <span style={{ color: "var(--accent-purple, #8C52FF)" }}>{formatINR(analysis.requiredSip.lumpSumToday)}</span></div>
                          <div className="gw-hint" style={{ marginTop: "2px" }}>Invest this lump sum today to catch up instantly.</div>
                        </div>
                      </div>

                      {analysis.requiredSip.extraMonths > 0 && (
                        <div style={{ display: "flex", alignItems: "center", gap: "12px", padding: "14px", borderRadius: "14px", background: "var(--input-bg)", border: "1px solid var(--input-border)" }}>
                          <div style={{ width: "28px", height: "28px", borderRadius: "50%", background: "rgba(140,82,255,0.15)", color: "var(--accent-purple, #8C52FF)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: "12px" }}>3</div>
                          <div>
                            <div style={{ fontWeight: 600, color: "var(--text-primary)", fontSize: "13px" }}>Extend deadline by <span style={{ color: "var(--accent-purple, #8C52FF)" }}>
                                {Math.floor(analysis.requiredSip.extraMonths / 12) > 0 ? `${Math.floor(analysis.requiredSip.extraMonths / 12)} yrs ` : ""}
                                {analysis.requiredSip.extraMonths % 12 > 0 ? `${analysis.requiredSip.extraMonths % 12} mos` : ""}
                              </span>
                            </div>
                            <div className="gw-hint" style={{ marginTop: "2px" }}>Keep current SIP but give investments more time.</div>
                          </div>
                        </div>
                      )}

                    </div>
                  </div>
                )}

              </div>
            ) : null}
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}
