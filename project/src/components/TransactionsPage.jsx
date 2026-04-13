import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Search, ArrowUpRight, ArrowDownLeft, RefreshCw, ChevronDown, X, Check, AlertTriangle, Filter } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import './TransactionsPage.css';

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const TXN_TYPES = [
  { value: 'PURCHASE_LUMPSUM', label: 'Lumpsum Purchase', icon: '⬆️', color: '#00D09C' },
  { value: 'PURCHASE_SIP', label: 'SIP Installment', icon: '🔄', color: '#00D09C' },
  { value: 'REDEMPTION', label: 'Redemption', icon: '⬇️', color: '#FF4D4D' },
  { value: 'SWITCH_IN', label: 'Switch In', icon: '↗️', color: '#7B61FF' },
  { value: 'SWITCH_OUT', label: 'Switch Out', icon: '↙️', color: '#FFB247' },
  { value: 'DIVIDEND_PAYOUT', label: 'Dividend Payout', icon: '💵', color: '#00D09C' },
  { value: 'DIVIDEND_REINVEST', label: 'Dividend Reinvest', icon: '🔁', color: '#7B61FF' },
  { value: 'SWP', label: 'SWP', icon: '💸', color: '#FF4D4D' },
  { value: 'STP_IN', label: 'STP In', icon: '➡️', color: '#00D09C' },
  { value: 'STP_OUT', label: 'STP Out', icon: '⬅️', color: '#FF4D4D' },
];

const isPurchaseType = (t) => ['PURCHASE_LUMPSUM','PURCHASE_SIP','SWITCH_IN','STP_IN','DIVIDEND_REINVEST'].includes(t);
const isRedemptionType = (t) => ['REDEMPTION','SWITCH_OUT','STP_OUT','SWP'].includes(t);

