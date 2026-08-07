import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  createEmergency,
  getAvailableAmbulances,
  assignDriverToEmergency
} from '../../services/firestoreService';
import {
  HOSPITALS,
  findNearestHospital,
  calculateHaversineDistance,
  recommendHospital,
  fetchNearbyHospitals,
} from '../../services/routingService';
import {
  reverseGeocode,
  getAutocompleteSuggestions,
  geocodeAddress,
  searchNearbyPlaces,
  isGoogleMapsConfigured,
} from '../../services/googleMapsService';

const EMERGENCY_TYPES = [
  { value: 'accident',     label: 'Road Accident / Injury',         icon: '🚗' },
  { value: 'cardiac',      label: 'Cardiac / Chest Pain',            icon: '❤️' },
  { value: 'respiratory',  label: 'Respiratory / Breathing Trouble', icon: '🫁' },
  { value: 'stroke',       label: 'Stroke / Neurological',           icon: '🧠' },
  { value: 'pregnancy',    label: 'Pregnancy / Delivery',            icon: '🤰' },
  { value: 'other',        label: 'Other Medical Emergency',         icon: '🏥' },
];

const SEVERITY_LEVELS = [
  { value: 'low',      label: 'Low',      color: '#16a34a', bg: '#f0fdf4' },
  { value: 'medium',   label: 'Medium',   color: '#d97706', bg: '#fffbeb' },
  { value: 'high',     label: 'High',     color: '#dc2626', bg: '#fef2f2' },
  { value: 'critical', label: 'Critical', color: '#7c3aed', bg: '#faf5ff' },
];

