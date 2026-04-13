import { motion } from 'framer-motion';
import { useState } from 'react';
import { Activity, CircleAlert as AlertCircle, ChartBar as BarChart2, Bell, BookOpen, Briefcase, Calendar, Clock, DollarSign, FileText, ListFilter as Filter, Flag, GitBranch, Globe, Heart, ChartLine as LineChart, Lock, Mail, ChartPie as PieChart, Repeat, Search, Settings, Shield, TrendingUp, Users, Zap } from 'lucide-react';
import './Modules.css';

const Modules = () => {
  const [hoveredIndex, setHoveredIndex] = useState(null);

  const modules = [
    { icon: FileText, name: 'CAS Import' },
    { icon: PieChart, name: 'Portfolio Dashboard' },
    { icon: Calculator, name: 'Tax Intelligence' },
    { icon: Flag, name: 'Goal Planning' },
    { icon: GitBranch, name: 'Fund Overlap' },
    { icon: TrendingUp, name: 'Performance' },
    { icon: BarChart2, name: 'Analytics' },
    { icon: Shield, name: 'Risk Analysis' },
    { icon: Bell, name: 'Alerts' },
    { icon: Calendar, name: 'SIP Manager' },
    { icon: DollarSign, name: 'Returns Calc' },
    { icon: LineChart, name: 'NAV Tracker' },
    { icon: Activity, name: 'Market Pulse' },
    { icon: Search, name: 'Fund Research' },
    { icon: Filter, name: 'Smart Filter' },
    { icon: Briefcase, name: 'Asset Allocation' },
    { icon: Repeat, name: 'Rebalancing' },
    { icon: Clock, name: 'Timeline View' },
    { icon: BookOpen, name: 'Reports' },
    { icon: Mail, name: 'Email Digest' },
    { icon: Users, name: 'Family View' },
    { icon: Lock, name: 'Security' },
    { icon: Zap, name: 'Quick Actions' },
    { icon: Settings, name: 'Preferences' },
    { icon: Globe, name: 'NRI Support' },
    { icon: Heart, name: 'Watchlist' },
  ];

  return (
    <section className="modules">
      <div className="modules-container">
        <motion.div
          className="modules-header"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
        >
          <h2 className="modules-title">
            <span className="gradient-text">26 Modules</span>
            <br />
            One Intelligent Platform
          </h2>
          <p className="modules-description">
            Every module is designed to work together, creating a seamless
            portfolio intelligence experience. From basic tracking to advanced
            analytics, WealthWise has you covered.
          </p>
        </motion.div>

        <div className="modules-grid">
          {modules.map((module, index) => (
            <motion.div
              key={index}
              className="module-card glassmorphism"
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true, margin: '-50px' }}
              transition={{ delay: index * 0.02, duration: 0.4 }}
              whileHover={{ scale: 1.05, transition: { duration: 0.2 } }}
              onHoverStart={() => setHoveredIndex(index)}
              onHoverEnd={() => setHoveredIndex(null)}
            >
              <div className={`module-icon ${hoveredIndex === index ? 'active' : ''}`}>
                <module.icon size={20} strokeWidth={2} />
              </div>
              <div className="module-name">{module.name}</div>
              {hoveredIndex === index && (
                <motion.div
                  className="module-glow"
                  layoutId="module-glow"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                />
              )}
            </motion.div>
          ))}
        </div>

        <motion.div
          className="modules-cta"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8, delay: 0.3 }}
        >
          <button className="modules-button">
            Explore All Modules
            <Zap size={18} />
          </button>
        </motion.div>
      </div>
    </section>
  );
};

const Calculator = DollarSign;

export default Modules;
