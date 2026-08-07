import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { subscribeToEmergency, updateEmergencyStatus, getEmergency } from '../../services/firestoreService';
import { fetchRoute, geocodeLocation } from '../../services/routingService';
import { openGoogleMapsNavigation, getPlaceDetails } from '../../services/googleMapsService';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Animated polyline easing helper
const lerp = (a, b, t) => a + (b - a) * t;

const STATUS_CONFIG = {
  pending:   { title: 'Waiting for Dispatch', icon: '⏳', color: 'var(--accent-yellow)', badge: 'badge-warning',
               desc: 'Emergency coordinates shared. Nearest ambulance will be assigned shortly.' },
  assigned:  { title: 'Ambulance En Route',   icon: '🚑', color: 'var(--primary)',       badge: 'btn-primary',
               desc: 'Driver has accepted your emergency and is driving to your location.' },
  on_the_way:{ title: 'Ambulance On The Way', icon: '🚑', color: 'var(--primary)',       badge: 'btn-primary',
               desc: 'Your ambulance is navigating to the incident scene.' },
  reached:   { title: 'Reached Scene',        icon: '📍', color: '#f57c00',             badge: 'badge-warning',
               desc: 'Paramedics have reached your location.' },
  arrived:   { title: 'Ambulance Arrived',    icon: '✅', color: 'var(--accent-green)', badge: 'btn-outline',
               desc: 'The response team has arrived at your location.' },
  patient_picked: { title: 'Patient Picked Up', icon: '👨‍⚕️', color: '#7b1fa2',        badge: 'badge-success',
               desc: 'Patient is in the ambulance, en route to hospital.' },
  hospital_reached: { title: 'At Hospital',   icon: '🏥', color: 'var(--accent-green)', badge: 'badge-success',
               desc: 'Ambulance has reached the hospital.' },
  completed: { title: 'Emergency Resolved',   icon: '🎉', color: 'var(--accent-green)', badge: 'badge-success',
               desc: 'The emergency case has been closed successfully.' },
  cancelled: { title: 'Emergency Cancelled',  icon: '❌', color: 'var(--accent-red)',   badge: 'badge-danger',
               desc: 'This emergency request was cancelled.' },
};

const STATUS_TIMELINE = ['pending', 'assigned', 'on_the_way', 'reached', 'patient_picked', 'hospital_reached', 'completed'];

