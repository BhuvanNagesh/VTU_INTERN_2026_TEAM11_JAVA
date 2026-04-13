import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Link2, Trash2 } from 'lucide-react';
import { supabase } from '../lib/supabaseClient';
import { formatINR } from '../lib/goalHelpers';

export default function GoalLinkModal({ goal, userId, onClose, onLinked }) {
  const [lots,     setLots]     = useState([]);
  const [selected, setSelected] = useState(null);
  const [allocPct, setAllocPct] = useState('');
  const [fetching, setFetching] = useState(true);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState(null);

  const links   = goal.goal_fund_links ?? [];
  const usedPct = links.reduce((s, l) => s + (l.allocation_pct ?? 0), 0);
  const freePct = Math.max(100 - usedPct, 0);

  useEffect(() => { fetchLots(); }, []);

  const fetchLots = async () => {
    setFetching(true);
    const { data, error: err } = await supabase
      .from('investment_lots')
      .select('id, folio_number, scheme_name, purchase_amount')
      .eq('user_id', userId);
    if (err) setError(err.message);
    else setLots(data ?? []);
    setFetching(false);
  };

  const existing = links.find(l => l.investment_lot_id === selected);

  const handleSelect = (id) => {
    setSelected(id);
    const lnk = links.find(l => l.investment_lot_id === id);
    setAllocPct(lnk ? String(lnk.allocation_pct) : '');
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selected) { setError('Select an investment lot'); return; }
    const pct = parseFloat(allocPct);
    if (!pct || pct <= 0 || pct > 100) { setError('Enter 1–100%'); return; }

    setLoading(true); setError(null);
    try {
      // validate total allocation for this lot across all goals
      const { data: allLinks } = await supabase
        .from('goal_fund_links')
        .select('allocation_pct, goal_id')
        .eq('investment_lot_id', selected);
      const usedElsewhere = (allLinks ?? [])
        .filter(l => l.goal_id !== goal.id)
        .reduce((s, l) => s + l.allocation_pct, 0);
      if (usedElsewhere + pct > 100)
        throw new Error(`Total for this lot would be ${(usedElsewhere + pct).toFixed(0)}% (limit 100%).`);

      const { error: err } = await supabase
        .from('goal_fund_links')
        .upsert(
          { goal_id: goal.id, investment_lot_id: selected, allocation_pct: pct },
          { onConflict: 'goal_id,investment_lot_id' }
        );
      if (err) throw err;
      onLinked?.();
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const handleUnlink = async (lotId) => {
    await supabase.from('goal_fund_links').delete()
      .eq('investment_lot_id', lotId).eq('goal_id', goal.id);
    onLinked?.();
  };

  return (
    <AnimatePresence>
      <motion.div className="gw-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
        onClick={onClose}>
        <motion.div className="gw-modal glassmorphism" style={{ maxWidth: 440 }}
          initial={{ opacity: 0, y: 40, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 40 }}
          onClick={e => e.stopPropagation()}
        >
          <div className="gw-header">
            <div>
              <h2 className="gw-title">Link a Fund</h2>
              <span className="gw-step-label">{goal.goal_icon} {goal.goal_name}</span>
            </div>
            <button className="gw-close" onClick={onClose}><X size={18} /></button>
          </div>

          <form onSubmit={handleSubmit} className="gw-body" style={{ gap: 18 }}>
            {/* Allocation bar */}
            <div className="gl-alloc-summary">
              <div className="gl-alloc-info">
                <span>Allocated: <strong>{usedPct.toFixed(0)}%</strong></span>
                <span>Free: <strong className={freePct > 0 ? 'green' : 'red'}>{freePct.toFixed(0)}%</strong></span>
              </div>
              <div className="gw-alloc-bar">
                <div className="gw-alloc-eq" style={{ width: `${usedPct}%` }} />
                <div className="gw-alloc-debt" style={{ width: `${freePct}%` }} />
              </div>
            </div>

            {/* Existing links */}
            {links.length > 0 && (
              <div className="gl-existing">
                <p className="gw-label">Currently linked</p>
                {links.map(link => {
                  const lot = lots.find(l => l.id === link.investment_lot_id);
                  return (
                    <div key={link.id} className="gl-link-row">
                      <span>{lot?.scheme_name ?? `Lot #${link.investment_lot_id}`}</span>
                      <div className="gl-link-right">
                        <span className="gl-pct">{link.allocation_pct}%</span>
                        <button type="button" className="gl-unlink" onClick={() => handleUnlink(link.investment_lot_id)}>
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* Lot list */}
            <div>
              <p className="gw-label">Select investment lot</p>
              {fetching ? <p className="gw-hint">Loading…</p> : lots.length === 0 ? (
                <p className="gw-hint">No investment lots found for this account.</p>
              ) : (
                <div className="gl-folio-list">
                  {lots.map(lot => {
                    const linked = links.find(l => l.investment_lot_id === lot.id);
                    return (
                      <button key={lot.id} type="button" onClick={() => handleSelect(lot.id)}
                        className={`gl-folio-card ${selected === lot.id ? 'active' : ''}`}
                      >
                        <div>
                          <p className="gl-folio-name">{lot.scheme_name ?? `Lot #${lot.id}`}</p>
                          <p className="gl-folio-sub">
                            {lot.folio_number && `${lot.folio_number} · `}{formatINR(lot.purchase_amount)}
                          </p>
                        </div>
                        {linked && <span className="gl-linked-badge">{linked.allocation_pct}%</span>}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* % input */}
            {selected && (
              <div>
                <p className="gw-label">Allocation %
                  {existing && <span className="gw-hint"> — currently {existing.allocation_pct}%</span>}
                </p>
                <div style={{ position: 'relative' }}>
                  <input type="number" min="1" max="100" step="1" className="gw-input"
                    placeholder={`Max ${freePct.toFixed(0)}`}
                    value={allocPct} onChange={e => setAllocPct(e.target.value)} />
                </div>
                {allocPct && (
                  <p className="gw-hint-sub">
                    = {formatINR((lots.find(l => l.id === selected)?.purchase_amount ?? 0) * parseFloat(allocPct) / 100)} of this lot
                  </p>
                )}
              </div>
            )}

            {error && <div className="gw-info-banner danger">{error}</div>}

            <div className="gw-footer" style={{ borderTop: 'none', paddingTop: 0 }}>
              <button type="button" className="gw-btn-back" onClick={onClose}>Cancel</button>
              <motion.button type="submit" className="gw-btn-next" disabled={loading || !selected || !allocPct}
                whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
                <Link2 size={14} /> {loading ? 'Saving…' : 'Link Fund'}
              </motion.button>
            </div>
          </form>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}
