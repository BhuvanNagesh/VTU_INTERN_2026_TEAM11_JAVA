import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    X, Mail, Lock, Eye, EyeOff, User, CreditCard,
    ChevronDown, ArrowRight, CheckCircle2, TrendingUp, AlertCircle
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { API_BASE } from '../lib/config';
import './AuthModal.css';

const currencies = ['INR – Indian Rupee', 'USD – US Dollar', 'EUR – Euro', 'GBP – British Pound', 'AED – UAE Dirham'];

// ── Helpers ───────────────────────────────────────────────────────────────
const InputField = ({ icon: Icon, type = 'text', placeholder, value, onChange, autoComplete }) => (
    <div className="auth-input-wrap">
        <Icon size={16} className="auth-input-icon" />
        <input type={type} placeholder={placeholder} value={value} onChange={onChange}
            className="auth-input" autoComplete={autoComplete || 'off'} />
    </div>
);

const SelectField = ({ value, onChange, options, placeholder }) => (
    <div className="auth-input-wrap">
        <ChevronDown size={16} className="auth-input-icon" />
        <select value={value} onChange={onChange} className="auth-input auth-select">
            <option value="">{placeholder}</option>
            {options.map(o => <option key={o} value={o}>{o}</option>)}
        </select>
        <ChevronDown size={14} className="auth-select-chevron" />
    </div>
);

const ErrorBanner = ({ msg }) => msg ? (
    <div className="auth-error"><AlertCircle size={14} /> {msg}</div>
) : null;

