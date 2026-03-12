import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, ChevronRight, Star, Activity, Loader2 } from 'lucide-react';
import { BarChart, Bar, ResponsiveContainer, Cell } from 'recharts';
import { batchGetNavs, SCHEME_CODES } from '../lib/mfApi';
import './MarketSection.css';

// SEBI-aligned fund categories
const tabs = [
    'Top Performers',
    'Large Cap',
    'Mid Cap',
    'Small Cap',
    'Index Funds',
    'Debt',
    'ELSS Tax Saver',
    'NFO',
];

// Base fund data — NAV values get randomized live
const baseFunds = {
    'Top Performers': [
        { name: 'Quant Small Cap', amc: 'Quant', baseNav: 248.74, ret1y: '+52.3%', aum: '₹18,420 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'Nippon India Growth', amc: 'Nippon', baseNav: 3412.80, ret1y: '+46.8%', aum: '₹24,100 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'HDFC Mid Cap Opp', amc: 'HDFC', baseNav: 148.52, ret1y: '+38.2%', aum: '₹62,340 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'Motilal Oswal Midcap', amc: 'Motilal', baseNav: 92.34, ret1y: '+35.7%', aum: '₹9,870 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Parag Parikh Flexicap', amc: 'PPFAS', baseNav: 78.92, ret1y: '+29.4%', aum: '₹52,100 Cr', sipMin: '₹1,000', star: 5, up: true },
        { name: 'Kotak Emerging Equity', amc: 'Kotak', baseNav: 112.44, ret1y: '+28.1%', aum: '₹38,200 Cr', sipMin: '₹100', star: 4, up: true },
    ],
    'Large Cap': [
        { name: 'Mirae Asset Large Cap', amc: 'Mirae', baseNav: 104.38, ret1y: '+21.4%', aum: '₹34,400 Cr', sipMin: '₹1,000', star: 5, up: true },
        { name: 'Axis Bluechip', amc: 'Axis', baseNav: 56.80, ret1y: '+18.7%', aum: '₹28,200 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'HDFC Top 100', amc: 'HDFC', baseNav: 948.62, ret1y: '+17.9%', aum: '₹24,800 Cr', sipMin: '₹100', star: 4, up: true },
        { name: 'ICICI Pru Bluechip', amc: 'ICICI', baseNav: 102.14, ret1y: '+17.2%', aum: '₹42,100 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'SBI Bluechip', amc: 'SBI', baseNav: 78.45, ret1y: '+16.8%', aum: '₹44,200 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Nippon India Large Cap', amc: 'Nippon', baseNav: 90.88, ret1y: '+16.1%', aum: '₹18,400 Cr', sipMin: '₹100', star: 4, up: true },
    ],
    'Mid Cap': [
        { name: 'HDFC Mid Cap Opp', amc: 'HDFC', baseNav: 148.52, ret1y: '+38.2%', aum: '₹62,340 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'Quant Mid Cap', amc: 'Quant', baseNav: 248.10, ret1y: '+36.8%', aum: '₹4,200 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'Motilal Oswal Midcap', amc: 'Motilal', baseNav: 92.34, ret1y: '+35.7%', aum: '₹9,870 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Kotak Emerging Equity', amc: 'Kotak', baseNav: 112.44, ret1y: '+28.1%', aum: '₹38,200 Cr', sipMin: '₹100', star: 4, up: true },
        { name: 'DSP Midcap', amc: 'DSP', baseNav: 118.92, ret1y: '+24.6%', aum: '₹19,400 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Axis Midcap', amc: 'Axis', baseNav: 94.38, ret1y: '+22.4%', aum: '₹22,100 Cr', sipMin: '₹500', star: 3, up: false },
    ],
    'Small Cap': [
        { name: 'Quant Small Cap', amc: 'Quant', baseNav: 248.74, ret1y: '+52.3%', aum: '₹18,420 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'Nippon India Small Cap', amc: 'Nippon', baseNav: 162.88, ret1y: '+41.5%', aum: '₹48,200 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'SBI Small Cap', amc: 'SBI', baseNav: 192.46, ret1y: '+38.8%', aum: '₹28,400 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'Axis Small Cap', amc: 'Axis', baseNav: 98.12, ret1y: '+31.2%', aum: '₹18,800 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'HDFC Small Cap', amc: 'HDFC', baseNav: 122.84, ret1y: '+27.4%', aum: '₹22,100 Cr', sipMin: '₹100', star: 4, up: false },
        { name: 'Kotak Small Cap', amc: 'Kotak', baseNav: 288.40, ret1y: '+24.8%', aum: '₹14,200 Cr', sipMin: '₹100', star: 4, up: true },
    ],
    'Index Funds': [
        { name: 'UTI Nifty 50 Index', amc: 'UTI', baseNav: 148.22, ret1y: '+19.8%', aum: '₹18,400 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'HDFC Nifty 50 Index', amc: 'HDFC', baseNav: 212.80, ret1y: '+19.6%', aum: '₹12,200 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'SBI Nifty 50 Index', amc: 'SBI', baseNav: 242.44, ret1y: '+19.4%', aum: '₹8,400 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Motilal Nifty Next 50', amc: 'Motilal', baseNav: 84.18, ret1y: '+17.2%', aum: '₹4,200 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Axis Nifty Midcap 50', amc: 'Axis', baseNav: 48.92, ret1y: '+24.8%', aum: '₹2,400 Cr', sipMin: '₹100', star: 4, up: true },
        { name: 'ICICI Pru Sensex Index', amc: 'ICICI', baseNav: 78.12, ret1y: '+18.9%', aum: '₹6,200 Cr', sipMin: '₹100', star: 4, up: false },
    ],
    'Debt': [
        { name: 'HDFC Short Term Debt', amc: 'HDFC', baseNav: 28.14, ret1y: '+7.8%', aum: '₹14,200 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'ICICI Pru Corporate Bond', amc: 'ICICI', baseNav: 26.80, ret1y: '+7.4%', aum: '₹18,400 Cr', sipMin: '₹100', star: 5, up: true },
        { name: 'Aditya Birla SL Corp Bond', amc: 'ABSL', baseNav: 24.20, ret1y: '+7.1%', aum: '₹10,800 Cr', sipMin: '₹1,000', star: 4, up: true },
        { name: 'Kotak Bond Short Term', amc: 'Kotak', baseNav: 52.34, ret1y: '+6.9%', aum: '₹12,300 Cr', sipMin: '₹100', star: 4, up: true },
        { name: 'SBI Magnum Medium Dur', amc: 'SBI', baseNav: 46.78, ret1y: '+6.5%', aum: '₹8,400 Cr', sipMin: '₹500', star: 3, up: false },
        { name: 'Nippon India Liquid', amc: 'Nippon', baseNav: 6082.14, ret1y: '+6.2%', aum: '₹28,400 Cr', sipMin: '₹100', star: 4, up: true },
    ],
    'ELSS Tax Saver': [
        { name: 'Quant ELSS Tax Saver', amc: 'Quant', baseNav: 348.26, ret1y: '+49.8%', aum: '₹8,200 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'Mirae Asset ELSS', amc: 'Mirae', baseNav: 38.42, ret1y: '+32.4%', aum: '₹18,400 Cr', sipMin: '₹500', star: 5, up: true },
        { name: 'Axis ELSS Tax Saver', amc: 'Axis', baseNav: 68.92, ret1y: '+19.4%', aum: '₹32,100 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'Canara Rob ELSS', amc: 'Canara', baseNav: 28.18, ret1y: '+18.8%', aum: '₹6,500 Cr', sipMin: '₹500', star: 4, up: true },
        { name: 'SBI Long Term Equity', amc: 'SBI', baseNav: 362.80, ret1y: '+17.4%', aum: '₹24,700 Cr', sipMin: '₹500', star: 4, up: false },
        { name: 'HDFC ELSS Tax Saver', amc: 'HDFC', baseNav: 1128.44, ret1y: '+16.2%', aum: '₹14,200 Cr', sipMin: '₹500', star: 3, up: true },
    ],
    'NFO': [
        { name: 'Motilal Oswal BSE 500', amc: 'Motilal', baseNav: 10.00, ret1y: 'New', aum: '₹842 Cr', sipMin: '₹500', star: 0, up: true, isNew: true },
        { name: 'Mirae Asset Nifty 200', amc: 'Mirae', baseNav: 10.00, ret1y: 'New', aum: '₹1,240 Cr', sipMin: '₹1,000', star: 0, up: true, isNew: true },
        { name: 'HDFC Innovation FOF', amc: 'HDFC', baseNav: 10.00, ret1y: 'New', aum: '₹2,180 Cr', sipMin: '₹500', star: 0, up: true, isNew: true },
        { name: 'Nippon India Passive', amc: 'Nippon', baseNav: 10.00, ret1y: 'New', aum: '₹680 Cr', sipMin: '₹100', star: 0, up: true, isNew: true },
        { name: 'SBI Nifty 500 Index', amc: 'SBI', baseNav: 10.00, ret1y: 'New', aum: '₹1,840 Cr', sipMin: '₹500', star: 0, up: true, isNew: true },
        { name: 'Axis Quant Fund', amc: 'Axis', baseNav: 10.00, ret1y: 'New', aum: '₹420 Cr', sipMin: '₹1,000', star: 0, up: true, isNew: true },
    ],
};

// Add sparkbar data to each fund
const withBars = (funds) => funds.map(f => ({
    ...f,
    bars: Array.from({ length: 7 }, () => Math.floor(Math.random() * 50 + 20)),
}));

const initialFundData = Object.fromEntries(
    Object.entries(baseFunds).map(([k, v]) => [k, withBars(v)])
);

const MiniSparkBar = ({ bars, up }) => (
    <ResponsiveContainer width={64} height={28}>
        <BarChart data={bars.map((v) => ({ v }))} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
            <Bar dataKey="v" radius={[2, 2, 0, 0]}>
                {bars.map((_, i) => (
                    <Cell key={i} fill={up ? '#00D09C' : '#FF4D4D'} opacity={0.45 + (i / bars.length) * 0.55} />
                ))}
            </Bar>
        </BarChart>
    </ResponsiveContainer>
);

const StarRating = ({ count }) => (
    <div style={{ display: 'flex', gap: 1 }}>
        {[1, 2, 3, 4, 5].map((s) => (
            <Star key={s} size={10} fill={s <= count ? '#FFD700' : 'none'} color={s <= count ? '#FFD700' : '#444'} />
        ))}
    </div>
);

const MutualFundSection = () => {
    const [activeTab, setActiveTab] = useState('Top Performers');
    const [fundData, setFundData] = useState(initialFundData);
    const [updatedKeys, setUpdatedKeys] = useState({});
    const [loading, setLoading] = useState(false);

    // Fetch real NAVs from MFAPI.in for the active tab
    const fetchRealNavs = async (tab) => {
        const funds = fundData[tab];
        if (!funds) return;
        const schemeCodes = funds
            .map(f => SCHEME_CODES[f.name])
            .filter(Boolean);
        if (!schemeCodes.length) return;

        setLoading(true);
        try {
            const navMap = await batchGetNavs(schemeCodes);
            setFundData(prev => ({
                ...prev,
                [tab]: prev[tab].map(fund => {
                    const code = SCHEME_CODES[fund.name];
                    if (code && navMap[code]) {
                        return { ...fund, baseNav: navMap[code].nav };
                    }
                    return fund;
                })
            }));
        } catch (err) {
            console.warn('[WealthWise] MFAPI.in fetch failed, using fallback data:', err.message);
        } finally {
            setLoading(false);
        }
    };

    // Load real NAVs on mount and tab switch
    useEffect(() => { fetchRealNavs(activeTab); }, [activeTab]);

    // Simulated 4s drift on top of real NAV for live-feel UX
    useEffect(() => {
        const interval = setInterval(() => {
            setFundData(prev => {
                const updated = { ...prev };
                const tab = Object.keys(updated);
                tab.forEach(tabKey => {
                    updated[tabKey] = updated[tabKey].map((fund, idx) => {
                        if (fund.isNew) return fund; // NFOs don't flicker
                        const delta = (Math.random() - 0.49) * 0.35; // tiny random drift
                        const newNav = Math.max(1, fund.baseNav * (1 + delta / 100));
                        return { ...fund, baseNav: parseFloat(newNav.toFixed(2)) };
                    });
                });
                return updated;
            });
            // Mark which rows just updated (for flash animation)
            setUpdatedKeys(prev => {
                const keys = {};
                fundData[activeTab]?.forEach((_, i) => {
                    if (Math.random() > 0.4) keys[i] = Date.now();
                });
                return keys;
            });
        }, 4000);
        return () => clearInterval(interval);
    }, [activeTab]);

    const formatNav = (nav) => {
        if (nav >= 1000) return `₹${nav.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
        return `₹${nav.toFixed(2)}`;
    };

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
                        <Activity size={12} /> Mutual Fund Explorer
                        <span className="live-badge">● LIVE NAV</span>
                    </div>
                    <h2 className="section-title">
                        Discover <span className="gradient-text">top-rated funds</span>
                        <br />across SEBI categories
                    </h2>
                    <p className="section-sub">
                        NAV updates every few seconds · Star ratings by CRISIL / Value Research
                    </p>
                </motion.div>

                {/* Tabs — scrollable for many categories */}
                <div className="mf-tabs-wrapper">
                    <div className="mf-tabs">
                        {tabs.map((tab) => (
                            <button
                                key={tab}
                                className={`mf-tab ${activeTab === tab ? 'active' : ''}`}
                                onClick={() => setActiveTab(tab)}
                            >
                                {tab}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Fund Table — full width, no side cards */}
                <motion.div
                    className="market-table-card glassmorphism mf-full-width"
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.5 }}
                >
                    <div className="table-header-row mf-grid">
                        <span>Fund</span>
                        <span>Live NAV</span>
                        <span>1Y Return</span>
                        <span>AUM</span>
                        <span>SIP Min</span>
                        <span>Rating</span>
                        <span>Trend</span>
                    </div>

                    <div className="stock-list">
                        <AnimatePresence mode="wait">
                            {fundData[activeTab]?.map((fund, i) => (
                                <motion.div
                                    key={`${activeTab}-${fund.name}`}
                                    className={`stock-row mf-grid ${updatedKeys[i] ? 'nav-flash' : ''}`}
                                    initial={{ opacity: 0, x: -16 }}
                                    animate={{ opacity: 1, x: 0 }}
                                    exit={{ opacity: 0 }}
                                    transition={{ delay: i * 0.05, duration: 0.3 }}
                                    whileHover={{ background: 'rgba(0,208,156,0.04)', x: 3 }}
                                >
                                    <div className="stock-name-col">
                                        <div className="stock-avatar">{fund.amc.slice(0, 3)}</div>
                                        <div>
                                            <p className="stock-sym">{fund.name}</p>
                                            <p className="fund-amc">{fund.amc} MF</p>
                                        </div>
                                    </div>
                                    <span className={`live-nav ${fund.up ? 'up' : 'down'}`}>
                                        {formatNav(fund.baseNav)}
                                    </span>
                                    <span className={`stock-change ${fund.ret1y === 'New' ? 'new-fund' : fund.up ? 'up' : 'down'}`}>
                                        {fund.ret1y !== 'New' && (fund.up ? <TrendingUp size={12} /> : <TrendingDown size={12} />)}
                                        {fund.ret1y === 'New' ? '🆕 NFO' : fund.ret1y}
                                    </span>
                                    <span className="stock-vol">{fund.aum}</span>
                                    <span className="stock-vol">{fund.sipMin}</span>
                                    <span>
                                        {fund.star > 0 ? <StarRating count={fund.star} /> : <span className="new-tag">NEW</span>}
                                    </span>
                                    <div className="stock-spark">
                                        <MiniSparkBar bars={fund.isNew ? [10, 10, 10, 10, 10, 10, 10] : fund.bars} up={fund.up} />
                                    </div>
                                </motion.div>
                            ))}
                        </AnimatePresence>
                    </div>

                    <button className="view-all-btn">
                        View all {activeTab} funds <ChevronRight size={14} />
                    </button>
                </motion.div>
            </div>
        </section>
    );
};

export default MutualFundSection;
