import { useEffect, useRef, useState } from 'react';
import { motion, useInView } from 'framer-motion';
import './Stats.css';

const AnimatedCounter = ({ end, suffix = '', duration = 2 }) => {
  const [count, setCount] = useState(0);
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true });

  useEffect(() => {
    if (!isInView) return;

    let startTime;
    const animate = (currentTime) => {
      if (!startTime) startTime = currentTime;
      const progress = Math.min((currentTime - startTime) / (duration * 1000), 1);

      setCount(Math.floor(progress * end));

      if (progress < 1) {
        requestAnimationFrame(animate);
      }
    };

    requestAnimationFrame(animate);
  }, [isInView, end, duration]);

  return (
    <span ref={ref}>
      {count}
      {suffix}
    </span>
  );
};

const Stats = () => {
  const stats = [
    { value: 26, suffix: '', label: 'Intelligent Modules' },
    { value: 50000, suffix: '+', label: 'Portfolios Analyzed' },
    { value: 98, suffix: '%', label: 'Accuracy Rate' },
    { value: 500, suffix: 'Cr+', label: 'AUM Tracked' },
  ];

  return (
    <section className="stats">
      <div className="stats-container">
        {stats.map((stat, index) => (
          <motion.div
            key={index}
            className="stat-card glassmorphism"
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: index * 0.1, duration: 0.6 }}
            whileHover={{ scale: 1.05, transition: { duration: 0.2 } }}
          >
            <div className="stat-value">
              <AnimatedCounter end={stat.value} suffix={stat.suffix} />
            </div>
            <div className="stat-label">{stat.label}</div>
            <div className="stat-glow"></div>
          </motion.div>
        ))}
      </div>
    </section>
  );
};

export default Stats;
