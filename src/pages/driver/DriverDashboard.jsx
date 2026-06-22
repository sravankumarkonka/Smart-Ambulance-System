import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  subscribeToPendingEmergencies,
  subscribeToDriverEmergencies,
  assignDriverToEmergency,
  getUserProfile,
  createOrUpdateAmbulance
} from '../../services/firestoreService';

const DriverDashboard = () => {
  const { currentUser } = useAuth();
  const navigate = useNavigate();
  const [driverProfile, setDriverProfile] = useState(null);
  const [pendingEmergencies, setPendingEmergencies] = useState([]);
  const [activeEmergencies, setActiveEmergencies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [zoomImageUrl, setZoomImageUrl] = useState(null);

  useEffect(() => {
    if (!currentUser) return;

    let unsubscribePending;
    let unsubscribeDriver;

    // Fetch driver profile and subscribe to updates
    const initDriver = async () => {
      try {
        const profile = await getUserProfile(currentUser.uid);
        setDriverProfile(profile);

        // Subscribe to pending
        unsubscribePending = subscribeToPendingEmergencies((list) => {
          setPendingEmergencies(list || []);
          setLoading(false);
        });

        // Subscribe to assigned to this driver
        unsubscribeDriver = subscribeToDriverEmergencies(currentUser.uid, (list) => {
          const active = (list || []).filter(e => e?.status === 'assigned' || e?.status === 'arrived');
          setActiveEmergencies(active);
          
          // Update database with latest available status
          createOrUpdateAmbulance(currentUser.uid, {
            driverName: profile?.name || 'Ambulance Driver',
            driverPhone: profile?.phone || 'N/A',
            status: active.length > 0 ? 'busy' : 'available'
          });
        });
      } catch (err) {
        console.error('Failed to initialize driver dashboard:', err);
        setLoading(false);
      }
    };

    initDriver();

    return () => {
      if (unsubscribePending) unsubscribePending();
      if (unsubscribeDriver) unsubscribeDriver();
    };
  }, [currentUser]);

  const handleAccept = async (emergencyId) => {
    if (actionLoading) return;
    if (activeEmergencies.length > 0) {
      alert('You already have an active emergency assignment!');
      return;
    }
    setActionLoading(true);
    console.log('[DriverDashboard] Driver accepting emergency request:', emergencyId);

    try {
      const driverName = driverProfile?.name || 'Ambulance Driver';
      const driverPhone = driverProfile?.phone || 'N/A';
      
      await assignDriverToEmergency(emergencyId, currentUser.uid, driverName, driverPhone);
      console.log('[DriverDashboard] Driver assignment completed successfully. Navigating to active route.');
      
      // Navigate to the active emergency tracking page
      navigate(`/driver/active/${emergencyId}`);
    } catch (error) {
      console.error('[DriverDashboard] Error accepting emergency request:', error);
      alert('Error accepting emergency: ' + error.message);
    } finally {
      setActionLoading(false);
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
          <p>Connecting to Emergency Dispatch Center...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-4" data-testid="driver-dashboard">
      <div className="mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>Driver Dashboard</h1>
          <p className="text-muted" style={{ fontSize: '14px' }}>Welcome back, {driverProfile?.name || 'Driver'}</p>
        </div>
        <span className="badge badge-success" style={{ padding: '8px 16px' }} data-testid="active-duty-badge">Active Duty</span>
      </div>

      <div className="card mb-4" data-testid="assigned-emergencies-panel">
        <h3>Assigned Emergencies</h3>
        {activeEmergencies.length > 0 ? (
          <div style={{ marginTop: '16px' }}>
            <h3 style={{ color: 'var(--accent-red)', fontSize: '16px', marginBottom: '8px' }}>Active Dispatch Assigned</h3>
            <p className="mt-1">You have an active emergency request that needs your immediate response.</p>
            
            <div className="mt-3" style={{ background: 'white', padding: '16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border)' }}>
              <p><strong>Patient:</strong> <span>{activeEmergencies[0]?.patientName || 'Unknown Patient'}</span></p>
              <p className="mt-1"><strong>Type:</strong> {(activeEmergencies[0]?.emergencyType || 'other').toUpperCase()}</p>
              <p className="mt-1"><strong>Description:</strong> {activeEmergencies[0]?.description || 'No description provided'}</p>
            </div>

            <div className="mt-3" style={{ display: 'flex', gap: '12px' }}>
              <Link to={`/driver/active/${activeEmergencies[0]?.id}`} className="btn btn-danger" data-testid="go-to-active-route-btn">
                Go to Active Route
              </Link>
              <button
                className="btn btn-outline"
                style={{ color: 'var(--accent-red)', borderColor: 'var(--accent-red)' }}
                onClick={async () => {
                  if (window.confirm('Are you sure you want to reject this assignment?')) {
                    const { releaseEmergency } = await import('../../services/firestoreService');
                    await releaseEmergency(activeEmergencies[0]?.id, currentUser.uid);
                  }
                }}
                data-testid="reject-assignment-btn"
              >
                Reject Assignment
              </button>
            </div>
          </div>
        ) : (
          <p className="text-muted mt-2" style={{ fontSize: '14px' }}>No active assignments.</p>
        )}
      </div>

      {/* 2. Pending Incoming List */}
      <h3>Incoming Emergency Broadcasts</h3>
      <p className="text-muted mb-3" style={{ fontSize: '14px' }}>Real-time requests awaiting ambulance assignment.</p>

      {pendingEmergencies.length === 0 ? (
        <div className="card text-center" style={{ padding: '48px', border: '1px dashed var(--border)' }}>
          <span style={{ fontSize: '40px' }}>📡</span>
          <h4 className="mt-2">Scanning for emergency signals...</h4>
          <p className="text-muted mt-1">No pending calls in your area currently.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {(pendingEmergencies || []).map((emergency) => (
            <div key={emergency?.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <h4 style={{ textTransform: 'capitalize', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {getEmergencyIcon(emergency?.emergencyType)} {emergency?.emergencyType || 'other'}
                  </h4>
                  <p className="text-muted" style={{ fontSize: '12px' }}>
                    Reported: {new Date(emergency?.createdAt || 0).toLocaleTimeString()}
                  </p>
                </div>
                <span className="badge badge-warning" data-testid="assigned-badge">Awaiting Driver</span>
              </div>

              <div style={{ background: '#f8fafc', padding: '12px 16px', borderRadius: 'var(--radius-md)', fontSize: '14px' }}>
                <p><strong>Patient Name:</strong> <span>{emergency?.patientName || 'Unknown Patient'}</span></p>
                <p className="mt-1"><strong>Description:</strong> {emergency?.description || 'No description provided'}</p>
                {emergency?.severityLevel && (
                  <p className="mt-1">
                    <strong>Severity:</strong> <span className={`badge ${emergency.severityLevel === 'critical' || emergency.severityLevel === 'high' ? 'badge-danger' : emergency.severityLevel === 'medium' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '11px', textTransform: 'capitalize' }}>{emergency.severityLevel}</span>
                  </p>
                )}
                <p className="mt-1" style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                  <strong>Coordinates:</strong> {Number(emergency?.latitude || 0).toFixed(6)}, {Number(emergency?.longitude || 0).toFixed(6)}
                </p>
                {emergency?.imageUrl && (
                  <div style={{ marginTop: '12px' }}>
                    <p style={{ fontWeight: 500, marginBottom: '6px' }}>Accident Evidence Image:</p>
                    <img
                      src={emergency.imageUrl}
                      alt="Accident Evidence"
                      style={{
                        width: '100%',
                        maxWidth: '240px',
                        maxHeight: '160px',
                        objectFit: 'cover',
                        borderRadius: 'var(--radius-md)',
                        cursor: 'zoom-in',
                        border: '1px solid var(--border)'
                      }}
                      onClick={() => setZoomImageUrl(emergency.imageUrl)}
                      data-testid="accident-image-preview"
                    />
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
                <button
                  onClick={() => handleAccept(emergency?.id)}
                  className="btn btn-danger"
                  style={{ padding: '8px 20px', fontSize: '14px' }}
                  disabled={actionLoading || activeEmergencies.length > 0}
                  data-testid="accept-dispatch-btn"
                >
                  Accept Assignment
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {zoomImageUrl && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            zIndex: 9999,
            cursor: 'zoom-out'
          }}
          onClick={() => setZoomImageUrl(null)}
          data-testid="zoom-modal"
        >
          <div style={{ position: 'relative', maxWidth: '90%', maxHeight: '90%' }}>
            <img
              src={zoomImageUrl}
              alt="Zoomed Accident Evidence"
              style={{
                maxWidth: '100%',
                maxHeight: '90vh',
                objectFit: 'contain',
                borderRadius: 'var(--radius-md)',
                boxShadow: '0 8px 32px rgba(0,0,0,0.5)'
              }}
            />
            <button
              onClick={() => setZoomImageUrl(null)}
              style={{
                position: 'absolute',
                top: '10px',
                right: '10px',
                background: 'white',
                border: 'none',
                borderRadius: '50%',
                width: '32px',
                height: '32px',
                cursor: 'pointer',
                fontWeight: 'bold',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              ✕
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default DriverDashboard;
