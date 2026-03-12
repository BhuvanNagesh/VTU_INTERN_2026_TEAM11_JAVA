import { useState } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, ChevronRight, Flame, Star, BarChart2 } from 'lucide-react';
import { BarChart, Bar, ResponsiveContainer, Cell, Tooltip } from 'recharts';
import './MarketSection.css';

const tabs = ['Top Gainers', 'Top Losers', 'Most Active', '52W High'];

const stockData = {
    'Top Gainers': [
        { name: 'SUZLON', price: '₹42.85', change: '+8.34%', vol: '12.4M', up: true, bars: [30, 45, 38, 52, 47, 65, 70] },
        { name: 'IRFC', price: '₹185.20', change: '+6.71%', vol: '8.2M', up: true, bars: [40, 48, 44, 58, 52, 62, 69] },
        { name: 'RVNL', price: '₹314.50', change: '+5.92%', vol: '6.1M', up: true, bars: [35, 42, 50, 46, 55, 59, 65] },
        { name: 'ADANIENT', price: '₹2,847', change: '+4.85%', vol: '4.3M', up: true, bars: [38, 44, 41, 54, 49, 58, 63] },
        { name: 'INDHOTEL', price: '₹578.30', change: '+4.20%', vol: '3.8M', up: true, bars: [32, 40, 47, 43, 52, 55, 60] },
    ],
    'Top Losers': [
        { name: 'PAYTM', price: '₹398.55', change: '-4.21%', vol: '9.6M', up: false, bars: [70, 62, 65, 50, 54, 42, 35] },
        { name: 'ZOMATO', price: '₹168.40', change: '-3.58%', vol: '15.2M', up: false, bars: [65, 58, 60, 48, 50, 40, 38] },
        { name: 'NYKAA', price: '₹174.20', change: '-3.10%', vol: '4.4M', up: false, bars: [62, 55, 57, 45, 47, 38, 35] },
        { name: 'CARTRADE', price: '₹614.85', change: '-2.87%', vol: '2.1M', up: false, bars: [60, 53, 55, 44, 46, 37, 34] },
        { name: 'POLICYBZR', price: '₹1,248', change: '-2.54%', vol: '1.8M', up: false, bars: [58, 52, 54, 42, 44, 35, 32] },
    ],
    'Most Active': [
        { name: 'RELIANCE', price: '₹2,847', change: '+1.23%', vol: '28.4M', up: true, bars: [55, 60, 58, 65, 62, 68, 72] },
        { name: 'TATASTEEL', price: '₹148.75', change: '+2.40%', vol: '24.1M', up: true, bars: [48, 54, 52, 59, 56, 63, 67] },
        { name: 'SBIN', price: '₹742.30', change: '-0.85%', vol: '20.8M', up: false, bars: [60, 56, 58, 52, 54, 48, 45] },
        { name: 'INFY', price: '₹1,487', change: '+0.91%', vol: '18.5M', up: true, bars: [45, 50, 47, 55, 52, 58, 61] },
        { name: 'HDFCBANK', price: '₹1,542', change: '-0.38%', vol: '16.2M', up: false, bars: [58, 54, 56, 50, 52, 47, 44] },
    ],
    '52W High': [
        { name: 'DIXON', price: '₹12,450', change: '+3.45%', vol: '1.2M', up: true, bars: [40, 50, 55, 62, 58, 68, 75] },
        { name: 'CESC', price: '₹158.40', change: '+2.90%', vol: '3.4M', up: true, bars: [38, 46, 52, 59, 55, 64, 71] },
        { name: 'PSUBANK', price: '₹84.20', change: '+2.15%', vol: '5.8M', up: true, bars: [35, 44, 50, 56, 52, 61, 68] },
        { name: 'HINDZINC', price: '₹348.55', change: '+1.87%', vol: '2.8M', up: true, bars: [32, 42, 48, 54, 50, 59, 65] },
        { name: 'TITAN', price: '₹3,428', change: '+1.62%', vol: '2.1M', up: true, bars: [30, 40, 46, 52, 48, 57, 63] },
    ],
};

const MiniSparkBar = ({ bars, up }) => (
    <ResponsiveContainer width={60} height={28}>
        <BarChart data={bars.map((v, i) => ({ v, i }))} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
            <Bar dataKey="v" radius={[2, 2, 0, 0]}>
                {bars.map((_, i) => (
                    <Cell key={i} fill={up ? '#00D09C' : '#FF4D4D'} opacity={0.6 + (i / bars.length) * 0.4} />
                ))}
            </Bar>
        </BarChart>
    </ResponsiveContainer>
);

