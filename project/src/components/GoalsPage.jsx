import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Target, TrendingUp, Wallet, AlertCircle, RefreshCw, Plus, Link2, Trash2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { supabase } from '../lib/supabaseClient';
import { GOAL_TYPES, formatINR } from '../lib/goalHelpers';
import GoalWizard from './GoalWizard';
import GoalLinkModal from './GoalLinkModal';
import GoalAnalysisModal from './GoalAnalysisModal';
import './GoalsPage.css';

const PRIORITY_COLOR = { HIGH: '#FF4D4D', MEDIUM: '#FFB247', LOW: '#00D09C' };
const STATUS_META = {
  ACTIVE:    { label: 'Active',    bg: 'rgba(0,208,156,0.1)',  color: '#00D09C', border: 'rgba(0,208,156,0.25)' },
  ACHIEVED:  { label: 'Achieved',  bg: 'rgba(140,82,255,0.1)', color: '#A67FFF', border: 'rgba(140,82,255,0.25)' },
  ABANDONED: { label: 'Abandoned', bg: 'rgba(150,150,150,0.1)',color: '#888',     border: 'rgba(150,150,150,0.2)' },
};
const TYPE_MAP = GOAL_TYPES.reduce((a, t) => { a[t.type] = t; return a; }, {});