const TrackAmbulance = () => {
  const { id } = useParams();
  const [emergency, setEmergency] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState(null);
  const [routePath, setRoutePath] = useState([]);
  const [routeDetails, setRouteDetails] = useState(null);
  const [routingLoading, setRoutingLoading] = useState(false);
  const [patientAddress, setPatientAddress] = useState('');
  const [hospitalDetails, setHospitalDetails] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);

  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const patientMarkerRef = useRef(null);
  const ambulanceMarkerRef = useRef(null);
  const hospitalMarkerRef = useRef(null);
  const routePolylineRef = useRef(null);
  const prevAmbulancePos = useRef(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  // ─── Subscribe to emergency ─────────────────────────────────────────────
  useEffect(() => {
    if (!id) { setError('No emergency ID provided.'); setLoading(false); return; }
    let isMounted = true;

    getEmergency(id).then((data) => {
      if (isMounted && data) { setEmergency(data); setError(''); setLoading(false); }
    }).catch(err => console.warn('[TrackAmbulance] Initial fetch notice:', err.message));

    const unsubscribe = subscribeToEmergency(id, (data) => {
      if (isMounted) {
        if (data) { setEmergency(data); setError(''); setLastUpdated(new Date()); }
        else {
          getEmergency(id).then(restData => {
            if (isMounted) {
              if (restData) { setEmergency(restData); setError(''); }
              else setError('Emergency request not found.');
            }
          }).catch(() => { if (isMounted && !emergency) setError('Emergency request not found.'); });
        }
        setLoading(false);
      }
    });

    return () => { isMounted = false; if (typeof unsubscribe === 'function') unsubscribe(); };
  }, [id]);

  // ─── Reverse geocode patient address ───────────────────────────────────
  useEffect(() => {
    if (!emergency?.latitude || !emergency?.longitude) return;
    geocodeLocation(emergency.latitude, emergency.longitude)
      .then(addr => { if (addr) setPatientAddress(addr); });
  }, [emergency?.latitude, emergency?.longitude]);

  // ─── Fetch hospital place details ──────────────────────────────────────
  useEffect(() => {
    if (!emergency?.hospitalPlaceId) return;
    getPlaceDetails(emergency.hospitalPlaceId)
      .then(details => { if (details) setHospitalDetails(details); })
      .catch(() => {});
  }, [emergency?.hospitalPlaceId]);

  // ─── Fetch route ────────────────────────────────────────────────────────
  useEffect(() => {
    if (loading || !emergency || error) return;
    const hasDriver = ['assigned', 'on_the_way', 'reached', 'arrived', 'patient_picked'].includes(emergency.status);
    if (!hasDriver) { setRoutePath([]); setRouteDetails(null); return; }

    const lat = Number(emergency?.latitude || 12.9716);
    const lng = Number(emergency?.longitude || 77.5946);
    const hLat = Number(emergency?.hospitalLatitude || 0);
    const hLng = Number(emergency?.hospitalLongitude || 0);
    const driverLat = Number(emergency?.driverLatitude !== undefined ? emergency.driverLatitude : lat + 0.005);
    const driverLng = Number(emergency?.driverLongitude !== undefined ? emergency.driverLongitude : lng + 0.005);

    const updateRoute = async () => {
      setRoutingLoading(true);
      try {
        let waypoints = [];
        if (['assigned', 'on_the_way'].includes(emergency?.status)) {
          waypoints = [[driverLat, driverLng], [lat, lng]];
          if (hLat !== 0 && hLng !== 0) waypoints.push([hLat, hLng]);
        } else {
          waypoints = hLat !== 0 && hLng !== 0
            ? [[driverLat, driverLng], [hLat, hLng]]
            : [[driverLat, driverLng], [lat, lng]];
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
    const intervalId = setInterval(updateRoute, 30000);
    return () => clearInterval(intervalId);
  }, [loading, error, emergency?.status, emergency?.driverLatitude, emergency?.driverLongitude]);

  // ─── Map init & update ──────────────────────────────────────────────────
  useEffect(() => {
    if (loading || !emergency || error) return;
    const lat = Number(emergency?.latitude || 12.9716);
    const lng = Number(emergency?.longitude || 77.5946);
    const hLat = Number(emergency?.hospitalLatitude || 0);
    const hLng = Number(emergency?.hospitalLongitude || 0);

    try {
      if (!mapInstance.current && mapRef.current) {
        if (mapRef.current._leaflet_id) delete mapRef.current._leaflet_id;
        mapInstance.current = L.map(mapRef.current, { zoomControl: true }).setView([lat, lng], 14);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap contributors', maxZoom: 19
        }).addTo(mapInstance.current);
      }

      if (mapInstance.current) {
        // Patient marker
        const patientIcon = L.divIcon({
          html: `<div style="background:white;border-radius:50%;padding:4px;box-shadow:0 2px 8px rgba(220,38,38,0.5);border:3px solid #dc2626;"><span style="font-size:20px;display:block;line-height:1;">📍</span></div>`,
          className: '', iconSize: [34, 34], iconAnchor: [17, 34]
        });
        if (patientMarkerRef.current) patientMarkerRef.current.setLatLng([lat, lng]);
        else {
          patientMarkerRef.current = L.marker([lat, lng], { icon: patientIcon })
            .addTo(mapInstance.current)
            .bindPopup(`<b>📍 Patient</b><br>${emergency?.patientName || 'Unknown'}<br><small>${patientAddress || ''}</small>`);
        }

        // Hospital marker
        if (hLat !== 0 && hLng !== 0) {
          const hospitalIcon = L.divIcon({
            html: `<div style="background:white;border-radius:50%;padding:4px;box-shadow:0 2px 8px rgba(16,185,129,0.5);border:3px solid #10b981;"><span style="font-size:20px;display:block;line-height:1;">🏥</span></div>`,
            className: '', iconSize: [34, 34], iconAnchor: [17, 17]
          });
          if (hospitalMarkerRef.current) hospitalMarkerRef.current.setLatLng([hLat, hLng]);
          else {
            hospitalMarkerRef.current = L.marker([hLat, hLng], { icon: hospitalIcon })
              .addTo(mapInstance.current)
              .bindPopup(`<b>🏥 ${emergency?.hospitalName || 'Hospital'}</b><br>${hospitalDetails?.phone ? '📞 ' + hospitalDetails.phone : ''}`);
          }
        } else if (hospitalMarkerRef.current) {
          mapInstance.current.removeLayer(hospitalMarkerRef.current);
          hospitalMarkerRef.current = null;
        }

        // Ambulance marker with smooth animation
        const hasDriver = ['assigned', 'on_the_way', 'reached', 'arrived', 'patient_picked'].includes(emergency?.status);
        const driverLat = Number(emergency?.driverLatitude !== undefined ? emergency.driverLatitude : (hasDriver ? lat + 0.005 : 0));
        const driverLng = Number(emergency?.driverLongitude !== undefined ? emergency.driverLongitude : (hasDriver ? lng + 0.005 : 0));

        if (hasDriver && driverLat !== 0 && driverLng !== 0 && !isNaN(driverLat) && !isNaN(driverLng)) {
          const ambulanceIcon = L.divIcon({
            html: `<div style="background:linear-gradient(135deg,#0066FF,#0052cc);border-radius:50%;padding:6px;box-shadow:0 3px 12px rgba(0,102,255,0.5);animation:pulse 1.5s infinite;"><span style="font-size:22px;display:block;line-height:1;">🚑</span></div>`,
            className: 'ambulance-animated', iconSize: [40, 40], iconAnchor: [20, 20]
          });

          if (ambulanceMarkerRef.current) {
            // Smooth movement via requestAnimationFrame
            const startPos = prevAmbulancePos.current || [driverLat, driverLng];
            const endPos = [driverLat, driverLng];
            let startTime = null;
            const duration = 1500;
            const animate = (timestamp) => {
              if (!startTime) startTime = timestamp;
              const progress = Math.min((timestamp - startTime) / duration, 1);
              const newLat = lerp(startPos[0], endPos[0], progress);
              const newLng = lerp(startPos[1], endPos[1], progress);
              if (ambulanceMarkerRef.current) ambulanceMarkerRef.current.setLatLng([newLat, newLng]);
              if (progress < 1) requestAnimationFrame(animate);
            };
            requestAnimationFrame(animate);
          } else {
            ambulanceMarkerRef.current = L.marker([driverLat, driverLng], { icon: ambulanceIcon })
              .addTo(mapInstance.current)
              .bindPopup(`<b>🚑 Ambulance</b><br>${emergency?.driverName || 'Driver'}<br>${emergency?.driverPhone ? '📞 ' + emergency.driverPhone : ''}`);
          }
          prevAmbulancePos.current = [driverLat, driverLng];

          // Route polyline
          const polylineData = routePath.length > 0 ? routePath : [[driverLat, driverLng], [lat, lng]];
          const polylineStyle = { color: '#0066FF', weight: 5, opacity: 0.85, lineCap: 'round', lineJoin: 'round',
            dashArray: routePath.length === 0 ? '5,10' : null };
          if (routePolylineRef.current) routePolylineRef.current.setLatLngs(polylineData);
          else {
            routePolylineRef.current = L.polyline(polylineData, polylineStyle).addTo(mapInstance.current);
          }

          // Fit bounds
          const bounds = [[lat, lng], [driverLat, driverLng]];
          if (hLat !== 0 && hLng !== 0) bounds.push([hLat, hLng]);
          mapInstance.current.fitBounds(bounds, { padding: [50, 50] });
        } else {
          if (ambulanceMarkerRef.current) { mapInstance.current.removeLayer(ambulanceMarkerRef.current); ambulanceMarkerRef.current = null; }
          if (routePolylineRef.current) { mapInstance.current.removeLayer(routePolylineRef.current); routePolylineRef.current = null; }
          mapInstance.current.setView([lat, lng], 14);
        }
      }
    } catch (e) { console.error('Leaflet update error:', e); }
  }, [loading, emergency, error, routePath, patientAddress, hospitalDetails]);

  // ─── Map cleanup ────────────────────────────────────────────────────────
  useEffect(() => () => {
    if (mapInstance.current) { mapInstance.current.remove(); mapInstance.current = null; }
    patientMarkerRef.current = null;
    ambulanceMarkerRef.current = null;
    hospitalMarkerRef.current = null;
    routePolylineRef.current = null;
  }, []);

  const handleCancel = async () => {
    if (window.confirm('Are you sure you want to cancel this emergency request?')) {
      try {
        await updateEmergencyStatus(id, 'cancelled');
        showToast('Emergency request cancelled.', 'success');
      } catch (err) {
        showToast('Failed to cancel: ' + (err.response?.data?.error || err.message), 'error');
      }
    }
  };

  if (loading) return (
    <div className="container mt-4 text-center">
      <div className="card"><p>Connecting to dispatch system...</p></div>
    </div>
  );

  if (error || !emergency) return (
    <div className="container mt-4">
      <div className="card text-center" style={{ padding: '40px' }}>
        <span style={{ fontSize: '48px' }}>⚠️</span>
        <h3 className="mt-2">{error || 'Emergency not found'}</h3>
        <Link to="/user/dashboard" className="btn btn-primary mt-3">Return to Dashboard</Link>
      </div>
    </div>
  );

  const statusInfo = STATUS_CONFIG[emergency.status] || STATUS_CONFIG.pending;
  const currentStepIndex = STATUS_TIMELINE.indexOf(emergency.status);
  const etaMinutes = routeDetails ? Math.ceil(Number(routeDetails?.durationSec || 0) / 60) : null;
  const distKm = routeDetails ? Number(routeDetails?.distanceKm || 0).toFixed(1) : null;
  const hasHospital = emergency?.hospitalLatitude && emergency?.hospitalLongitude;

  return (
    <div className="container mt-4" style={{ paddingBottom: '40px' }}>
      {/* Toast */}
      {toast && (
        <div style={{
          position: 'fixed', top: '24px', right: '24px', zIndex: 9999,
          backgroundColor: toast.type === 'error' ? 'var(--accent-red)' : 'var(--accent-green)',
          color: '#fff', padding: '12px 24px', borderRadius: 'var(--radius-md)',
          boxShadow: 'var(--shadow-lg)', display: 'flex', alignItems: 'center', gap: '8px',
          fontWeight: 500, animation: 'fadeIn 0.3s ease-out'
        }} data-testid="toast-notification">
          {toast.type === 'error' ? '❌' : '✅'} {toast.message}
        </div>
      )}

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>

        {/* Left: Map */}
        <div style={{ flex: '1 1 580px' }}>
          <div className="card" style={{ padding: 0, overflow: 'hidden', height: '500px', position: 'relative', boxShadow: 'var(--shadow-lg)' }}>
            {routingLoading && (
              <div style={{ position: 'absolute', top: '12px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000,
                background: 'rgba(255,255,255,0.97)', padding: '8px 18px', borderRadius: 'var(--radius-full)',
                display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 500,
                border: '1px solid var(--border)', boxShadow: 'var(--shadow-sm)' }}>
                <span className="spinner" style={{ width: '12px', height: '12px', border: '2px solid rgba(0,0,0,0.1)', borderTop: '2px solid var(--primary)' }}></span>
                Calculating route via Google Maps...
              </div>
            )}
            <div ref={mapRef} id="track-map" style={{ width: '100%', height: '100%', zIndex: 1 }} />

            {/* Map legend */}
            <div style={{ position: 'absolute', bottom: '12px', left: '12px', zIndex: 1000,
              background: 'rgba(255,255,255,0.95)', borderRadius: 'var(--radius-md)', padding: '8px 12px',
              fontSize: '11px', boxShadow: 'var(--shadow-sm)', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
              <span>📍 Patient</span>
              <span>🚑 Ambulance</span>
              {hasHospital && <span>🏥 Hospital</span>}
            </div>
          </div>

          {/* Route source badge */}
          {routeDetails?.source && (
            <div style={{ marginTop: '8px', textAlign: 'right' }}>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', background: '#f1f5f9', padding: '3px 8px', borderRadius: '4px' }}>
                🗺️ {routeDetails.source}
              </span>
            </div>
          )}
        </div>

        {/* Right: Info Panel */}
        <div style={{ flex: '1 1 340px', display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* Status Card */}
          <div className="card glass-panel" style={{ borderLeft: `6px solid ${statusInfo.color}`, padding: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <span style={{ fontSize: '28px' }}>{statusInfo.icon}</span>
              <div>
                <span className={`badge ${statusInfo.badge || ''}`} style={{ display: 'inline-block', marginBottom: '4px' }} data-testid="status-badge">
                  {(emergency?.status || 'unknown').replace(/_/g, ' ').toUpperCase()}
                </span>
                <h3 style={{ margin: 0, fontSize: '17px' }}>{statusInfo.title}</h3>
              </div>
            </div>
            <p className="text-muted" style={{ fontSize: '13px', margin: 0 }}>
              {statusInfo.desc.replace('Driver', emergency.driverName || 'Driver')}
            </p>
          </div>

          {/* Status Timeline */}
          <div className="card" style={{ padding: '16px' }}>
            <h4 style={{ marginBottom: '14px', fontSize: '13px', fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.5px' }}>
              Dispatch Timeline
            </h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
              {STATUS_TIMELINE.map((status, idx) => {
                const cfg = STATUS_CONFIG[status];
                const isDone = idx <= currentStepIndex;
                const isCurrent = idx === currentStepIndex;
                return (
                  <div key={status} style={{ display: 'flex', gap: '12px', alignItems: 'flex-start', paddingBottom: idx < STATUS_TIMELINE.length - 1 ? '10px' : '0' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flexShrink: 0 }}>
                      <div style={{
                        width: '28px', height: '28px', borderRadius: '50%',
                        background: isDone ? (isCurrent ? statusInfo.color : 'var(--accent-green)') : '#e2e8f0',
                        color: isDone ? 'white' : '#94a3b8',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: isCurrent ? '14px' : '12px',
                        fontWeight: 700, boxShadow: isCurrent ? `0 0 0 3px ${statusInfo.color}33` : 'none',
                        transition: 'all 0.3s ease'
                      }}>
                        {isDone ? (isCurrent ? cfg.icon : '✓') : '○'}
                      </div>
                      {idx < STATUS_TIMELINE.length - 1 && (
                        <div style={{ width: '2px', height: '100%', minHeight: '16px', background: idx < currentStepIndex ? 'var(--accent-green)' : '#e2e8f0', marginTop: '4px' }} />
                      )}
                    </div>
                    <div style={{ paddingTop: '4px', opacity: isDone ? 1 : 0.5 }}>
                      <div style={{ fontSize: '13px', fontWeight: isCurrent ? 700 : 500, color: isCurrent ? statusInfo.color : 'var(--text-main)' }}>
                        {cfg.title}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* ETA Card */}
          {routeDetails && (
            <div className="card" style={{ borderLeft: '5px solid var(--primary)', background: 'var(--primary-light)', padding: '16px' }}>
              <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary-hover)', marginBottom: '14px', fontSize: '14px' }}>
                ⏱️ Live ETA & Route Info
                {routeDetails?.source === 'Google Directions API' && (
                  <span style={{ fontSize: '10px', background: '#1a73e8', color: 'white', padding: '2px 6px', borderRadius: '4px', fontWeight: 700 }}>
                    GOOGLE MAPS
                  </span>
                )}
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div style={{ textAlign: 'center', padding: '10px', background: 'white', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-sm)' }}>
                  <div style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '4px' }}>Distance</div>
                  <div style={{ fontSize: '22px', fontWeight: 800, color: 'var(--primary)' }}>{distKm} km</div>
                </div>
                <div style={{ textAlign: 'center', padding: '10px', background: 'white', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-sm)' }}>
                  <div style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '4px' }}>ETA</div>
                  <div style={{ fontSize: '22px', fontWeight: 800, color: 'var(--primary)' }}>{etaMinutes} min</div>
                </div>
              </div>
              <div style={{ marginTop: '10px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span className={`badge ${routeDetails?.traffic?.status === 'Normal' ? 'badge-success' : routeDetails?.traffic?.status === 'Moderate' ? 'badge-warning' : 'badge-danger'}`}
                  style={{ fontSize: '11px', padding: '3px 8px' }}>
                  {(routeDetails?.traffic?.status || 'Normal').toUpperCase()}
                </span>
                <span style={{ fontSize: '12px', color: 'var(--text-main)' }}>{routeDetails?.traffic?.message || ''}</span>
              </div>
              {routeDetails?.summary && (
                <div style={{ marginTop: '8px', fontSize: '12px', color: 'var(--text-muted)' }}>
                  📍 Via {routeDetails.summary}
                </div>
              )}
              {lastUpdated && (
                <div style={{ marginTop: '6px', fontSize: '11px', color: 'var(--text-muted)' }}>
                  🔄 Updated {lastUpdated.toLocaleTimeString()}
                </div>
              )}
            </div>
          )}

          {/* Patient + Hospital Info */}
          <div className="card" style={{ padding: '16px' }}>
            <h4 style={{ marginBottom: '14px' }}>📋 Emergency Details</h4>
            <div style={{ fontSize: '14px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div><strong>Patient:</strong> {emergency?.patientName || 'Unknown Patient'}</div>
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
                <div style={{ background: '#eff6ff', borderRadius: 'var(--radius-sm)', padding: '8px', marginTop: '4px' }}>
                  <strong>📍 Location:</strong> {patientAddress}
                </div>
              )}
              {emergency?.hospitalName && (
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: '10px', marginTop: '6px' }}>
                  <div style={{ fontWeight: 700, color: '#10b981', marginBottom: '6px' }}>🏥 Destination Hospital</div>
                  <div style={{ fontWeight: 600 }}>{emergency.hospitalName}</div>
                  {hospitalDetails?.address && <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>{hospitalDetails.address}</div>}
                  {hospitalDetails?.phone && (
                    <a href={`tel:${hospitalDetails.phone}`}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', marginTop: '8px',
                        background: '#d1fae5', color: '#065f46', padding: '6px 12px', borderRadius: 'var(--radius-sm)',
                        textDecoration: 'none', fontWeight: 600, fontSize: '13px' }}>
                      📞 Call Hospital: {hospitalDetails.phone}
                    </a>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Driver Info + Navigation */}
          {['assigned', 'on_the_way', 'reached'].includes(emergency?.status) && emergency?.driverName && (
            <div className="card" style={{ background: 'var(--primary-light)', borderColor: 'var(--primary)', padding: '16px' }}>
              <h4 style={{ marginBottom: '12px' }}>🚑 Ambulance Team</h4>
              <div style={{ display: 'flex', alignItems: 'center', gap: '14px', marginBottom: '14px' }}>
                <div style={{ width: '48px', height: '48px', borderRadius: '50%', background: 'var(--primary)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontSize: '22px', flexShrink: 0 }}>
                  👨‍⚕️
                </div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: '15px' }}>{emergency.driverName}</div>
                  {emergency.driverPhone && <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>{emergency.driverPhone}</div>}
                </div>
              </div>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {emergency.driverPhone && (
                  <a href={`tel:${emergency.driverPhone}`}
                    style={{ flex: 1, padding: '10px', borderRadius: 'var(--radius-md)', background: 'var(--primary)',
                      color: 'white', textAlign: 'center', textDecoration: 'none', fontWeight: 700, fontSize: '13px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
                    📞 Call Driver
                  </a>
                )}
                {hasHospital && (
                  <button onClick={() => openGoogleMapsNavigation(emergency.hospitalLatitude, emergency.hospitalLongitude, emergency.hospitalName)}
                    style={{ flex: 1, padding: '10px', borderRadius: 'var(--radius-md)', background: '#10b981',
                      color: 'white', border: 'none', cursor: 'pointer', fontWeight: 700, fontSize: '13px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
                    🧭 Navigate to Hospital
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Actions */}
          <div style={{ display: 'flex', gap: '12px', width: '100%' }}>
            {(emergency.status === 'pending' || emergency.status === 'assigned') && (
              <button onClick={handleCancel} className="btn btn-outline"
                style={{ flex: 1, borderColor: 'var(--accent-red)', color: 'var(--accent-red)' }}
                data-testid="cancel-request-btn">
                Cancel Request
              </button>
            )}
            <Link to="/user/dashboard" className="btn btn-primary" style={{ flex: 1 }}>
              Dashboard
            </Link>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% { transform: scale(1); box-shadow: 0 3px 12px rgba(0,102,255,0.5); }
          50% { transform: scale(1.05); box-shadow: 0 4px 20px rgba(0,102,255,0.7); }
        }
        .ambulance-animated > div { animation: pulse 2s infinite; }
      `}</style>
    </div>
  );
};

export default TrackAmbulance;
