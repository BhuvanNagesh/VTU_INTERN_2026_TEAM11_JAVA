import { useState, useRef, useEffect, useCallback } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  TrendingUp, Bell, Search, Menu, X, User, LogOut,
  LayoutDashboard, List, UserCircle, Activity, Target,
  Sun, Moon, CheckCircle, AlertTriangle, Info, TrendingDown,
  ArrowRight, Clock, ChevronRight
} from 'lucide-react';
import './Navbar.css';

const tickerData = [
  { symbol: 'SENSEX',       value: '73,847',  change: '+0.82%', up: true },
  { symbol: 'NIFTY 50',     value: '22,475',  change: '+0.67%', up: true },
  { symbol: 'NIFTY BANK',   value: '48,023',  change: '-0.21%', up: false },
  { symbol: 'HDFC MF NAV',  value: '₹842.34', change: '+1.2%',  up: true },
  { symbol: 'SBI Bluechip', value: '₹78.45',  change: '+0.54%', up: true },
  { symbol: 'MIRAE MF NAV', value: '₹105.20', change: '+0.88%', up: true },
  { symbol: 'AXIS ELSS',    value: '₹68.92',  change: '-0.31%', up: false },
  { symbol: 'GOLD ETF',     value: '₹72,340', change: '+0.31%', up: true },
];

// ── Static notifications (in real app these'd come from /api/notifications) ──
const NOTIFICATIONS = [
  {
    id: 1, type: 'success', icon: CheckCircle,
    title: 'SIP Executed Successfully',
    body: 'Axis Midcap — ₹5,000 debited on 15-Mar-2024',
    time: '2h ago', read: false,
  },
  {
    id: 2, type: 'warning', icon: AlertTriangle,
    title: 'NAV Data Refreshed',
    body: '4 schemes updated with latest NAV from AMFI',
    time: '5h ago', read: false,
  },
  {
    id: 3, type: 'info', icon: TrendingDown,
    title: 'Portfolio Alert',
    body: 'Parag Parikh Flexi Cap down 2.3% this week',
    time: '1d ago', read: true,
  },
  {
    id: 4, type: 'info', icon: Info,
    title: 'Goal Milestone',
    body: 'Retirement Fund is 40% of target 🎉',
    time: '2d ago', read: true,
  },
  {
    id: 5, type: 'success', icon: CheckCircle,
    title: 'CAS Import Complete',
    body: '40 transactions imported across 4 folios',
    time: '3d ago', read: true,
  },
];

// ── Searchable pages / quick actions ─────────────────────────────────────────
const SEARCH_ITEMS = [
  { label: 'Dashboard',       path: '/dashboard',    icon: LayoutDashboard, desc: 'Portfolio overview' },
  { label: 'Transactions',    path: '/transactions', icon: List,            desc: 'Add & view transactions' },
  { label: 'Analytics',       path: '/analytics',    icon: Activity,        desc: 'Performance & risk metrics' },
  { label: 'Goals',           path: '/goals',        icon: Target,          desc: 'Goal planner & tracking' },
  { label: 'Profile',         path: '/profile',      icon: UserCircle,      desc: 'Account settings' },
  { label: 'SIP Intelligence',path: '/analytics',    icon: TrendingUp,      desc: 'Smart SIP analytics' },
];

