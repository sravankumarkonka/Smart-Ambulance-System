import React, { useEffect, useState, useRef } from 'react';
import { db } from '../../config/firebase';
import { collection, query, where, onSnapshot } from 'firebase/firestore';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Link } from 'react-router-dom';

const LiveMap = () => {
  const [emergencies, setEmergencies] = useState([]);
  const [selectedEmergency, setSelectedEmergency] = useState(null);
  const [zoomImageUrl, setZoomImageUrl] = useState(null);
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const markersRef = useRef({});

  // 1. Subscribe to emergencies (pending, assigned, arrived)
  useEffect(() => {
    const q = query(
      collection(db, 'emergencies'),
      where('status', 'in', ['pending', 'assigned', 'arrived'])
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
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
      }).addTo(mapInstance.current);
    }

    return () => {
      if (mapInstance.current) {
        mapInstance.current.remove();
        mapInstance.current = null;
      }
    };
  }, []);

  // 3. Update Markers when emergencies list changes
  useEffect(() => {
    if (!mapInstance.current) return;

    // Clear existing markers and lines
    Object.values(markersRef.current).forEach(({ markers, line }) => {
      markers.forEach(m => {
        if (mapInstance.current) {
          mapInstance.current.removeLayer(m);
        }
      });
      if (line && mapInstance.current) {
        mapInstance.current.removeLayer(line);
      }
    });
    markersRef.current = {};

    if (emergencies.length === 0) return;

    const patientIcon = L.divIcon({
      html: `<div style="font-size: 28px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); cursor: pointer;">📍</div>`,
      className: 'live-patient-marker',
      iconSize: [30, 30],
      iconAnchor: [15, 30]
    });

    const ambulanceIcon = L.divIcon({
      html: `<div style="font-size: 28px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); cursor: pointer;">🚑</div>`,
      className: 'live-ambulance-marker',
      iconSize: [30, 30],
      iconAnchor: [15, 15]
    });

    const bounds = [];

    (emergencies || []).forEach((em) => {
      const emMarkers = [];
      let emLine = null;

      const lat = Number(em?.latitude);
      const lng = Number(em?.longitude);

      // Patient Marker
      if (!isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0 && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
        const pMarker = L.marker([lat, lng], { icon: patientIcon })
          .addTo(mapInstance.current)
          .bindPopup(`<b>Patient:</b> ${em?.patientName || 'Unknown Patient'}<br><b>Type:</b> ${em?.emergencyType || 'other'}<br><b>Status:</b> ${(em?.status || 'unknown').toUpperCase()}`);
        emMarkers.push(pMarker);
        bounds.push([lat, lng]);

        // Ambulance Marker (if assigned/arrived)
        if (em?.status === 'assigned' || em?.status === 'arrived') {
          const driverLat = Number(em?.driverLatitude !== undefined ? em.driverLatitude : lat + 0.004);
          const driverLng = Number(em?.driverLongitude !== undefined ? em.driverLongitude : lng + 0.004);

          if (!isNaN(driverLat) && !isNaN(driverLng) && driverLat !== 0 && driverLng !== 0 && driverLat >= -90 && driverLat <= 90 && driverLng >= -180 && driverLng <= 180) {
            const aMarker = L.marker([driverLat, driverLng], { icon: ambulanceIcon })
              .addTo(mapInstance.current)
              .bindPopup(`<b>Ambulance:</b> ${em?.driverName || 'En route'}<br><b>Status:</b> Driving to scene`);
            emMarkers.push(aMarker);
            bounds.push([driverLat, driverLng]);

            // Draw route line
            emLine = L.polyline([[driverLat, driverLng], [lat, lng]], {
              color: '#CC2F26',
              weight: 3,
              dashArray: '5, 10',
              opacity: 0.8
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
    if (mapInstance.current && !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0 && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      mapInstance.current.setView([lat, lng], 14, { animate: true });
      if (em?.id) {
        const data = markersRef.current[em.id];
        if (data && data.markers && data.markers.length > 0) {
          data.markers[0].openPopup();
        }
      }
    }
  };

  return (
    <div className="container mt-4" style={{ maxWidth: '1200px' }}>
      <div className="mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2>Live Fleet & Dispatch Map</h2>
          <p className="text-muted" style={{ fontSize: '14px' }}>Real-time monitoring of all active incidents and ambulances.</p>
        </div>
        <Link to="/admin/dashboard" className="btn btn-outline" data-testid="back-to-dashboard-btn">
          Back to Dashboard
        </Link>
      </div>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        {/* Incident List Sidebar */}
        <div style={{ flex: '1 1 300px', maxHeight: '550px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <h3>Active Incidents ({emergencies.length})</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {emergencies.length === 0 ? (
              <div className="card text-center" style={{ padding: '24px', color: 'var(--text-muted)' }}>
                No active incidents on the network.
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
                    background: selectedEmergency?.id === em?.id ? '#f1f5f9' : 'white',
                    transition: 'all 0.2s ease-in-out',
                    padding: '16px'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong style={{ fontSize: '15px' }}>{em?.patientName || 'Unknown Patient'}</strong>
                    <span className={`badge ${em?.status === 'pending' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '11px' }}>
                      {(em?.status || 'unknown').toUpperCase()}
                    </span>
                  </div>
                  <p className="text-muted mt-1" style={{ fontSize: '13px' }}>Type: {(em?.emergencyType || 'other').toUpperCase()}</p>
                  {em?.driverName && (
                    <p className="mt-1" style={{ fontSize: '12px', color: 'var(--primary)' }}>
                      🚑 Assigned: {em.driverName}
                    </p>
                  )}
                </div>
              ))
            )}
          </div>

          {selectedEmergency && (
            <div className="card mt-2" style={{ borderLeft: '5px solid var(--accent-red)', background: 'var(--accent-red-light)', cursor: 'default' }}>
              <h4 style={{ color: '#CC2F26' }}>Incident Assessment</h4>
              <div style={{ fontSize: '13px', marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '6px', color: 'var(--text-main)' }}>
                <div><strong>Patient:</strong> {selectedEmergency.patientName || 'Unknown Patient'}</div>
                <div><strong>Type:</strong> {(selectedEmergency.emergencyType || 'other').toUpperCase()}</div>
                {selectedEmergency.severityLevel && (
                  <div>
                    <strong>Severity:</strong> <span className={`badge ${selectedEmergency.severityLevel === 'critical' || selectedEmergency.severityLevel === 'high' ? 'badge-danger' : selectedEmergency.severityLevel === 'medium' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '11px', textTransform: 'capitalize' }}>{selectedEmergency.severityLevel}</span>
                  </div>
                )}
                <div><strong>Description:</strong> {selectedEmergency.description || 'No description provided'}</div>
                <div><strong>Coordinates:</strong> {Number(selectedEmergency.latitude || 0).toFixed(6)}, {Number(selectedEmergency.longitude || 0).toFixed(6)}</div>
                {selectedEmergency.imageUrl && (
                  <div style={{ marginTop: '10px' }}>
                    <strong>Accident Evidence:</strong>
                    <img
                      src={selectedEmergency.imageUrl}
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
          <div className="card" style={{ padding: '0', overflow: 'hidden', height: '550px', border: '1px solid var(--border)', position: 'relative' }}>
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
