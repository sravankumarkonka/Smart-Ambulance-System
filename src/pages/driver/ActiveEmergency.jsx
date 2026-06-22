import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { subscribeToEmergency, updateEmergencyStatus, releaseEmergency, updateDriverLocation } from '../../services/firestoreService';
import { fetchRoute } from '../../services/routingService';
import { useAuth } from '../../context/AuthContext';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const ActiveEmergency = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  
  const [emergency, setEmergency] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [zoomImageUrl, setZoomImageUrl] = useState(null);

  const [driverLat, setDriverLat] = useState(null);
  const [driverLng, setDriverLng] = useState(null);

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
        const lat = Number(data.latitude || 12.9716);
        const lng = Number(data.longitude || 77.5946);
        setDriverLat((prev) => {
          if (prev !== null) return prev;
          return Number(data.driverLatitude !== undefined ? data.driverLatitude : lat + 0.004);
        });
        setDriverLng((prev) => {
          if (prev !== null) return prev;
          return Number(data.driverLongitude !== undefined ? data.driverLongitude : lng + 0.004);
        });
      } else {
        setError('Emergency not found');
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, [id]);

  // 2. Real-time GPS Tracker & Simulator loop (updates every 5 seconds)
  useEffect(() => {
    if (loading || !emergency || error || !currentUser || driverLat === null || driverLng === null) return;

    let currentLat = Number(driverLat || 12.9716);
    let currentLng = Number(driverLng || 77.5946);

    const publishLocation = async (lat, lng) => {
      setDriverLat(lat);
      setDriverLng(lng);
      try {
        await updateDriverLocation(currentUser.uid, lat, lng, id);
      } catch (err) {
        console.error('Failed to update driver coordinates in Firestore:', err);
      }
    };

    // Initial update scheduled asynchronously to avoid synchronous setState inside render/effect body
    const timeoutId = setTimeout(() => {
      publishLocation(currentLat, currentLng);
    }, 0);

    // Geolocation Watcher/Timer
    const intervalId = setInterval(() => {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            // Use real GPS
            publishLocation(position.coords.latitude, position.coords.longitude);
          },
          (_geoErr) => {
            // Geolocation blocked or failed: Run movement simulator toward target
            const eLat = Number(emergency.latitude || 12.9716);
            const eLng = Number(emergency.longitude || 77.5946);
            const destLat = Number(emergency.status === 'assigned' ? eLat : (emergency.hospitalLatitude || eLat));
            const destLng = Number(emergency.status === 'assigned' ? eLng : (emergency.hospitalLongitude || eLng));

            const step = 0.0004; // Simulated speed step
            const diffLat = destLat - currentLat;
            const diffLng = destLng - currentLng;
            const distance = Math.sqrt(diffLat * diffLat + diffLng * diffLng);

            if (distance > step) {
              currentLat += (diffLat / distance) * step;
              currentLng += (diffLng / distance) * step;
              publishLocation(currentLat, currentLng);
            } else {
              publishLocation(destLat, destLng);
            }
          },
          { enableHighAccuracy: true }
        );
      }
    }, 5000);

    return () => {
      clearInterval(intervalId);
      clearTimeout(timeoutId);
    };
  }, [loading, emergency, error, currentUser, id, driverLat, driverLng]);

  // 3. Dynamic route re-calculation (every 30 seconds)
  useEffect(() => {
    if (loading || !emergency || error || !driverLat || !driverLng) return;

    const calculateDirections = async () => {
      setRoutingLoading(true);
      try {
        let waypoints = [];
        const eLat = Number(emergency.latitude || 12.9716);
        const eLng = Number(emergency.longitude || 77.5946);
        const hLat = Number(emergency.hospitalLatitude || 0);
        const hLng = Number(emergency.hospitalLongitude || 0);

        if (emergency.status === 'assigned') {
          // Route: Driver -> Patient -> Hospital
          waypoints = [[driverLat, driverLng], [eLat, eLng]];
          if (hLat !== 0 && hLng !== 0) {
            waypoints.push([hLat, hLng]);
          }
        } else {
          // Arrived at patient. Route: Patient/Driver -> Hospital
          if (hLat !== 0 && hLng !== 0) {
            waypoints = [[driverLat, driverLng], [hLat, hLng]];
          } else {
            waypoints = [[driverLat, driverLng], [eLat, eLng]];
          }
        }

        const data = await fetchRoute(waypoints);
        setRoutePath(data?.coordinates || []);
        setRouteDetails(data);
      } catch (err) {
        console.error('Failed to generate dynamic directions:', err);
      } finally {
        setRoutingLoading(false);
      }
    };

    calculateDirections();

    const routeInterval = setInterval(calculateDirections, 30000);
    return () => clearInterval(routeInterval);
  }, [loading, error, emergency, driverLat, driverLng]);

  // 4. Map rendering and updates
  useEffect(() => {
    if (loading || !emergency || error || !driverLat || !driverLng) return;

    const lat = Number(emergency.latitude || 12.9716);
    const lng = Number(emergency.longitude || 77.5946);
    const hLat = Number(emergency.hospitalLatitude || 0);
    const hLng = Number(emergency.hospitalLongitude || 0);

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
        // Update patient marker
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
            .bindPopup(`<b>Patient:</b> ${emergency.patientName || 'Unknown Patient'}`);
        }

        // Update hospital marker
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
              .bindPopup(`<b>Hospital:</b> ${emergency.hospitalName || 'Hospital'}`);
          }
        }

        // Update driver/ambulance marker
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
            .bindPopup('Your Ambulance Location');
        }

        // Draw route line
        if (routePath.length > 0) {
          if (routePolylineRef.current) {
            routePolylineRef.current.setLatLngs(routePath);
          } else {
            routePolylineRef.current = L.polyline(routePath, {
              color: '#FF3B30',
              weight: 6,
              opacity: 0.85,
              lineCap: 'round',
              lineJoin: 'round'
            }).addTo(mapInstance.current);
          }
        } else {
          const straightLine = [[driverLat, driverLng], [lat, lng]];
          if (hLat !== 0 && hLng !== 0) {
            straightLine.push([hLat, hLng]);
          }
          if (routePolylineRef.current) {
            routePolylineRef.current.setLatLngs(straightLine);
          } else {
            routePolylineRef.current = L.polyline(straightLine, {
              color: '#FF3B30',
              weight: 4,
              opacity: 0.85,
              dashArray: '5, 10'
            }).addTo(mapInstance.current);
          }
        }

        // Center map around route bounds
        const bounds = [];
        if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) bounds.push([lat, lng]);
        if (driverLat >= -90 && driverLat <= 90 && driverLng >= -180 && driverLng <= 180) bounds.push([driverLat, driverLng]);
        if (hLat >= -90 && hLat <= 90 && hLng >= -180 && hLng <= 180 && hLat !== 0 && hLng !== 0) {
          bounds.push([hLat, hLng]);
        }
        if (bounds.length > 0) {
          mapInstance.current.fitBounds(bounds, { padding: [50, 50] });
        }
      }
    } catch (e) {
      console.error("Leaflet rendering error in ActiveEmergency:", e);
    }
  }, [loading, emergency, error, driverLat, driverLng, routePath]);

  // 5. Cleanup Map on unmount
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

  const handleUpdateStatus = async (newStatus) => {
    setActionLoading(true);
    try {
      await updateEmergencyStatus(id, newStatus);
      if (newStatus === 'completed') {
        alert('Emergency request completed successfully.');
        navigate('/driver/dashboard');
      }
    } catch (err) {
      alert('Error updating status: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleRelease = async () => {
    if (window.confirm('Are you sure you want to release this emergency? It will return to the pending pool.')) {
      setActionLoading(true);
      try {
        await releaseEmergency(id, currentUser.uid);
        navigate('/driver/dashboard');
      } catch (err) {
        alert('Error releasing emergency: ' + err.message);
      } finally {
        setActionLoading(false);
      }
    }
  };

  if (loading) {
    return (
      <div className="container mt-4 text-center">
        <div className="card">
          <p>Loading active dispatch route...</p>
        </div>
      </div>
    );
  }

  if (error || !emergency) {
    return (
      <div className="container mt-4">
        <div className="card text-center" style={{ padding: '40px' }}>
          <h3>⚠️ {error || 'Emergency not found'}</h3>
          <Link to="/driver/dashboard" className="btn btn-primary mt-3">Go to Dashboard</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-4">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <div>
          <h2>Active Route Guide</h2>
          <p className="text-muted">Navigate to the patient and update dispatcher in real-time.</p>
        </div>
        <span className="badge badge-success" style={{ padding: '8px 16px' }} data-testid="active-duty-badge">Active Duty</span>
      </div>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        {/* Navigation Map */}
        <div style={{ flex: '1 1 600px' }}>
          <div className="card" style={{ padding: '0', overflow: 'hidden', height: '480px', position: 'relative' }}>
            {routingLoading && (
              <div style={{ position: 'absolute', top: '10px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000, background: 'rgba(255,255,255,0.95)', padding: '6px 16px', borderRadius: 'var(--radius-full)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 500, border: '1px solid var(--border)', boxShadow: 'var(--shadow-sm)' }}>
                <span className="spinner" style={{ width: '12px', height: '12px', border: '2px solid rgba(0,0,0,0.1)', borderTop: '2px solid var(--primary)' }}></span>
                Calculating live route...
              </div>
            )}
            <div ref={mapRef} id="map" style={{ width: '100%', height: '100%', zIndex: 1 }} />
          </div>

          {/* Navigation Step Instructions List */}
          {routeDetails && routeDetails.steps && (
            <div className="card mt-4" style={{ maxHeight: '250px', overflowY: 'auto' }}>
              <h4 style={{ marginBottom: '12px', color: 'var(--text-main)' }}>Driving Directions ({routeDetails.source})</h4>
              <ul style={{ paddingLeft: '16px', fontSize: '14px', lineHeight: '2' }}>
                {routeDetails.steps.map((st, sIdx) => (
                  <li key={sIdx} style={{ marginBottom: '6px', borderBottom: '1px solid #f1f5f9', paddingBottom: '4px' }}>
                    {st.instruction} {st.distance > 0 && <span style={{ color: 'var(--text-muted)' }}>({(Number(st?.distance || 0) / 1000).toFixed(2)} km)</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Dispatch Controls & Telemetry */}
        <div style={{ flex: '1 1 350px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
          
          {/* Real-time routing telemetry */}
          {routeDetails && (
            <div className="card" style={{ borderLeft: '5px solid var(--accent-red)', background: 'var(--accent-red-light)' }}>
              <h4 style={{ color: 'var(--accent-red)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                ⏱️ Telemetry & Dispatch ETA
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginTop: '12px' }}>
                <div>
                  <span className="text-muted" style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Distance Remaining</span>
                  <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-main)' }}>
                    {Number(routeDetails?.distanceKm || 0).toFixed(1)} km
                  </div>
                </div>
                <div>
                  <span className="text-muted" style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Estimated ETA</span>
                  <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-main)' }}>
                    {Math.ceil(Number(routeDetails?.durationSec || 0) / 60)} mins
                  </div>
                </div>
              </div>

              <div style={{ marginTop: '12px', borderTop: '1px solid rgba(255, 59, 48, 0.1)', paddingTop: '12px' }}>
                <span className="text-muted" style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Traffic Optimizer Status</span>
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

          <div className="card glass-panel" style={{ borderLeft: '6px solid var(--accent-red)' }}>
            <h3>Dispatch Control Panel</h3>
            <p className="text-muted mt-1">Status: <strong style={{ textTransform: 'uppercase' }}>{(emergency?.status || 'unknown')}</strong></p>

            <div className="mt-3" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {emergency?.status === 'assigned' && (
                <button
                  onClick={() => handleUpdateStatus('arrived')}
                  className="btn btn-primary"
                  style={{ padding: '14px', width: '100%', fontSize: '15px' }}
                  disabled={actionLoading}
                  data-testid="mark-arrived-btn"
                >
                  Update Status
                </button>
              )}

              {emergency?.status === 'arrived' && (
                <button
                  onClick={() => handleUpdateStatus('completed')}
                  className="btn btn-primary"
                  style={{ padding: '14px', width: '100%', fontSize: '15px', backgroundColor: 'var(--accent-green)', boxShadow: '0 4px 12px rgba(52, 199, 89, 0.25)' }}
                  disabled={actionLoading}
                  data-testid="mark-completed-btn"
                >
                  Update Status
                </button>
              )}

              <button
                onClick={handleRelease}
                className="btn btn-outline"
                style={{ padding: '10px', width: '100%', color: 'var(--accent-red)', borderColor: 'var(--accent-red)' }}
                disabled={actionLoading}
                data-testid="release-emergency-btn"
              >
                Reject Assignment
              </button>
            </div>
          </div>

          <div className="card">
            <h4>Patient Bio</h4>
            <div style={{ fontSize: '14px', marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div><strong>Name:</strong> {emergency?.patientName || 'Unknown Patient'}</div>
              <div><strong>Symptom/Type:</strong> {(emergency?.emergencyType || 'other').toUpperCase()}</div>
              {emergency?.severityLevel && (
                <div>
                  <strong>Severity:</strong> <span className={`badge ${emergency.severityLevel === 'critical' || emergency.severityLevel === 'high' ? 'badge-danger' : emergency.severityLevel === 'medium' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '11px', textTransform: 'capitalize' }}>{emergency.severityLevel}</span>
                </div>
              )}
              <div><strong>Description:</strong> {emergency?.description || 'No description provided'}</div>
              <div><strong>Coordinates:</strong> {Number(emergency?.latitude || 0).toFixed(6)}, {Number(emergency?.longitude || 0).toFixed(6)}</div>
              
              {emergency?.hospitalName && (
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: '8px', marginTop: '8px' }}>
                  <strong>Destination Hospital:</strong>
                  <div style={{ color: 'var(--primary)', fontWeight: 600, fontSize: '14px', marginTop: '4px' }}>
                    🏥 {emergency.hospitalName}
                  </div>
                </div>
              )}

              {emergency?.imageUrl && (
                <div style={{ marginTop: '12px' }}>
                  <strong>Accident Evidence Image:</strong>
                  <img
                    src={emergency.imageUrl}
                    alt="Accident Evidence"
                    style={{
                      width: '100%',
                      maxHeight: '180px',
                      objectFit: 'cover',
                      borderRadius: 'var(--radius-md)',
                      marginTop: '6px',
                      cursor: 'zoom-in',
                      border: '1px solid var(--border)'
                    }}
                    onClick={() => setZoomImageUrl(emergency.imageUrl)}
                    data-testid="accident-image-preview"
                  />
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

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

export default ActiveEmergency;