// ── Navbar Component ──────────────────────────────────────────────────────────
const Navbar = ({ scrollY, user, onSignIn, onSignUp, onSignOut, theme, onToggleTheme }) => {
  const [mobileOpen,   setMobileOpen]   = useState(false);
  const [searchOpen,   setSearchOpen]   = useState(false);
  const [searchQuery,  setSearchQuery]  = useState('');
  const [notifOpen,    setNotifOpen]    = useState(false);
  const [notifications, setNotifications] = useState(NOTIFICATIONS);

  const searchRef = useRef(null);
  const notifRef  = useRef(null);
  const navigate  = useNavigate();
  const location  = useLocation();
  const isScrolled = scrollY > 50;
  const isDark     = theme === 'dark';
  const unreadCount = notifications.filter(n => !n.read).length;

  // Search results derived from query
  const searchResults = searchQuery.trim().length > 0
    ? SEARCH_ITEMS.filter(item =>
        item.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.desc.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : SEARCH_ITEMS;

  // Close panels on outside click
  useEffect(() => {
    const handler = (e) => {
      if (searchRef.current && !searchRef.current.contains(e.target)) setSearchOpen(false);
      if (notifRef.current  && !notifRef.current.contains(e.target))  setNotifOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Close on Escape
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'Escape') { setSearchOpen(false); setNotifOpen(false); }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  // Auto-focus search input when opened
  useEffect(() => {
    if (searchOpen) {
      setTimeout(() => {
        const input = document.getElementById('navbar-search-input');
        if (input) input.focus();
      }, 80);
    } else {
      setSearchQuery('');
    }
  }, [searchOpen]);

  const handleSearchSelect = (path) => {
    navigate(path);
    setSearchOpen(false);
    setSearchQuery('');
  };

  const markAllRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  const markRead = (id) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
  };

  const isActive = (path) => location.pathname === path;

  return (
    <>
      <header className={`navbar ${isScrolled ? 'navbar-scrolled' : ''}`}>

        {/* ── Live Ticker ──────────────────────────────────────────────── */}
        <div className="ticker-bar">
          <div className="ticker-label">
            <TrendingUp size={12} />
            <span>LIVE</span>
          </div>
          <div className="ticker-track-wrapper">
            <div className="ticker-track">
              {[...tickerData, ...tickerData].map((item, i) => (
                <div key={i} className="ticker-item">
                  <span className="ticker-symbol">{item.symbol}</span>
                  <span className="ticker-value">{item.value}</span>
                  <span className={`ticker-change ${item.up ? 'up' : 'down'}`}>
                    {item.up ? '▲' : '▼'} {item.change}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── Main Nav ─────────────────────────────────────────────────── */}
        <nav className="nav-main">
          <div className="nav-inner">

            {/* Logo */}
            <Link to="/" style={{ textDecoration: 'none' }}>
              <motion.div className="nav-logo" whileHover={{ scale: 1.02 }}>
                <div className="logo-mark">
                  <div className="logo-inner-ring" />
                  <TrendingUp size={16} color="#00D09C" />
                </div>
                <span className="logo-wordmark">WealthWise</span>
              </motion.div>
            </Link>

            {/* App Nav Links — only when signed in, no Profile tab */}
            {user && (
              <div className="nav-app-links">
                <Link to="/dashboard"    className={`nav-app-link ${isActive('/dashboard')    ? 'active' : ''}`}>
                  <LayoutDashboard size={15} /> Dashboard
                </Link>
                <Link to="/transactions" className={`nav-app-link ${isActive('/transactions') ? 'active' : ''}`}>
                  <List size={15} /> Transactions
                </Link>
                <Link to="/analytics"   className={`nav-app-link ${isActive('/analytics')    ? 'active' : ''}`}>
                  <Activity size={15} /> Analytics
                </Link>
                <Link to="/goals"       className={`nav-app-link ${isActive('/goals')        ? 'active' : ''}`}>
                  <Target size={15} /> Goals
                </Link>
              </div>
            )}

            {/* ── Nav Actions ──────────────────────────────────────────── */}
            <div className="nav-actions">

              {/* 🔍 Search */}
              <div className="nav-panel-anchor" ref={searchRef}>
                <motion.button
                  className={`nav-icon-btn ${searchOpen ? 'active' : ''}`}
                  aria-label="Search"
                  onClick={() => { setSearchOpen(o => !o); setNotifOpen(false); }}
                  whileHover={{ scale: 1.08 }}
                  whileTap={{ scale: 0.92 }}
                >
                  <Search size={18} />
                </motion.button>

                <AnimatePresence>
                  {searchOpen && (
                    <motion.div
                      className="nav-panel search-panel"
                      initial={{ opacity: 0, y: -8, scale: 0.96 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: -8, scale: 0.96 }}
                      transition={{ duration: 0.18 }}
                    >
                      <div className="search-input-wrap">
                        <Search size={15} className="search-icon-inner" />
                        <input
                          id="navbar-search-input"
                          className="search-input"
                          placeholder="Search pages, analytics, goals…"
                          value={searchQuery}
                          onChange={e => setSearchQuery(e.target.value)}
                          onKeyDown={e => {
                            if (e.key === 'Enter' && searchResults.length > 0)
                              handleSearchSelect(searchResults[0].path);
                          }}
                        />
                        {searchQuery && (
                          <button className="search-clear" onClick={() => setSearchQuery('')}>
                            <X size={13} />
                          </button>
                        )}
                      </div>
                      <div className="search-results">
                        {searchResults.length === 0 ? (
                          <div className="search-empty">No pages match "{searchQuery}"</div>
                        ) : (
                          searchResults.map(item => (
                            <button
                              key={item.path + item.label}
                              className="search-result-item"
                              onClick={() => handleSearchSelect(item.path)}
                            >
                              <div className="search-result-icon">
                                <item.icon size={15} />
                              </div>
                              <div className="search-result-text">
                                <span className="search-result-label">{item.label}</span>
                                <span className="search-result-desc">{item.desc}</span>
                              </div>
                              <ChevronRight size={14} className="search-result-arrow" />
                            </button>
                          ))
                        )}
                      </div>
                      <div className="search-footer">
                        <kbd>↑↓</kbd> navigate &nbsp;&nbsp; <kbd>↵</kbd> open &nbsp;&nbsp; <kbd>Esc</kbd> close
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {/* 🔔 Notifications — only when signed in */}
              {user && (
                <div className="nav-panel-anchor" ref={notifRef}>
                  <motion.button
                    className={`nav-icon-btn ${notifOpen ? 'active' : ''}`}
                    aria-label="Notifications"
                    onClick={() => { setNotifOpen(o => !o); setSearchOpen(false); }}
                    whileHover={{ scale: 1.08 }}
                    whileTap={{ scale: 0.92 }}
                  >
                    <Bell size={18} />
                    {unreadCount > 0 && (
                      <motion.span
                        className="notif-badge"
                        initial={{ scale: 0 }}
                        animate={{ scale: 1 }}
                        transition={{ type: 'spring', stiffness: 400, damping: 15 }}
                      >
                        {unreadCount}
                      </motion.span>
                    )}
                  </motion.button>

                  <AnimatePresence>
                    {notifOpen && (
                      <motion.div
                        className="nav-panel notif-panel"
                        initial={{ opacity: 0, y: -8, scale: 0.96 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: -8, scale: 0.96 }}
                        transition={{ duration: 0.18 }}
                      >
                        <div className="notif-header">
                          <span className="notif-title">Notifications</span>
                          {unreadCount > 0 && (
                            <button className="notif-mark-all" onClick={markAllRead}>
                              Mark all read
                            </button>
                          )}
                        </div>
                        <div className="notif-list">
                          {notifications.map(n => (
                            <button
                              key={n.id}
                              className={`notif-item ${!n.read ? 'unread' : ''} notif-type-${n.type}`}
                              onClick={() => markRead(n.id)}
                            >
                              <div className={`notif-item-icon type-${n.type}`}>
                                <n.icon size={14} />
                              </div>
                              <div className="notif-item-body">
                                <div className="notif-item-title">{n.title}</div>
                                <div className="notif-item-text">{n.body}</div>
                                <div className="notif-item-time">
                                  <Clock size={11} /> {n.time}
                                </div>
                              </div>
                              {!n.read && <span className="notif-unread-dot" />}
                            </button>
                          ))}
                        </div>
                        <div className="notif-footer">
                          <Link to="/dashboard" onClick={() => setNotifOpen(false)}>
                            View all activity <ArrowRight size={13} />
                          </Link>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              )}

              {/* 🌙 Theme Toggle */}
              <motion.button
                className="theme-toggle-btn"
                onClick={onToggleTheme}
                aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
                whileHover={{ scale: 1.08 }}
                whileTap={{ scale: 0.92 }}
                title={isDark ? 'Light Mode' : 'Dark Mode'}
              >
                <AnimatePresence mode="wait" initial={false}>
                  <motion.span
                    key={theme}
                    initial={{ opacity: 0, rotate: -30, scale: 0.7 }}
                    animate={{ opacity: 1, rotate: 0, scale: 1 }}
                    exit={{ opacity: 0, rotate: 30, scale: 0.7 }}
                    transition={{ duration: 0.2 }}
                    style={{ display: 'flex', alignItems: 'center' }}
                  >
                    {isDark ? <Sun size={17} /> : <Moon size={17} />}
                  </motion.span>
                </AnimatePresence>
              </motion.button>

              {/* 👤 User greeting → Profile  |  Auth buttons */}
              {user ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {/* Clicking the greeting goes to /profile */}
                  <motion.div
                    className="user-greeting"
                    onClick={() => navigate('/profile')}
                    whileHover={{ scale: 1.03 }}
                    whileTap={{ scale: 0.97 }}
                    title="View Profile"
                  >
                    <div className="user-avatar">
                      {(user.fullName || user.email || 'U')[0].toUpperCase()}
                    </div>
                    <span className="user-greeting-text">
                      Hi, {user.fullName ? user.fullName.split(' ')[0] : 'User'}
                    </span>
                  </motion.div>

                  <motion.button
                    className="nav-btn-signout"
                    onClick={onSignOut}
                    whileHover={{ scale: 1.04 }}
                    whileTap={{ scale: 0.96 }}
                    title="Sign Out"
                  >
                    <LogOut size={15} />
                  </motion.button>
                </div>
              ) : (
                <>
                  <motion.button
                    className="nav-btn-login"
                    onClick={onSignIn}
                    whileHover={{ scale: 1.03 }}
                    whileTap={{ scale: 0.97 }}
                  >
                    Sign In
                  </motion.button>
                  <motion.button
                    className="nav-btn-signup"
                    onClick={onSignUp}
                    whileHover={{ scale: 1.03, boxShadow: '0 0 20px rgba(0,208,156,0.45)' }}
                    whileTap={{ scale: 0.97 }}
                  >
                    Get Started Free
                  </motion.button>
                </>
              )}

              <button
                className="nav-mobile-toggle"
                onClick={() => setMobileOpen(!mobileOpen)}
                aria-label="Toggle menu"
              >
                {mobileOpen ? <X size={20} /> : <Menu size={20} />}
              </button>
            </div>
          </div>
        </nav>

        {/* ── Mobile Nav ───────────────────────────────────────────────── */}
        <AnimatePresence>
          {mobileOpen && (
            <motion.div
              className="mobile-nav glassmorphism"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.3 }}
            >
              {user ? (
                <>
                  <Link to="/dashboard"    className="mobile-nav-item" onClick={() => setMobileOpen(false)}><LayoutDashboard size={15} /> Dashboard</Link>
                  <Link to="/transactions" className="mobile-nav-item" onClick={() => setMobileOpen(false)}><List size={15} /> Transactions</Link>
                  <Link to="/analytics"   className="mobile-nav-item" onClick={() => setMobileOpen(false)}><Activity size={15} /> Analytics</Link>
                  <Link to="/goals"        className="mobile-nav-item" onClick={() => setMobileOpen(false)}><Target size={15} /> Goals</Link>
                  <Link to="/profile"      className="mobile-nav-item" onClick={() => setMobileOpen(false)}><UserCircle size={15} /> Profile</Link>
                </>
              ) : (
                ['Mutual Funds', 'Analytics', 'Features'].map(label => (
                  <a key={label} href="#" className="mobile-nav-item">{label}</a>
                ))
              )}
              <div className="mobile-nav-btns">
                {user ? (
                  <>
                    <div style={{ color: 'var(--text-secondary)', marginBottom: '10px', textAlign: 'center', fontSize: '13px' }}>
                      {user.email}
                    </div>
                    <button className="nav-btn-login" onClick={onSignOut} style={{ width: '100%' }}>Sign Out</button>
                  </>
                ) : (
                  <>
                    <button className="nav-btn-login" onClick={onSignIn} style={{ marginBottom: '10px' }}>Sign In</button>
                    <button className="nav-btn-signup" onClick={onSignUp}>Get Started</button>
                  </>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </header>
    </>
  );
};

export default Navbar;