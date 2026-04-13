import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ThemeProvider, useTheme } from './context/ThemeContext';
import { BackendWarmupProvider } from './context/BackendWarmupContext';
import Navbar from './components/Navbar';
import Hero from './components/Hero';
import MutualFundSection from './components/MutualFundSection';
import AnalyticsSection from './components/AnalyticsSection';
import Features from './components/Features';
import CTA from './components/CTA';
import Footer from './components/Footer';
import ParticleField from './components/ParticleField';
import AuthModal from './components/AuthModal';
import DashboardPage from './components/DashboardPage';
import TransactionsPage from './components/TransactionsPage';
import ProfilePage from './components/ProfilePage';
import AnalyticsPage from './components/AnalyticsPage';
import ErrorBoundary from './components/ErrorBoundary';
import WarmupOverlay from './components/WarmupOverlay';
import './App.css';

// Landing page (existing)
function LandingPage({ scrollY, openAuth }) {
  return (
    <>
      <Hero scrollY={scrollY} onOpenAuth={openAuth} />
      <MutualFundSection />
      <AnalyticsSection />
      <Features />
      <CTA onOpenAuth={openAuth} />
      <Footer />
    </>
  );
}

// Protected route wrapper — guards against expired JWT tokens
function ProtectedRoute({ children }) {
  const { user, getToken, signOut, loading } = useAuth();

  // Check actual stored JWT expiry on every render while user is logged in
  useEffect(() => {
    if (!user) return; // not logged in, nothing to check
    const token = getToken();
    if (!token) { signOut(); return; }
    try {
      // JWT payload is base64-encoded: header.payload.signature
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expMs = payload.exp * 1000; // exp is in seconds
      if (Date.now() >= expMs) {
        // Token already expired — sign out immediately
        signOut();
        return;
      }
      // Schedule auto-logout exactly when the token expires
      const msUntilExpiry = expMs - Date.now();
      const timer = setTimeout(() => signOut(), msUntilExpiry);
      return () => clearTimeout(timer);
    } catch {
      // Malformed token — force logout
      signOut();
    }
  }, [user, getToken, signOut]);

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#0A0A0F' }}>
      <div style={{ width: 40, height: 40, border: '3px solid rgba(0,208,156,0.2)', borderTopColor: '#00D09C', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
    </div>
  );
  return user ? children : <Navigate to="/" replace />;
}

function AppContent() {
  const [scrollY, setScrollY] = useState(0);
  const [authOpen, setAuthOpen] = useState(false);
  const [authTab, setAuthTab] = useState('signin');
  const { user, signOut } = useAuth();
  const { theme, toggleTheme } = useTheme();

  useEffect(() => {
    const handleScroll = () => setScrollY(window.scrollY);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const openAuth = (tab = 'signin') => {
    setAuthTab(tab);
    setAuthOpen(true);
  };

  return (
    <div className="app">
      <ParticleField />
      <Navbar
        scrollY={scrollY}
        user={user}
        onSignIn={() => openAuth('signin')}
        onSignUp={() => openAuth('signup')}
        onSignOut={signOut}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
      <main>
        <Routes>
          {/* Public landing */}
          <Route path="/" element={<LandingPage scrollY={scrollY} openAuth={openAuth} />} />

          {/* Protected app routes */}
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/transactions" element={<ProtectedRoute><TransactionsPage /></ProtectedRoute>} />
          <Route path="/analytics" element={<ProtectedRoute><AnalyticsPage /></ProtectedRoute>} />
          <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <AuthModal isOpen={authOpen} onClose={() => setAuthOpen(false)} initialTab={authTab} />
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <BackendWarmupProvider>
          <AuthProvider>
            <ErrorBoundary>
              <AppContent />
              <WarmupOverlay />
            </ErrorBoundary>
          </AuthProvider>
        </BackendWarmupProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}

export default App;
