import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, Bell, Search, Menu, X, User, LogOut, LayoutDashboard, List, UserCircle, Activity, Sun, Moon } from 'lucide-react';
import './Navbar.css';

const tickerData = [
  { symbol: 'SENSEX', value: '73,847', change: '+0.82%', up: true },
  { symbol: 'NIFTY 50', value: '22,475', change: '+0.67%', up: true },
  { symbol: 'NIFTY BANK', value: '48,023', change: '-0.21%', up: false },
  { symbol: 'HDFC MF NAV', value: '₹842.34', change: '+1.2%', up: true },
  { symbol: 'SBI Bluechip NAV', value: '₹78.45', change: '+0.54%', up: true },
  { symbol: 'MIRAE MF NAV', value: '₹105.20', change: '+0.88%', up: true },
  { symbol: 'AXIS ELSS NAV', value: '₹68.92', change: '-0.31%', up: false },
  { symbol: 'GOLD ETF', value: '₹72,340', change: '+0.31%', up: true },
  { symbol: 'NIFTY 50 Index', value: '18,653', change: '+0.45%', up: true },
  { symbol: 'SENSEX TRI', value: '73,210', change: '+0.60%', up: true },
];

const navItems = [
  { label: 'Mutual Funds' },
  { label: 'Analytics' },
  { label: 'Features' }
];

// We now accept the user and auth functions passed down from App.jsx
const Navbar = ({ scrollY, user, onSignIn, onSignUp, onSignOut, theme, onToggleTheme }) => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const isScrolled = scrollY > 50;
  const isDark = theme === 'dark';

  return (
    <>
      <header className={`navbar ${isScrolled ? 'navbar-scrolled' : ''}`}>
        {/* Live NAV Ticker Bar */}
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

        {/* Main Navigation */}
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

            {/* App Nav Links — only shown when signed in */}
            {user && (
              <div className="nav-app-links">
                <Link to="/dashboard" className="nav-app-link">
                  <LayoutDashboard size={15} />
                  Dashboard
                </Link>
                <Link to="/transactions" className="nav-app-link">
                  <List size={15} />
                  Transactions
                </Link>
                <Link to="/analytics" className="nav-app-link">
                  <Activity size={15} />
                  Analytics
                </Link>
                <Link to="/profile" className="nav-app-link">
                  <UserCircle size={15} />
                  Profile
                </Link>
              </div>
            )}

            {/* Nav Actions */}
            <div className="nav-actions">
              <button className="nav-icon-btn" aria-label="Search">
                <Search size={18} />
              </button>
              
              {user && (
                <button className="nav-icon-btn" aria-label="Notifications">
                  <Bell size={18} />
                  <span className="notif-dot" />
                </button>
              )}

              {/* Theme Toggle */}
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

              {/* Conditional Rendering: Show Profile if logged in, otherwise show Auth buttons */}
              {user ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-primary)', fontSize: '14px', fontWeight: '500' }}>
                    <User size={16} color="var(--accent-green)" />
                    <span>Hi, {user.fullName ? user.fullName.split(' ')[0] : 'User'}</span>
                  </div>
                  <motion.button
                    className="nav-btn-login"
                    onClick={onSignOut}
                    whileHover={{ scale: 1.03 }}
                    whileTap={{ scale: 0.97 }}
                    title="Sign Out"
                    style={{ padding: '8px 12px' }}
                  >
                    <LogOut size={16} />
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

        {/* Mobile Nav */}
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
                  <Link to="/dashboard" className="mobile-nav-item" onClick={() => setMobileOpen(false)}>
                    <LayoutDashboard size={15} /> Dashboard
                  </Link>
                  <Link to="/transactions" className="mobile-nav-item" onClick={() => setMobileOpen(false)}>
                    <List size={15} /> Transactions
                  </Link>
                  <Link to="/analytics" className="mobile-nav-item" onClick={() => setMobileOpen(false)}>
                    <Activity size={15} /> Analytics
                  </Link>
                </>
              ) : (
                navItems.map((item) => (
                  <a key={item.label} href="#" className="mobile-nav-item">{item.label}</a>
                ))
              )}
              <div className="mobile-nav-btns">
                {user ? (
                   <>
                     <div style={{ color: '#fff', marginBottom: '10px', textAlign: 'center' }}>
                       Logged in as: <strong>{user.email}</strong>
                     </div>
                     <button className="nav-btn-login" onClick={onSignOut} style={{ width: '100%', marginBottom: '10px' }}>Sign Out</button>
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