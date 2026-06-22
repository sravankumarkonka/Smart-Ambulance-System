import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getEmergencyHistory } from '../../services/firestoreService';

const EmergencyHistory = () => {
  const { currentUser } = useAuth();
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        if (currentUser) {
          const data = await getEmergencyHistory(currentUser.uid);
          setHistory(data);
        }
      } catch (err) {
        setError('Failed to load emergency history: ' + err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [currentUser]);

  const formatDate = (isoString) => {
    if (!isoString) return 'N/A';
    const date = new Date(isoString);
    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'pending':
        return <span className="badge badge-warning">Pending</span>;
      case 'assigned':
        return <span className="badge btn-primary" style={{ boxShadow: 'none', pointerEvents: 'none' }}>Assigned</span>;
      case 'arrived':
        return <span className="badge btn-outline" style={{ border: 'none', background: 'var(--primary-light)', color: 'var(--primary)', pointerEvents: 'none' }}>Arrived</span>;
      case 'completed':
        return <span className="badge badge-success">Completed</span>;
      case 'cancelled':
        return <span className="badge badge-danger">Cancelled</span>;
      default:
        return <span className="badge">{status}</span>;
    }
  };

  const getEmergencyIcon = (type) => {
    switch (type) {
      case 'accident': return '🚗';
      case 'cardiac': return '❤️';
      case 'respiratory': return '🫁';
      case 'stroke': return '🧠';
      case 'pregnancy': return '👶';
      default: return '🚨';
    }
  };

  if (loading) {
    return (
      <div className="container mt-4 text-center">
        <div className="card">
          <p>Loading emergency history...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-4">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h2>Emergency History</h2>
        <Link to="/report-emergency" className="btn btn-danger">🚨 Report New Accident</Link>
      </div>

      {error && <div className="badge badge-danger mb-3" style={{ display: 'block', padding: '12px' }}>{error}</div>}

      {history.length === 0 ? (
        <div className="card text-center" style={{ padding: '48px' }}>
          <span style={{ fontSize: '48px', display: 'block', marginBottom: '16px' }}>📋</span>
          <h3>No emergencies reported yet</h3>
          <p className="text-muted mt-1 mb-3">If you need immediate medical assistance or ambulance routing, please submit a request.</p>
          <Link to="/report-emergency" className="btn btn-danger" style={{ display: 'inline-flex' }}>Report Accident Now</Link>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {(history || []).map((emergency) => (
            <div key={emergency?.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
                <div>
                  <h3 style={{ textTransform: 'capitalize', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {getEmergencyIcon(emergency?.emergencyType)} {emergency?.emergencyType || 'other'}
                  </h3>
                  <p className="text-muted" style={{ fontSize: '13px' }}>Reported: {formatDate(emergency?.createdAt)}</p>
                </div>
                {getStatusBadge(emergency?.status || 'unknown')}
              </div>

              <div style={{ background: '#f8fafc', padding: '12px 16px', borderRadius: 'var(--radius-md)', fontSize: '14px' }}>
                <p><strong>Patient Name:</strong> {emergency?.patientName || 'Unknown Patient'}</p>
                <p className="mt-1"><strong>Description:</strong> {emergency?.description || 'No description provided'}</p>
                <p className="mt-1" style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                  <strong>Location:</strong> {Number(emergency?.latitude || 0).toFixed(6)}, {Number(emergency?.longitude || 0).toFixed(6)}
                </p>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                {emergency?.status !== 'completed' && emergency?.status !== 'cancelled' ? (
                  <Link to={`/user/track/${emergency?.id}`} className="btn btn-primary" style={{ padding: '8px 16px', fontSize: '13px' }}>
                    Live Track
                  </Link>
                ) : (
                  <Link to={`/user/track/${emergency?.id}`} className="btn btn-outline" style={{ padding: '8px 16px', fontSize: '13px' }}>
                    View Request Details
                  </Link>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default EmergencyHistory;
