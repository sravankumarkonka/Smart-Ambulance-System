import React, { useEffect, useState, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { subscribeToEmergency, updateEmergencyStatus } from '../../services/firestoreService';
import { fetchRoute } from '../../services/routingService';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const TrackAmbulance = () => {
  const { id } = useParams();
  const [emergency, setEmergency] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const [routePath, setRoutePath] = useState([]);
  const [routeDetails, setRouteDetails] = useState(null);
  const [routingLoading, setRoutingLoading] = useState(false);

  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const patientMarkerRef = useRef(null);
  const ambulanceMarkerRef = useRef(null);
  const hospitalMarkerRef = useRef(null);
  const routePolylineRef = useRef(null);

  // 1. Subscribe to emergency document
  useEffect(() => {
    const unsubscribe = subscribeToEmergency(id, (data) => {
      if (data) {
        setEmergency(data);
      } else {
        setError('Emergency request not found.');
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, [id]);

  // 2. Fetch routing path dynamically (with 30s recalculation)
  useEffect(() => {
    if (loading || !emergency || error) return;
    const hasDriver = emergency.status === 'assigned' || emergency.status === 'arrived';
    
    let timeoutId;
    if (!hasDriver) {
      timeoutId = setTimeout(() => {
        if (routePath.length > 0 || routeDetails !== null) {
          setRoutePath([]);
          setRouteDetails(null);
        }
      }, 0);
      return () => {
        if (timeoutId) clearTimeout(timeoutId);
      };
    }

    const lat = Number(emergency?.latitude || 12.9716);
    const lng = Number(emergency?.longitude || 77.5946);
    const hLat = Number(emergency?.hospitalLatitude || 0);
    const hLng = Number(emergency?.hospitalLongitude || 0);
    
    const driverLat = Number(emergency?.driverLatitude !== undefined ? emergency.driverLatitude : (emergency?.status === 'assigned' ? lat + 0.005 : lat));
    const driverLng = Number(emergency?.driverLongitude !== undefined ? emergency.driverLongitude : (emergency?.status === 'assigned' ? lng + 0.005 : lng));

    const updateRoute = async () => {
      setRoutingLoading(true);
      try {
        let waypoints = [];
        if (emergency?.status === 'assigned') {
          // Segment 1 + Segment 2: Ambulance -> Patient -> Hospital
          waypoints = [
            [driverLat, driverLng],
            [lat, lng]
          ];
          if (hLat !== 0 && hLng !== 0) {
            waypoints.push([hLat, hLng]);
          }
        } else {
          // Segment 2 only (arrived): Patient/Ambulance -> Hospital
          if (hLat !== 0 && hLng !== 0) {
            waypoints = [
              [driverLat, driverLng],
              [hLat, hLng]
            ];
          } else {
            waypoints = [[driverLat, driverLng], [lat, lng]];
          }
        }

        const data = await fetchRoute(waypoints);
        setRoutePath(data?.coordinates || []);
        setRouteDetails(data);
      } catch (err) {
        console.error('Routing error:', err);
      } finally {
        setRoutingLoading(false);
      }
    };

    updateRoute();

    // Recalculate route every 30 seconds
    const intervalId = setInterval(updateRoute, 30000);

    return () => {
      clearInterval(intervalId);
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [loading, error, emergency, routePath.length, routeDetails]);

  // 3. Handle Leaflet Map Initialization and updates
  useEffect(() => {
    if (loading || !emergency || error) return;

    const lat = Number(emergency?.latitude || 12.9716);
    const lng = Number(emergency?.longitude || 77.5946);
    const hLat = Number(emergency?.hospitalLatitude || 0);
    const hLng = Number(emergency?.hospitalLongitude || 0);

    try {
      // Initialize Map if not already initialized
      if (!mapInstance.current && mapRef.current) {
        if (mapRef.current._leaflet_id) {
          mapRef.current._leaflet_id = null;
        }
        mapInstance.current = L.map(mapRef.current).setView([lat, lng], 14);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap contributors'
        }).addTo(mapInstance.current);
      }

      if (mapInstance.current) {
        // Update or create patient marker
        const patientIcon = L.divIcon({
          html: `<div style="font-size: 32px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3));">📍</div>`,
          className: 'patient-marker',
          iconSize: [32, 32],
          iconAnchor: [16, 32]
        });

        if (patientMarkerRef.current) {
          patientMarkerRef.current.setLatLng([lat, lng]);
        } else {
          patientMarkerRef.current = L.marker([lat, lng], { icon: patientIcon })
            .addTo(mapInstance.current)
            .bindPopup(`<b>Patient Location</b><br>${emergency?.patientName || 'Unknown Patient'}`);
        }

        // Update or create hospital marker if selected
        if (hLat !== 0 && hLng !== 0) {
          const hospitalIcon = L.divIcon({
            html: `<div style="font-size: 32px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3));">🏥</div>`,
            className: 'hospital-marker',
            iconSize: [32, 32],
            iconAnchor: [16, 16]
          });

          if (hospitalMarkerRef.current) {
            hospitalMarkerRef.current.setLatLng([hLat, hLng]);
          } else {
            hospitalMarkerRef.current = L.marker([hLat, hLng], { icon: hospitalIcon })
              .addTo(mapInstance.current)
              .bindPopup(`<b>Destination Hospital</b><br>${emergency?.hospitalName || 'Hospital'}`);
          }
        } else {
          if (hospitalMarkerRef.current) {
            mapInstance.current.removeLayer(hospitalMarkerRef.current);
            hospitalMarkerRef.current = null;
          }
        }

        // Update or create ambulance/driver marker
        const hasDriver = emergency?.status === 'assigned' || emergency?.status === 'arrived';
        const driverLat = Number(emergency?.driverLatitude !== undefined ? emergency.driverLatitude : (hasDriver ? lat + 0.005 : 0));
        const driverLng = Number(emergency?.driverLongitude !== undefined ? emergency.driverLongitude : (hasDriver ? lng + 0.005 : 0));

        if (hasDriver && driverLat !== 0 && driverLng !== 0 && !isNaN(driverLat) && !isNaN(driverLng)) {
          const ambulanceIcon = L.divIcon({
            html: `<div style="font-size: 32px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3));">🚑</div>`,
            className: 'ambulance-marker',
            iconSize: [32, 32],
            iconAnchor: [16, 16]
          });

          if (ambulanceMarkerRef.current) {
            ambulanceMarkerRef.current.setLatLng([driverLat, driverLng]);
          } else {
            ambulanceMarkerRef.current = L.marker([driverLat, driverLng], { icon: ambulanceIcon })
              .addTo(mapInstance.current)
              .bindPopup(`<b>Ambulance</b><br>${emergency?.driverName || 'Driver'}`);
          }

          // Update route polyline
          if (routePath.length > 0) {
            if (routePolylineRef.current) {
              routePolylineRef.current.setLatLngs(routePath);
            } else {
              routePolylineRef.current = L.polyline(routePath, {
                color: '#0066FF',
                weight: 5,
                opacity: 0.85,
                lineCap: 'round',
                lineJoin: 'round'
              }).addTo(mapInstance.current);
            }
          } else {
            // Fallback straight line
            const fallbackPath = [[driverLat, driverLng], [lat, lng]];
            if (hLat !== 0 && hLng !== 0) {
              fallbackPath.push([hLat, hLng]);
            }
            if (routePolylineRef.current) {
              routePolylineRef.current.setLatLngs(fallbackPath);
            } else {
              routePolylineRef.current = L.polyline(fallbackPath, {
                color: '#0066FF',
                weight: 5,
                opacity: 0.85,
                dashArray: '5, 10'
              }).addTo(mapInstance.current);
            }
          }

          // Fit map bounds to show markers and route
          const bounds = [];
          if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) bounds.push([lat, lng]);
          if (driverLat >= -90 && driverLat <= 90 && driverLng >= -180 && driverLng <= 180) bounds.push([driverLat, driverLng]);
          if (hLat >= -90 && hLat <= 90 && hLng >= -180 && hLng <= 180 && hLat !== 0 && hLng !== 0) {
            bounds.push([hLat, hLng]);
          }
          if (bounds.length > 0) {
            mapInstance.current.fitBounds(bounds, { padding: [50, 50] });
          }
        } else {
          // Remove ambulance marker and route line if not assigned
          if (ambulanceMarkerRef.current) {
            mapInstance.current.removeLayer(ambulanceMarkerRef.current);
            ambulanceMarkerRef.current = null;
          }
          if (routePolylineRef.current) {
            mapInstance.current.removeLayer(routePolylineRef.current);
            routePolylineRef.current = null;
          }
          mapInstance.current.setView([lat, lng], 14);
        }
      }
    } catch (e) {
      console.error("Leaflet update error in TrackAmbulance component:", e);
    }
  }, [loading, emergency, error, routePath]);

  // 4. Cleanup Map on unmount
  useEffect(() => {
    return () => {
      if (mapInstance.current) {
        mapInstance.current.remove();
        mapInstance.current = null;
      }
      patientMarkerRef.current = null;
      ambulanceMarkerRef.current = null;
      hospitalMarkerRef.current = null;
      routePolylineRef.current = null;
    };
  }, []);

  const handleCancel = async () => {
    if (window.confirm('Are you sure you want to cancel this emergency request?')) {
      try {
        await updateEmergencyStatus(id, 'cancelled');
        showToast('Emergency request cancelled successfully.', 'success');
      } catch (err) {
        showToast('Failed to cancel request: ' + (err.response?.data?.error || err.message), 'error');
      }
    }
  };

  const getStatusDisplay = () => {
    switch (emergency.status) {
      case 'pending':
        return {
          title: 'Waiting for Dispatch',
          description: 'Emergency coordinates and details have been shared. Nearest available ambulance will be assigned shortly.',
          badgeClass: 'badge-warning',
          color: 'var(--accent-yellow)'
        };
      case 'assigned':
        return {
          title: 'Ambulance En Route',
          description: `Driver ${emergency.driverName || 'assigned'} has accepted your emergency and is driving to your location.`,
          badgeClass: 'btn-primary',
          color: 'var(--primary)'
        };
      case 'arrived':
        return {
          title: 'Ambulance Arrived',
          description: 'The response team has arrived at your location.',
          badgeClass: 'btn-outline',
          color: 'var(--accent-green)'
        };
      case 'completed':
        return {
          title: 'Emergency Resolved',
          description: 'The emergency case has been closed successfully.',
          badgeClass: 'badge-success',
          color: 'var(--accent-green)'
        };
      case 'cancelled':
        return {
          title: 'Emergency Cancelled',
          description: 'This emergency request was cancelled.',
          badgeClass: 'badge-danger',
          color: 'var(--accent-red)'
        };
      default:
        return {
          title: 'Unknown State',
          description: '',
          badgeClass: '',
          color: 'gray'
        };
    }
  };

  if (loading) {
    return (
      <div className="container mt-4 text-center">
        <div className="card">
          <p>Connecting to dispatch system...</p>
        </div>
      </div>
    );
  }

  if (error || !emergency) {
    return (
      <div className="container mt-4">
        <div className="card text-center" style={{ padding: '40px' }}>
          <span style={{ fontSize: '48px' }}>⚠️</span>
          <h3 className="mt-2">{error || 'Emergency not found'}</h3>
          <Link to="/user/dashboard" className="btn btn-primary mt-3">Return to Dashboard</Link>
        </div>
      </div>
    );
  }

  const statusInfo = getStatusDisplay();

  return (
    <div className="container mt-4">
      {toast && (
        <div style={{
          position: 'fixed',
          top: '24px',
          right: '24px',
          backgroundColor: toast.type === 'error' ? 'var(--accent-red)' : 'var(--accent-green)',
          color: '#fff',
          padding: '12px 24px',
          borderRadius: 'var(--radius-md)',
          boxShadow: 'var(--shadow-lg)',
          zIndex: 9999,
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          fontWeight: 500,
          animation: 'fadeIn 0.3s ease-out'
        }} data-testid="toast-notification">
          {toast.type === 'error' ? '❌' : '✅'} {toast.message}
        </div>
      )}
      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        {/* Tracking Map Card */}
        <div style={{ flex: '1 1 600px' }}>
          <div className="card" style={{ padding: '0', overflow: 'hidden', height: '500px', position: 'relative' }}>
            {routingLoading && (
              <div style={{ position: 'absolute', top: '10px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000, background: 'rgba(255,255,255,0.95)', padding: '6px 16px', borderRadius: 'var(--radius-full)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 500, border: '1px solid var(--border)', boxShadow: 'var(--shadow-sm)' }}>
                <span className="spinner" style={{ width: '12px', height: '12px', border: '2px solid rgba(0,0,0,0.1)', borderTop: '2px solid var(--primary)' }}></span>
                Calculating live route...
              </div>
            )}
            <div ref={mapRef} id="map" style={{ width: '100%', height: '100%', zIndex: 1 }} />
          </div>
        </div>

        {/* Info panel */}
        <div style={{ flex: '1 1 350px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div className="card glass-panel" style={{ borderLeft: `6px solid ${statusInfo.color}` }}>
            <span className={`badge ${statusInfo.badgeClass || ''} mb-2`} style={{ display: 'inline-block', boxShadow: 'none', pointerEvents: 'none' }} data-testid="status-badge">
              {(emergency?.status || 'unknown').toUpperCase()}
            </span>
            <h3>{statusInfo.title}</h3>
            <p className="text-muted mt-1" style={{ fontSize: '14px' }}>{statusInfo.description}</p>
          </div>

          {/* Real-time routing telemetry */}
          {routeDetails && (
            <div className="card" style={{ borderLeft: '5px solid var(--primary)', background: 'var(--primary-light)' }}>
              <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary-hover)' }}>
                ⏱️ Telemetry & ETA
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginTop: '12px' }}>
                <div>
                  <span className="text-muted" style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Remaining Distance</span>
                  <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-main)' }}>
                    {Number(routeDetails?.distanceKm || 0).toFixed(1)} km
                  </div>
                </div>
                <div>
                  <span className="text-muted" style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Estimated Time</span>
                  <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-main)' }}>
                    {Math.ceil(Number(routeDetails?.durationSec || 0) / 60)} mins
                  </div>
                </div>
              </div>

              <div style={{ marginTop: '12px', borderTop: '1px solid rgba(0, 102, 255, 0.1)', paddingTop: '12px' }}>
                <span className="text-muted" style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Traffic Congestion</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '4px' }}>
                  <span
                    className={`badge ${
                      routeDetails?.traffic?.status === 'Normal'
                        ? 'badge-success'
                        : routeDetails?.traffic?.status === 'Moderate'
                        ? 'badge-warning'
                        : 'badge-danger'
                    }`}
                    style={{ fontSize: '11px', padding: '2px 8px' }}
                  >
                    {(routeDetails?.traffic?.status || 'Normal').toUpperCase()}
                  </span>
                  <span style={{ fontSize: '12px', color: 'var(--text-main)', fontWeight: 500 }}>
                    {routeDetails?.traffic?.message || ''}
                  </span>
                </div>
              </div>
            </div>
          )}

          <div className="card">
            <h4>Request Details</h4>
            <div style={{ fontSize: '14px', marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div><strong>Patient Name:</strong> {emergency?.patientName || 'Unknown Patient'}</div>
              <div><strong>Severity/Type:</strong> {(emergency?.emergencyType || 'other').toUpperCase()}</div>
              <div><strong>Description:</strong> {emergency?.description || 'No description provided'}</div>
              <div>
                <strong>Coordinates:</strong> {Number(emergency?.latitude || 0).toFixed(6)}, {Number(emergency?.longitude || 0).toFixed(6)}
              </div>
              {emergency?.hospitalName && (
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: '8px', marginTop: '8px' }}>
                  <strong>Destination Hospital:</strong>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '4px', color: 'var(--primary-hover)', fontWeight: 600 }}>
                    🏥 {emergency.hospitalName}
                  </div>
                </div>
              )}
            </div>
          </div>

          {emergency?.status === 'assigned' && (
            <div className="card" style={{ backgroundColor: 'var(--primary-light)', borderColor: 'var(--primary)' }}>
              <h4>Ambulance Team</h4>
              <div style={{ fontSize: '14px', marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div><strong>Driver Name:</strong> {emergency?.driverName || 'Responder'}</div>
                <div><strong>Contact Number:</strong> {emergency?.driverPhone || 'N/A'}</div>
              </div>
            </div>
          )}

          <div style={{ display: 'flex', gap: '12px', width: '100%' }}>
            {(emergency.status === 'pending' || emergency.status === 'assigned') && (
              <button onClick={handleCancel} className="btn btn-outline" style={{ flex: 1, borderColor: 'var(--accent-red)', color: 'var(--accent-red)' }} data-testid="cancel-request-btn">
                Cancel Request
              </button>
            )}
            <Link to="/user/dashboard" className="btn btn-primary" style={{ flex: 1 }}>
              Dashboard
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TrackAmbulance;
