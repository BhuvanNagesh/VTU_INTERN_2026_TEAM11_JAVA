import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import Hero from './components/Hero';
import MutualFundSection from './components/MutualFundSection';
import Features from './components/Features';
import AnalyticsSection from './components/AnalyticsSection';
import CTA from './components/CTA';
import Footer from './components/Footer';
import ParticleField from './components/ParticleField';
import AuthModal from './components/AuthModal';
import './App.css';

// Inner app that has access to AuthContext
function AppContent() {
  const [scrollY, setScrollY] = useState(0);
  const [authOpen, setAuthOpen] = useState(false);
  const [authTab, setAuthTab] = useState('signin');
  const { user, signOut } = useAuth();

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
      <Navbar scrollY={scrollY} user={user} onSignIn={() => openAuth('signin')} onSignUp={() => openAuth('signup')} onSignOut={signOut} />
      <main>
        <Hero scrollY={scrollY} onOpenAuth={openAuth} />
        <MutualFundSection />
        <AnalyticsSection />
        <Features />
        <CTA onOpenAuth={openAuth} />
      </main>
      <Footer />
      <AuthModal isOpen={authOpen} onClose={() => setAuthOpen(false)} initialTab={authTab} />
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

export default App;