export default function GoalsPage() {
  const { user } = useAuth();
  const [goals,         setGoals]       = useState([]);
  const [progress,      setProgress]    = useState({});
  const [loading,       setLoading]     = useState(true);
  const [refreshing,    setRefreshing]  = useState(false);
  const [error,         setError]       = useState(null);
  
  // Modal states
  const [showWizard,    setShowWizard]  = useState(false);
  const [linkGoal,      setLinkGoal]    = useState(null);
  const [analyzeGoal,   setAnalyzeGoal] = useState(null);

  const fetchGoals = useCallback(async () => {
    if (!user?.id) return;
    setRefreshing(true); setError(null);
    try {
      const { data, error: err } = await supabase
        .from('goals')
        .select('*, goal_fund_links ( id, investment_lot_id, allocation_pct )')
        .eq('user_id', user.id)
        .order('created_at', { ascending: false });
      if (err) throw err;
      setGoals(data ?? []);
      if (data?.length) await computeProgress(data);
      else setProgress({});
    } catch (e) { setError(e.message); }
    finally { setLoading(false); setRefreshing(false); }
  }, [user]);

  const computeProgress = async (goalsData) => {
    // investment_lots doesn't have current_value — use purchase_amount as proxy
    const lotIds = [...new Set(goalsData.flatMap(g => (g.goal_fund_links ?? []).map(l => l.investment_lot_id)))];
    const lotValues = {};
    if (lotIds.length) {
      const { data } = await supabase
        .from('investment_lots').select('id, purchase_amount').in('id', lotIds);
      (data ?? []).forEach(l => { lotValues[l.id] = l.purchase_amount ?? 0; });
    }
    const pm = {};
    goalsData.forEach(g => {
      const cur = (g.goal_fund_links ?? []).reduce(
        (s, l) => s + (lotValues[l.investment_lot_id] ?? 0) * l.allocation_pct / 100, 0
      );
      pm[g.id] = { currentValue: cur, progressPct: Math.min((cur / (g.target_amount_future || 1)) * 100, 100) };
    });
    setProgress(pm);
  };

  useEffect(() => { fetchGoals(); }, [fetchGoals]);

  const handleDelete = async (id) => {
    if (!confirm('Delete this goal and its fund links?')) return;
    await supabase.from('goals').delete().eq('id', id);
    fetchGoals();
  };

  // Summary stats
  const totalTarget = goals.reduce((s, g) => s + (g.target_amount_future ?? 0), 0);
  const totalCurrent = Object.values(progress).reduce((s, p) => s + p.currentValue, 0);
  const totalSIP = goals.reduce((s, g) => s + (g.monthly_sip_allocated ?? 0), 0);

  if (loading) return (
    <div className="goals-page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '60vh' }}>
      <div style={{ width: 40, height: 40, border: '3px solid rgba(0,208,156,0.2)', borderTopColor: '#00D09C', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
    </div>
  );

  return (
    <div className="goals-page">
      {/* Header */}
      <div className="dash-header">
        <div>
          <div className="page-tag"><Target size={12} /> M15 — Goal Planner</div>
          <h1 className="page-title">
            Your <span className="text-gradient">Financial Goals</span>
          </h1>
          <p className="page-subtitle">Plan, track, and achieve your milestones</p>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <motion.button className="refresh-btn" onClick={fetchGoals} disabled={refreshing}
            whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
            <RefreshCw size={14} className={refreshing ? 'spin' : ''} /> Refresh
          </motion.button>
          <motion.button className="goals-btn-new" onClick={() => setShowWizard(true)}
            whileHover={{ scale: 1.05, boxShadow: '0 0 20px rgba(0,208,156,0.4)' }}
            whileTap={{ scale: 0.95 }}>
            <Plus size={16} /> New Goal
          </motion.button>
        </div>
      </div>

      {error && <div className="dash-error"><AlertCircle size={14} /> {error}</div>}

      {/* KPI cards */}
      {goals.length > 0 && (
        <div className="kpi-grid" style={{ marginBottom: 28 }}>
          <motion.div className="kpi-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}>
            <div className="kpi-icon-wrap"><Target size={20} color="#00D09C" /></div>
            <div className="kpi-label">Total Goals</div>
            <div className="kpi-value">{goals.length}</div>
            <div className="kpi-sub">{goals.filter(g => g.status === 'ACTIVE').length} active</div>
          </motion.div>
          <motion.div className="kpi-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
            <div className="kpi-icon-wrap"><TrendingUp size={20} color="#8C52FF" /></div>
            <div className="kpi-label">Target corpus</div>
            <div className="kpi-value purple">{formatINR(totalTarget)}</div>
            <div className="kpi-sub">Inflation-adjusted</div>
          </motion.div>
          <motion.div className="kpi-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
            <div className="kpi-icon-wrap"><TrendingUp size={20} color="#00D09C" /></div>
            <div className="kpi-label">Currently funded</div>
            <div className="kpi-value">{formatINR(totalCurrent)}</div>
            <div className="kpi-sub">{totalTarget > 0 ? ((totalCurrent / totalTarget) * 100).toFixed(1) : 0}% of target</div>
          </motion.div>
          <motion.div className="kpi-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
            <div className="kpi-icon-wrap"><Wallet size={20} color="#FFB247" /></div>
            <div className="kpi-label">Total SIP/month</div>
            <div className="kpi-value">{formatINR(totalSIP)}</div>
            <div className="kpi-sub">Across all goals</div>
          </motion.div>
        </div>
      )}

      {/* Empty state */}
      {goals.length === 0 && !error && (
        <div className="dash-empty">
          <motion.div className="empty-card"
            initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.5, type: 'spring' }}>
            <motion.div className="empty-icon"
              animate={{ y: [0, -10, 0] }} transition={{ repeat: Infinity, duration: 4, ease: 'easeInOut' }}>
              <Target size={54} color="#00F298" />
            </motion.div>
            <h2>No Goals Yet</h2>
            <p>Start planning your financial future — set a retirement target, house fund, or education goal.</p>
            <motion.button className="btn-goto-txns" onClick={() => setShowWizard(true)}
              whileHover={{ scale: 1.05, boxShadow: '0 0 20px rgba(0,242,152,0.4)' }}
              whileTap={{ scale: 0.95 }}>
              + Create Your First Goal
            </motion.button>
          </motion.div>
        </div>
      )}

      {/* Goal cards */}
      <div className="goals-grid">
        {goals.map((goal, i) => {
          const p      = progress[goal.id] ?? { currentValue: 0, progressPct: 0 };
          const target = goal.target_amount_future ?? 1;
          const pct    = p.progressPct;
          const sm     = STATUS_META[goal.status] ?? STATUS_META.ACTIVE;
          const links  = goal.goal_fund_links ?? [];

          return (
            <motion.div key={goal.id} className="goal-card glassmorphism"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.04 * i }}>

              {/* Card header */}
              <div className="gc-header">
                <div className="gc-left">
                  <span className="gc-icon">{goal.goal_icon}</span>
                  <div>
                    <h3 className="gc-name">{goal.goal_name}</h3>
                    <div className="gc-badges">
                      <span className="gc-status-badge" style={{ background: sm.bg, color: sm.color, borderColor: sm.border }}>
                        {sm.label}
                      </span>
                      <span className="gc-priority-dot" style={{ background: PRIORITY_COLOR[goal.priority] }} />
                      <span className="gc-priority-text" style={{ color: PRIORITY_COLOR[goal.priority] }}>
                        {goal.priority}
                      </span>
                    </div>
                  </div>
                </div>
                <div className="gc-actions">
                  <motion.button className="gc-action-btn" onClick={() => setAnalyzeGoal(goal)}
                    whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }} title="Analyze Goal">
                    <TrendingUp size={14} />
                  </motion.button>
                  <motion.button className="gc-action-btn" onClick={() => setLinkGoal(goal)}
                    whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }} title="Link fund">
                    <Link2 size={14} />
                  </motion.button>
                  <motion.button className="gc-action-btn danger" onClick={() => handleDelete(goal.id)}
                    whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }} title="Delete">
                    <Trash2 size={14} />
                  </motion.button>
                </div>
              </div>

              {/* Progress bar */}
              <div className="gc-progress">
                <div className="gc-progress-labels">
                  <span>Current: <strong>{formatINR(p.currentValue)}</strong></span>
                  <span>Target: <strong>{formatINR(target)}</strong></span>
                </div>
                <div className="gc-bar-track">
                  <motion.div className="gc-bar-fill"
                    initial={{ width: 0 }}
                    animate={{ width: `${pct}%` }}
                    transition={{ duration: 0.8, ease: 'easeOut' }}
                    style={{ background: pct >= 100 ? '#00D09C' : pct >= 50 ? 'var(--accent-purple)' : '#FFB247' }}
                  />
                </div>
                <span className="gc-pct-text">{pct.toFixed(1)}% funded</span>
              </div>

              {/* Stats row */}
              <div className="gc-stats">
                <div className="gc-stat"><span className="gc-stat-label">Target date</span><span className="gc-stat-value">{goal.target_date}</span></div>
                <div className="gc-stat"><span className="gc-stat-label">SIP / month</span><span className="gc-stat-value">{formatINR(goal.monthly_sip_allocated)}</span></div>
                <div className="gc-stat"><span className="gc-stat-label">Years left</span><span className="gc-stat-value">{(goal.years_remaining ?? 0).toFixed(1)}</span></div>
              </div>

              {/* Linked funds */}
              {links.length > 0 && (
                <div className="gc-links">
                  {links.map(link => (
                    <span key={link.id} className="gc-link-tag">
                      Lot #{link.investment_lot_id} — {link.allocation_pct}%
                    </span>
                  ))}
                </div>
              )}
            </motion.div>
          );
        })}
      </div>

      {/* Modals */}
      {showWizard && (
        <GoalWizard userId={user.id}
          onClose={() => setShowWizard(false)} onCreated={() => fetchGoals()} />
      )}
      {linkGoal && (
        <GoalLinkModal goal={linkGoal} userId={user.id}
          onClose={() => setLinkGoal(null)} onLinked={() => { setLinkGoal(null); fetchGoals(); }} />
      )}
      {analyzeGoal && (
        <GoalAnalysisModal 
          goal={analyzeGoal} 
          currentValue={progress[analyzeGoal.id]?.currentValue || 0}
          onClose={() => setAnalyzeGoal(null)} 
        />
      )}
    </div>
  );
}