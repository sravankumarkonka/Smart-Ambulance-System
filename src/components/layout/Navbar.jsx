import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { auth } from '../../config/firebase';
import { signOut } from 'firebase/auth';

const Navbar = () => {
  const { currentUser, userRole } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      if (localStorage.getItem('mockUser')) {
        localStorage.setItem('mockUser', '');
        localStorage.setItem('mockToken', '');
        localStorage.setItem('userRole', '');
        localStorage.removeItem('mockUser');
        localStorage.removeItem('mockToken');
        localStorage.removeItem('userRole');
        window.dispatchEvent(new Event('mock-login-changed'));
      }
      await signOut(auth);
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
              {userRole === 'admin' ? (
                <>
                  <Link to="/admin/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Dashboard
                  </Link>
                  <Link to="/admin/live-map" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Live Map
                  </Link>
                </>
              ) : userRole === 'driver' ? (
                <>
                  <Link to="/driver/dashboard" className="btn btn-outline" style={{ padding: '8px 16px' }}>
                    Dashboard
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
        </nav>
      </div>
    </header>
  );
};

export default Navbar;
