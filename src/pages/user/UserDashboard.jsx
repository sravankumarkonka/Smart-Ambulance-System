import React from 'react';
import { Link } from 'react-router-dom';

const UserDashboard = () => {
  return (
    <div className="container mt-4" data-testid="user-dashboard">
      <h1 className="mb-4">Patient Dashboard</h1>
      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        <div className="card" style={{ flex: 1, minWidth: '240px' }}>
          <h3>Report Emergency</h3>
          <p className="mt-1 mb-3 text-muted">Request immediate ambulance assistance.</p>
          <Link to="/report-emergency" className="btn btn-danger" data-testid="report-emergency-link">Report Emergency</Link>
        </div>
        <div className="card" style={{ flex: 1, minWidth: '240px' }}>
          <h3>Live Tracking</h3>
          <p className="mt-1 mb-3 text-muted">Track your dispatched ambulance in real time.</p>
          <Link to="/live-tracking" className="btn btn-primary" data-testid="live-tracking-link">Live Tracking</Link>
        </div>
        <div className="card" style={{ flex: 1, minWidth: '240px' }}>
          <h3>Emergency History</h3>
          <p className="mt-1 mb-3 text-muted">View past emergency requests.</p>
          <Link to="/emergency-history" className="btn btn-outline" data-testid="history-link">View History</Link>
        </div>
      </div>
    </div>
  );
};
export default UserDashboard;