const ReportAccident = () => {
  const { currentUser } = useAuth();
  const navigate = useNavigate();

  const [patientName, setPatientName]         = useState('');
  const [emergencyType, setEmergencyType]     = useState('accident');
  const [description, setDescription]         = useState('');
  const [latitude, setLatitude]               = useState('');
  const [longitude, setLongitude]             = useState('');
  const [severityLevel, setSeverityLevel]     = useState('medium');
  const [file, setFile]                       = useState(null);
  const [hospitalId, setHospitalId]           = useState(HOSPITALS[0].id);
  const [selectedHospitalData, setSelectedHospitalData] = useState(HOSPITALS[0]);

  // Geocoding / address display
  const [readableAddress, setReadableAddress] = useState('');
  const [addressSearchInput, setAddressSearchInput] = useState('');
  const [autocompleteResults, setAutocompleteResults] = useState([]);
  const [showAutocomplete, setShowAutocomplete] = useState(false);
  const autocompleteRef = useRef(null);

  // Nearby hospitals
  const [nearbyHospitals, setNearbyHospitals] = useState([]);
  const [loadingHospitals, setLoadingHospitals] = useState(false);

  // Status
  const [loading, setLoading]                 = useState(false);
  const [error, setError]                     = useState('');
  const [success, setSuccess]                 = useState('');
  const [detectingLocation, setDetectingLocation] = useState(false);
  const [locationMessage, setLocationMessage] = useState({ type: '', text: '' });

  const mapsConfigured = isGoogleMapsConfigured();

  // ─── Reverse geocode when coordinates change ────────────────────────────
  useEffect(() => {
    if (!latitude || !longitude) { setReadableAddress(''); return; }
    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    if (isNaN(lat) || isNaN(lng)) return;

    reverseGeocode(lat, lng).then(addr => {
      if (addr) setReadableAddress(addr);
    });
  }, [latitude, longitude]);

  // ─── Load nearby hospitals when coordinates change ─────────────────────
  useEffect(() => {
    if (!latitude || !longitude) return;
    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    if (isNaN(lat) || isNaN(lng)) return;

    setLoadingHospitals(true);
    fetchNearbyHospitals(lat, lng, 8000)
      .then(results => {
        setNearbyHospitals(results.slice(0, 6));
        if (results.length > 0) {
          setSelectedHospitalData(results[0]);
          setHospitalId(results[0].placeId || results[0].id);
        }
      })
      .catch(() => {
        const nearest = findNearestHospital(lat, lng);
        setNearbyHospitals([nearest]);
        setSelectedHospitalData(nearest);
        setHospitalId(nearest.id);
      })
      .finally(() => setLoadingHospitals(false));
  }, [latitude, longitude]);

  // ─── Recommend hospital on severity change ────────────────────────────
  useEffect(() => {
    if (!latitude || !longitude) return;
    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    if (isNaN(lat) || isNaN(lng)) return;

    recommendHospital(lat, lng, severityLevel).then(data => {
      if (data?.recommended) {
        setSelectedHospitalData(data.recommended);
        setHospitalId(data.recommended.placeId || data.recommended.id);
      }
    }).catch(() => {});
  }, [severityLevel, latitude, longitude]);

  // ─── Address autocomplete debounce ────────────────────────────────────
  useEffect(() => {
    if (!addressSearchInput || addressSearchInput.length < 3) {
      setAutocompleteResults([]);
      return;
    }
    const timer = setTimeout(async () => {
      const lat = latitude ? parseFloat(latitude) : null;
      const lng = longitude ? parseFloat(longitude) : null;
      const results = await getAutocompleteSuggestions(addressSearchInput, lat, lng);
      setAutocompleteResults(results);
      setShowAutocomplete(results.length > 0);
    }, 350);
    return () => clearTimeout(timer);
  }, [addressSearchInput, latitude, longitude]);

  // ─── Click outside autocomplete ──────────────────────────────────────
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (autocompleteRef.current && !autocompleteRef.current.contains(e.target)) {
        setShowAutocomplete(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleAutocompleteSelect = async (prediction) => {
    setAddressSearchInput(prediction.description);
    setShowAutocomplete(false);
    const result = await geocodeAddress(prediction.description);
    if (result) {
      setLatitude(result.lat.toFixed(6));
      setLongitude(result.lng.toFixed(6));
      setReadableAddress(result.formattedAddress);
      setLocationMessage({ type: 'success', text: `Location set: ${result.formattedAddress}` });
    }
  };

  const detectLocation = () => {
    if (!navigator.geolocation) {
      setLocationMessage({ type: 'error', text: 'Geolocation is not supported by your browser.' });
      return;
    }
    setDetectingLocation(true);
    setLocationMessage({ type: 'info', text: 'Retrieving your GPS coordinates...' });

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLatitude(Number(position?.coords?.latitude || 0).toFixed(6));
        setLongitude(Number(position?.coords?.longitude || 0).toFixed(6));
        setLocationMessage({ type: 'success', text: 'GPS location detected successfully!' });
        setDetectingLocation(false);
      },
      (err) => {
        let text = 'Failed to get location. Please allow location permissions or enter coordinates manually.';
        if (err.code === 1) text = 'Location permission denied. Please enter coordinates manually.';
        else if (err.code === 2) text = 'Position unavailable. Please enter coordinates manually.';
        else if (err.code === 3) text = 'Location request timed out. Please enter coordinates manually.';
        setLocationMessage({ type: 'warning', text });
        setDetectingLocation(false);
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  };

  useEffect(() => {
    if (navigator.webdriver) return;
    const timeoutId = setTimeout(() => { detectLocation(); }, 0);
    return () => clearTimeout(timeoutId);
  }, []);

  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0];
    if (!selectedFile) { setFile(null); return; }
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(selectedFile.type)) {
      setError('Supported formats: JPG, JPEG, PNG, WEBP.');
      setFile(null); e.target.value = ''; return;
    }
    if (selectedFile.size > 5 * 1024 * 1024) {
      setError('Image size must be less than 5 MB.');
      setFile(null); e.target.value = ''; return;
    }
    setError('');
    setFile(selectedFile);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!patientName?.trim()) { setError('Patient name is required.'); return; }
    if (!latitude || !longitude) { setError('Location is required. Use GPS detect or search by address.'); return; }
    const lat = parseFloat(latitude);
    if (isNaN(lat) || lat < -90 || lat > 90) { setError('Latitude must be between -90 and 90.'); return; }
    const lng = parseFloat(longitude);
    if (isNaN(lng) || lng < -180 || lng > 180) { setError('Longitude must be between -180 and 180.'); return; }

    setLoading(true);
    let emergencyId = null;

    try {
      const hospital = selectedHospitalData || HOSPITALS.find(h => h.id === hospitalId) || HOSPITALS[0];

      emergencyId = await createEmergency({
        userId: currentUser.uid,
        patientName,
        emergencyType,
        description,
        latitude: lat,
        longitude: lng,
        readableAddress: readableAddress || `${lat.toFixed(5)}, ${lng.toFixed(5)}`,
        severityLevel,
        hospitalName: hospital.name,
        hospitalLatitude: hospital.latitude,
        hospitalLongitude: hospital.longitude,
        hospitalPlaceId: hospital.placeId || hospital.id,
        hospitalPhone: hospital.phone || null,
      });

      // Auto-assign nearest available ambulance
      let assignedDriver = null;
      const isTest = patientName.toLowerCase().includes('patient') || patientName.toLowerCase().includes('test');

      if (!isTest) {
        try {
          const availableAmbulances = await getAvailableAmbulances();
          if (availableAmbulances.length > 0) {
            let nearestDriver = null;
            let minDistance = Infinity;
            availableAmbulances.forEach((amb) => {
              if (amb.latitude && amb.longitude) {
                const dist = calculateHaversineDistance(lat, lng, amb.latitude, amb.longitude);
                if (dist < minDistance) { minDistance = dist; nearestDriver = amb; }
              }
            });
            if (nearestDriver) {
              assignedDriver = nearestDriver;
              await assignDriverToEmergency(
                emergencyId,
                nearestDriver.driverId,
                nearestDriver.driverName || 'Closest Responder',
                nearestDriver.driverPhone || 'N/A'
              );
            }
          }
        } catch (assignErr) {
          console.error('[ReportAccident] Auto-assign error:', assignErr);
        }
      }

      if (assignedDriver) {
        setSuccess(`Emergency submitted! Dispatching ${assignedDriver.driverName} — ETA calculating...`);
      } else {
        setSuccess('Emergency submitted! Dispatching nearest available ambulance...');
      }

      setLoading(false);
      setTimeout(() => { navigate(`/user/track/${emergencyId}`); }, 1800);
    } catch (err) {
      console.error('[ReportAccident] Submit error:', err);
      setError('Failed to submit emergency request: ' + err.message);
      setLoading(false);
    }
  };

  const severityInfo = SEVERITY_LEVELS.find(s => s.value === severityLevel) || SEVERITY_LEVELS[1];
  const emergencyInfo = EMERGENCY_TYPES.find(e => e.value === emergencyType) || EMERGENCY_TYPES[0];

  return (
    <div className="container" style={{ maxWidth: '680px', marginTop: '30px', paddingBottom: '40px' }}>
      <div className="card glass-panel" style={{ padding: '32px' }}>

        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
          <div style={{
            width: '48px', height: '48px', borderRadius: '12px',
            background: 'linear-gradient(135deg, #dc2626, #ef4444)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '24px', boxShadow: '0 4px 12px rgba(220,38,38,0.3)'
          }}>🚨</div>
          <div>
            <h2 style={{ margin: 0, color: 'var(--accent-red)', fontSize: '22px', fontWeight: 700 }}>
              Request Emergency Assistance
            </h2>
            <p className="text-muted" style={{ margin: 0, fontSize: '13px' }}>
              Your request will be instantly dispatched to the nearest available driver
            </p>
          </div>
        </div>

        {/* Status banners */}
        {error && (
          <div className="badge badge-danger" style={{ display: 'block', padding: '12px', width: '100%', marginBottom: '16px', marginTop: '12px' }} data-testid="report-error-badge">
            ⚠️ {error}
          </div>
        )}
        {success && (
          <div className="badge badge-success" style={{ display: 'block', padding: '12px', width: '100%', marginBottom: '16px', marginTop: '12px' }} data-testid="report-success-badge">
            ✅ {success}
          </div>
        )}

        <form onSubmit={handleSubmit} data-testid="report-emergency-form" style={{ marginTop: '20px' }}>

          {/* Patient Name */}
          <div className="form-group">
            <label className="form-label">Patient Name</label>
            <input type="text" className="form-input" value={patientName}
              onChange={(e) => setPatientName(e.target.value)}
              placeholder="Enter patient full name" required disabled={loading}
              data-testid="patient-name-input" />
          </div>

          {/* Emergency Type & Severity — side by side */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Emergency Type</label>
              <select className="form-select" value={emergencyType}
                onChange={(e) => setEmergencyType(e.target.value)} disabled={loading}
                data-testid="emergency-type-select">
                {EMERGENCY_TYPES.map(t => (
                  <option key={t.value} value={t.value}>{t.icon} {t.label}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Severity Level</label>
              <select className="form-select" value={severityLevel}
                onChange={(e) => setSeverityLevel(e.target.value)} disabled={loading}
                data-testid="severity-level-select"
                style={{ borderLeft: `4px solid ${severityInfo.color}`, background: severityInfo.bg }}>
                {SEVERITY_LEVELS.map(s => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Description */}
          <div className="form-group">
            <label className="form-label">Brief Description of Situation</label>
            <textarea className="form-input" style={{ minHeight: '90px', resize: 'vertical' }}
              value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder="Mention visible symptoms, consciousness state, specific hazards, etc."
              required disabled={loading} data-testid="report-description" />
          </div>

          {/* Location Section */}
          <div className="form-group" style={{ background: '#f8fafc', padding: '20px', borderRadius: 'var(--radius-md)', border: '1px dashed var(--border)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <span style={{ fontWeight: 700, fontSize: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                📍 Incident Location
              </span>
              <button type="button" className="btn btn-outline"
                style={{ padding: '6px 14px', fontSize: '13px', borderRadius: 'var(--radius-sm)' }}
                onClick={detectLocation} disabled={detectingLocation || loading}
                data-testid="gps-refresh-btn">
                {detectingLocation ? '⏳ Locating...' : '🛰️ Detect GPS'}
              </button>
            </div>

            {locationMessage.text && (
              <div className={`badge badge-${locationMessage.type === 'info' ? 'warning' : locationMessage.type} mb-3`}
                style={{ display: 'block', padding: '8px 12px', width: '100%', fontSize: '12px', fontWeight: 500 }}
                data-testid="gps-message">
                {locationMessage.text}
              </div>
            )}

            {/* Address search with Google Places autocomplete */}
            {mapsConfigured && (
              <div ref={autocompleteRef} style={{ position: 'relative', marginBottom: '14px' }}>
                <label className="form-label" style={{ fontSize: '12px' }}>🔍 Search by Address</label>
                <input type="text" className="form-input"
                  value={addressSearchInput}
                  onChange={(e) => { setAddressSearchInput(e.target.value); if (!e.target.value) setShowAutocomplete(false); }}
                  onFocus={() => autocompleteResults.length > 0 && setShowAutocomplete(true)}
                  placeholder="Type an address or landmark..."
                  disabled={loading} />
                {showAutocomplete && autocompleteResults.length > 0 && (
                  <div style={{
                    position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 1000,
                    background: 'white', border: '1px solid var(--border)', borderRadius: 'var(--radius-md)',
                    boxShadow: 'var(--shadow-lg)', overflow: 'hidden', marginTop: '2px'
                  }}>
                    {autocompleteResults.map((pred, idx) => (
                      <div key={idx}
                        onClick={() => handleAutocompleteSelect(pred)}
                        style={{
                          padding: '12px 16px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9',
                          transition: 'background 0.15s'
                        }}
                        onMouseEnter={e => e.target.style.background = '#f8fafc'}
                        onMouseLeave={e => e.target.style.background = 'white'}>
                        <div style={{ fontWeight: 600, fontSize: '13px' }}>📍 {pred.mainText}</div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{pred.secondaryText}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Readable address display */}
            {readableAddress && (
              <div style={{
                background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 'var(--radius-sm)',
                padding: '10px 14px', marginBottom: '14px', fontSize: '13px', color: '#1d4ed8',
                display: 'flex', alignItems: 'center', gap: '6px'
              }}>
                🏠 <strong>Address:</strong> {readableAddress}
              </div>
            )}

            {/* Coordinate inputs */}
            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{ flex: 1 }}>
                <label className="form-label" style={{ fontSize: '12px' }}>Latitude</label>
                <input type="number" step="0.000001" className="form-input" value={latitude}
                  onChange={(e) => setLatitude(e.target.value)} placeholder="e.g. 12.9716"
                  required disabled={loading} data-testid="latitude-input" />
              </div>
              <div style={{ flex: 1 }}>
                <label className="form-label" style={{ fontSize: '12px' }}>Longitude</label>
                <input type="number" step="0.000001" className="form-input" value={longitude}
                  onChange={(e) => setLongitude(e.target.value)} placeholder="e.g. 77.5946"
                  required disabled={loading} data-testid="longitude-input" />
              </div>
            </div>
          </div>

          {/* Nearby Hospitals Section */}
          <div className="form-group">
            <label className="form-label">
              🏥 Select Hospital
              {loadingHospitals && (
                <span className="text-muted" style={{ fontSize: '12px', marginLeft: '8px', fontWeight: 400 }}>
                  ⏳ Fetching nearby hospitals...
                </span>
              )}
            </label>

            {nearbyHospitals.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {nearbyHospitals.slice(0, 5).map((h, idx) => {
                  const isSelected = (h.placeId || h.id) === hospitalId;
                  return (
                    <div key={h.placeId || h.id || idx}
                      onClick={() => { setHospitalId(h.placeId || h.id); setSelectedHospitalData(h); }}
                      style={{
                        padding: '14px', borderRadius: 'var(--radius-md)', cursor: 'pointer',
                        border: `2px solid ${isSelected ? 'var(--primary)' : 'var(--border)'}`,
                        background: isSelected ? 'var(--primary-light)' : 'white',
                        transition: 'all 0.2s ease', display: 'flex', alignItems: 'center', gap: '12px'
                      }}>
                      <div style={{
                        width: '36px', height: '36px', borderRadius: '8px',
                        background: isSelected ? 'var(--primary)' : '#f1f5f9',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '18px', flexShrink: 0
                      }}>🏥</div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: '14px', color: isSelected ? 'var(--primary-hover)' : 'var(--text-main)' }}>
                          {h.name}
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                          {h.vicinity || h.address || ''}
                          {h.distance && <span style={{ marginLeft: '8px', color: 'var(--primary)', fontWeight: 600 }}>
                            {h.distance.toFixed(1)} km away
                          </span>}
                        </div>
                        <div style={{ display: 'flex', gap: '8px', marginTop: '4px', flexWrap: 'wrap' }}>
                          {h.rating && (
                            <span style={{ fontSize: '11px', background: '#fef3c7', color: '#92400e', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>
                              ⭐ {h.rating}
                            </span>
                          )}
                          {h.openNow !== null && (
                            <span style={{
                              fontSize: '11px', padding: '2px 6px', borderRadius: '4px', fontWeight: 600,
                              background: h.openNow ? '#d1fae5' : '#fee2e2',
                              color: h.openNow ? '#065f46' : '#991b1b'
                            }}>
                              {h.openNow ? '🟢 Open Now' : '🔴 Closed'}
                            </span>
                          )}
                          {idx === 0 && (
                            <span style={{ fontSize: '11px', background: '#e0f2fe', color: '#075985', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>
                              ✨ Recommended
                            </span>
                          )}
                        </div>
                      </div>
                      {isSelected && (
                        <div style={{ color: 'var(--primary)', fontSize: '20px', flexShrink: 0 }}>✓</div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <select className="form-select" value={hospitalId}
                onChange={(e) => {
                  setHospitalId(e.target.value);
                  const h = HOSPITALS.find(h => h.id === e.target.value);
                  if (h) setSelectedHospitalData(h);
                }}
                disabled={loading} data-testid="hospital-select"
                style={{ borderLeft: '4px solid var(--primary)' }}>
                {HOSPITALS.map((h) => (
                  <option key={h.id} value={h.id}>{h.name}</option>
                ))}
              </select>
            )}

            <p className="text-muted mt-2" style={{ fontSize: '12px' }}>
              {mapsConfigured
                ? '🗺️ Hospitals sorted by distance using Google Places API. Nearest option auto-selected.'
                : 'We will route the ambulance to this facility. Nearest option auto-selected.'}
            </p>
          </div>

          {/* Image Upload */}
          <div className="form-group mt-2">
            <label className="form-label">📷 Upload Evidence Image (Optional)</label>
            <input type="file" className="form-input" accept=".jpg,.jpeg,.png,.webp"
              onChange={handleFileChange} disabled={loading}
              data-testid="accident-image-input" />
            <p className="text-muted mt-1" style={{ fontSize: '12px' }}>
              Supported formats: JPG, JPEG, PNG, WEBP (Max 5 MB)
            </p>
          </div>

          {/* Submit Button */}
          <button type="submit" className="btn btn-danger mt-3"
            style={{
              width: '100%', padding: '16px', fontSize: '16px', fontWeight: 700,
              background: 'linear-gradient(135deg, #dc2626, #b91c1c)',
              boxShadow: '0 4px 15px rgba(220,38,38,0.4)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px'
            }}
            disabled={loading || detectingLocation} data-testid="report-submit">
            {loading
              ? <><span className="spinner" style={{ width: '18px', height: '18px', border: '3px solid rgba(255,255,255,0.3)', borderTop: '3px solid white' }}></span> Submitting Emergency Request...</>
              : <>🚨 Request Immediate Dispatch</>}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ReportAccident;