const MarketSection = () => {
    const [activeTab, setActiveTab] = useState('Top Gainers');

    return (
        <section className="market-section">
            <div className="market-inner">
                <motion.div
                    className="section-header"
                    initial={{ opacity: 0, y: 24 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.6 }}
                >
                    <div className="section-eyebrow">
                        <Flame size={14} /> Market Pulse
                    </div>
                    <h2 className="section-title">
                        Discover the <span className="gradient-text">hottest stocks</span>
                        <br />right now
                    </h2>
                    <p className="section-sub">
                        Real-time market movers, updated every minute
                    </p>
                </motion.div>

                <div className="market-grid">
                    {/* Stocks Table */}
                    <motion.div
                        className="market-table-card glassmorphism"
                        initial={{ opacity: 0, x: -30 }}
                        whileInView={{ opacity: 1, x: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.6 }}
                    >
                        <div className="table-tabs">
                            {tabs.map((tab) => (
                                <button
                                    key={tab}
                                    className={`table-tab ${activeTab === tab ? 'active' : ''}`}
                                    onClick={() => setActiveTab(tab)}
                                >
                                    {tab}
                                </button>
                            ))}
                        </div>

                        <div className="table-header-row">
                            <span>Stock</span>
                            <span>Price</span>
                            <span>Change</span>
                            <span>Volume</span>
                            <span>7D</span>
                        </div>

                        <div className="stock-list">
                            {stockData[activeTab].map((stock, i) => (
                                <motion.div
                                    key={stock.name}
                                    className="stock-row"
                                    initial={{ opacity: 0, x: -16 }}
                                    animate={{ opacity: 1, x: 0 }}
                                    transition={{ delay: i * 0.07, duration: 0.35 }}
                                    whileHover={{ background: 'rgba(0,208,156,0.04)', x: 4 }}
                                >
                                    <div className="stock-name-col">
                                        <div className="stock-avatar">{stock.name[0]}</div>
                                        <div>
                                            <p className="stock-sym">{stock.name}</p>
                                            <p className="stock-exchange">NSE</p>
                                        </div>
                                    </div>
                                    <span className="stock-price">{stock.price}</span>
                                    <span className={`stock-change ${stock.up ? 'up' : 'down'}`}>
                                        {stock.up ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
                                        {stock.change}
                                    </span>
                                    <span className="stock-vol">{stock.vol}</span>
                                    <div className="stock-spark">
                                        <MiniSparkBar bars={stock.bars} up={stock.up} />
                                    </div>
                                </motion.div>
                            ))}
                        </div>

                        <button className="view-all-btn">
                            View all stocks <ChevronRight size={14} />
                        </button>
                    </motion.div>

                    {/* Right side: category cards */}
                    <div className="market-side-cards">
                        {[
                            {
                                icon: Star,
                                title: 'Mutual Funds',
                                sub: 'Invest based on your risk profile',
                                color: '#7B61FF',
                                metrics: [
                                    { label: 'Avg. 5Y Return', val: '18.4%', color: '#00D09C' },
                                    { label: 'Active Funds', val: '2,847+' },
                                ],
                                cta: 'Explore Funds',
                            },
                            {
                                icon: TrendingUp,
                                title: 'F&O Trading',
                                sub: 'Options & Futures with advanced charts',
                                color: '#FF6B6B',
                                metrics: [
                                    { label: 'PCR Ratio', val: '0.84' },
                                    { label: 'VIX', val: '14.2', color: '#FF6B6B' },
                                ],
                                cta: 'Trade Now',
                            },
                            {
                                icon: BarChart2,
                                title: 'IPO Corner',
                                sub: 'Apply for upcoming IPOs instantly',
                                color: '#FFB74D',
                                metrics: [
                                    { label: 'Open IPOs', val: '3' },
                                    { label: 'Upcoming', val: '12+', color: '#FFB74D' },
                                ],
                                cta: 'Apply IPO',
                            },
                        ].map((card, i) => (
                            <motion.div
                                key={card.title}
                                className="side-card glassmorphism"
                                initial={{ opacity: 0, x: 30 }}
                                whileInView={{ opacity: 1, x: 0 }}
                                viewport={{ once: true }}
                                transition={{ delay: i * 0.1, duration: 0.5 }}
                                whileHover={{ scale: 1.02, y: -3 }}
                            >
                                <div className="side-card-icon" style={{ background: `${card.color}22`, border: `1px solid ${card.color}33` }}>
                                    <card.icon size={20} color={card.color} />
                                </div>
                                <h3 className="side-card-title">{card.title}</h3>
                                <p className="side-card-sub">{card.sub}</p>
                                <div className="side-card-metrics">
                                    {card.metrics.map((m) => (
                                        <div key={m.label} className="side-metric">
                                            <span className="side-metric-val" style={{ color: m.color || 'var(--text-primary)' }}>{m.val}</span>
                                            <span className="side-metric-label">{m.label}</span>
                                        </div>
                                    ))}
                                </div>
                                <button className="side-card-btn" style={{ borderColor: `${card.color}40`, color: card.color }}>
                                    {card.cta} <ChevronRight size={14} />
                                </button>
                            </motion.div>
                        ))}
                    </div>
                </div>
            </div>
        </section>
    );
};

export default MarketSection;
