import { createContext, useContext, useEffect, useState, useRef } from 'react';
import { API_BASE } from '../lib/config';

const BackendWarmupContext = createContext(null);

const HEALTH_URL = `${API_BASE}/api/auth/health`;
const KEEPALIVE_INTERVAL_MS = 9 * 60 * 1000; // ping every 9 minutes to prevent 15-min spin-down
const MAX_WAIT_MS = 90_000; // give backend up to 90 seconds to wake
const POLL_INTERVAL_MS = 3_000;  // check every 3 seconds

/**
 * BackendWarmupProvider
 * ─────────────────────
 * On mount, immediately pings the Spring Boot health endpoint.
 * If it responds fast → isWarm = true right away (backend was already running).
 * If it times out or errors → starts polling every 3 s until the backend wakes up.
 *
 * All child components can read { isWarm, warmupElapsed } to decide whether
 * to show a loading overlay.
 *
 * A keep-alive ping fires every 9 minutes to prevent Render from spinning the
 * container back down while the tab is open.
 */
export const BackendWarmupProvider = ({ children }) => {
    const [isWarm, setIsWarm] = useState(false);
    const [warmupElapsed, setWarmupElapsed] = useState(0); // seconds since first attempt
    const startRef = useRef(null);
    const pollTimerRef = useRef(null);
    const elapsedTimerRef = useRef(null);
    const keepAliveRef = useRef(null);

    const pingHealth = async () => {
        try {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), 5000);
            const res = await fetch(HEALTH_URL, {
                method: 'GET',
                signal: controller.signal,
                cache: 'no-store',
            });
            clearTimeout(timeout);
            return res.ok;
        } catch {
            return false;
        }
    };

    const startKeepAlive = () => {
        if (keepAliveRef.current) clearInterval(keepAliveRef.current);
        keepAliveRef.current = setInterval(() => {
            pingHealth(); // fire-and-forget, just keeps backend warm
        }, KEEPALIVE_INTERVAL_MS);
    };

    const stopPolling = () => {
        clearInterval(pollTimerRef.current);
        clearInterval(elapsedTimerRef.current);
    };

    useEffect(() => {
        startRef.current = Date.now();

        // Elapsed second counter so UI can show "Waking up… Xs"
        elapsedTimerRef.current = setInterval(() => {
            setWarmupElapsed(Math.floor((Date.now() - startRef.current) / 1000));
        }, 1000);

        const attempt = async () => {
            const alive = await pingHealth();
            if (alive) {
                stopPolling();
                setIsWarm(true);
                startKeepAlive();
                return;
            }
            // Give up after MAX_WAIT_MS — let the user try anyway
            if (Date.now() - startRef.current >= MAX_WAIT_MS) {
                stopPolling();
                setIsWarm(true); // unblock UI even if backend is struggling
            }
        };

        // First attempt immediately
        attempt();

        // Then poll every POLL_INTERVAL_MS
        pollTimerRef.current = setInterval(attempt, POLL_INTERVAL_MS);

        return () => {
            stopPolling();
            if (keepAliveRef.current) clearInterval(keepAliveRef.current);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return (
        <BackendWarmupContext.Provider value={{ isWarm, warmupElapsed }}>
            {children}
        </BackendWarmupContext.Provider>
    );
};

export const useBackendWarmup = () => {
    const ctx = useContext(BackendWarmupContext);
    if (!ctx) throw new Error('useBackendWarmup must be inside <BackendWarmupProvider>');
    return ctx;
};
