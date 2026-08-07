import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const Navbar = () => {
  const { currentUser, userRole, logout } = useAuth();
  const navigate = useNavigate();
  const [theme, setTheme] = React.useState(localStorage.getItem('appTheme') || 'light');

  React.useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('appTheme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  };

  const handleLogout = async () => {
    try {
      await logout();
      navigate('/login');
    } catch (error) {
      console.error('Failed to log out', error);
    }
  };

  return (
    <header className="navbar">
      <div className="container nav-container">
        <Link to="/" className="logo">
          🚑 SmartAmbulance
        </Link>
        
        <nav style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
          {currentUser ? (
            <>
              {userRole === 'super_admin' ? (
                <>
                  <Link to="/super-admin/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Super Admin
                  </Link>
                  <Link to="/admin/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Operations
                  </Link>
                  <Link to="/admin/history" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    History
                  </Link>
                </>
              ) : userRole === 'admin' ? (
                <>
                  <Link to="/admin/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Dashboard
                  </Link>
                  <Link to="/admin/live-map" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Live Map
                  </Link>
                  <Link to="/admin/history" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    History
                  </Link>
                </>
              ) : userRole === 'driver' ? (
                <>
                  <Link to="/driver/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Dashboard
                  </Link>
                  <Link to="/driver/history" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    History
                  </Link>
                </>
              ) : (
                <>
                  <Link to="/user/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Dashboard
                  </Link>
                  <Link to="/user/report" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Report Emergency
                  </Link>
                  <Link to="/user/history" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    History
                  </Link>
                </>
              )}
              <button onClick={handleLogout} className="btn btn-danger" style={{ padding: '8px 16px' }} data-testid="logout-button">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                Login
              </Link>
              <Link to="/register" className="btn btn-primary" style={{ padding: '8px 16px' }}>
                Register
              </Link>
            </>
          )}
          <button
            type="button"
            onClick={toggleTheme}
            className="btn btn-outline"
            style={{ padding: '8px 12px', fontSize: '14px', borderRadius: '8px', cursor: 'pointer' }}
            title="Toggle Light/Dark Theme"
          >
            {theme === 'dark' ? '☀️ Light' : '🌙 Dark'}
          </button>
        </nav>
      </div>
    </header>
  );
};

export default Navbar;
