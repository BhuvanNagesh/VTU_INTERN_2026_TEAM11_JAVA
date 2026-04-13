import { motion } from 'framer-motion';
import { FileText, BarChart3, Calculator, Target, Zap, Shield, TrendingUp, GitMerge } from 'lucide-react';
import './Features.css';

const features = [
  {
    icon: FileText,
    title: 'Portfolio Sync',
    description: 'Connect Groww, Zerodha, or direct AMC accounts. Auto-synced portfolio data always up to date.',
    tag: 'Auto',
    color: '#00D09C',
  },
  {
    icon: BarChart3,
    title: 'Unified Dashboard',
    description: 'All funds from Groww, Zerodha, direct AMCs in one place — NAV, current value, P&L at a glance.',
    tag: 'Live',
    color: '#7B61FF',
  },
  {
    icon: Calculator,
    title: 'XIRR & CAGR',
    description: 'True XIRR and CAGR calculated accurately across all transactions — not fund-level estimates.',
    tag: 'Exact',
    color: '#FFB74D',
  },
  {
    icon: Target,
    title: 'Goal Planning',
    description: 'Map SIPs to life goals — retirement, child education, home. Track milestone completion in real-time.',
    tag: 'Smart',
    color: '#4DFFDF',
  },
  {
    icon: GitMerge,
    title: 'Fund Overlap Detector',
    description: 'Find hidden overlaps across your funds. Reduce redundant stocks and improve true diversification.',
    tag: 'Unique',
    color: '#FF6B6B',
  },
  {
    icon: Zap,
    title: 'SIP Tracker',
    description: 'Track every SIP instalment — upcoming, due, and past — across all platforms. Never miss a SIP.',
    tag: 'Fast',
    color: '#00D09C',
  },
  {
    icon: Shield,
    title: 'Risk Profiling',
    description: "Know your portfolio's Sharpe ratio, beta, standard deviation, and exposure to sector risk.",
    tag: 'Safe',
    color: '#7B61FF',
  },
  {
    icon: TrendingUp,
    title: 'Tax Harvesting',
    description: 'Spot LTCG and STCG opportunities. Sell and rebuy strategically to reduce your tax burden.',
    tag: 'Tax',
    color: '#FFB74D',
  },
];

const Features = () => {
  return (
    <section className="features-new">
      <div className="features-inner">
        <motion.div
          className="section-header"
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
        >
          <div className="section-eyebrow">
            <Zap size={12} /> What WealthWise Does
          </div>
          <h2 className="section-title">
            Everything you need to
            <br />
            <span className="gradient-text">master your mutual funds</span>
          </h2>
          <p className="section-sub">
            From portfolio sync to tax harvesting — built specifically for Indian mutual fund investors
          </p>
        </motion.div>

        <div className="features-grid-new">
          {features.map((feature, index) => (
            <motion.div
              key={feature.title}
              className="feature-card-new glassmorphism"
              initial={{ opacity: 0, y: 28 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-80px' }}
              transition={{ delay: index * 0.08, duration: 0.5 }}
              whileHover={{
                scale: 1.03,
                y: -4,
                boxShadow: `0 12px 40px ${feature.color}22`,
                borderColor: `${feature.color}30`,
                transition: { duration: 0.2 },
              }}
            >
              <div className="fc-top">
                <div
                  className="fc-icon"
                  style={{ background: `${feature.color}18`, border: `1px solid ${feature.color}30` }}
                >
                  <feature.icon size={20} color={feature.color} />
                </div>
                <span className="fc-tag" style={{ color: feature.color, background: `${feature.color}12`, borderColor: `${feature.color}25` }}>
                  {feature.tag}
                </span>
              </div>
              <h3 className="fc-title">{feature.title}</h3>
              <p className="fc-desc">{feature.description}</p>
              <div
                className="fc-glow"
                style={{ background: `radial-gradient(circle at 50% 100%, ${feature.color}18, transparent 60%)` }}
              />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Features;
