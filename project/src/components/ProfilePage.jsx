import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { User, Phone, CreditCard, Lock, Save, Check, AlertTriangle, ChevronDown, DollarSign } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import './ProfilePage.css';

import { API_BASE as API } from '../lib/config';

const CURRENCIES = [
  { value: 'INR', label: '₹ Indian Rupee (INR)' },
  { value: 'USD', label: '$ US Dollar (USD)' },
  { value: 'EUR', label: '€ Euro (EUR)' },
  { value: 'GBP', label: '£ British Pound (GBP)' },
  { value: 'SGD', label: 'S$ Singapore Dollar (SGD)' },
  { value: 'AED', label: 'AED UAE Dirham' },
];

const RELATIONSHIPS = [
  'Self', 'Spouse', 'Child', 'Parent', 'Sibling', 'Other'
];

function Section({ title, icon, children }) {
  return (
    <motion.div className="profile-section glassmorphism"
      initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
      <div className="section-header">
        <div className="section-icon">{icon}</div>
        <h2 className="section-title">{title}</h2>
      </div>
      {children}
    </motion.div>
  );
}

function Toast({ msg, type }) {
  return (
    <motion.div className={`profile-toast ${type}`}
      initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 24 }}>
      {type === 'success' ? <Check size={14} /> : <AlertTriangle size={14} />}
      {msg}
    </motion.div>
  );
}

