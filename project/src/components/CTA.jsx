import { motion } from 'framer-motion';
import { ArrowRight, CheckCircle2, Upload } from 'lucide-react';
import './CTA.css';

const perks = [
  'Free to use — no subscription needed',
  'Connect Groww, Zerodha & direct AMCs',
  'XIRR & CAGR calculated accurately',
  'No broker or demat required',
];

const CTA = ({ onOpenAuth }) => {
  return (
    <section className="cta-new">
      <div className="cta-inner">
        <motion.div
          className="cta-card-new glassmorphism"
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.7 }}
        >
          <div className="cta-glow-1" />
          <div className="cta-glow-2" />
          <div className="cta-glow-3" />

          <div className="cta-content">
            <motion.div
              className="cta-badge-new"
              initial={{ opacity: 0, scale: 0.85 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.2, duration: 0.4 }}
            >
              <Upload size={13} />
              <span>Unified Mutual Fund Portfolio Intelligence</span>
            </motion.div>

            <motion.h2
              className="cta-title-new"
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: 0.3, duration: 0.6 }}
            >
              Stop guessing your returns.
              <br />
              <span className="cta-gradient">Know your true XIRR today.</span>
            </motion.h2>

            <motion.p
              className="cta-desc-new"
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.4, duration: 0.6 }}
            >
              Thousands of Indian mutual fund investors use WealthWise to unify their portfolios
              across Groww, Zerodha, direct AMCs, and more — getting a single, accurate picture
              of their wealth, goals, and SIP progress.
            </motion.p>

            <motion.div
              className="cta-perks"
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.5, duration: 0.6 }}
            >
              {perks.map((p) => (
                <div key={p} className="cta-perk">
                  <CheckCircle2 size={14} color="#00D09C" />
                  <span>{p}</span>
                </div>
              ))}
            </motion.div>

            <motion.div
              className="cta-actions-new"
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: 0.6, duration: 0.6 }}
            >
              <motion.button
                className="cta-primary-btn"
                onClick={() => onOpenAuth && onOpenAuth('signup')}
                whileHover={{ scale: 1.04, boxShadow: '0 0 36px rgba(0,208,156,0.55)' }}
                whileTap={{ scale: 0.97 }}
              >
                <span>Analyse My Portfolio</span>
                <ArrowRight size={18} />
              </motion.button>
            </motion.div>
          </div>
        </motion.div>
      </div>
    </section>
  );
};

export default CTA;
