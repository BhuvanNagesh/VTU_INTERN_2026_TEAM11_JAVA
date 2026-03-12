import { useEffect, useRef, useState } from 'react';
import { motion, useInView } from 'framer-motion';
import { TrendingUp, FileText, BarChart3, Activity } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import './AnalyticsSection.css';

// Simulated XIRR trend comparison: WealthWise portfolio intelligently routed vs benchmark avg
const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const generateLine = (base, vol) => {
    let v = base;
    return months.map((m) => { v = v * (1 + (Math.random() * vol * 2 - vol)); return { month: m, val: Math.round(v) }; });
};
const portfolioLine = generateLine(100000, 0.055);
const categoryLine = generateLine(100000, 0.038);
const combinedData = months.map((m, i) => ({
    month: m,
    portfolio: portfolioLine[i].val,
    category: categoryLine[i].val,
}));

const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload?.length) {
        return (
            <div className="an-tooltip">
                <p className="an-tooltip-label">{label}</p>
                {payload.map((p) => (
                    <p key={p.dataKey} style={{ color: p.color }} className="an-tooltip-val">
                        {p.dataKey === 'portfolio' ? 'Your Portfolio' : 'Category Avg'}: ₹{p.value.toLocaleString()}
                    </p>
                ))}
            </div>
        );
    }
    return null;
};

const AnimatedCounter = ({ end, suffix = '', prefix = '', decimals = 0, duration = 1800 }) => {
    const [count, setCount] = useState(0);
    const ref = useRef(null);
    const isInView = useInView(ref, { once: true });
    useEffect(() => {
        if (!isInView) return;
        let start = null, frame;
        const animate = (ts) => {
            if (!start) start = ts;
            const p = Math.min((ts - start) / duration, 1);
            const eased = 1 - Math.pow(1 - p, 3);
            setCount(parseFloat((eased * end).toFixed(decimals)));
            if (p < 1) frame = requestAnimationFrame(animate);
        };
        frame = requestAnimationFrame(animate);
        return () => cancelAnimationFrame(frame);
    }, [isInView, end, duration, decimals]);
    return <span ref={ref}>{prefix}{count.toLocaleString()}{suffix}</span>;
};

const statItems = [
    { icon: Activity, label: 'Portfolios Analyzed', val: 50000, suffix: '+', color: '#00D09C' },
    { icon: TrendingUp, label: 'Avg Portfolio XIRR', val: 18.4, suffix: '%', decimals: 1, color: '#7B61FF' },
    { icon: FileText, label: 'SIPs Tracked', val: 180000, suffix: '+', color: '#FFB74D' },
    { icon: BarChart3, label: 'AMC Integrations', val: 44, suffix: '+', color: '#4DFFDF' },
];

const AnalyticsSection = () => {
    return (
        <section className="analytics-section">
            <div className="analytics-inner">
                {/* Stats row */}
                <div className="stats-row">
                    {statItems.map((item, i) => (
                        <motion.div
                            key={item.label}
                            className="stat-block glassmorphism"
                            initial={{ opacity: 0, y: 24 }}
                            whileInView={{ opacity: 1, y: 0 }}
                            viewport={{ once: true }}
                            transition={{ delay: i * 0.1, duration: 0.5 }}
                            whileHover={{ y: -4, boxShadow: `0 8px 30px ${item.color}22` }}
                        >
                            <div className="stat-icon-wrap" style={{ background: `${item.color}14`, border: `1px solid ${item.color}25` }}>
                                <item.icon size={18} color={item.color} />
                            </div>
                            <div className="stat-number" style={{ color: item.color }}>
                                <AnimatedCounter end={item.val} suffix={item.suffix} decimals={item.decimals || 0} />
                            </div>
                            <div className="stat-label">{item.label}</div>
                        </motion.div>
                    ))}
                </div>

                {/* Chart section */}
                <div className="analytics-chart-section">
                    <motion.div
                        className="chart-content"
                        initial={{ opacity: 0, x: -30 }}
                        whileInView={{ opacity: 1, x: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.6 }}
                    >
                        <div className="section-eyebrow" style={{ justifyContent: 'flex-start' }}>
                            <Activity size={12} /> Portfolio Analytics
                        </div>
                        <h2 className="section-title" style={{ textAlign: 'left' }}>
                            Your unified portfolio
                            <br />
                            <span className="gradient-text">outperforms the average</span>
                        </h2>
                        <p className="section-sub" style={{ textAlign: 'left', marginBottom: 28 }}>
                            WealthWise consolidates your investments across all AMCs and platforms
                            — giving you a true XIRR and CAGR picture that individual apps can't show.
                        </p>

                        <div className="performance-pills">
                            {[
                                { label: '1M XIRR', val: '+4.2%', up: true },
                                { label: '3M CAGR', val: '+9.8%', up: true },
                                { label: '6M XIRR', val: '+14.5%', up: true },
                                { label: '1Y XIRR', val: '+18.4%', up: true },
                                { label: '3Y CAGR', val: '+14.2%', up: true },
                            ].map((p) => (
                                <div key={p.label} className="perf-pill">
                                    <span className="pill-period">{p.label}</span>
                                    <span className={`pill-val ${p.up ? 'up' : 'down'}`}>{p.val}</span>
                                </div>
                            ))}
                        </div>

                        {/* AMC sources */}
                        <div className="amc-sources">
                            <p className="amc-sources-label">Sources integrated:</p>
                            <div className="amc-chips">
                                {['CAMS', 'KFintech', 'Groww', 'Zerodha', 'MFU', 'Direct AMC'].map((s) => (
                                    <span key={s} className="amc-chip">{s}</span>
                                ))}
                            </div>
                        </div>
                    </motion.div>

                    <motion.div
                        className="chart-panel glassmorphism"
                        initial={{ opacity: 0, x: 30 }}
                        whileInView={{ opacity: 1, x: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.6, delay: 0.15 }}
                    >
                        <div className="chart-panel-header">
                            <span className="chart-panel-title">Portfolio Value vs Category Average (2024)</span>
                            <div className="chart-legend-row">
                                <span className="cl-dot" style={{ background: '#00D09C' }} /> Your Portfolio
                                <span className="cl-dot" style={{ background: '#7B61FF', marginLeft: 12 }} /> Category Avg
                            </div>
                        </div>
                        <ResponsiveContainer width="100%" height={220}>
                            <LineChart data={combinedData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
                                <XAxis dataKey="month" tick={{ fill: '#6B6B7B', fontSize: 10 }} axisLine={false} tickLine={false} />
                                <YAxis hide />
                                <Tooltip content={<CustomTooltip />} />
                                <Line type="monotone" dataKey="portfolio" stroke="#00D09C" strokeWidth={2.5} dot={false} activeDot={{ r: 5, fill: '#00D09C', stroke: '#fff', strokeWidth: 2 }} />
                                <Line type="monotone" dataKey="category" stroke="#7B61FF" strokeWidth={2} strokeDasharray="5 4" dot={false} activeDot={{ r: 4, fill: '#7B61FF', stroke: '#fff', strokeWidth: 2 }} />
                            </LineChart>
                        </ResponsiveContainer>
                    </motion.div>
                </div>
            </div>
        </section>
    );
};

export default AnalyticsSection;