// ── Sign In ───────────────────────────────────────────────────────────────
const SignInPanel = ({ onSwitch, onClose }) => {
    const { signIn } = useAuth();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPass, setShowPass] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [successMsg, setSuccessMsg] = useState('');
    const [loadingSeconds, setLoadingSeconds] = useState(0);
    const timerRef = useRef(null);

    // Counts up seconds while loading so we can show a helpful cold-start message
    useEffect(() => {
        if (loading) {
            setLoadingSeconds(0);
            timerRef.current = setInterval(() => setLoadingSeconds(s => s + 1), 1000);
        } else {
            clearInterval(timerRef.current);
            setLoadingSeconds(0);
        }
        return () => clearInterval(timerRef.current);
    }, [loading]);
    
    // Forgot Password states: 0 = sign in, 1 = ask email, 2 = ask OTP + new pass
    const [forgotStep, setForgotStep] = useState(0); 
    const [otp, setOtp] = useState('');
    const [newPassword, setNewPassword] = useState('');

    const handleSignIn = async (e) => {
        e.preventDefault();
        setError('');
        if (!email || !password) { setError('Please enter email and password.'); return; }
        setLoading(true);
        try {
            await signIn({ email, password });
            onClose(); 
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleSendOtp = async () => {
        setError('');
        setSuccessMsg('');
        if (!email) { setError('Please enter an email address first.'); return; }
        setLoading(true);
        try {
            const res = await fetch(`${API_BASE}/api/auth/forgot-password`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email }),
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Failed to send reset link');
            
            setSuccessMsg('OTP sent to email!');
            setForgotStep(2); 
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleResetPassword = async () => {
        setError('');
        setSuccessMsg('');
        if (!otp || !newPassword) { setError('Please enter the OTP and new password.'); return; }
        if (newPassword.length < 8) { setError('Password must be at least 8 characters.'); return; }
        
        setLoading(true);
        try {
            const res = await fetch(`${API_BASE}/api/auth/reset-password`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, otp, newPassword }),
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Failed to reset password');

            setSuccessMsg('Password reset successful! I can now sign in.');
            setForgotStep(0); 
            setPassword(''); 
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    if (forgotStep === 1) {
        return (
            <div className="auth-form">
                <button type="button" className="auth-back-btn" onClick={() => { setForgotStep(0); setError(''); setSuccessMsg(''); }}>← Back to Sign In</button>
                <h3 className="auth-form-title">Forgot Password</h3>
                <p className="auth-form-sub">Enter email to receive a 6-digit OTP.</p>
                <ErrorBanner msg={error} />
                
                <InputField icon={Mail} type="email" placeholder="Email address" value={email} onChange={e => setEmail(e.target.value)} />
                
                <motion.button type="button" className="auth-primary-btn" disabled={loading}
                    onClick={handleSendOtp}
                    whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                    {loading ? 'Sending...' : 'Send OTP'} <ArrowRight size={16} />
                </motion.button>
            </div>
        );
    }

    if (forgotStep === 2) {
        return (
            <div className="auth-form">
                <button type="button" className="auth-back-btn" onClick={() => { setForgotStep(1); setError(''); }}>← Back</button>
                <h3 className="auth-form-title">Reset Password</h3>
                <p className="auth-form-sub">Enter the OTP sent to {email}</p>
                <ErrorBanner msg={error} />
                {successMsg && <div className="auth-error" style={{color: '#00D09C', backgroundColor: 'rgba(0, 208, 156, 0.1)'}}><CheckCircle2 size={14} /> {successMsg}</div>}
                
                <InputField icon={Lock} type="text" placeholder="6-digit OTP" value={otp} onChange={e => setOtp(e.target.value)} />
                
                <div className="auth-input-wrap">
                    <Lock size={16} className="auth-input-icon" />
                    <input type={showPass ? 'text' : 'password'} placeholder="New Password (min 8 chars)"
                        value={newPassword} onChange={e => setNewPassword(e.target.value)}
                        className="auth-input" />
                    <button type="button" className="auth-eye-btn" onClick={() => setShowPass(!showPass)}>
                        {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                    </button>
                </div>

                <motion.button type="button" className="auth-primary-btn" disabled={loading}
                    onClick={handleResetPassword}
                    whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                    {loading ? 'Resetting...' : 'Confirm Reset'} <CheckCircle2 size={16} />
                </motion.button>
            </div>
        );
    }

    return (
        <form className="auth-form" onSubmit={handleSignIn}>
            <h3 className="auth-form-title">Welcome back</h3>
            <p className="auth-form-sub">Sign in to my WealthWise account</p>
            <ErrorBanner msg={error} />
            {successMsg && <div className="auth-error" style={{color: '#00D09C', backgroundColor: 'rgba(0, 208, 156, 0.1)'}}><CheckCircle2 size={14} /> {successMsg}</div>}
            
            <InputField icon={Mail} type="email" placeholder="Email address" value={email}
                onChange={e => setEmail(e.target.value)} autoComplete="email" />
            <div className="auth-input-wrap">
                <Lock size={16} className="auth-input-icon" />
                <input type={showPass ? 'text' : 'password'} placeholder="Password"
                    value={password} onChange={e => setPassword(e.target.value)}
                    className="auth-input" autoComplete="current-password" />
                <button type="button" className="auth-eye-btn" onClick={() => setShowPass(!showPass)}>
                    {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
            </div>
            <div className="auth-forgot-row">
                <button type="button" className="auth-link" onClick={() => { setForgotStep(1); setError(''); setSuccessMsg(''); }}>
                    Forgot password?
                </button>
            </div>
            <motion.button type="submit" className="auth-primary-btn" disabled={loading}
                whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                {loading ? `Signing in… ${loadingSeconds > 0 ? `(${loadingSeconds}s)` : ''}` : 'Sign In'} <ArrowRight size={16} />
            </motion.button>
            {/* Cold-start warming notice — only shows after 5 seconds of loading */}
            {loading && loadingSeconds >= 5 && (
                <div style={{
                    marginTop: '10px', padding: '10px 14px', borderRadius: '10px',
                    background: 'rgba(255, 178, 71, 0.08)', border: '1px solid rgba(255, 178, 71, 0.25)',
                    fontSize: '12px', color: '#FFB247', lineHeight: 1.6, textAlign: 'center'
                }}>
                    ⏳ <strong>Backend is starting up</strong> — the first request after idle takes a moment.
                    <br />Please wait, you'll be signed in shortly!
                </div>
            )}
            <p className="auth-switch-prompt">
                Don't have an account?{' '}
                <button type="button" className="auth-link" onClick={onSwitch}>Sign up free</button>
            </p>
        </form>
    );
};

// ── Sign Up ───────────────────────────────────────────────────────────────
const SignUpPanel = ({ onSwitch, onClose }) => {
    const { signUp } = useAuth();
    const [step, setStep] = useState(1);
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [phone, setPhone] = useState('');
    const [pass, setPass] = useState('');
    const [showPass, setShowPass] = useState(false);
    const [currency, setCurrency] = useState('');
    const [pan, setPan] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [loadingSeconds, setLoadingSeconds] = useState(0);
    const timerRef = useRef(null);

    useEffect(() => {
        if (loading) {
            setLoadingSeconds(0);
            timerRef.current = setInterval(() => setLoadingSeconds(s => s + 1), 1000);
        } else {
            clearInterval(timerRef.current);
            setLoadingSeconds(0);
        }
        return () => clearInterval(timerRef.current);
    }, [loading]);

    const handleNext = (e) => {
        e.preventDefault();
        setError('');
        if (!name.trim()) { setError('Please enter full name.'); return; }
        if (!email.trim()) { setError('Email is required.'); return; }
        if (pass.length < 8) { setError('Password must be at least 8 characters.'); return; }
        setStep(2);
    };

    const handleSignUp = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            await signUp({
                fullName: name,
                email,
                password: pass,
                phone,
                currency: currency || 'INR – Indian Rupee',
                panCard: pan.toUpperCase() || null,
            });
            onClose(); 
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-form">
            {step === 2 && (
                <button type="button" className="auth-back-btn" onClick={() => { setStep(1); setError(''); }}>← Back</button>
            )}
            <h3 className="auth-form-title">{step === 1 ? 'Create account' : 'A few more details'}</h3>
            <p className="auth-form-sub">
                {step === 1 ? 'Track all mutual funds in one place' : 'Personalise the portfolio experience'}
            </p>

            <div className="auth-progress">
                <div className={`auth-prog-step ${step >= 1 ? 'done' : ''}`}>1</div>
                <div className="auth-prog-line" />
                <div className={`auth-prog-step ${step >= 2 ? 'done' : ''}`}>2</div>
            </div>

            <ErrorBanner msg={error} />

            <AnimatePresence mode="wait">
                {step === 1 && (
                    <motion.form key="s1" className="auth-fields"
                        initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.22 }}
                        onSubmit={handleNext}
                    >
                        <InputField icon={User} placeholder="Full name" value={name} onChange={e => setName(e.target.value)} />
                        <InputField icon={Mail} type="email" placeholder="Email address" value={email}
                            onChange={e => setEmail(e.target.value)} autoComplete="email" />
                        <InputField icon={User} type="tel" placeholder="Phone (+91…)" value={phone} onChange={e => setPhone(e.target.value)} />
                        <div className="auth-input-wrap">
                            <Lock size={16} className="auth-input-icon" />
                            <input type={showPass ? 'text' : 'password'} placeholder="Create password (min 8 chars)"
                                value={pass} onChange={e => setPass(e.target.value)}
                                className="auth-input" autoComplete="new-password" />
                            <button type="button" className="auth-eye-btn" onClick={() => setShowPass(!showPass)}>
                                {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                            </button>
                        </div>
                        <motion.button type="submit" className="auth-primary-btn"
                            whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                            Continue <ArrowRight size={16} />
                        </motion.button>
                    </motion.form>
                )}

                {step === 2 && (
                    <motion.form key="s2" className="auth-fields"
                        initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.22 }}
                        onSubmit={handleSignUp}
                    >
                        <SelectField value={currency} onChange={e => setCurrency(e.target.value)}
                            options={currencies} placeholder="Select Currency (optional)" />
                        <InputField icon={CreditCard} placeholder="PAN Card e.g. ABCDE1234F (optional)"
                            value={pan} onChange={e => setPan(e.target.value.toUpperCase())} />
                        <p className="auth-disclaimer">
                            🔒 PAN is encrypted and used only for portfolio matching. Optional for now.
                        </p>
                        <motion.button type="submit" className="auth-primary-btn" disabled={loading}
                            whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                            {loading ? `Creating account… ${loadingSeconds > 0 ? `(${loadingSeconds}s)` : ''}` : 'Create Account'} <CheckCircle2 size={16} />
                        </motion.button>
                        {loading && loadingSeconds >= 5 && (
                            <div style={{
                                marginTop: '10px', padding: '10px 14px', borderRadius: '10px',
                                background: 'rgba(255, 178, 71, 0.08)', border: '1px solid rgba(255, 178, 71, 0.25)',
                                fontSize: '12px', color: '#FFB247', lineHeight: 1.6, textAlign: 'center'
                            }}>
                                ⏳ <strong>Backend is starting up</strong> — the first request after idle takes a moment.
                                <br />Please wait, your account is being created!
                            </div>
                        )}
                    </motion.form>
                )}
            </AnimatePresence>

            <p className="auth-switch-prompt">
                Already have an account?{' '}
                <button type="button" className="auth-link" onClick={onSwitch}>Sign in</button>
            </p>
        </div>
    );
};

// ── Modal shell ───────────────────────────────────────────────────────────
const AuthModal = ({ isOpen, onClose, initialTab = 'signin' }) => {
    const [tab, setTab] = useState(initialTab);

    if (!isOpen) return null;

    return (
        <AnimatePresence>
            {isOpen && (
                <motion.div className="auth-backdrop"
                    initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                    onClick={onClose}>
                    <motion.div className="auth-modal glassmorphism"
                        initial={{ opacity: 0, scale: 0.88, y: 40 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.92, y: 20 }}
                        transition={{ type: 'spring', damping: 20, stiffness: 260 }}
                        onClick={e => e.stopPropagation()}>

                        <motion.button className="auth-close" onClick={onClose}
                            whileHover={{ scale: 1.1, rotate: 90 }} transition={{ duration: 0.2 }}>
                            <X size={18} />
                        </motion.button>

                        <div className="auth-top-logo">
                            <div className="auth-logo-mark"><TrendingUp size={14} color="#00D09C" /></div>
                            <span className="auth-logo-text">WealthWise</span>
                        </div>

                        <div className="auth-tabs">
                            <button className={`auth-tab ${tab === 'signin' ? 'active' : ''}`} onClick={() => setTab('signin')}>Sign In</button>
                            <button className={`auth-tab ${tab === 'signup' ? 'active' : ''}`} onClick={() => setTab('signup')}>Sign Up</button>
                            <motion.div className="auth-tab-indicator"
                                animate={{ x: tab === 'signin' ? 0 : '100%' }}
                                transition={{ type: 'spring', stiffness: 300, damping: 28 }} />
                        </div>

                        <AnimatePresence mode="wait">
                            <motion.div key={tab}
                                initial={{ opacity: 0, x: tab === 'signin' ? -20 : 20 }}
                                animate={{ opacity: 1, x: 0 }}
                                exit={{ opacity: 0, x: tab === 'signin' ? 20 : -20 }}
                                transition={{ duration: 0.22 }}>
                                {tab === 'signin'
                                    ? <SignInPanel onSwitch={() => setTab('signup')} onClose={onClose} />
                                    : <SignUpPanel onSwitch={() => setTab('signin')} onClose={onClose} />
                                }
                            </motion.div>
                        </AnimatePresence>
                    </motion.div>
                </motion.div>
            )}
        </AnimatePresence>
    );
};

export default AuthModal;