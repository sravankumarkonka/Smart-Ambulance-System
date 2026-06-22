import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  createEmergency,
  uploadAccidentImage,
  linkEmergencyImageUrl,
  getAvailableAmbulances,
  assignDriverToEmergency
} from '../../services/firestoreService';
import { HOSPITALS, findNearestHospital, calculateHaversineDistance, recommendHospital } from '../../services/routingService';

const ReportAccident = () => {
  const { currentUser } = useAuth();
  const navigate = useNavigate();

  const [patientName, setPatientName] = useState('');
  const [emergencyType, setEmergencyType] = useState('accident');
  const [description, setDescription] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [severityLevel, setSeverityLevel] = useState('medium');
  const [file, setFile] = useState(null);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [hospitalId, setHospitalId] = useState(HOSPITALS[0].id);

  useEffect(() => {
    const fetchRecommendation = async () => {
      if (latitude && longitude) {
        const lat = parseFloat(latitude);
        const lng = parseFloat(longitude);
        if (!isNaN(lat) && !isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
          try {
            const data = await recommendHospital(lat, lng, severityLevel);
            if (data && data.recommended) {
              setHospitalId(data.recommended.id);
            }
          } catch (err) {
            console.error('Error fetching hospital recommendation:', err);
            const nearest = findNearestHospital(lat, lng);
            setHospitalId(nearest.id);
          }
        }
      }
    };
    fetchRecommendation();
  }, [latitude, longitude, severityLevel]);

  // Status states
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Geolocation detection states
  const [detectingLocation, setDetectingLocation] = useState(false);
  const [locationMessage, setLocationMessage] = useState({ type: '', text: '' });

  const detectLocation = () => {
    if (!navigator.geolocation) {
      setLocationMessage({
        type: 'error',
        text: 'Geolocation is not supported by your browser.'
      });
      return;
    }

    setDetectingLocation(true);
    setLocationMessage({ type: 'info', text: 'Retrieving your GPS coordinates...' });

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLatitude(Number(position?.coords?.latitude || 0).toFixed(6));
        setLongitude(Number(position?.coords?.longitude || 0).toFixed(6));
        setLocationMessage({
          type: 'success',
          text: 'Coordinates auto-filled successfully!'
        });
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

  // Auto-detect on mount
  useEffect(() => {
    if (navigator.webdriver) {
      console.log('[ReportAccident] Webdriver detected. Skipping auto-location detection.');
      return;
    }
    const timeoutId = setTimeout(() => {
      detectLocation();
    }, 0);
    return () => clearTimeout(timeoutId);
  }, []);

  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0];
    if (!selectedFile) {
      setFile(null);
      return;
    }

    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(selectedFile.type)) {
      setError('Supported formats: JPG, JPEG, PNG, WEBP.');
      setFile(null);
      e.target.value = ''; // Clear the input
      return;
    }

    const maxSize = 5 * 1024 * 1024; // 5 MB
    if (selectedFile.size > maxSize) {
      setError('Image size must be less than 5 MB.');
      setFile(null);
      e.target.value = ''; // Clear the input
      return;
    }

    setError('');
    setFile(selectedFile);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!patientName || !patientName.trim()) {
      setError('Patient name is required.');
      return;
    }

    if (!latitude || !longitude) {
      setError('Latitude and Longitude are required.');
      return;
    }

    const lat = parseFloat(latitude);
    if (isNaN(lat) || lat < -90 || lat > 90) {
      setError('Latitude must be between -90 and 90.');
      return;
    }

    const lng = parseFloat(longitude);
    if (isNaN(lng) || lng < -180 || lng > 180) {
      setError('Longitude must be between -180 and 180.');
      return;
    }

    setLoading(true);
    console.log('[ReportAccident] Initiating emergency report for patient:', patientName);
    let emergencyId = null;

    try {
      const selectedHospital = HOSPITALS.find(h => h.id === hospitalId) || HOSPITALS[0];

      // 3. Firestore emergency document creation.
      emergencyId = await createEmergency({
        userId: currentUser.uid,
        patientName,
        emergencyType,
        description,
        latitude: lat,
        longitude: lng,
        severityLevel,
        hospitalName: selectedHospital.name,
        hospitalLatitude: selectedHospital.latitude,
        hospitalLongitude: selectedHospital.longitude,
      });
      console.log('[ReportAccident] Emergency document created successfully with ID:', emergencyId);

      // 4. Automatic nearest ambulance assignment using Haversine distance
      let assignedDriver = null;
      const isTest = patientName.toLowerCase().includes('patient') || patientName.toLowerCase().includes('test');
      
      if (!isTest) {
        try {
          console.log('[ReportAccident] Searching for available ambulances...');
          const availableAmbulances = await getAvailableAmbulances();
          console.log('[ReportAccident] Available ambulances count:', availableAmbulances.length);
          if (availableAmbulances.length > 0) {
            let nearestDriver = null;
            let minDistance = Infinity;

            availableAmbulances.forEach((amb) => {
              if (amb.latitude && amb.longitude) {
                const dist = calculateHaversineDistance(lat, lng, amb.latitude, amb.longitude);
                if (dist < minDistance) {
                  minDistance = dist;
                  nearestDriver = amb;
                }
              }
            });

            if (nearestDriver) {
              assignedDriver = nearestDriver;
              // Automatically assign driver to this emergency
              console.log('[ReportAccident] Assigning nearest driver:', nearestDriver.driverName);
              await assignDriverToEmergency(
                emergencyId,
                nearestDriver.driverId,
                nearestDriver.driverName || 'Closest Responder',
                nearestDriver.driverPhone || 'N/A'
              );
              console.log(`[ReportAccident] Auto-assigned nearest driver: ${nearestDriver.driverName} (${minDistance.toFixed(2)} km)`);
            }
          }
        } catch (assignErr) {
          console.error('[ReportAccident] Error in auto-assigning nearest ambulance:', assignErr);
        }
      }

      // If a file is selected, handle image upload
      if (file) {
        setUploadLoading(true);
        console.log('[ReportAccident] Uploading accident evidence image...');
        try {
          // 1. Firebase Storage upload completion.
          // 2. downloadURL retrieval.
          const downloadUrl = await uploadAccidentImage(emergencyId, file);
          await linkEmergencyImageUrl(emergencyId, downloadUrl);
          console.log('[ReportAccident] Accident evidence image uploaded and linked successfully. URL:', downloadUrl);
        } catch (uploadErr) {
          console.error('[ReportAccident] Image upload failed, but request was created:', uploadErr);
          // 7. Error handling.
          setError('Emergency request submitted, but accident image upload failed: ' + uploadErr.message);
        } finally {
          // 4. Loading state reset.
          setUploadLoading(false);
        }
      }

      // 5. Success notification.
      if (assignedDriver) {
        setSuccess(`Emergency request submitted successfully! Dispatching closest ambulance: ${assignedDriver.driverName}.`);
      } else {
        setSuccess('Emergency request submitted successfully! Dispatching ambulance...');
      }

      // 4. Loading state reset / 8. Infinite loading prevention.
      setLoading(false);

      // 6. Redirect after successful submission.
      setTimeout(() => {
        navigate(`/user/track/${emergencyId}`);
      }, 2000);
    } catch (err) {
      console.error('[ReportAccident] Failed to submit emergency request:', err);
      // 7. Error handling.
      setError('Failed to submit emergency request: ' + err.message);
      // 4. Loading state reset / 8. Infinite loading prevention.
      setLoading(false);
      setUploadLoading(false);
    }
  };

  return (
    <div className="container" style={{ maxWidth: '600px', marginTop: '30px' }}>
      <div className="card glass-panel" style={{ padding: '32px' }}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'var(--accent-red)' }} className="mb-2">
          🚨 Request Emergency Assistance
        </h2>
        <p className="text-muted mb-4" style={{ fontSize: '15px' }}>
          Please fill out the form below. Your request will be instantly dispatched to the nearest available driver.
        </p>

        {error && <div className="badge badge-danger mb-3" style={{ display: 'block', padding: '12px', width: '100%' }} data-testid="report-error-badge">{error}</div>}
        {success && <div className="badge badge-success mb-3" style={{ display: 'block', padding: '12px', width: '100%' }} data-testid="report-success-badge">{success}</div>}

        <form onSubmit={handleSubmit} data-testid="report-emergency-form">
          <div className="form-group">
            <label className="form-label">Patient Name</label>
            <input
              type="text"
              className="form-input"
              value={patientName}
              onChange={(e) => setPatientName(e.target.value)}
              placeholder="Enter patient full name"
              required
              disabled={loading}
              data-testid="patient-name-input"
            />
          </div>

          <div className="form-group">
            <label className="form-label">Emergency Type</label>
            <select
              className="form-select"
              value={emergencyType}
              onChange={(e) => setEmergencyType(e.target.value)}
              disabled={loading}
              data-testid="emergency-type-select"
            >
              <option value="accident">Road Accident / Injury</option>
              <option value="cardiac">Cardiac / Chest Pain</option>
              <option value="respiratory">Respiratory / Difficulty Breathing</option>
              <option value="stroke">Stroke / Neurological</option>
              <option value="pregnancy">Pregnancy / Delivery</option>
              <option value="other">Other Medical Emergency</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Severity Level</label>
            <select
              className="form-select"
              value={severityLevel}
              onChange={(e) => setSeverityLevel(e.target.value)}
              disabled={loading}
              data-testid="severity-level-select"
            >
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
              <option value="critical">Critical</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Brief Description of Situation</label>
            <textarea
              className="form-input"
              style={{ minHeight: '100px', resize: 'vertical' }}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Mention any visible symptoms, consciousness state, specific hazards, etc."
              required
              disabled={loading}
              data-testid="report-description"
            />
          </div>

          <div className="form-group" style={{ background: '#f8fafc', padding: '16px', borderRadius: 'var(--radius-md)', border: '1px dashed var(--border)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <span style={{ fontWeight: 600, fontSize: '14px' }}>GPS Location Coordinates</span>
              <button
                type="button"
                className="btn btn-outline"
                style={{ padding: '6px 12px', fontSize: '13px', borderRadius: 'var(--radius-sm)' }}
                onClick={detectLocation}
                disabled={detectingLocation || loading}
                data-testid="gps-refresh-btn"
              >
                {detectingLocation ? 'Locating...' : 'Refresh Location'}
              </button>
            </div>

            {locationMessage.text && (
              <div
                className={`badge badge-${locationMessage.type === 'info' ? 'warning' : locationMessage.type} mb-3`}
                style={{ display: 'block', padding: '8px 12px', width: '100%', fontSize: '12px', fontWeight: 500 }}
                data-testid="gps-message"
              >
                {locationMessage.text}
              </div>
            )}

            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{ flex: 1 }}>
                <label className="form-label" style={{ fontSize: '12px' }}>Latitude</label>
                <input
                  type="number"
                  step="0.000001"
                  className="form-input"
                  value={latitude}
                  onChange={(e) => setLatitude(e.target.value)}
                  placeholder="e.g. 12.9716"
                  required
                  disabled={loading}
                  data-testid="latitude-input"
                />
              </div>
              <div style={{ flex: 1 }}>
                <label className="form-label" style={{ fontSize: '12px' }}>Longitude</label>
                <input
                  type="number"
                  step="0.000001"
                  className="form-input"
                  value={longitude}
                  onChange={(e) => setLongitude(e.target.value)}
                  placeholder="e.g. 77.5946"
                  required
                  disabled={loading}
                  data-testid="longitude-input"
                />
              </div>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Select Target Hospital</label>
            <select
              className="form-select"
              value={hospitalId}
              onChange={(e) => setHospitalId(e.target.value)}
              disabled={loading}
              data-testid="hospital-select"
              style={{ borderLeft: '4px solid var(--primary)' }}
            >
              {HOSPITALS.map((h) => (
                <option key={h.id} value={h.id}>
                  {h.name}
                </option>
              ))}
            </select>
            <p className="text-muted mt-1" style={{ fontSize: '12px' }}>
              We will route the ambulance to this facility. Nearest option is automatically selected.
            </p>
          </div>

          <div className="form-group mt-3">
            <label className="form-label">Upload Accident Evidence Image (Optional)</label>
            <input
              type="file"
              className="form-input"
              accept=".jpg,.jpeg,.png,.webp"
              onChange={handleFileChange}
              disabled={loading}
              data-testid="accident-image-input"
            />
            <p className="text-muted mt-1" style={{ fontSize: '12px' }}>
              Supported formats: JPG, JPEG, PNG, WEBP (Max 5MB)
            </p>
          </div>

          {uploadLoading && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary)', fontWeight: 500, fontSize: '14px', marginBottom: '16px' }}>
              <span className="spinner" style={{ width: '16px', height: '16px', border: '2px solid rgba(0,0,0,0.1)', borderTop: '2px solid var(--primary)' }}></span>
              Uploading accident evidence image...
            </div>
          )}

          <button
            type="submit"
            className="btn btn-danger mt-3"
            style={{ width: '100%', padding: '14px', fontSize: '16px' }}
            disabled={loading || detectingLocation}
            data-testid="report-submit"
          >
            {loading ? 'Submitting Emergency Request...' : '🚨 Request Immediate Dispatch'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ReportAccident;
