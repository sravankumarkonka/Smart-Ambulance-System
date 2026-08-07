import React from 'react';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
    this.setState({ errorInfo });
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          backgroundColor: '#F7F9FC',
          padding: '24px',
          fontFamily: "'Inter', sans-serif"
        }}>
          <div className="card glass-panel" style={{
            maxWidth: '550px',
            width: '100%',
            padding: '40px',
            textAlign: 'center',
            borderTop: '6px solid var(--accent-red)'
          }}>
            <span style={{ fontSize: '64px', display: 'block', marginBottom: '16px' }}>🚨</span>
            <h2 style={{ color: 'var(--text-main)', marginBottom: '12px', fontSize: '24px' }}>
              Application Error Encountered
            </h2>
            <p className="text-muted mb-4" style={{ fontSize: '15px' }}>
              A critical runtime error has occurred in the application. The system remains operational; please reload or return to safety.
            </p>
            
            {this.state.error && (
              <div style={{
                textAlign: 'left',
                backgroundColor: 'var(--accent-red-light)',
                color: '#CC2F26',
                padding: '16px',
                borderRadius: 'var(--radius-md)',
                fontSize: '13px',
                fontFamily: 'monospace',
                overflowX: 'auto',
                marginBottom: '24px',
                border: '1.5px solid rgba(255, 59, 48, 0.1)'
              }}>
                <strong>Error:</strong> {this.state.error.toString()}
              </div>
            )}

            <div style={{ display: 'flex', gap: '16px', justifyContent: 'center' }}>
              <button 
                onClick={() => window.location.reload()}
                className="btn btn-primary"
                style={{ flex: 1 }}
              >
                Reload Page
              </button>
              <button 
                onClick={this.handleReset}
                className="btn btn-outline"
                style={{ flex: 1, borderColor: 'var(--primary)', color: 'var(--primary)' }}
              >
                Return Home
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
