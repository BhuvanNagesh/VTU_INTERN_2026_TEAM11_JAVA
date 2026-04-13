/**
 * Centralized application configuration.
 *
 * Import this instead of inlining the env var check in every component:
 *   import { API_BASE } from '../lib/config';
 *
 * Works for both:
 *   - Local dev: http://localhost:8080  (when VITE_API_BASE_URL is not set)
 *   - Render:    https://wealthwise-backend-zv5r.onrender.com (set via env var)
 */
export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
