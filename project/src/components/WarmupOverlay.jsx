import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, Zap } from 'lucide-react';
import { useBackendWarmup } from '../context/BackendWarmupContext';
import './WarmupOverlay.css';

const TIPS = [
    'Crunching your mutual fund NAVs…',
    'Connecting to the analytics engine…',
    'Preparing your portfolio dashboard…',
    'Loading SIP intelligence suite…',
    'Almost there — spinning up the server…',
];

export default function WarmupOverlay() {
    const { isWarm, warmupElapsed } = useBackendWarmup();
    const [tipIndex, setTipIndex] = useState(0);

    // Rotate tips every 4 seconds
    useEffect(() => {
        if (isWarm) return;
        const t = setInterval(() => setTipIndex(i => (i + 1) % TIPS.length), 4000);
        return () => clearInterval(t);
    }, [isWarm]);

    // Only show overlay when backend hasn't warmed up AND we've been waiting > 1.5s
    // (instant loads = no flicker)
    const showOverlay = !isWarm && warmupElapsed >= 2;

    return (
        <AnimatePresence>
            {showOverlay && (
                <motion.div
                    className="warmup-overlay"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0, transition: { duration: 0.6 } }}
                >
                    {/* Animated background blobs */}
                    <div className="warmup-blob warmup-blob-1" />
                    <div className="warmup-blob warmup-blob-2" />
                    <div className="warmup-blob warmup-blob-3" />

                    <motion.div
                        className="warmup-card"
                        initial={{ scale: 0.9, opacity: 0, y: 20 }}
                        animate={{ scale: 1, opacity: 1, y: 0 }}
                        transition={{ delay: 0.1, type: 'spring', damping: 18, stiffness: 200 }}
                    >
                        {/* Logo */}
                        <div className="warmup-logo">
                            <div className="warmup-logo-mark">
                                <TrendingUp size={20} color="#00D09C" />
                            </div>
                            <span className="warmup-logo-text">WealthWise</span>
                        </div>

                        {/* Animated spinner ring */}
                        <div className="warmup-spinner-wrap">
                            <div className="warmup-spinner-ring" />
                            <div className="warmup-spinner-ring warmup-spinner-ring--inner" />
                            <div className="warmup-spinner-center">
                                <Zap size={18} color="#00D09C" />
                            </div>
                        </div>

                        {/* Status text */}
                        <h2 className="warmup-title">Starting up…</h2>

                        <AnimatePresence mode="wait">
                            <motion.p
                                key={tipIndex}
                                className="warmup-tip"
                                initial={{ opacity: 0, y: 6 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -6 }}
                                transition={{ duration: 0.35 }}
                            >
                                {TIPS[tipIndex]}
                            </motion.p>
                        </AnimatePresence>

                        {/* Progress bar */}
                        <div className="warmup-progress-track">
                            <motion.div
                                className="warmup-progress-fill"
                                animate={{ width: `${Math.min((warmupElapsed / 50) * 100, 95)}%` }}
                                transition={{ ease: 'easeOut', duration: 1 }}
                            />
                        </div>

                        {/* Cold-start explanation */}
                        <div className="warmup-notice">
                            <span className="warmup-notice-icon">⏳</span>
                            <div>
                                <strong>First visit of the day?</strong> Our free-tier server takes
                                up&nbsp;to&nbsp;60&nbsp;s to wake from sleep.
                                {warmupElapsed > 5 && (
                                    <span className="warmup-elapsed"> ({warmupElapsed}s elapsed)</span>
                                )}
                            </div>
                        </div>
                    </motion.div>
                </motion.div>
            )}
        </AnimatePresence>
    );
}
