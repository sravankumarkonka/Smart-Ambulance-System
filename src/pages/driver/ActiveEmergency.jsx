import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { subscribeToEmergency, updateEmergencyStatus, releaseEmergency, updateDriverLocation } from '../../services/firestoreService';
import { fetchRoute, geocodeLocation } from '../../services/routingService';
import { openGoogleMapsNavigation, getPlaceDetails } from '../../services/googleMapsService';
import { useAuth } from '../../context/AuthContext';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const lerp = (a, b, t) => a + (b - a) * t;

const STATUS_STEPS = [
  { key: 'assigned',        label: 'Assigned',          icon: '✅', action: 'on_the_way',       actionLabel: '🚑 En Route (On The Way)',    color: '#0288D1' },
  { key: 'on_the_way',      label: 'En Route',           icon: '🚑', action: 'reached',           actionLabel: '📍 Reached Incident Scene',  color: '#F57C00' },
  { key: 'reached',         label: 'At Scene',           icon: '📍', action: 'patient_picked',    actionLabel: '👨‍⚕️ Patient Picked Up',       color: '#7B1FA2' },
  { key: 'patient_picked',  label: 'Patient Picked',     icon: '👨‍⚕️', action: 'hospital_reached', actionLabel: '🏥 Reached Hospital',         color: '#388E3C' },
  { key: 'hospital_reached',label: 'At Hospital',        icon: '🏥', action: 'completed',         actionLabel: '✅ Complete Dispatch',         color: '#2E7D32' },
  { key: 'completed',       label: 'Completed',          icon: '🎉', action: null,                actionLabel: null,                          color: '#16a34a' },
];

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
  const [patientAddress, setPatientAddress] = useState('');
  const [hospitalDetails, setHospitalDetails] = useState(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [lastGpsUpdate, setLastGpsUpdate] = useState(null);

  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const patientMarkerRef = useRef(null);
  const ambulanceMarkerRef = useRef(null);
  const hospitalMarkerRef = useRef(null);
  const routePolylineRef = useRef(null);
  const prevAmbPos = useRef(null);

  // ─── Subscribe to emergency ─────────────────────────────────────────────
  useEffect(() => {
    const unsubscribe = subscribeToEmergency(id, (data) => {
      if (data) {
        setEmergency(data);
        const lat = Number(data.latitude || 12.9716);
        const lng = Number(data.longitude || 77.5946);
        setDriverLat(prev => prev !== null ? prev : Number(data.driverLatitude !== undefined ? data.driverLatitude : lat + 0.004));
        setDriverLng(prev => prev !== null ? prev : Number(data.driverLongitude !== undefined ? data.driverLongitude : lng + 0.004));

        const stepIdx = STATUS_STEPS.findIndex(s => s.key === data.status);
        if (stepIdx >= 0) setCurrentStep(stepIdx);
      } else {
        setError('Emergency not found');
      }
      setLoading(false);
    });
    return () => unsubscribe();
  }, [id]);

  // ─── Reverse geocode patient address ───────────────────────────────────
  useEffect(() => {
    if (!emergency?.latitude || !emergency?.longitude) return;
    geocodeLocation(emergency.latitude, emergency.longitude).then(addr => {
      if (addr) setPatientAddress(addr);
    });
  }, [emergency?.latitude, emergency?.longitude]);

  // ─── Fetch hospital details ─────────────────────────────────────────────
  useEffect(() => {
    if (!emergency?.hospitalPlaceId) return;
    getPlaceDetails(emergency.hospitalPlaceId).then(details => {
      if (details) setHospitalDetails(details);
    });
  }, [emergency?.hospitalPlaceId]);

  // ─── Real-time GPS update loop ─────────────────────────────────────────
  useEffect(() => {
    if (loading || !emergency || error || !currentUser || driverLat === null || driverLng === null) return;
    let currentLat = Number(driverLat || 12.9716);
    let currentLng = Number(driverLng || 77.5946);

    const publishLocation = async (lat, lng) => {
      setDriverLat(lat);
      setDriverLng(lng);
      setLastGpsUpdate(new Date());
      try { await updateDriverLocation(currentUser.uid, lat, lng, id); } catch (err) { console.error('GPS update failed:', err); }
    };

    const timeoutId = setTimeout(() => publishLocation(currentLat, currentLng), 0);

    const intervalId = setInterval(() => {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => publishLocation(position.coords.latitude, position.coords.longitude),
          (_geoErr) => {
            const eLat = Number(emergency.latitude || 12.9716);
            const eLng = Number(emergency.longitude || 77.5946);
            const destLat = Number(emergency.status === 'assigned' || emergency.status === 'on_the_way' ? eLat : (emergency.hospitalLatitude || eLat));
            const destLng = Number(emergency.status === 'assigned' || emergency.status === 'on_the_way' ? eLng : (emergency.hospitalLongitude || eLng));
            const step = 0.0004;
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

    return () => { clearInterval(intervalId); clearTimeout(timeoutId); };
  }, [loading, emergency, error, currentUser, id, driverLat, driverLng]);

  // ─── Route calculation ──────────────────────────────────────────────────
  useEffect(() => {
    if (loading || !emergency || error || !driverLat || !driverLng) return;

    const calculateDirections = async () => {
      setRoutingLoading(true);
      try {
        const eLat = Number(emergency.latitude || 12.9716);
        const eLng = Number(emergency.longitude || 77.5946);
        const hLat = Number(emergency.hospitalLatitude || 0);
        const hLng = Number(emergency.hospitalLongitude || 0);
        let waypoints = [];

        if (['assigned', 'on_the_way'].includes(emergency.status)) {
          waypoints = [[driverLat, driverLng], [eLat, eLng]];
          if (hLat !== 0 && hLng !== 0) waypoints.push([hLat, hLng]);
        } else if (hLat !== 0 && hLng !== 0) {
          waypoints = [[driverLat, driverLng], [hLat, hLng]];
        } else {
          waypoints = [[driverLat, driverLng], [eLat, eLng]];
        }

        const data = await fetchRoute(waypoints);
        setRoutePath(data?.coordinates || []);
        setRouteDetails(data);
      } catch (err) {
        console.error('Route calculation error:', err);
      } finally {
        setRoutingLoading(false);
      }
    };

    calculateDirections();
    const routeInterval = setInterval(calculateDirections, 30000);
    return () => clearInterval(routeInterval);
  }, [loading, error, emergency, driverLat, driverLng]);

  // ─── Map rendering ──────────────────────────────────────────────────────
  useEffect(() => {
    if (loading || !emergency || error || !driverLat || !driverLng) return;
    const lat = Number(emergency.latitude || 12.9716);
    const lng = Number(emergency.longitude || 77.5946);
    const hLat = Number(emergency.hospitalLatitude || 0);
    const hLng = Number(emergency.hospitalLongitude || 0);

    try {
      if (!mapInstance.current && mapRef.current) {
        if (mapRef.current._leaflet_id) delete mapRef.current._leaflet_id;
        mapInstance.current = L.map(mapRef.current, { zoomControl: true }).setView([lat, lng], 14);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap', maxZoom: 19
        }).addTo(mapInstance.current);
      }

      if (mapInstance.current) {
        // Patient
        const patientIcon = L.divIcon({
          html: `<div style="background:white;border-radius:50%;padding:4px;box-shadow:0 2px 8px rgba(220,38,38,0.5);border:3px solid #dc2626;"><span style="font-size:20px;display:block;line-height:1;">📍</span></div>`,
          className: '', iconSize: [34, 34], iconAnchor: [17, 34]
        });
        if (patientMarkerRef.current) patientMarkerRef.current.setLatLng([lat, lng]);
        else {
          patientMarkerRef.current = L.marker([lat, lng], { icon: patientIcon })
            .addTo(mapInstance.current)
            .bindPopup(`<b>Patient: ${emergency.patientName || 'Unknown'}</b><br><small>${patientAddress}</small>`);
        }

        // Hospital
        if (hLat !== 0 && hLng !== 0) {
          const hospitalIcon = L.divIcon({
            html: `<div style="background:white;border-radius:50%;padding:4px;box-shadow:0 2px 8px rgba(16,185,129,0.5);border:3px solid #10b981;"><span style="font-size:20px;display:block;line-height:1;">🏥</span></div>`,
            className: '', iconSize: [34, 34], iconAnchor: [17, 17]
          });
          if (hospitalMarkerRef.current) hospitalMarkerRef.current.setLatLng([hLat, hLng]);
          else {
            hospitalMarkerRef.current = L.marker([hLat, hLng], { icon: hospitalIcon })
              .addTo(mapInstance.current)
              .bindPopup(`<b>🏥 ${emergency.hospitalName || 'Hospital'}</b><br>${hospitalDetails?.phone || ''}`);
          }
        }

        // Ambulance with smooth animation
        const ambulanceIcon = L.divIcon({
          html: `<div style="background:linear-gradient(135deg,#dc2626,#b91c1c);border-radius:50%;padding:6px;box-shadow:0 3px 12px rgba(220,38,38,0.5);"><span style="font-size:22px;display:block;line-height:1;">🚑</span></div>`,
          className: '', iconSize: [40, 40], iconAnchor: [20, 20]
        });

        if (ambulanceMarkerRef.current) {
          const startPos = prevAmbPos.current || [driverLat, driverLng];
          let startTime = null;
          const duration = 1200;
          const animate = (ts) => {
            if (!startTime) startTime = ts;
            const progress = Math.min((ts - startTime) / duration, 1);
            if (ambulanceMarkerRef.current)
              ambulanceMarkerRef.current.setLatLng([lerp(startPos[0], driverLat, progress), lerp(startPos[1], driverLng, progress)]);
            if (progress < 1) requestAnimationFrame(animate);
          };
          requestAnimationFrame(animate);
        } else {
          ambulanceMarkerRef.current = L.marker([driverLat, driverLng], { icon: ambulanceIcon })
            .addTo(mapInstance.current)
            .bindPopup('🚑 Your Ambulance');
        }
        prevAmbPos.current = [driverLat, driverLng];

        // Route polyline
        const polylineData = routePath.length > 0 ? routePath : [[driverLat, driverLng], [lat, lng]];
        if (routePolylineRef.current) routePolylineRef.current.setLatLngs(polylineData);
        else {
          routePolylineRef.current = L.polyline(polylineData, {
            color: '#FF3B30', weight: 6, opacity: 0.85, lineCap: 'round', lineJoin: 'round',
            dashArray: routePath.length === 0 ? '5,10' : null
          }).addTo(mapInstance.current);
        }

        // Fit bounds
        const bounds = [[lat, lng], [driverLat, driverLng]];
        if (hLat !== 0 && hLng !== 0) bounds.push([hLat, hLng]);
        mapInstance.current.fitBounds(bounds, { padding: [50, 50] });
      }
    } catch (e) { console.error('Map error:', e); }
  }, [loading, emergency, error, driverLat, driverLng, routePath, patientAddress, hospitalDetails]);

  // ─── Map cleanup ─────────────────────────────────────────────────────────
  useEffect(() => () => {
    if (mapInstance.current) { mapInstance.current.remove(); mapInstance.current = null; }
  }, []);

  const handleUpdateStatus = async (newStatus) => {
    setActionLoading(true);
    try {
      await updateEmergencyStatus(id, newStatus);
      if (newStatus === 'completed') { alert('Emergency completed successfully.'); navigate('/driver/dashboard'); }
    } catch (err) {
      alert('Error updating status: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleRelease = async () => {
    if (window.confirm('Release this emergency back to the pending pool?')) {
      setActionLoading(true);
      try {
        await releaseEmergency(id, currentUser.uid);
        navigate('/driver/dashboard');
      } catch (err) { alert('Error releasing: ' + err.message); }
      finally { setActionLoading(false); }
    }
  };

  const navigateToPatient = () => emergency?.latitude && emergency?.longitude &&
    openGoogleMapsNavigation(emergency.latitude, emergency.longitude, emergency.patientName);
  const navigateToHospital = () => emergency?.hospitalLatitude && emergency?.hospitalLongitude &&
    openGoogleMapsNavigation(emergency.hospitalLatitude, emergency.hospitalLongitude, emergency.hospitalName);

  const currentStepData = STATUS_STEPS.find(s => s.key === emergency?.status) || STATUS_STEPS[0];
  const etaMinutes = routeDetails ? Math.ceil(Number(routeDetails?.durationSec || 0) / 60) : null;
  const distKm = routeDetails ? Number(routeDetails?.distanceKm || 0).toFixed(1) : null;

  if (loading) return (
    <div className="container mt-4 text-center"><div className="card"><p>Loading active dispatch route...</p></div></div>
  );
  if (error || !emergency) return (
    <div className="container mt-4">
      <div className="card text-center" style={{ padding: '40px' }}>
        <h3>⚠️ {error || 'Emergency not found'}</h3>
        <Link to="/driver/dashboard" className="btn btn-primary mt-3">Go to Dashboard</Link>
      </div>
    </div>
  );

  return (
    <div className="container mt-4" style={{ paddingBottom: '40px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ margin: 0 }}>🚑 Active Dispatch Navigation</h2>
          <p className="text-muted" style={{ margin: 0, fontSize: '14px' }}>Navigate in real-time and update status at each milestone.</p>
        </div>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
          <span className="badge badge-success" style={{ padding: '8px 16px' }} data-testid="active-duty-badge">🟢 Active Duty</span>
          {lastGpsUpdate && (
            <span style={{ fontSize: '11px', color: 'var(--text-muted)', background: '#f1f5f9', padding: '4px 8px', borderRadius: '4px' }}>
              GPS: {lastGpsUpdate.toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>

        {/* Left: Map + Directions */}
        <div style={{ flex: '1 1 580px' }}>
          <div className="card" style={{ padding: 0, overflow: 'hidden', height: '450px', position: 'relative', boxShadow: 'var(--shadow-lg)' }}>
            {routingLoading && (
              <div style={{ position: 'absolute', top: '12px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000,
                background: 'rgba(255,255,255,0.97)', padding: '8px 18px', borderRadius: 'var(--radius-full)',
                display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 500,
                border: '1px solid var(--border)', boxShadow: 'var(--shadow-sm)' }}>
                <span className="spinner" style={{ width: '12px', height: '12px', border: '2px solid rgba(0,0,0,0.1)', borderTop: '2px solid #ff3b30' }}></span>
                Getting Google Directions...
              </div>
            )}
            <div ref={mapRef} id="driver-map" style={{ width: '100%', height: '100%', zIndex: 1 }} />

            {/* One-click nav buttons overlay */}
            <div style={{ position: 'absolute', bottom: '12px', right: '12px', zIndex: 1000, display: 'flex', gap: '8px' }}>
              <button onClick={navigateToPatient}
                style={{ background: '#dc2626', color: 'white', border: 'none', padding: '10px 14px',
                  borderRadius: 'var(--radius-md)', cursor: 'pointer', fontWeight: 700, fontSize: '12px',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.3)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                🧭 Patient
              </button>
              {emergency?.hospitalLatitude && (
                <button onClick={navigateToHospital}
                  style={{ background: '#10b981', color: 'white', border: 'none', padding: '10px 14px',
                    borderRadius: 'var(--radius-md)', cursor: 'pointer', fontWeight: 700, fontSize: '12px',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.3)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  🏥 Hospital
                </button>
              )}
            </div>
          </div>

          {/* Turn-by-turn directions */}
          {routeDetails?.steps && routeDetails.steps.length > 0 && (
            <div className="card mt-3" style={{ maxHeight: '220px', overflowY: 'auto' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                <h4 style={{ margin: 0 }}>🗺️ Turn-by-Turn Directions</h4>
                {routeDetails.source === 'Google Directions API' && (
                  <span style={{ fontSize: '10px', background: '#1a73e8', color: 'white', padding: '2px 6px', borderRadius: '4px', fontWeight: 700 }}>
                    GOOGLE MAPS
                  </span>
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {routeDetails.steps.map((step, idx) => (
                  <div key={idx} style={{
                    display: 'flex', gap: '12px', alignItems: 'flex-start', padding: '10px',
                    background: idx === 0 ? '#fef3c7' : '#f8fafc', borderRadius: 'var(--radius-sm)',
                    borderLeft: idx === 0 ? '3px solid #f59e0b' : '3px solid #e2e8f0'
                  }}>
                    <div style={{ width: '24px', height: '24px', background: idx === 0 ? '#f59e0b' : '#e2e8f0',
                      borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '11px', fontWeight: 700, color: idx === 0 ? 'white' : '#64748b', flexShrink: 0 }}>
                      {idx + 1}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '13px', fontWeight: idx === 0 ? 600 : 400, color: 'var(--text-main)' }}>
                        {step.instruction}
                      </div>
                      {step.distanceText && (
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                          {step.distanceText} · {step.durationText}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right: Controls */}
        <div style={{ flex: '1 1 340px', display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* ETA Panel */}
          {routeDetails && (
            <div className="card" style={{ borderLeft: '5px solid var(--accent-red)', background: '#fff5f5', padding: '16px' }}>
              <h4 style={{ color: 'var(--accent-red)', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                ⏱️ Live Dispatch ETA
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div style={{ textAlign: 'center', padding: '10px', background: 'white', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>Distance</div>
                  <div style={{ fontSize: '22px', fontWeight: 800, color: 'var(--accent-red)' }}>{distKm} km</div>
                </div>
                <div style={{ textAlign: 'center', padding: '10px', background: 'white', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>ETA</div>
                  <div style={{ fontSize: '22px', fontWeight: 800, color: 'var(--accent-red)' }}>{etaMinutes} min</div>
                </div>
              </div>
              <div style={{ marginTop: '10px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span className={`badge ${routeDetails?.traffic?.status === 'Normal' ? 'badge-success' : routeDetails?.traffic?.status === 'Moderate' ? 'badge-warning' : 'badge-danger'}`}
                  style={{ fontSize: '11px', padding: '3px 8px' }}>
                  {(routeDetails?.traffic?.status || 'Normal').toUpperCase()}
                </span>
                <span style={{ fontSize: '12px' }}>{routeDetails?.traffic?.message || ''}</span>
              </div>
            </div>
          )}

          {/* Dispatch Control */}
          <div className="card glass-panel" style={{ borderLeft: '6px solid var(--accent-red)', padding: '20px' }}>
            <h3 style={{ marginBottom: '6px' }}>Dispatch Control Panel</h3>
            <p className="text-muted" style={{ fontSize: '13px', marginBottom: '16px' }}>
              Status: <strong style={{ textTransform: 'uppercase', color: currentStepData.color }}>{emergency?.status?.replace(/_/g, ' ')}</strong>
            </p>

            {/* Progress steps */}
            <div style={{ display: 'flex', marginBottom: '20px', gap: '4px' }}>
              {STATUS_STEPS.map((step, idx) => (
                <div key={step.key} style={{
                  flex: 1, height: '4px', borderRadius: '4px',
                  background: idx <= currentStep ? step.color : '#e2e8f0',
                  transition: 'background 0.4s ease'
                }} />
              ))}
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {currentStepData.action && (
                <button onClick={() => handleUpdateStatus(currentStepData.action)}
                  className="btn btn-primary"
                  style={{ padding: '14px', width: '100%', fontSize: '15px', fontWeight: 700,
                    background: `linear-gradient(135deg, ${currentStepData.color}, ${currentStepData.color}cc)`,
                    boxShadow: `0 4px 15px ${currentStepData.color}55` }}
                  disabled={actionLoading}>
                  {actionLoading ? '⏳ Updating...' : currentStepData.actionLabel}
                </button>
              )}

              {/* Navigation buttons */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                <button onClick={navigateToPatient}
                  style={{ padding: '12px 10px', borderRadius: 'var(--radius-md)', background: '#dc2626',
                    color: 'white', border: 'none', cursor: 'pointer', fontWeight: 700, fontSize: '13px',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px' }}>
                  🧭 Go to Patient
                </button>
                {emergency?.hospitalLatitude && (
                  <button onClick={navigateToHospital}
                    style={{ padding: '12px 10px', borderRadius: 'var(--radius-md)', background: '#10b981',
                      color: 'white', border: 'none', cursor: 'pointer', fontWeight: 700, fontSize: '13px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px' }}>
                    🏥 Go to Hospital
                  </button>
                )}
              </div>

              <button onClick={handleRelease} className="btn btn-outline"
                style={{ padding: '10px', width: '100%', color: 'var(--accent-red)', borderColor: 'var(--accent-red)', fontSize: '13px' }}
                disabled={actionLoading} data-testid="release-emergency-btn">
                Reject / Release Assignment
              </button>
            </div>
          </div>

          {/* Patient Info Card */}
          <div className="card" style={{ padding: '16px' }}>
            <h4 style={{ marginBottom: '12px' }}>👤 Patient Information</h4>
            <div style={{ fontSize: '14px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div><strong>Name:</strong> {emergency?.patientName || 'Unknown Patient'}</div>
              <div><strong>Type:</strong> {(emergency?.emergencyType || 'other').toUpperCase()}</div>
              {emergency?.severityLevel && (
                <div><strong>Severity:</strong>
                  <span className={`badge ${emergency.severityLevel === 'critical' || emergency.severityLevel === 'high' ? 'badge-danger' : emergency.severityLevel === 'medium' ? 'badge-warning' : 'badge-success'}`}
                    style={{ marginLeft: '6px', fontSize: '11px' }}>
                    {emergency.severityLevel.toUpperCase()}
                  </span>
                </div>
              )}
              <div><strong>Description:</strong> {emergency?.description || 'No description'}</div>
              {patientAddress && (
                <div style={{ background: '#fef2f2', borderRadius: 'var(--radius-sm)', padding: '8px', marginTop: '4px', fontSize: '13px' }}>
                  📍 {patientAddress}
                </div>
              )}

              {emergency?.hospitalName && (
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: '10px', marginTop: '4px' }}>
                  <div style={{ fontWeight: 700, color: '#10b981', marginBottom: '6px' }}>🏥 Destination Hospital</div>
                  <div style={{ fontWeight: 600 }}>{emergency.hospitalName}</div>
                  {hospitalDetails?.address && <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>{hospitalDetails.address}</div>}
                  {hospitalDetails?.phone && (
                    <a href={`tel:${hospitalDetails.phone}`}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', marginTop: '8px',
                        background: '#d1fae5', color: '#065f46', padding: '6px 12px', borderRadius: 'var(--radius-sm)',
                        textDecoration: 'none', fontWeight: 600, fontSize: '13px' }}>
                      📞 Call Hospital
                    </a>
                  )}
                  {hospitalDetails?.rating && (
                    <span style={{ fontSize: '12px', background: '#fef3c7', color: '#92400e', padding: '4px 8px', borderRadius: '4px', fontWeight: 600, display: 'inline-block', marginTop: '6px' }}>
                      ⭐ {hospitalDetails.rating} / 5
                    </span>
                  )}
                </div>
              )}

              {emergency?.imageUrl && (
                <div style={{ marginTop: '10px' }}>
                  <strong>Evidence Image:</strong>
                  <img src={emergency.imageUrl} alt="Evidence"
                    style={{ width: '100%', maxHeight: '160px', objectFit: 'cover', borderRadius: 'var(--radius-md)',
                      marginTop: '6px', cursor: 'zoom-in', border: '1px solid var(--border)' }}
                    onClick={() => setZoomImageUrl(emergency.imageUrl)}
                    data-testid="accident-image-preview" />
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Image Zoom Modal */}
      {zoomImageUrl && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
          backgroundColor: 'rgba(0,0,0,0.8)', display: 'flex', justifyContent: 'center',
          alignItems: 'center', zIndex: 9999, cursor: 'zoom-out' }}
          onClick={() => setZoomImageUrl(null)} data-testid="zoom-modal">
          <div style={{ position: 'relative', maxWidth: '90%', maxHeight: '90%' }}>
            <img src={zoomImageUrl} alt="Zoomed Evidence"
              style={{ maxWidth: '100%', maxHeight: '90vh', objectFit: 'contain',
                borderRadius: 'var(--radius-md)', boxShadow: '0 8px 32px rgba(0,0,0,0.5)' }} />
            <button onClick={() => setZoomImageUrl(null)}
              style={{ position: 'absolute', top: '10px', right: '10px', background: 'white',
                border: 'none', borderRadius: '50%', width: '32px', height: '32px', cursor: 'pointer',
                fontWeight: 'bold', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>✕</button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ActiveEmergency;