export default function ProfilePage() {
  const { getToken, user: authUser, signOut } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState(null);

  // Form states
  const [info, setInfo] = useState({ fullName: '', phone: '', panCard: '', currency: 'INR' });
  const [pwd, setPwd] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [savingInfo, setSavingInfo] = useState(false);
  const [savingPwd, setSavingPwd] = useState(false);

  useEffect(() => {
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    try {
      const res = await fetch(`${API}/api/user/profile`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      const data = await res.json();
      setProfile(data);
      setInfo({
        fullName: data.fullName || '',
        phone: data.phone || '',
        panCard: data.panCard || '',
        currency: data.currency || 'INR',
      });
    } catch (e) {
      showToast('Failed to load profile', 'error');
    } finally {
      setLoading(false);
    }
  };

  const saveProfile = async () => {
    setSavingInfo(true);
    try {
      const res = await fetch(`${API}/api/user/profile`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` },
        body: JSON.stringify(info),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error);
      setProfile(data.user);
      showToast('Profile updated successfully!', 'success');
    } catch (e) {
      showToast(e.message || 'Update failed', 'error');
    } finally {
      setSavingInfo(false);
    }
  };

  const changePassword = async () => {
    if (pwd.newPassword !== pwd.confirmPassword) {
      showToast('New passwords do not match', 'error'); return;
    }
    if (pwd.newPassword.length < 8) {
      showToast('New password must be at least 8 characters', 'error'); return;
    }
    setSavingPwd(true);
    try {
      const res = await fetch(`${API}/api/user/change-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` },
        body: JSON.stringify({ currentPassword: pwd.currentPassword, newPassword: pwd.newPassword }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error);
      setPwd({ currentPassword: '', newPassword: '', confirmPassword: '' });
      showToast('Password changed successfully!', 'success');
    } catch (e) {
      showToast(e.message || 'Password change failed', 'error');
    } finally {
      setSavingPwd(false);
    }
  };

  const showToast = (msg, type = 'success') => {
    setToast({ msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  const getInitials = (name) => {
    if (!name) return '?';
    return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
  };

  return (
    <div className="profile-page">
      <div className="profile-header">
        <div>
          <div className="page-tag"><User size={12} /> M01 — Profile Settings</div>
          <h1 className="page-title">My <span className="text-gradient">Profile</span></h1>
          <p className="page-subtitle">Manage your account details and security settings</p>
        </div>
      </div>

      {loading ? (
        <div className="profile-skeleton-wrap">
          {[1,2,3].map(i => <div key={i} className="profile-skeleton" />)}
        </div>
      ) : (
        <div className="profile-grid">
          {/* Left — Avatar Panel */}
          <motion.div className="avatar-panel glassmorphism" initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }}>
            <div className="avatar-ring">
              <div className="avatar-circle">
                {getInitials(profile?.fullName)}
              </div>
              <div className="avatar-status" />
            </div>
            <h3 className="avatar-name">{profile?.fullName || 'User'}</h3>
            <p className="avatar-email">{profile?.email}</p>

            <div className="avatar-stats">
              <div className="avatar-stat">
                <span className="as-label">Member Since</span>
                <span className="as-value">2026</span>
              </div>
              <div className="avatar-stat">
                <span className="as-label">Currency</span>
                <span className="as-value">{profile?.currency || 'INR'}</span>
              </div>
              <div className="avatar-stat">
                <span className="as-label">PAN Card</span>
                <span className="as-value">{profile?.panCard ? '●●●●●' + profile.panCard.slice(-5) : 'Not set'}</span>
              </div>
            </div>

            <button className="btn-signout" onClick={signOut}>Sign Out</button>
          </motion.div>

          {/* Right — Settings Sections */}
          <div className="profile-right">
            {/* Personal Info */}
            <Section title="Personal Information" icon={<User size={16} color="#00D09C" />}>
              <div className="form-grid-2">
                <div className="form-field">
                  <label className="field-label">Full Name</label>
                  <input className="field-input" type="text" value={info.fullName}
                    onChange={e => setInfo(p => ({ ...p, fullName: e.target.value }))}
                    placeholder="Your full name" id="profile-fullname" />
                </div>

                <div className="form-field">
                  <label className="field-label">Email Address</label>
                  <input className="field-input disabled" type="email" value={profile?.email || ''} disabled
                    title="Email cannot be changed" id="profile-email" />
                  <span className="field-hint">Email cannot be changed</span>
                </div>

                <div className="form-field">
                  <label className="field-label">Phone Number</label>
                  <div className="input-prefix-wrap">
                    <Phone size={14} className="input-prefix-icon" />
                    <input className="field-input with-prefix" type="tel" value={info.phone}
                      onChange={e => setInfo(p => ({ ...p, phone: e.target.value }))}
                      placeholder="+91 9876543210" id="profile-phone" />
                  </div>
                </div>

                <div className="form-field">
                  <label className="field-label">PAN Card Number</label>
                  <div className="input-prefix-wrap">
                    <CreditCard size={14} className="input-prefix-icon" />
                    <input className="field-input with-prefix" type="text" value={info.panCard}
                      onChange={e => setInfo(p => ({ ...p, panCard: e.target.value.toUpperCase() }))}
                      placeholder="ABCDE1234F" maxLength={10} id="profile-pan" />
                  </div>
                </div>

                <div className="form-field full-col">
                  <label className="field-label">Default Currency Display</label>
                  <div className="select-wrap">
                    <DollarSign size={14} className="input-prefix-icon" />
                    <select className="field-input field-select"
                      value={info.currency}
                      onChange={e => setInfo(p => ({ ...p, currency: e.target.value }))}
                      id="profile-currency">
                      {CURRENCIES.map(c => (
                        <option key={c.value} value={c.value}>{c.label}</option>
                      ))}
                    </select>
                    <ChevronDown size={14} className="select-chevron" />
                  </div>
                </div>
              </div>

              <div className="section-footer">
                <motion.button className="btn-save" onClick={saveProfile} disabled={savingInfo}
                  whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}>
                  {savingInfo ? <span className="spinner" /> : <Save size={14} />}
                  {savingInfo ? 'Saving...' : 'Save Changes'}
                </motion.button>
              </div>
            </Section>

            {/* Change Password */}
            <Section title="Change Password" icon={<Lock size={16} color="#7B61FF" />}>
              <div className="form-grid-2">
                <div className="form-field full-col">
                  <label className="field-label">Current Password</label>
                  <input className="field-input" type="password" value={pwd.currentPassword}
                    onChange={e => setPwd(p => ({ ...p, currentPassword: e.target.value }))}
                    placeholder="Enter current password" id="current-password" />
                </div>
                <div className="form-field">
                  <label className="field-label">New Password</label>
                  <input className="field-input" type="password" value={pwd.newPassword}
                    onChange={e => setPwd(p => ({ ...p, newPassword: e.target.value }))}
                    placeholder="Min 8 characters" id="new-password" />
                  <PasswordStrength password={pwd.newPassword} />
                </div>
                <div className="form-field">
                  <label className="field-label">Confirm New Password</label>
                  <input className="field-input" type="password" value={pwd.confirmPassword}
                    onChange={e => setPwd(p => ({ ...p, confirmPassword: e.target.value }))}
                    placeholder="Repeat new password" id="confirm-password" />
                  {pwd.confirmPassword && pwd.newPassword !== pwd.confirmPassword && (
                    <span className="field-error">Passwords don't match</span>
                  )}
                </div>
              </div>
              <div className="section-footer">
                <motion.button className="btn-save purple" onClick={changePassword} disabled={savingPwd}
                  whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}>
                  {savingPwd ? <span className="spinner" /> : <Lock size={14} />}
                  {savingPwd ? 'Changing...' : 'Change Password'}
                </motion.button>
              </div>
            </Section>
          </div>
        </div>
      )}

      {/* Toast */}
      <AnimatePresence>
        {toast && <Toast msg={toast.msg} type={toast.type} />}
      </AnimatePresence>
    </div>
  );
}

function PasswordStrength({ password }) {
  if (!password) return null;
  const checks = [
    { label: '8+ chars', ok: password.length >= 8 },
    { label: 'Uppercase', ok: /[A-Z]/.test(password) },
    { label: 'Number', ok: /[0-9]/.test(password) },
    { label: 'Special', ok: /[^A-Za-z0-9]/.test(password) },
  ];
  const score = checks.filter(c => c.ok).length;
  const colors = ['#FF4D4D', '#FF4D4D', '#FFB247', '#00D09C', '#00D09C'];
  const labels = ['', 'Weak', 'Weak', 'Fair', 'Strong'];
  return (
    <div className="pwd-strength">
      <div className="pwd-bars">
        {[0,1,2,3].map(i => (
          <div key={i} className="pwd-bar" style={{ background: i < score ? colors[score] : 'rgba(255,255,255,0.08)' }} />
        ))}
      </div>
      <span className="pwd-label" style={{ color: colors[score] }}>{labels[score]}</span>
    </div>
  );
}
