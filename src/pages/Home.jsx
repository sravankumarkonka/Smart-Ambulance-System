import React from 'react';
import { Link } from 'react-router-dom';

const Home = () => {
  return (
    <div className="container" style={{ textAlign: 'center', padding: '60px 0' }}>
      <h1 style={{ fontSize: '48px', color: 'var(--primary)', marginBottom: '16px' }}>
        Smart Ambulance Tracking & Routing
      </h1>
      <p style={{ fontSize: '18px', color: 'var(--text-muted)', maxWidth: '600px', margin: '0 auto 40px' }}>
        A dynamic, real-time emergency response system. Connecting patients in need with the nearest available ambulance and routing them to the optimal healthcare facility.
      </p>
      
      <div style={{ display: 'flex', gap: '24px', justifyContent: 'center' }}>
        <div className="card glass-panel" style={{ width: '300px' }}>
          <h3>Need Help?</h3>
          <p className="mt-1 mb-3" style={{ color: 'var(--text-muted)' }}>Report an emergency and track your ambulance live.</p>
          <Link to="/register" className="btn btn-danger" style={{ width: '100%' }}>Report Accident</Link>
        </div>
        
        <div className="card glass-panel" style={{ width: '300px' }}>
          <h3>Staff Login</h3>
          <p className="mt-1 mb-3" style={{ color: 'var(--text-muted)' }}>For drivers and administrators to manage dispatch.</p>
          <Link to="/login" className="btn btn-primary" style={{ width: '100%' }}>Staff Portal</Link>
        </div>
      </div>
    </div>
  );
};

export default Home;
