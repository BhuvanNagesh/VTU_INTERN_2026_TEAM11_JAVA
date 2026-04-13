import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowRight, Upload, BarChart2, Users, Database } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid
} from 'recharts';
import './Hero.css';

// Simulated portfolio growth data (mutual fund portfolio)
const generatePortfolioData = () => {
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  let invested = 120000;
  let current = 120000;
  return months.map((month) => {
    invested += 10000; // monthly SIP
    current = current * (1 + (Math.random() * 0.07 - 0.01)) + 10000;
    return { month, invested: Math.round(invested), current: Math.round(current) };
  });
};

const chartData = generatePortfolioData();

const CustomTooltip = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    return (
      <div className="chart-tooltip">
        <p className="tooltip-label">{label}</p>
        <p className="tooltip-value" style={{ color: '#00D09C' }}>Current: ₹{payload[0]?.value?.toLocaleString()}</p>
        <p className="tooltip-nifty" style={{ color: '#7B61FF' }}>Invested: ₹{payload[1]?.value?.toLocaleString()}</p>
      </div>
    );
  }
  return null;
};

const GlowBlob = () => (
  <div className="glow-blob-wrapper">
    <div className="glow-blob blob-1" />
    <div className="glow-blob blob-2" />
    <div className="glow-blob blob-3" />
  </div>
);

const fundCards = [
  { name: 'HDFC Mid Cap', nav: '₹148.52', xirr: '+22.3%', type: 'Equity' },
  { name: 'SBI Bluechip', nav: '₹78.45', xirr: '+16.8%', type: 'Equity' },
  { name: 'Axis ELSS', nav: '₹68.92', xirr: '+19.4%', type: 'ELSS' },
];

const Hero = ({ scrollY, onOpenAuth }) => {
  const parallaxOffset = scrollY * 0.25;
  const [xirr, setXirr] = useState(0);
  const [cagr, setCagr] = useState(0);

  useEffect(() => {
    let frame;
    let start = null;
    const animate = (ts) => {
      if (!start) start = ts;
      const p = Math.min((ts - start) / 1800, 1);
      setXirr(parseFloat((p * 18.4).toFixed(1)));
      setCagr(parseFloat((p * 14.2).toFixed(1)));
      if (p < 1) frame = requestAnimationFrame(animate);
    };
    const t = setTimeout(() => { frame = requestAnimationFrame(animate); }, 600);
    return () => { clearTimeout(t); cancelAnimationFrame(frame); };
  }, []);

  return (
    <section className="hero-new">
      <GlowBlob />
      <div className="grid-overlay" />

      <div className="hero-new-inner">
        {/* Left Content */}
        <div className="hero-left">
          <motion.div
            className="hero-pill"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            <span className="pill-dot" />
            <span>Unified Mutual Fund Portfolio Intelligence</span>
          </motion.div>

          <motion.h1
            className="hero-headline"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15, duration: 0.7 }}
          >
            One dashboard for
            <br />
            <span className="hero-gradient-text">all your funds</span>
            <br />
            across every AMC
          </motion.h1>

          <motion.p
            className="hero-sub"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.35, duration: 0.7 }}
          >
            Connect your accounts and instantly see your complete mutual fund portfolio
            — XIRR, CAGR, SIP performance, fund overlaps, and goal tracking — across Groww,
            Zerodha, and direct AMCs.
          </motion.p>

          <motion.div
            className="hero-cta-row"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5, duration: 0.6 }}
          >
            <motion.button
              className="hero-btn-primary"
              onClick={() => onOpenAuth && onOpenAuth('signup')}
              whileHover={{ scale: 1.04, boxShadow: '0 0 28px rgba(0,208,156,0.5)' }}
              whileTap={{ scale: 0.97 }}
            >
              <Upload size={17} /> Analyse My Portfolio
            </motion.button>
          </motion.div>

          <motion.div
            className="hero-trust"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.7, duration: 0.8 }}
          >
            {[
              { icon: Users, val: '50,000+', label: 'Portfolios' },
              { icon: Database, val: '2,800+', label: 'Funds Tracked' },
              { icon: BarChart2, val: 'XIRR', label: 'Realtime' },
              { icon: ArrowRight, val: 'CAMS+', label: 'KFintech' },
            ].map(({ icon: Icon, val, label }) => (
              <div className="trust-item" key={label}>
                <Icon size={14} color="#00D09C" />
                <span className="trust-val">{val}</span>
                <span className="trust-label">{label}</span>
              </div>
            ))}
          </motion.div>
        </div>

        {/* Right: Portfolio Chart Panel */}
        <motion.div
          className="hero-right"
          style={{ transform: `translateY(${parallaxOffset}px)` }}
          initial={{ opacity: 0, x: 40 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.3, duration: 0.9, ease: 'easeOut' }}
        >
          <div className="portfolio-card glassmorphism">
            <div className="portfolio-card-header">
              <div>
                <p className="portfolio-label">Unified Portfolio Value (12 Funds · 3 Platforms)</p>
                <p className="portfolio-value">₹24,58,340</p>
                <p className="portfolio-returns" style={{ color: '#00D09C' }}>
                  ▲ XIRR {xirr}% &nbsp;·&nbsp; CAGR {cagr}% &nbsp;·&nbsp; Total Invested ₹20.2L
                </p>
              </div>
              <div className="portfolio-badge">1Y</div>
            </div>

            <div className="portfolio-chart">
              <ResponsiveContainer width="100%" height={160}>
                <AreaChart data={chartData} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorCurr" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#00D09C" stopOpacity={0.35} />
                      <stop offset="95%" stopColor="#00D09C" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="colorInv" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#7B61FF" stopOpacity={0.2} />
                      <stop offset="95%" stopColor="#7B61FF" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
                  <XAxis dataKey="month" tick={{ fill: '#6B6B7B', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <YAxis hide />
                  <Tooltip content={<CustomTooltip />} />
                  <Area type="monotone" dataKey="current" stroke="#00D09C" strokeWidth={2.5} fill="url(#colorCurr)" dot={false} activeDot={{ r: 5, fill: '#00D09C', stroke: '#fff', strokeWidth: 2 }} />
                  <Area type="monotone" dataKey="invested" stroke="#7B61FF" strokeWidth={1.5} fill="url(#colorInv)" dot={false} strokeDasharray="4 4" />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            <div className="chart-legend">
              <span className="legend-dot green" /> Portfolio Value
              <span className="legend-dot purple" style={{ marginLeft: 16 }} /> Amount Invested
            </div>
          </div>

          {/* Floating fund NAV cards */}
          <div className="mini-cards">
            {fundCards.map((card, i) => (
              <motion.div
                key={card.name}
                className="mini-card glassmorphism"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.7 + i * 0.15, duration: 0.5 }}
                whileHover={{ scale: 1.04, y: -2 }}
              >
                <span className="mini-symbol">{card.name}</span>
                <span className="mini-value">{card.nav}</span>
                <span className="mini-change up">▲ {card.xirr}</span>
                <span className="mini-tag">{card.type}</span>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </div>

      <motion.div
        className="scroll-indicator"
        animate={{ y: [0, 8, 0] }}
        transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
      >
        <div className="scroll-line" />
        <span>Scroll to explore</span>
      </motion.div>
    </section>
  );
};

export default Hero;
