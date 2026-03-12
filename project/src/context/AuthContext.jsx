import { createContext, useContext, useEffect, useState } from 'react';

const AuthContext = createContext(null);

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [token, setToken] = useState(null);
    const [loading, setLoading] = useState(true);

    // Restore session from localStorage on mount
    useEffect(() => {
        const storedToken = localStorage.getItem('ww_token');
        const storedUser = localStorage.getItem('ww_user');
        if (storedToken && storedUser) {
            setToken(storedToken);
            setUser(JSON.parse(storedUser));
        }
        setLoading(false);
    }, []);

    /**
     * Sign up: POST /api/auth/signup
     * Returns { token, user } or throws with error message
     */
    const signUp = async ({ fullName, email, password, phone, currency, panCard }) => {
        const res = await fetch(`${API_BASE}/api/auth/signup`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, email, password, phone, currency, panCard }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Sign up failed');
        _saveSession(data.token, data.user);
        return data;
    };

    /**
     * Sign in: POST /api/auth/signin
     * Returns { token, user } or throws with error message
     */
    const signIn = async ({ email, password }) => {
        const res = await fetch(`${API_BASE}/api/auth/signin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Sign in failed');
        _saveSession(data.token, data.user);
        return data;
    };

    const signOut = () => {
        localStorage.removeItem('ww_token');
        localStorage.removeItem('ww_user');
        setToken(null);
        setUser(null);
    };

    const _saveSession = (tok, usr) => {
        localStorage.setItem('ww_token', tok);
        localStorage.setItem('ww_user', JSON.stringify(usr));
        setToken(tok);
        setUser(usr);
    };

    /** Returns the stored JWT for use in API calls */
    const getToken = () => localStorage.getItem('ww_token');

    return (
        <AuthContext.Provider value={{ user, token, loading, signUp, signIn, signOut, getToken }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
    return ctx;
};
