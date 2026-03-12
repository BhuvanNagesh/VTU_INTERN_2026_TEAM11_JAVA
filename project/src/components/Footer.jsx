import { motion } from 'framer-motion';
import { Twitter, Linkedin, Github, Mail, TrendingUp, Phone, MapPin } from 'lucide-react';
import './Footer.css';

const footerLinks = {
  Platform: ['Portfolio Tracker', 'SIP Tracker', 'Fund Comparison', 'XIRR Calculator', 'CAGR Calculator', 'Goal Planner'],
  Resources: ['Getting Started Guide', 'Mutual Fund Basics', 'SIP Guide', 'Tax Harvesting Tips', 'XIRR vs CAGR', 'Blog'],
  Company: ['About WealthWise', 'Careers', 'Press', 'Security', 'Contact Us', 'Privacy Policy'],
};

const Footer = () => {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="footer-new">
      <div className="footer-glow-line" />
      <div className="footer-inner">
        <div className="footer-top">
          {/* Brand col */}
          <motion.div
            className="footer-brand-col"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
          >
            <div className="footer-logo">
              <div className="footer-logo-mark">
                <TrendingUp size={16} color="#00D09C" />
              </div>
              <span className="footer-logo-text">WealthWise</span>
            </div>
            <p className="footer-desc">
              India's portfolio intelligence platform for mutual fund investors.
              Unify all your funds from CAMS, KFintech, Groww, Zerodha and direct AMCs
              into one powerful dashboard.
            </p>
            <div className="footer-socials">
              {[
                { icon: Twitter, label: 'Twitter' },
                { icon: Linkedin, label: 'LinkedIn' },
                { icon: Github, label: 'GitHub' },
                { icon: Mail, label: 'Email' },
              ].map(({ icon: Icon, label }) => (
                <motion.a
                  key={label}
                  href="#"
                  className="social-btn"
                  aria-label={label}
                  whileHover={{ scale: 1.1, background: 'rgba(0,208,156,0.12)', borderColor: 'rgba(0,208,156,0.3)' }}
                  whileTap={{ scale: 0.95 }}
                >
                  <Icon size={16} />
                </motion.a>
              ))}
            </div>
            <div className="footer-contact">
              <div className="contact-item">
                <Phone size={13} color="#00D09C" />
                <span>support@wealthwise.in</span>
              </div>
              <div className="contact-item">
                <MapPin size={13} color="#00D09C" />
                <span>Bengaluru, Karnataka, India</span>
              </div>
            </div>
          </motion.div>

          {/* Links */}
          {Object.entries(footerLinks).map(([category, links], i) => (
            <motion.div
              key={category}
              className="footer-links-col"
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: (i + 1) * 0.08, duration: 0.5 }}
            >
              <h3 className="footer-col-title">{category}</h3>
              <ul className="footer-col-list">
                {links.map((link) => (
                  <li key={link}>
                    <a href="#" className="footer-link-item">{link}</a>
                  </li>
                ))}
              </ul>
            </motion.div>
          ))}

          {/* Newsletter */}
          <motion.div
            className="footer-newsletter-col"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.4, duration: 0.5 }}
          >
            <h3 className="footer-col-title">Portfolio Insights</h3>
            <p className="newsletter-desc">Get weekly fund analysis, XIRR tips, and SIP strategies in your inbox.</p>
            <div className="newsletter-input-row">
              <input type="email" placeholder="your@email.com" className="newsletter-input" />
              <button className="newsletter-btn">Subscribe</button>
            </div>
            <p className="newsletter-trust">🔒 No spam. Unsubscribe anytime.</p>
          </motion.div>
        </div>

        {/* Bottom bar */}
        <motion.div
          className="footer-bottom"
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          transition={{ delay: 0.3, duration: 0.5 }}
        >
          <div className="footer-bottom-left">
            <p className="footer-copyright">© {currentYear} WealthWise Analytics Pvt Ltd. All rights reserved.</p>
            <p className="footer-disclaimer">
              WealthWise is a portfolio analytics and tracking tool. It does not provide any investment advice,
              brokerage, or execution services. Mutual fund investments are subject to market risks.
              Please read all scheme-related documents carefully. Data shown is indicative & not financial advice.
            </p>
          </div>
          <div className="footer-bottom-links">
            {['Privacy Policy', 'Terms of Use', 'Cookie Policy', 'Data & Security'].map((l) => (
              <a key={l} href="#" className="footer-bottom-link">{l}</a>
            ))}
          </div>
        </motion.div>
      </div>
    </footer>
  );
};

export default Footer;
