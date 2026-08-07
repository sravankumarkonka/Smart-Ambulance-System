import React, { useEffect, useState, useRef } from 'react';
import { db } from '../../config/firebase';
import { collection, query, where, onSnapshot } from 'firebase/firestore';
import { reverseGeocode, openGoogleMapsNavigation } from '../../services/googleMapsService';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Link } from 'react-router-dom';

const LiveMap = () => {
  const [emergencies, setEmergencies] = useState([]);
  const [selectedEmergency, setSelectedEmergency] = useState(null);
  const [zoomImageUrl, setZoomImageUrl] = useState(null);
  const [mapLayer, setMapLayer] = useState('street');
  const [selectedAddress, setSelectedAddress] = useState('');
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const tileLayerRef = useRef(null);
  const markersRef = useRef({});

  // 1. Subscribe to emergencies (pending, assigned, arrived, on_the_way, reached, patient_picked)
  useEffect(() => {
    const q = query(
      collection(db, 'emergencies'),
      where('status', 'in', ['pending', 'assigned', 'on_the_way', 'reached', 'arrived', 'patient_picked'])
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      setEmergencies(list);
    }, (error) => {
      console.error("Firestore onSnapshot error:", error);
    });

    return () => unsubscribe();
  }, []);

  // 2. Map Initialization
  useEffect(() => {
    if (mapRef.current && !mapInstance.current) {
      mapInstance.current = L.map(mapRef.current).setView([12.9716, 77.5946], 12);
      tileLayerRef.current = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors', maxZoom: 19
      }).addTo(mapInstance.current);
    }

    return () => {
      if (mapInstance.current) {
        mapInstance.current.remove();
        mapInstance.current = null;
      }
    };
  }, []);

  // 3. Switch map layer
  const toggleMapLayer = (layerType) => {
    if (!mapInstance.current) return;
    setMapLayer(layerType);
    if (tileLayerRef.current) mapInstance.current.removeLayer(tileLayerRef.current);

    if (layerType === 'satellite') {
      tileLayerRef.current = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: 'Tiles &copy; Esri', maxZoom: 18
      }).addTo(mapInstance.current);
    } else {
      tileLayerRef.current = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors', maxZoom: 19
      }).addTo(mapInstance.current);
    }
  };

  // 4. Update Markers when emergencies list changes
  useEffect(() => {
    if (!mapInstance.current) return;

    // Clear existing markers and lines
    Object.values(markersRef.current).forEach(({ markers, line }) => {
      markers.forEach(m => {
        if (mapInstance.current) mapInstance.current.removeLayer(m);
      });
      if (line && mapInstance.current) mapInstance.current.removeLayer(line);
    });
    markersRef.current = {};

    if (emergencies.length === 0) return;

    const bounds = [];

    (emergencies || []).forEach((em) => {
      const emMarkers = [];
      let emLine = null;

      const lat = Number(em?.latitude);
      const lng = Number(em?.longitude);

      const sevColor = em?.severityLevel === 'critical' ? '#dc2626' : em?.severityLevel === 'high' ? '#ea580c' : '#d97706';
      const patientIcon = L.divIcon({
        html: `<div style="background:${sevColor};color:white;border-radius:50%;width:34px;height:34px;display:flex;align-items:center;justify-content:center;font-size:18px;box-shadow:0 3px 10px rgba(0,0,0,0.3);border:2px solid white;cursor:pointer;">📍</div>`,
        className: 'live-patient-marker',
        iconSize: [34, 34],
        iconAnchor: [17, 34]
      });

      const ambulanceIcon = L.divIcon({
        html: `<div style="background:#0066FF;color:white;border-radius:50%;width:36px;height:36px;display:flex;align-items:center;justify-content:center;font-size:20px;box-shadow:0 3px 10px rgba(0,102,255,0.4);border:2px solid white;cursor:pointer;">🚑</div>`,
        className: 'live-ambulance-marker',
        iconSize: [36, 36],
        iconAnchor: [18, 18]
      });

      // Patient Marker
      if (!isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0 && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
        const pMarker = L.marker([lat, lng], { icon: patientIcon })
          .addTo(mapInstance.current)
          .bindPopup(`
            <div style="font-family: inherit; font-size: 13px; padding: 4px;">
              <strong style="color: ${sevColor}; font-size: 14px;">📍 ${em?.patientName || 'Unknown Patient'}</strong><br/>
              <b>Type:</b> ${(em?.emergencyType || 'other').toUpperCase()}<br/>
              <b>Status:</b> ${(em?.status || 'unknown').toUpperCase()}<br/>
              ${em?.hospitalName ? `<b>Hospital:</b> ${em.hospitalName}<br/>` : ''}
              <a href="/user/track/${em.id}" style="color: #0066FF; font-weight: bold; text-decoration: underline; display: block; margin-top: 6px;">Open Incident Tracker →</a>
            </div>
          `);
        emMarkers.push(pMarker);
        bounds.push([lat, lng]);

        // Ambulance Marker (if assigned/arrived/on_the_way)
        if (['assigned', 'on_the_way', 'reached', 'arrived', 'patient_picked'].includes(em?.status)) {
          const driverLat = Number(em?.driverLatitude !== undefined ? em.driverLatitude : lat + 0.004);
          const driverLng = Number(em?.driverLongitude !== undefined ? em.driverLongitude : lng + 0.004);

          if (!isNaN(driverLat) && !isNaN(driverLng) && driverLat !== 0 && driverLng !== 0) {
            const aMarker = L.marker([driverLat, driverLng], { icon: ambulanceIcon })
              .addTo(mapInstance.current)
              .bindPopup(`
                <div style="font-family: inherit; font-size: 13px;">
                  <strong style="color: #0066FF;">🚑 Unit: ${em?.driverName || 'En route'}</strong><br/>
                  <b>Phone:</b> ${em?.driverPhone || 'N/A'}<br/>
                  <b>Status:</b> ${em?.status?.toUpperCase()}
                </div>
              `);
            emMarkers.push(aMarker);
            bounds.push([driverLat, driverLng]);

            // Route connection line
            emLine = L.polyline([[driverLat, driverLng], [lat, lng]], {
              color: '#0066FF',
              weight: 4,
              dashArray: '6, 8',
              opacity: 0.85
            }).addTo(mapInstance.current);
          }
        }
      }

      if (em?.id) {
        markersRef.current[em.id] = { markers: emMarkers, line: emLine };
      }
    });

    if (bounds.length > 0 && mapInstance.current) {
      try {
        mapInstance.current.fitBounds(bounds, { padding: [50, 50] });
      } catch (err) {
        console.error("Leaflet fitBounds error:", err);
      }
    }
  }, [emergencies]);

  const handleFocusIncident = (em) => {
    setSelectedEmergency(em);
    const lat = Number(em?.latitude);
    const lng = Number(em?.longitude);

    if (lat && lng) {
      reverseGeocode(lat, lng).then(addr => setSelectedAddress(addr || `${lat.toFixed(5)}, ${lng.toFixed(5)}`));
    }

    if (mapInstance.current && !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0) {
      mapInstance.current.setView([lat, lng], 15, { animate: true });
      if (em?.id) {
        const data = markersRef.current[em.id];
        if (data && data.markers && data.markers.length > 0) {
          data.markers[0].openPopup();
        }
      }
    }
  };

  return (
    <div className="container mt-4" style={{ maxWidth: '1400px' }}>
      <div className="mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ margin: 0, fontWeight: 700 }}>Live Fleet & Dispatch Telemetry Map</h2>
          <p className="text-muted" style={{ fontSize: '14px', margin: 0 }}>Real-time monitoring powered by Google Maps APIs & Firebase telemetry.</p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={() => toggleMapLayer(mapLayer === 'street' ? 'satellite' : 'street')}
            className="btn btn-outline" style={{ padding: '8px 16px', fontSize: '13px' }}>
            {mapLayer === 'street' ? '🛰️ Satellite View' : '🗺️ Street View'}
          </button>
          <Link to="/admin/dashboard" className="btn btn-outline" data-testid="back-to-dashboard-btn" style={{ padding: '8px 16px', fontSize: '13px' }}>
            Back to Dashboard
          </Link>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        {/* Incident List Sidebar */}
        <div style={{ flex: '1 1 340px', maxHeight: '600px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Active Dispatch Incidents ({emergencies.length})</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {emergencies.length === 0 ? (
              <div className="card text-center" style={{ padding: '24px', color: 'var(--text-muted)' }}>
                No active incidents on network.
              </div>
            ) : (
              (emergencies || []).map((em) => (
                <div
                  key={em?.id}
                  onClick={() => handleFocusIncident(em)}
                  className={`card incident-item ${selectedEmergency?.id === em?.id ? 'active' : ''}`}
                  style={{
                    cursor: 'pointer',
                    borderLeft: `5px solid ${em?.status === 'pending' ? 'var(--accent-yellow)' : 'var(--primary)'}`,
                    background: selectedEmergency?.id === em?.id ? '#eff6ff' : 'white',
                    transition: 'all 0.2s ease-in-out',
                    padding: '14px'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong style={{ fontSize: '14px' }}>{em?.patientName || 'Unknown Patient'}</strong>
                    <span className={`badge ${em?.status === 'pending' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '11px' }}>
                      {(em?.status || 'unknown').toUpperCase()}
                    </span>
                  </div>
                  <p className="text-muted mt-1" style={{ fontSize: '12px', margin: '4px 0 0 0' }}>Type: {(em?.emergencyType || 'other').toUpperCase()}</p>
                  {em?.driverName && (
                    <p style={{ fontSize: '12px', color: 'var(--primary)', margin: '4px 0 0 0', fontWeight: 600 }}>
                      🚑 {em.driverName}
                    </p>
                  )}
                </div>
              ))
            )}
          </div>

          {selectedEmergency && (
            <div className="card mt-2" style={{ borderLeft: '5px solid var(--accent-red)', background: '#fff5f5', cursor: 'default', padding: '16px' }}>
              <h4 style={{ color: '#CC2F26', margin: '0 0 10px 0' }}>Incident Telemetry</h4>
              <div style={{ fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '6px', color: 'var(--text-main)' }}>
                <div><strong>Patient:</strong> {selectedEmergency.patientName || 'Unknown Patient'}</div>
                <div><strong>Type:</strong> {(selectedEmergency.emergencyType || 'other').toUpperCase()}</div>
                {selectedEmergency.severityLevel && (
                  <div>
                    <strong>Severity:</strong> <span className={`badge ${selectedEmergency.severityLevel === 'critical' || selectedEmergency.severityLevel === 'high' ? 'badge-danger' : selectedEmergency.severityLevel === 'medium' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '11px', textTransform: 'capitalize' }}>{selectedEmergency.severityLevel}</span>
                  </div>
                )}
                <div><strong>Description:</strong> {selectedEmergency.description || 'No description provided'}</div>
                {selectedAddress && (
                  <div style={{ background: 'white', padding: '8px', borderRadius: '4px', border: '1px solid #fee2e2' }}>
                    <strong>📍 Address:</strong> {selectedAddress}
                  </div>
                )}
                {selectedEmergency.latitude && (
                  <button onClick={() => openGoogleMapsNavigation(selectedEmergency.latitude, selectedEmergency.longitude, selectedEmergency.patientName)}
                    style={{ marginTop: '8px', padding: '8px 12px', background: '#0066FF', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 600, fontSize: '12px' }}>
                    🧭 Open in Google Maps Navigation
                  </button>
                )}
                {selectedEmergency.imageUrl && (
                  <div style={{ marginTop: '10px' }}>
                    <strong>Accident Evidence:</strong>
                    <img
                      src={selectedEmergency.imageUrl}
                      alt="Accident Evidence"
                      style={{
                        width: '100%',
                        maxHeight: '160px',
                        objectFit: 'cover',
                        borderRadius: 'var(--radius-md)',
                        marginTop: '6px',
                        cursor: 'zoom-in',
                        border: '1px solid var(--border)'
                      }}
                      onClick={() => setZoomImageUrl(selectedEmergency.imageUrl)}
                      data-testid="admin-accident-image"
                    />
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Map Area */}
        <div style={{ flex: '2 1 700px' }}>
          <div className="card" style={{ padding: '0', overflow: 'hidden', height: '600px', border: '1px solid var(--border)', position: 'relative', boxShadow: 'var(--shadow-lg)' }}>
            <div ref={mapRef} id="map" style={{ width: '100%', height: '100%', zIndex: 1 }} />
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

export default LiveMap;
