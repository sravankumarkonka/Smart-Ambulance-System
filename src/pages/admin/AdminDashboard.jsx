import React, { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { db } from '../../config/firebase';
import { collection, query, onSnapshot } from 'firebase/firestore';
import { subscribeToAllAmbulances } from '../../services/firestoreService';
import { HOSPITALS } from '../../services/routingService';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const AdminDashboard = () => {
  const [emergencies, setEmergencies] = useState([]);
  const [ambulances, setAmbulances] = useState([]);
  const [selectedIncident, setSelectedIncident] = useState(null);

  const mapRef = useRef(null);
  const mapInstance = useRef(null);

  const markersRef = useRef({
    patients: {},
    ambulances: {},
    hospitals: []
  });

  // 1. Subscribe to all emergencies
  useEffect(() => {
    const q = query(collection(db, 'emergencies'));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setEmergencies(list);
    }, (error) => {
      console.error("Error subscribing to emergencies:", error);
    });

    return () => unsubscribe();
  }, []);

  // 2. Subscribe to all driver ambulances
  useEffect(() => {
    const unsubscribe = subscribeToAllAmbulances((list) => {
      setAmbulances(list);
    });

    return () => unsubscribe();
  }, []);

  // 3. Initialize Leaflet Map
  useEffect(() => {
    if (mapRef.current && !mapInstance.current) {
      mapInstance.current = L.map(mapRef.current).setView([12.9716, 77.5946], 12);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
      }).addTo(mapInstance.current);

      // Add static hospital markers once
      HOSPITALS.forEach((h) => {
        const hospitalIcon = L.divIcon({
          html: `<div style="font-size: 24px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.35));">🏥</div>`,
          className: 'admin-hospital-marker',
          iconSize: [26, 26],
          iconAnchor: [13, 13]
        });

        const marker = L.marker([h.latitude, h.longitude], { icon: hospitalIcon })
          .addTo(mapInstance.current)
          .bindPopup(`<b>Hospital:</b> ${h.name}`);

        markersRef.current.hospitals.push(marker);
      });
    }

    return () => {
      if (mapInstance.current) {
        mapInstance.current.remove();
        mapInstance.current = null;
      }
    };
  }, []);

  // 4. Update Map Markers dynamically
  useEffect(() => {
    if (!mapInstance.current) return;

    // Clear old patient markers
    Object.values(markersRef.current.patients).forEach(m => mapInstance.current.removeLayer(m));
    markersRef.current.patients = {};

    // Clear old ambulance markers
    Object.values(markersRef.current.ambulances).forEach(m => mapInstance.current.removeLayer(m));
    markersRef.current.ambulances = {};

    // Redraw active patients (pending, assigned, arrived)
    const activeEmergencies = emergencies.filter(e => ['pending', 'assigned', 'arrived'].includes(e.status));

    activeEmergencies.forEach((em) => {
      if (!em.latitude || !em.longitude) return;

      const markerColor = em.severityLevel === 'critical' ? '🔴' : em.severityLevel === 'high' ? '🟠' : em.severityLevel === 'medium' ? '🟡' : '🟢';

      const patientIcon = L.divIcon({
        html: `<div style="font-size: 28px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); cursor: pointer;">${markerColor}</div>`,
        className: 'admin-patient-pin',
        iconSize: [30, 30],
        iconAnchor: [15, 30]
      });

      const marker = L.marker([em.latitude, em.longitude], { icon: patientIcon })
        .addTo(mapInstance.current)
        .bindPopup(`
          <div style="font-family: inherit; font-size: 13px;">
            <strong>Patient:</strong> ${em.patientName}<br/>
            <strong>Severity:</strong> <span style="text-transform: capitalize; font-weight: bold;">${em.severityLevel || 'medium'}</span><br/>
            <strong>Status:</strong> ${(em.status || 'unknown').toUpperCase()}<br/>
            <a href="/user/track/${em.id}" style="color: var(--primary); font-weight: 600; text-decoration: underline; display: block; margin-top: 6px;">Open tracker</a>
          </div>
        `);

      markersRef.current.patients[em.id] = marker;
    });

    // Redraw ambulances
    ambulances.forEach((amb) => {
      if (!amb.latitude || !amb.longitude) return;

      const emoji = amb.status === 'busy' ? '🚒' : '🚑';
      const ambulanceIcon = L.divIcon({
        html: `<div style="font-size: 28px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); cursor: pointer;">${emoji}</div>`,
        className: 'admin-ambulance-pin',
        iconSize: [30, 30],
        iconAnchor: [15, 15]
      });

      const marker = L.marker([amb.latitude, amb.longitude], { icon: ambulanceIcon })
        .addTo(mapInstance.current)
        .bindPopup(`
          <div style="font-family: inherit; font-size: 13px;">
            <strong>Ambulance Unit</strong><br/>
            <strong>Driver:</strong> ${amb.driverName}<br/>
            <strong>Contact:</strong> ${amb.driverPhone || 'N/A'}<br/>
            <strong>Status:</strong> <span style="color: ${amb.status === 'busy' ? 'var(--accent-red)' : 'var(--accent-green)'}; font-weight: bold;">${amb.status.toUpperCase()}</span>
          </div>
        `);

      markersRef.current.ambulances[amb.driverId] = marker;
    });

  }, [emergencies, ambulances]);

  // Handle focus mapping on incident click
  const handleFocusIncident = (em) => {
    setSelectedIncident(em);
    if (mapInstance.current && em.latitude && em.longitude) {
      mapInstance.current.setView([em.latitude, em.longitude], 14, { animate: true });
      const marker = markersRef.current.patients[em.id];
      if (marker) {
        marker.openPopup();
      }
    }
  };

  // Metric Calculation
  const totalCount = emergencies.length;
  const activeCount = emergencies.filter(e => ['pending', 'assigned', 'arrived'].includes(e.status)).length;
  const criticalCount = emergencies.filter(e => ['pending', 'assigned', 'arrived'].includes(e.status) && e.severityLevel === 'critical').length;
  const completedCount = emergencies.filter(e => e.status === 'completed').length;
  const availableAmbs = ambulances.filter(a => a.status === 'available').length;
  const busyAmbs = ambulances.filter(a => a.status === 'busy').length;
  const totalDrivers = ambulances.length;

  const calculateAverageResponseTime = (list) => {
    const assignedCases = list.filter(e => e.assignedAt && e.createdAt);
    if (assignedCases.length === 0) return '0.0 mins';

    let totalDiffMs = 0;
    assignedCases.forEach((e) => {
      const diff = new Date(e.assignedAt) - new Date(e.createdAt);
      if (diff > 0) totalDiffMs += diff;
    });

    const avgMins = (totalDiffMs / assignedCases.length) / 60000;
    return `${avgMins.toFixed(1)} mins`;
  };

  // Sorting Active Emergencies as a Priority Queue (Critical -> High -> Medium -> Low, then newest)
  const getSeverityScore = (level) => {
    switch (level) {
      case 'critical': return 4;
      case 'high': return 3;
      case 'medium': return 2;
      case 'low': return 1;
      default: return 2;
    }
  };

  const priorityQueue = emergencies
    .filter(e => ['pending', 'assigned', 'arrived'].includes(e.status))
    .sort((a, b) => {
      const scoreA = getSeverityScore(a.severityLevel);
      const scoreB = getSeverityScore(b.severityLevel);
      if (scoreA !== scoreB) return scoreB - scoreA; // Descending severity
      return new Date(b.createdAt) - new Date(a.createdAt); // Newest first
    });

  // Calculate incident categories analytics
  const typeCounts = {
    accident: emergencies.filter(e => e.emergencyType === 'accident').length,
    cardiac: emergencies.filter(e => e.emergencyType === 'cardiac').length,
    respiratory: emergencies.filter(e => e.emergencyType === 'respiratory').length,
    stroke: emergencies.filter(e => e.emergencyType === 'stroke').length,
    pregnancy: emergencies.filter(e => e.emergencyType === 'pregnancy').length,
    other: emergencies.filter(e => e.emergencyType === 'other').length,
  };

  // Timeline events generation
  const getTimelineEvents = () => {
    const events = [];
    emergencies.forEach((e) => {
      if (e.createdAt) {
        events.push({
          time: new Date(e.createdAt),
          text: `[Reported] Emergency reported for ${e.patientName} (${(e.emergencyType || 'other').toUpperCase()})`,
          type: 'report'
        });
      }
      if (e.assignedAt) {
        events.push({
          time: new Date(e.assignedAt),
          text: `[Assigned] Driver ${e.driverName || 'Responder'} dispatched to ${e.patientName}`,
          type: 'assign'
        });
      }
      if (e.updatedAt && e.status === 'arrived') {
        events.push({
          time: new Date(e.updatedAt),
          text: `[Arrived] Driver ${e.driverName || 'Responder'} arrived at ${e.patientName}'s location`,
          type: 'arrive'
        });
      }
      if (e.updatedAt && e.status === 'completed') {
        events.push({
          time: new Date(e.updatedAt),
          text: `[Completed] Case for ${e.patientName} successfully completed`,
          type: 'complete'
        });
      }
      if (e.updatedAt && e.status === 'cancelled') {
        events.push({
          time: new Date(e.updatedAt),
          text: `[Cancelled] Request for ${e.patientName} was cancelled`,
          type: 'cancel'
        });
      }
    });
    return events.sort((a, b) => b.time - a.time).slice(0, 10);
  };

  return (
    <div className="container mt-4" style={{ maxWidth: '1400px' }} data-testid="admin-dashboard">
      <div className="mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '26px', fontWeight: 700, color: 'var(--text-main)', margin: 0 }}>Admin Dashboard</h2>
          <p className="text-muted" style={{ fontSize: '14px' }}>Real-time emergency monitoring, priority dispatch queue, and telemetry map.</p>
        </div>
        <Link to="/admin/live-map" className="btn btn-outline" style={{ display: 'inline-flex', padding: '10px 20px' }} data-testid="view-live-map-btn">
          View Live Map
        </Link>
      </div>

      {/* Analytics Grid Rows wrapped in data-testid="statistics-cards" */}
      <div data-testid="statistics-cards">
        {/* Analytics Grid Row 1 */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px', marginBottom: '20px' }}>
          <div className="card" style={{ borderLeft: '6px solid var(--primary)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Total Emergencies</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: 'var(--primary)' }}>{totalCount}</div>
          </div>

          <div className="card" style={{ borderLeft: '6px solid var(--accent-red)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Active Emergencies</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: 'var(--accent-red)' }}>{activeCount}</div>
          </div>

          <div className="card" style={{ borderLeft: '6px solid #B80000', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Critical Cases</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: '#B80000', display: 'flex', alignItems: 'center', gap: '8px' }}>
              {criticalCount} {criticalCount > 0 && <span className="pulse-dot" style={{ width: '12px', height: '12px', background: 'red', borderRadius: '50%', display: 'inline-block' }}></span>}
            </div>
          </div>

          <div className="card" style={{ borderLeft: '6px solid var(--accent-green)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Completed Cases</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: 'var(--accent-green)' }}>{completedCount}</div>
          </div>
        </div>

        {/* Analytics Grid Row 2 */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px', marginBottom: '24px' }}>
          <div className="card" style={{ borderLeft: '6px solid var(--accent-green)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Ambulances</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: 'var(--accent-green)' }}>{availableAmbs}</div>
          </div>

          <div className="card" style={{ borderLeft: '6px solid var(--primary)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Busy Ambulances</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: 'var(--primary)' }}>{busyAmbs}</div>
          </div>

          <div className="card" style={{ borderLeft: '6px solid var(--text-main)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Drivers</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: 'var(--text-main)' }}>{totalDrivers}</div>
          </div>

          <div className="card" style={{ borderLeft: '6px solid var(--accent-yellow)', padding: '20px' }}>
            <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Avg Response Time</span>
            <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '8px', color: '#B28E00' }}>{calculateAverageResponseTime(emergencies)}</div>
          </div>
        </div>
      </div>

      {/* Main Command Center Layout */}
      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>

        {/* Left Side: Priority Incident List */}
        <div style={{ flex: '1 1 380px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div className="card" style={{ maxHeight: '420px', overflowY: 'auto' }}>
            <h3 style={{ marginBottom: '16px', fontSize: '18px', borderBottom: '1px solid var(--border)', paddingBottom: '12px' }}>
              🚨 Priority Queue ({priorityQueue.length})
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {priorityQueue.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                  All systems clear. No active emergencies.
                </div>
              ) : (
                priorityQueue.map((em) => (
                  <div
                    key={em.id}
                    onClick={() => handleFocusIncident(em)}
                    style={{
                      cursor: 'pointer',
                      border: selectedIncident?.id === em.id ? '1.5px solid var(--primary)' : '1px solid var(--border)',
                      borderLeft: `5px solid ${em.severityLevel === 'critical' ? 'var(--accent-red)' : em.severityLevel === 'high' ? '#FF9500' : 'var(--accent-yellow)'}`,
                      borderRadius: 'var(--radius-md)',
                      padding: '14px',
                      background: selectedIncident?.id === em.id ? 'var(--primary-light)' : '#ffffff',
                      transition: 'all 0.2s'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <strong style={{ fontSize: '15px' }}>{em.patientName}</strong>
                      <span className={`badge ${em.severityLevel === 'critical' ? 'badge-danger' : em.severityLevel === 'high' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '10px', textTransform: 'uppercase' }}>
                        {em.severityLevel || 'medium'}
                      </span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px', fontSize: '12px', color: 'var(--text-muted)' }}>
                      <span>Type: {(em.emergencyType || 'other').toUpperCase()}</span>
                      <span className="badge" style={{ padding: '2px 6px', fontSize: '10px', background: em.status === 'pending' ? 'var(--accent-yellow-light)' : 'var(--accent-green-light)', color: em.status === 'pending' ? '#B28E00' : '#248A3D' }}>
                        {(em.status || 'unknown').toUpperCase()}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Active Incident Details Panel */}
          {selectedIncident && (
            <div className="card" style={{ borderLeft: '5px solid var(--primary)', background: '#f8fafc' }}>
              <h4 style={{ color: 'var(--primary-hover)' }}>Incident Details & Image Viewer</h4>
              <div style={{ fontSize: '13px', marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div><strong>Patient Name:</strong> {selectedIncident.patientName}</div>
                <div><strong>Symptoms/Description:</strong> {selectedIncident.description}</div>
                <div><strong>Coordinates:</strong> {selectedIncident.latitude.toFixed(6)}, {selectedIncident.longitude.toFixed(6)}</div>
                {selectedIncident.hospitalName && (
                  <div><strong>Destination:</strong> 🏥 {selectedIncident.hospitalName}</div>
                )}
                {selectedIncident.driverName && (
                  <div style={{ color: 'var(--primary-hover)', fontWeight: 600 }}>
                    🚑 Responder: {selectedIncident.driverName} ({selectedIncident.driverPhone || 'N/A'})
                  </div>
                )}
                {selectedIncident.imageUrl && (
                  <div style={{ marginTop: '8px' }}>
                    <p style={{ fontWeight: 600 }}>Scene Image Evidence:</p>
                    <img
                      src={selectedIncident.imageUrl}
                      alt="Accident scene"
                      style={{ width: '100%', maxHeight: '160px', objectFit: 'cover', borderRadius: 'var(--radius-md)', border: '1px solid var(--border)', marginTop: '4px' }}
                    />
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Right Side: Map & Ambulance Fleet List */}
        <div style={{ flex: '2 1 600px', display: 'flex', flexDirection: 'column', gap: '24px' }}>

          {/* Dispatch Live Map */}
          <div className="card" style={{ padding: '0', overflow: 'hidden', height: '420px', border: '1px solid var(--border)', position: 'relative' }}>
            <div ref={mapRef} id="map" style={{ width: '100%', height: '100%', zIndex: 1 }} />
          </div>
        </div>
      </div>

      {/* Incident Analytics Widget */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))', gap: '24px', marginTop: '24px' }}>
        <div className="card">
          <h3 style={{ fontSize: '16px', borderBottom: '1px solid var(--border)', paddingBottom: '12px', marginBottom: '16px' }}>
            📊 Incident Categories Analytics
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {Object.entries(typeCounts).map(([type, count]) => {
              const percentage = totalCount > 0 ? (count / totalCount) * 100 : 0;
              return (
                <div key={type}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: 500, textTransform: 'capitalize', marginBottom: '4px' }}>
                    <span>{type}</span>
                    <span>{count} ({percentage.toFixed(0)}%)</span>
                  </div>
                  <div style={{ width: '100%', height: '8px', background: '#f1f5f9', borderRadius: '4px', overflow: 'hidden' }}>
                    <div style={{ width: `${percentage}%`, height: '100%', background: 'var(--primary)', borderRadius: '4px' }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="card">
          <h3 style={{ fontSize: '16px', borderBottom: '1px solid var(--border)', paddingBottom: '12px', marginBottom: '16px' }}>
            📈 Severity Level Breakdown
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {['critical', 'high', 'medium', 'low'].map((sev) => {
              const count = emergencies.filter(e => e.severityLevel === sev).length;
              const percentage = totalCount > 0 ? (count / totalCount) * 100 : 0;
              const color = sev === 'critical' ? '#B80000' : sev === 'high' ? 'var(--accent-red)' : sev === 'medium' ? 'var(--accent-yellow)' : 'var(--accent-green)';
              return (
                <div key={sev}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: 500, textTransform: 'capitalize', marginBottom: '4px' }}>
                    <span>{sev}</span>
                    <span>{count} ({percentage.toFixed(0)}%)</span>
                  </div>
                  <div style={{ width: '100%', height: '8px', background: '#f1f5f9', borderRadius: '4px', overflow: 'hidden' }}>
                    <div style={{ width: `${percentage}%`, height: '100%', background: color, borderRadius: '4px' }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Live Incidents Log Table & Timeline */}
      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap', marginTop: '24px', marginBottom: '40px' }}>

        {/* Live Emergency Table */}
        <div className="card" style={{ flex: '2 1 650px', maxHeight: '450px', overflowY: 'auto' }}>
          <h3 style={{ fontSize: '18px', borderBottom: '1px solid var(--border)', paddingBottom: '12px', marginBottom: '16px' }}>
            📋 Live Incidents & Response Log
          </h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left', fontWeight: 'bold' }}>
                <th style={{ padding: '10px' }}>Patient</th>
                <th style={{ padding: '10px' }}>Type</th>
                <th style={{ padding: '10px' }}>Severity</th>
                <th style={{ padding: '10px' }}>Responder</th>
                <th style={{ padding: '10px' }}>Status</th>
                <th style={{ padding: '10px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {emergencies.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>
                    No reported incidents on network.
                  </td>
                </tr>
              ) : (
                emergencies.map((em) => (
                  <tr key={em.id} style={{ borderBottom: '1px solid var(--border)', background: selectedIncident?.id === em.id ? 'var(--primary-light)' : 'transparent' }}>
                    <td style={{ padding: '10px', fontWeight: 600 }}>{em.patientName}</td>
                    <td style={{ padding: '10px', textTransform: 'capitalize' }}>{em.emergencyType}</td>
                    <td style={{ padding: '10px' }}>
                      <span className={`badge ${em.severityLevel === 'critical' ? 'badge-danger' : em.severityLevel === 'high' ? 'badge-warning' : 'badge-success'}`} style={{ fontSize: '10px' }}>
                        {em.severityLevel || 'medium'}
                      </span>
                    </td>
                    <td style={{ padding: '10px' }}>{em.driverName || 'Awaiting dispatch'}</td>
                    <td style={{ padding: '10px' }}>
                      <span className="badge" style={{
                        fontSize: '10px',
                        background: em.status === 'pending' ? 'var(--accent-yellow-light)' : em.status === 'completed' ? 'var(--accent-green-light)' : em.status === 'cancelled' ? '#f1f5f9' : 'var(--primary-light)',
                        color: em.status === 'pending' ? '#B28E00' : em.status === 'completed' ? '#248A3D' : em.status === 'cancelled' ? 'var(--text-muted)' : 'var(--primary)'
                      }}>
                        {em.status.toUpperCase()}
                      </span>
                    </td>
                    <td style={{ padding: '10px' }}>
                      <button className="btn btn-outline" style={{ padding: '4px 10px', fontSize: '11px', borderRadius: '4px' }} onClick={() => handleFocusIncident(em)}>
                        Focus
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Live Timeline Feed */}
        <div className="card" style={{ flex: '1 1 300px', maxHeight: '450px', overflowY: 'auto' }}>
          <h3 style={{ fontSize: '18px', borderBottom: '1px solid var(--border)', paddingBottom: '12px', marginBottom: '16px' }}>
            ⏱️ Recent Activity
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {getTimelineEvents().length === 0 ? (
              <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '20px' }}>
                Awaiting telemetry updates...
              </div>
            ) : (
              getTimelineEvents().map((evt, idx) => (
                <div key={idx} style={{ display: 'flex', gap: '12px', borderLeft: '2px solid var(--border)', paddingLeft: '12px', marginLeft: '6px', position: 'relative' }}>
                  <div style={{
                    position: 'absolute',
                    left: '-5px',
                    top: '2px',
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    background: evt.type === 'complete' ? 'var(--accent-green)' : evt.type === 'report' ? 'var(--accent-red)' : 'var(--primary)'
                  }} />
                  <div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                      {evt.time.toLocaleTimeString()}
                    </div>
                    <div style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-main)', marginTop: '2px' }}>
                      {evt.text}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

      </div>
    </div>
  );
};

export default AdminDashboard;
