import { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import './PortfolioVisualization.css';

const PortfolioVisualization = () => {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    canvas.width = 500 * dpr;
    canvas.height = 500 * dpr;
    canvas.style.width = '500px';
    canvas.style.height = '500px';
    ctx.scale(dpr, dpr);

    const centerX = 250;
    const centerY = 250;
    let rotation = 0;

    const segments = [
      { value: 35, color: '#00D09C', label: 'Equity' },
      { value: 25, color: '#00FFB8', label: 'Debt' },
      { value: 20, color: '#4DFFDF', label: 'Hybrid' },
      { value: 12, color: '#80FFE8', label: 'Gold' },
      { value: 8, color: '#B3FFF0', label: 'International' },
    ];

    const animate = () => {
      ctx.clearRect(0, 0, 500, 500);

      rotation += 0.002;

      let currentAngle = rotation;
      const radius = 120;

      segments.forEach((segment, index) => {
        const angle = (segment.value / 100) * Math.PI * 2;
        const endAngle = currentAngle + angle;

        ctx.save();
        ctx.translate(centerX, centerY);

        ctx.beginPath();
        ctx.arc(0, 0, radius, currentAngle, endAngle);
        ctx.lineTo(0, 0);
        ctx.closePath();

        const gradient = ctx.createRadialGradient(0, 0, 0, 0, 0, radius);
        gradient.addColorStop(0, segment.color);
        gradient.addColorStop(1, segment.color + '80');
        ctx.fillStyle = gradient;
        ctx.fill();

        ctx.strokeStyle = 'rgba(10, 10, 15, 0.5)';
        ctx.lineWidth = 2;
        ctx.stroke();

        const midAngle = currentAngle + angle / 2;
        const labelRadius = radius + 50;
        const labelX = Math.cos(midAngle) * labelRadius;
        const labelY = Math.sin(midAngle) * labelRadius;

        ctx.fillStyle = '#FFFFFF';
        ctx.font = '600 14px Inter';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(segment.label, labelX, labelY);

        ctx.font = '700 18px Space Grotesk';
        ctx.fillText(`${segment.value}%`, labelX, labelY + 18);

        ctx.restore();

        currentAngle = endAngle;
      });

      ctx.save();
      ctx.translate(centerX, centerY);
      ctx.beginPath();
      ctx.arc(0, 0, 60, 0, Math.PI * 2);
      ctx.fillStyle = '#0A0A0F';
      ctx.fill();
      ctx.strokeStyle = '#00D09C';
      ctx.lineWidth = 3;
      ctx.stroke();
      ctx.restore();

      for (let i = 0; i < 3; i++) {
        const ringRadius = radius + 80 + i * 30;
        ctx.save();
        ctx.translate(centerX, centerY);
        ctx.beginPath();
        ctx.arc(0, 0, ringRadius, 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(0, 208, 156, ${0.1 - i * 0.03})`;
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.restore();
      }

      requestAnimationFrame(animate);
    };

    animate();
  }, []);

  return (
    <div className="portfolio-visualization">
      <motion.div
        className="viz-glow"
        animate={{
          scale: [1, 1.1, 1],
          opacity: [0.3, 0.5, 0.3],
        }}
        transition={{
          duration: 3,
          repeat: Infinity,
          ease: 'easeInOut',
        }}
      />
      <canvas ref={canvasRef} className="viz-canvas" />
      <div className="viz-labels">
        <motion.div
          className="viz-label"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.8 }}
        >
          <div className="viz-label-value">₹24.5L</div>
          <div className="viz-label-text">Total Value</div>
        </motion.div>
        <motion.div
          className="viz-label"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 1 }}
        >
          <div className="viz-label-value">+18.4%</div>
          <div className="viz-label-text">Returns XIRR</div>
        </motion.div>
      </div>
    </div>
  );
};

export default PortfolioVisualization;