function formatCurrency(val) {
  if (!val && val !== 0) return '—';
  return '₹' + parseFloat(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function TxnTypeBadge({ type }) {
  const def = TXN_TYPES.find(t => t.value === type) || { label: type, icon: '•', color: '#A0A0B0' };
  return (
    <span className="txn-type-badge" style={{ color: def.color, background: `${def.color}15`, border: `1px solid ${def.color}30` }}>
      {def.icon} {def.label}
    </span>
  );
}

// Scheme autocomplete component
function SchemeAutocomplete({ value, onChange }) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);

  useEffect(() => {
    if (q.length < 2) { setResults([]); return; }
    const t = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await fetch(`https://api.mfapi.in/mf/search?q=${encodeURIComponent(q)}`);
        const data = await res.json();
        // mfapi returns [{ schemeCode, schemeName }]
        const mapped = (data || []).slice(0, 8).map(d => ({
          amfiCode: d.schemeCode.toString(),
          schemeName: d.schemeName
        }));
        setResults(mapped);
        setSelectedIndex(-1);
        setOpen(true);
      } catch (e) { console.error(e); }
      finally { setLoading(false); }
    }, 300);
    return () => clearTimeout(t);
  }, [q]);

  const select = (s) => {
    onChange(s);
    setQ(s.schemeName);
    setOpen(false);
  };

  const handleKeyDown = (e) => {
    if (!open || results.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => (prev + 1) % results.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => (prev - 1 + results.length) % results.length);
    } else if (e.key === 'Enter' && selectedIndex >= 0) {
      e.preventDefault();
      select(results[selectedIndex]);
    }
  };

  return (
    <div className="autocomplete-wrap">
      <input
        className="form-input"
        placeholder="Search fund name..."
        value={q || (value?.schemeName || '')}
        onChange={e => { setQ(e.target.value); if (!e.target.value) onChange(null); }}
        onFocus={() => q.length >= 2 && setOpen(true)}
        onKeyDown={handleKeyDown}
        id="scheme-autocomplete"
        autoComplete="off"
      />
      {loading && <span className="autocomplete-spinner">⟳</span>}
      <AnimatePresence>
        {open && results.length > 0 && (
          <motion.div className="autocomplete-dropdown" initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            {results.map((s, i) => (
              <div 
                key={s.amfiCode} 
                className={`autocomplete-item ${i === selectedIndex ? 'selected' : ''}`} 
                onClick={() => select(s)}
                onMouseEnter={() => setSelectedIndex(i)}
              >
                <div className="ac-info">
                  <div className="ac-name">{s.schemeName}</div>
                  <div className="ac-meta">Code: {s.amfiCode}</div>
                </div>
              </div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// Add Transaction Modal
function AddTransactionModal({ onClose, onSuccess, token }) {
  const [mode, setMode] = useState('single'); // 'single' or 'bulk-sip'
  const [form, setForm] = useState({
    scheme: null,
    folioNumber: '',
    transactionType: 'PURCHASE_SIP',
    transactionDate: new Date().toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0], // for SIP bulk
    amount: '',
    units: '',
    nav: '',
    notes: '',
    stepUp: '', // for SIP bulk
  });
  const [submitting, setSubmitting] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(''); // shown during CAS upload
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [navHint, setNavHint] = useState('');
  const [casualFile, setCasualFile] = useState(null);

  // Auto-fetch NAV when scheme OR date changes (only in single entry mode)
  useEffect(() => {
    if (mode === 'bulk-sip' || mode === 'upload-cas') return;
    if (!form.scheme || !form.transactionDate) return;
    const fetchNav = async () => {
      try {
        // First: try date-specific NAV (historical from mfapi.in, Caffeine-cached)
        const res = await fetch(
          `${API}/api/nav/${form.scheme.amfiCode}/date/${form.transactionDate}`
        );
        const data = await res.json();
        if (data.nav && data.nav !== null) {
          setForm(f => ({ ...f, nav: data.nav.toString() }));
          setNavHint(`NAV on ${form.transactionDate}: ₹${parseFloat(data.nav).toFixed(4)}`);
        } else {
          // Fallback: latest NAV
          const latestRes = await fetch(`${API}/api/nav/latest/${form.scheme.amfiCode}`);
          const latestData = await latestRes.json();
          if (latestData.nav) {
            setForm(f => ({ ...f, nav: latestData.nav.toString() }));
            setNavHint(`Using latest NAV (${latestData.date}): ₹${parseFloat(latestData.nav).toFixed(4)}`);
          }
        }
      } catch (e) {
        // Fallback to scheme's stored NAV
        if (form.scheme.lastNav) {
          setForm(f => ({ ...f, nav: form.scheme.lastNav.toString() }));
          setNavHint(`Using stored NAV: ₹${parseFloat(form.scheme.lastNav).toFixed(4)}`);
        }
      }
    };
    fetchNav();
  }, [form.scheme, form.transactionDate]);

  const set = (key, val) => setForm(f => ({ ...f, [key]: val }));

  const calcUnits = () => {
    const nav = parseFloat(form.nav);
    const amount = parseFloat(form.amount);
    if (nav > 0 && amount > 0) {
      const stampDuty = amount * 0.00005;
      return ((amount - stampDuty) / nav).toFixed(6);
    }
    return '';
  };

  const submit = async () => {
    if (mode !== 'upload-cas') {
      if (!form.scheme) { setError('Please select a scheme'); return; }
      if (!form.transactionDate) { setError('Date is required'); return; }
      if (isPurchaseType(form.transactionType) && !form.amount) { setError('Amount is required for purchase'); return; }
      if (isRedemptionType(form.transactionType) && !form.units && !form.amount) { setError('Amount or units required for redemption'); return; }
    }

    setSubmitting(true);
    setError('');
    setSuccessMsg('');
    
    if (mode === 'upload-cas') {
      if (!casualFile) {
        setError('Please select a CAS PDF file');
        setSubmitting(false);
        return;
      }
      
      const formData = new FormData();
      formData.append('file', casualFile);

      // Show rotating progress messages so the user knows it's working (CAS parse can take 5-15s)
      const progressSteps = [
        '📄 Reading PDF…',
        '🔍 Identifying folios…',
        '📊 Parsing transactions…',
        '🏦 Matching fund schemes…',
        '💾 Saving to ledger…',
      ];
      let stepIdx = 0;
      setUploadProgress(progressSteps[0]);
      const progressTimer = setInterval(() => {
        stepIdx = (stepIdx + 1) % progressSteps.length;
        setUploadProgress(progressSteps[stepIdx]);
      }, 2500);

      try {
        const res = await fetch(`${API}/api/transactions/upload-cas`, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` }, // Do NOT set Content-Type for FormData
          body: formData,
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Failed to upload CAS');
        // data = { status, totalFolios, totalTransactions, uploadId }
        onSuccess({ _mode: 'upload-cas', ...data });
      } catch (e) {
        setError(e.message);
      } finally {
        clearInterval(progressTimer);
        setUploadProgress('');
        setSubmitting(false);
      }
      return;
    }
    
    if (mode === 'bulk-sip') {
      try {
        const body = {
          schemeAmfiCode: form.scheme.amfiCode,
          folioNumber: form.folioNumber || null,
          startDate: form.transactionDate,
          endDate: form.endDate,
          amount: parseFloat(form.amount),
          annualStepUpPct: form.stepUp ? parseFloat(form.stepUp) : null,
        };
        const res = await fetch(`${API}/api/transactions/bulk-sip`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
          body: JSON.stringify(body),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Failed to bulk generate SIP');
        // data = { message, transactions: [...] }
        onSuccess({ _mode: 'bulk-sip', ...data });
      } catch (e) {
        setError(e.message);
      } finally {
        setSubmitting(false);
      }
      return;
    }

    try {
      const body = {
        schemeAmfiCode: form.scheme.amfiCode,
        folioNumber: form.folioNumber || null,
        transactionType: form.transactionType,
        transactionDate: form.transactionDate,
        amount: form.amount ? parseFloat(form.amount) : null,
        units: form.units ? parseFloat(form.units) : null,
        nav: form.nav ? parseFloat(form.nav) : null,
        notes: form.notes || null,
      };
      const res = await fetch(`${API}/api/transactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to record transaction');
      // data = Transaction object with transactionRef
      onSuccess({ _mode: 'single', ...data });
    } catch (e) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const estimatedUnits = calcUnits();

  return (
    <motion.div className="modal-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      onClick={onClose}>
      <motion.div className="txn-modal glassmorphism" initial={{ scale: 0.9, y: 30 }} animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.9, y: 30 }} onClick={e => e.stopPropagation()}>
        <div className="modal-head" style={{ marginBottom: '16px' }}>
          <h2 id="add-txn-title">Record Transaction</h2>
          <button className="modal-close-btn" onClick={onClose}><X size={18} /></button>
        </div>

        <div className="modal-tabs">
          <button className={`modal-tab ${mode === 'single' ? 'active' : ''}`} onClick={() => setMode('single')}>Single Entry</button>
          <button className={`modal-tab ${mode === 'bulk-sip' ? 'active' : ''}`} onClick={() => setMode('bulk-sip')}>Historical SIP Generator</button>
          <button className={`modal-tab ${mode === 'upload-cas' ? 'active' : ''}`} onClick={() => setMode('upload-cas')}>Upload CAS</button>
        </div>

        <div className="form-grid" style={{ marginTop: '20px' }}>
          {mode === 'single' || mode === 'bulk-sip' ? (
            <div className="form-group full-width">
              <label className="form-label">Fund / Scheme *</label>
              <SchemeAutocomplete value={form.scheme} onChange={s => set('scheme', s)} />
            </div>
          ) : null}

          {mode === 'single' ? (
            <>
              <div className="form-group">
                <label className="form-label">Transaction Type *</label>
                <select className="form-input" value={form.transactionType} onChange={e => set('transactionType', e.target.value)} id="txn-type-select">
                  {TXN_TYPES.map(t => <option key={t.value} value={t.value}>{t.icon} {t.label}</option>)}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Date *</label>
                <input className="form-input" type="date" value={form.transactionDate}
                  max={new Date().toISOString().split('T')[0]}
                  onChange={e => set('transactionDate', e.target.value)} id="txn-date" />
              </div>

              {isPurchaseType(form.transactionType) || form.transactionType === 'DIVIDEND_PAYOUT' ? (
                <div className="form-group">
                  <label className="form-label">Amount (₹) *</label>
                  <input className="form-input" type="number" min="0" step="0.01" placeholder="e.g. 10000"
                    value={form.amount} onChange={e => set('amount', e.target.value)} id="txn-amount" />
                </div>
              ) : null}

              {isRedemptionType(form.transactionType) ? (
                <>
                  <div className="form-group">
                    <label className="form-label">Amount (₹)</label>
                    <input className="form-input" type="number" min="0" step="0.01" placeholder="Or enter units"
                      value={form.amount} onChange={e => set('amount', e.target.value)} id="txn-redeem-amount" />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Units</label>
                    <input className="form-input" type="number" min="0" step="0.000001" placeholder="Or enter amount"
                      value={form.units} onChange={e => set('units', e.target.value)} id="txn-units" />
                  </div>
                </>
              ) : null}

              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  NAV (₹)
                  <AnimatePresence>
                    {navHint && (
                      <motion.span 
                        className="nav-hint" 
                        initial={{ opacity: 0, x: -5 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }}
                        style={{ color: '#00F298', display: 'flex', alignItems: 'center', gap: '4px' }}
                      >
                        <motion.div animate={{ opacity: [0.3, 1, 0.3] }} transition={{ repeat: Infinity, duration: 1.5 }} style={{ width: 6, height: 6, borderRadius: '50%', background: '#00F298', boxShadow: '0 0 8px #00F298' }} />
                        {navHint}
                      </motion.span>
                    )}
                  </AnimatePresence>
                </label>
                <input className="form-input" type="number" min="0" step="0.0001" placeholder="NAV on transaction date"
                  value={form.nav} onChange={e => set('nav', e.target.value)} id="txn-nav" />
              </div>

              <div className="form-group">
                <label className="form-label">Folio Number</label>
                <input className="form-input" type="text" placeholder="Auto-generated if blank"
                  value={form.folioNumber} onChange={e => set('folioNumber', e.target.value)} id="txn-folio" />
              </div>

              <div className="form-group full-width">
                <label className="form-label">Notes</label>
                <input className="form-input" type="text" placeholder="Optional note..."
                  value={form.notes} onChange={e => set('notes', e.target.value)} id="txn-notes" />
              </div>
            </>
          ) : mode === 'bulk-sip' ? (
            <>
              {/* Bulk SIP Settings */}
              <div className="form-group">
                <label className="form-label">Started On *</label>
                <input className="form-input" type="date" value={form.transactionDate}
                  max={new Date().toISOString().split('T')[0]}
                  onChange={e => set('transactionDate', e.target.value)} id="txn-start-date" />
              </div>
              <div className="form-group">
                <label className="form-label">Ended On *</label>
                <input className="form-input" type="date" value={form.endDate}
                  max={new Date().toISOString().split('T')[0]}
                  onChange={e => set('endDate', e.target.value)} id="txn-end-date" />
              </div>
              <div className="form-group">
                <label className="form-label">SIP Amount (₹) *</label>
                <input className="form-input" type="number" min="0" step="100" placeholder="e.g. 5000"
                  value={form.amount} onChange={e => set('amount', e.target.value)} id="txn-sip-amount" />
              </div>
              <div className="form-group">
                <label className="form-label">Annual Step-Up % (Optional)</label>
                <input className="form-input" type="number" min="0" step="1" placeholder="e.g. 10%"
                  value={form.stepUp} onChange={e => set('stepUp', e.target.value)} id="txn-step-up" />
              </div>
              <div className="form-group full-width">
                <div style={{ padding: '12px', background: 'rgba(140, 82, 255, 0.1)', borderRadius: '10px', border: '1px solid rgba(140, 82, 255, 0.3)' }}>
                  <p style={{ margin: 0, fontSize: '13px', color: '#E0E0FF', lineHeight: 1.5 }}>
                    <strong>Intelligent Generation:</strong> We will automatically fetch the exact historical NAV from <code style={{color: '#8C52FF'}}>mfapi.in</code> for each calendar month and precisely recreate your entire SIP history out to 4 decimal places of units.
                  </p>
                </div>
              </div>
            </>
          ) : (
            <>
              {/* Upload CAS */}
              <div className="form-group full-width">
                <label className="form-label">CAS PDF File *</label>
                <div style={{
                  border: '2px dashed #00F29830',
                  borderRadius: '10px',
                  padding: '40px 20px',
                  textAlign: 'center',
                  background: 'rgba(0, 242, 152, 0.05)',
                  cursor: 'pointer'
                }} onClick={() => document.getElementById('cas-upload').click()}>
                  <input type="file" id="cas-upload" accept=".pdf" style={{ display: 'none' }} onChange={e => {
                      if (e.target.files && e.target.files[0]) setCasualFile(e.target.files[0]);
                  }} />
                  {casualFile ? (
                    <div style={{ color: '#00F298', fontWeight: 500 }}>
                      <Check size={20} style={{ display: 'block', margin: '0 auto 8px' }} />
                      {casualFile.name}
                    </div>
                  ) : (
                    <div>
                      <Plus size={24} color="#00F298" style={{ display: 'block', margin: '0 auto 8px' }} />
                      <span style={{ color: '#A0A0B0' }}>Click to select your AMFI/Karvy CAS PDF</span>
                    </div>
                  )}
                </div>
              </div>
              <div className="form-group full-width">
                <div style={{ padding: '12px', background: 'rgba(0, 208, 156, 0.1)', borderRadius: '10px', border: '1px solid rgba(0, 208, 156, 0.3)' }}>
                  <p style={{ margin: 0, fontSize: '13px', color: '#E0E0FF', lineHeight: 1.5 }}>
                    <strong>Automated Parsing:</strong> We will securely read your valid CAMS/KFintech CAS statement and import all folios, historical units, amounts, NAV along with derived categories and risk metrics directly into your ledger.
                  </p>
                </div>
              </div>
            </>
          )}
        </div>

        {estimatedUnits && isPurchaseType(form.transactionType) && (
          <div className="calc-hint">
            <Check size={14} /> Estimated units: <strong>{estimatedUnits}</strong> (after 0.005% stamp duty)
          </div>
        )}

        {error && (
          <div className="form-error">
            <AlertTriangle size={14} /> {error}
          </div>
        )}

        <div className="modal-actions" style={{ flexDirection: 'column', gap: '8px' }}>
          {uploadProgress && (
            <div style={{
              width: '100%', padding: '8px 14px', borderRadius: '8px',
              background: 'rgba(0,208,156,0.08)', border: '1px solid rgba(0,208,156,0.2)',
              fontSize: '13px', color: '#00D09C', textAlign: 'center', fontWeight: 500
            }}>
              {uploadProgress}
            </div>
          )}
          <div style={{ display: 'flex', gap: '10px', width: '100%' }}>
            <button className="btn-cancel" onClick={onClose}>Cancel</button>
            <motion.button className="btn-submit" onClick={submit} disabled={submitting}
              whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}>
              {submitting ? <RefreshCw size={14} className="spin" /> : <Plus size={14} />}
              {submitting ? (mode === 'bulk-sip' ? 'Generating...' : mode === 'upload-cas' ? 'Parsing PDF...' : 'Recording...') : (mode === 'bulk-sip' ? 'Import Historical SIP' : mode === 'upload-cas' ? 'Upload & Parse CAS' : 'Record Transaction')}
            </motion.button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}

export default function TransactionsPage() {
  const { getToken } = useAuth();
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [filter, setFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [reversing, setReversing] = useState(null);
  const [toast, setToast] = useState('');

  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API}/api/transactions`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      const data = await res.json();
      setTransactions(Array.isArray(data) ? data : []);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, [getToken]);

  useEffect(() => { fetchTransactions(); }, [fetchTransactions]);

  const handleReverse = async (id) => {
    if (!window.confirm('Create a reversal for this transaction?')) return;
    setReversing(id);
    try {
      const res = await fetch(`${API}/api/transactions/${id}/reverse`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error);
      showToast('✅ Reversal created');
      fetchTransactions();
    } catch (e) {
      showToast('❌ ' + e.message);
    } finally { setReversing(null); }
  };

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const filtered = transactions.filter(t => {
    if (filter !== 'ALL' && t.transactionType !== filter) return false;
    if (search && !(t.schemeName?.toLowerCase().includes(search.toLowerCase()) ||
        t.schemeAmfiCode?.includes(search) || t.folioNumber?.includes(search))) return false;
    return true;
  });

  // Totals
  const totalInvested = transactions
    .filter(t => isPurchaseType(t.transactionType))
    .reduce((s, t) => s + (parseFloat(t.amount) || 0), 0);
  const totalRedeemed = transactions
    .filter(t => isRedemptionType(t.transactionType))
    .reduce((s, t) => s + (parseFloat(t.amount) || 0), 0);

  return (
    <div className="txns-page">
      {/* Header */}
      <div className="txns-header">
        <div>
          <div className="page-tag"><Filter size={12} /> M06 — Transaction Ledger</div>
          <h1 className="page-title">Transaction <span className="text-gradient">History</span></h1>
          <p className="page-subtitle">Immutable ledger — corrections via reversal entries</p>
        </div>
        <motion.button className="btn-add-txn" onClick={() => setShowAdd(true)}
          whileHover={{ scale: 1.03, boxShadow: '0 0 20px rgba(0,208,156,0.35)' }} whileTap={{ scale: 0.97 }}>
          <Plus size={16} /> Add Transaction
        </motion.button>
      </div>

      {/* Stats Bar */}
      <div className="txns-stats-bar">
        <div className="txn-stat-card">
          <span className="txn-stat-label">Total Invested</span>
          <span className="txn-stat-value green">{formatCurrency(totalInvested)}</span>
        </div>
        <div className="txn-stat-card">
          <span className="txn-stat-label">Total Redeemed</span>
          <span className="txn-stat-value red">{formatCurrency(totalRedeemed)}</span>
        </div>
        <div className="txn-stat-card">
          <span className="txn-stat-label">Net Invested</span>
          <span className="txn-stat-value">{formatCurrency(totalInvested - totalRedeemed)}</span>
        </div>
        <div className="txn-stat-card">
          <span className="txn-stat-label">Transactions</span>
          <span className="txn-stat-value">{transactions.length}</span>
        </div>
      </div>

      {/* Controls */}
      <div className="txns-controls glassmorphism">
        <div className="txn-search-wrap">
          <Search size={15} />
          <input className="txn-search" placeholder="Search by fund or folio..." value={search}
            onChange={e => setSearch(e.target.value)} id="txn-search" />
        </div>
        <div className="txn-type-filters">
          {['ALL', 'PURCHASE_SIP', 'PURCHASE_LUMPSUM', 'REDEMPTION', 'SWITCH_IN', 'SWITCH_OUT'].map(t => (
            <button key={t} className={`txn-filter-btn ${filter === t ? 'active' : ''}`}
              onClick={() => setFilter(t)}>
              {t === 'ALL' ? 'All' : TXN_TYPES.find(x => x.value === t)?.label || t}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <div className="txns-loading">
          {[...Array(5)].map((_, i) => <div key={i} className="txn-row-skeleton" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="txns-empty">
          <motion.div className="empty-card" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.5, type: 'spring' }}>
            <motion.div className="empty-icon" animate={{ y: [0, -10, 0] }} transition={{ repeat: Infinity, duration: 4, ease: "easeInOut" }}>
              <ArrowUpRight size={54} color="#00F298" />
            </motion.div>
            <h3>No Transactions Yet</h3>
            <p>Your immutable ledger is pristine. Log your first investment or casually record bulk history manually.</p>
            <motion.button className="btn-add-txn" style={{ marginTop: '16px' }} onClick={() => setShowAdd(true)}
              whileHover={{ scale: 1.05, boxShadow: '0 0 20px rgba(0, 242, 152, 0.4)' }} whileTap={{ scale: 0.95 }}>
              + Add Transaction
            </motion.button>
          </motion.div>
        </div>
      ) : (
        <div className="txns-table-wrap glassmorphism">
          <table className="txns-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Fund</th>
                <th>Category</th>
                <th>Risk</th>
                <th>Type</th>
                <th>Amount</th>
                <th>Units</th>
                <th>NAV</th>
                <th>Folio</th>
                <th>Action</th>
              </tr>
            </thead>
            <motion.tbody 
              initial="hidden" 
              animate="show" 
              variants={{
                hidden: { opacity: 0 },
                show: { opacity: 1, transition: { staggerChildren: 0.04 } }
              }}
            >
              <AnimatePresence>
                {filtered.map((t, i) => (
                  <motion.tr key={t.id} 
                    variants={{
                      hidden: { opacity: 0, x: -10 },
                      show: { opacity: 1, x: 0 }
                    }}
                    className={`txn-row ${t.transactionType === 'REVERSAL' ? 'reversal-row' : ''}`}>
                    <td className="txn-date">{t.transactionDate}</td>
                    <td className="txn-fund">
                      <div className="txn-fund-name">{t.schemeName || t.schemeAmfiCode}</div>
                      <div className="txn-fund-code">{t.schemeAmfiCode}</div>
                    </td>
                    <td>
                      {t.category ? (
                        <span style={{ fontSize: '11px', background: '#2C2C3E', padding: '2px 6px', borderRadius: '4px', border: '1px solid #3C3C4E', color: '#C0C0E0', display: 'inline-block', maxWidth: '100px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={t.category}>
                          {t.category}
                        </span>
                      ) : <span style={{ color: '#555' }}>—</span>}
                    </td>
                    <td>
                      {t.risk ? (
                        <span style={{ 
                          fontSize: '11px', 
                          background: t.risk <= 2 ? '#00D09C20' : t.risk <= 4 ? '#FFB24720' : '#FF4D4D20', 
                          color: t.risk <= 2 ? '#00D09C' : t.risk <= 4 ? '#FFB247' : '#FF4D4D',
                          padding: '2px 6px', borderRadius: '4px', border: `1px solid ${t.risk <= 2 ? '#00D09C50' : t.risk <= 4 ? '#FFB24750' : '#FF4D4D50'}` 
                        }}>
                          Risk {t.risk}
                        </span>
                      ) : <span style={{ color: '#555' }}>—</span>}
                    </td>
                    <td><TxnTypeBadge type={t.transactionType} /></td>
                    <td className={`txn-amount ${isPurchaseType(t.transactionType) ? 'green' : isRedemptionType(t.transactionType) ? 'red' : ''}`}>
                      {t.amount ? (isPurchaseType(t.transactionType) ? '+' : isRedemptionType(t.transactionType) ? '-' : '') + formatCurrency(Math.abs(t.amount)) : '—'}
                    </td>
                    <td className="txn-units">{t.units ? parseFloat(t.units).toFixed(4) : '—'}</td>
                    <td className="txn-nav">{t.nav ? `₹${parseFloat(t.nav).toFixed(4)}` : '—'}</td>
                    <td className="txn-folio">{t.folioNumber || '—'}</td>
                    <td>
                      {t.transactionType !== 'REVERSAL' && (
                        <button className="btn-reverse" onClick={() => handleReverse(t.id)}
                          disabled={reversing === t.id} title="Create reversal">
                          {reversing === t.id ? <RefreshCw size={12} className="spin" /> : '↩'}
                        </button>
                      )}
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </motion.tbody>
          </table >
        </div >
      )}

      {/* Add Transaction Modal */}
      <AnimatePresence>
        {showAdd && (
          <AddTransactionModal
            onClose={() => setShowAdd(false)}
            token={getToken()}
            onSuccess={(result) => {
              setShowAdd(false);
              fetchTransactions();
              // Show mode-appropriate success message
              if (result._mode === 'upload-cas') {
                showToast(`✅ CAS imported: ${result.totalTransactions} transactions across ${result.totalFolios} folio(s)`);
              } else if (result._mode === 'bulk-sip') {
                const count = result.transactions?.length ?? result.message?.match(/\d+/)?.[0] ?? '';
                showToast(`✅ SIP history generated: ${count} entries`);
              } else {
                // single transaction — transactionRef is present
                showToast(`✅ Transaction recorded: ${result.transactionRef || result.id}`);
              }
            }}
          />
        )}
      </AnimatePresence>

      {/* Toast */}
      <AnimatePresence>
        {toast && (
          <motion.div className="toast" initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 30 }}>
            {toast}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
