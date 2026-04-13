import { Component } from 'react';

/**
 * React ErrorBoundary — catches unhandled JS errors in any child component.
 * Without this, a single component crash whites out the entire app.
 *
 * Wrap around the main App or individual page-level components.
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    // Log to console — replace with a logging service (e.g. Sentry) in production
    console.error('[WealthWise ErrorBoundary]', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          justifyContent: 'center', minHeight: '100vh',
          background: '#0A0A0F', color: '#fff', textAlign: 'center', padding: 32,
        }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
          <h2 style={{ color: '#FF4D4D', marginBottom: 8 }}>Something went wrong</h2>
          <p style={{ color: '#888', maxWidth: 400, marginBottom: 24 }}>
            An unexpected error occurred. Please refresh the page. If the issue persists, try logging out and back in.
          </p>
          <button
            onClick={() => window.location.reload()}
            style={{
              padding: '10px 28px', background: 'rgba(0,210,156,0.15)',
              border: '1px solid rgba(0,210,156,0.4)', borderRadius: 24,
              color: '#00D29C', fontSize: 14, cursor: 'pointer', fontWeight: 600,
            }}
          >
            Refresh Page
          </button>
          {import.meta.env.DEV && (
            <pre style={{
              marginTop: 24, padding: 16, background: '#111', borderRadius: 8,
              fontSize: 11, color: '#FF6B6B', textAlign: 'left',
              maxWidth: 700, overflow: 'auto',
            }}>
              {this.state.error?.toString()}
            </pre>
          )}
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
