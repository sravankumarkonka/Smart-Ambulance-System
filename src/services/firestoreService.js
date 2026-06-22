import api from './api';
import { db } from '../config/firebase';
import {
  doc,
  collection,
  query,
  where,
  onSnapshot
} from 'firebase/firestore';

/**
 * Sanitizes and sets defaults for an emergency document.
 */
export const validateEmergencyDoc = (data) => {
  if (!data) return null;
  const latitude = Number(data.latitude !== undefined ? data.latitude : (data.coordinates?.latitude !== undefined ? data.coordinates.latitude : 0));
  const longitude = Number(data.longitude !== undefined ? data.longitude : (data.coordinates?.longitude !== undefined ? data.coordinates.longitude : 0));
  const hospLat = Number(data.hospitalLatitude !== undefined ? data.hospitalLatitude : 0);
  const hospLng = Number(data.hospitalLongitude !== undefined ? data.hospitalLongitude : 0);
  const driverLat = Number(data.driverLatitude !== undefined ? data.driverLatitude : 0);
  const driverLng = Number(data.driverLongitude !== undefined ? data.driverLongitude : 0);

  return {
    ...data,
    id: data.id || '',
    patientName: data.patientName || 'Unknown Patient',
    emergencyType: data.emergencyType || 'other',
    severityLevel: data.severityLevel || 'medium',
    status: data.status || 'pending',
    latitude: isNaN(latitude) ? 0 : latitude,
    longitude: isNaN(longitude) ? 0 : longitude,
    hospitalName: data.hospitalName || '',
    hospitalLatitude: isNaN(hospLat) ? 0 : hospLat,
    hospitalLongitude: isNaN(hospLng) ? 0 : hospLng,
    driverId: data.driverId || '',
    driverName: data.driverName || '',
    driverPhone: data.driverPhone || '',
    driverLatitude: isNaN(driverLat) ? 0 : driverLat,
    driverLongitude: isNaN(driverLng) ? 0 : driverLng,
    imageUrl: data.imageUrl || '',
    createdAt: data.createdAt || new Date().toISOString(),
    description: data.description || ''
  };
};

/**
 * Sanitizes and sets defaults for an ambulance document.
 */
export const validateAmbulanceDoc = (data) => {
  if (!data) return null;
  const latitude = Number(data.latitude !== undefined ? data.latitude : (data.location?.latitude !== undefined ? data.location.latitude : 0));
  const longitude = Number(data.longitude !== undefined ? data.longitude : (data.location?.longitude !== undefined ? data.location.longitude : 0));

  return {
    ...data,
    id: data.id || '',
    driverId: data.driverId || data.id || '',
    driverName: data.driverName || 'Unknown Driver',
    driverPhone: data.driverPhone || '',
    status: data.status || 'available',
    latitude: isNaN(latitude) ? 0 : latitude,
    longitude: isNaN(longitude) ? 0 : longitude
  };
};

/**
 * Creates or updates a user profile in the 'users' collection via backend.
 */
export const createUserProfile = async (uid, profileData) => {
  await api.post(`/api/auth/profile/${uid}`, profileData);
};

/**
 * Fetches user profile from the 'users' collection via backend.
 */
export const getUserProfile = async (uid) => {
  const res = await api.get(`/api/auth/profile/${uid}`);
  return res.data;
};

/**
 * Creates an emergency report in the 'emergencies' collection via backend.
 */
export const createEmergency = async (emergencyData) => {
  const res = await api.post('/api/emergencies', emergencyData);
  return res.data.id;
};

/**
 * Fetches a single emergency document by ID via backend.
 */
export const getEmergency = async (id) => {
  const res = await api.get(`/api/emergencies/${id}`);
  return validateEmergencyDoc(res.data);
};

/**
 * Subscribes to updates on a specific emergency (real-time listener remains local).
 */
export const subscribeToEmergency = (id, callback) => {
  return onSnapshot(doc(db, 'emergencies', id), (docSnap) => {
    if (docSnap.exists()) {
      callback(validateEmergencyDoc({ id: docSnap.id, ...docSnap.data() }));
    } else {
      callback(null);
    }
  });
};

/**
 * Fetches the emergency history for a specific user via backend.
 */
export const getEmergencyHistory = async (userId) => {
  const res = await api.get(`/api/emergencies/history/${userId}`);
  return (res.data || []).map(validateEmergencyDoc);
};

/**
 * Subscribes to all pending emergencies (real-time listener remains local).
 */
export const subscribeToPendingEmergencies = (callback) => {
  const q = query(
    collection(db, 'emergencies'),
    where('status', '==', 'pending')
  );
  return onSnapshot(q, (querySnapshot) => {
    const list = querySnapshot.docs.map(doc => validateEmergencyDoc({ id: doc.id, ...doc.data() }));
    list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    callback(list);
  });
};

/**
 * Subscribes to emergencies assigned to a driver (real-time listener remains local).
 */
export const subscribeToDriverEmergencies = (driverId, callback) => {
  const q = query(
    collection(db, 'emergencies'),
    where('driverId', '==', driverId)
  );
  return onSnapshot(q, (querySnapshot) => {
    const list = querySnapshot.docs.map(doc => validateEmergencyDoc({ id: doc.id, ...doc.data() }));
    list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    callback(list);
  });
};

/**
 * Assigns a driver to an emergency via backend.
 */
export const assignDriverToEmergency = async (emergencyId, driverId, driverName, driverPhone) => {
  await api.post(`/api/driver/emergencies/${emergencyId}/assign`, {
    driverId,
    driverName,
    driverPhone
  });
};

/**
 * Updates an emergency status via backend.
 */
export const updateEmergencyStatus = async (emergencyId, status) => {
  if (status === 'cancelled') {
    await api.post(`/api/emergencies/${emergencyId}/cancel`);
  } else {
    await api.patch(`/api/driver/emergencies/${emergencyId}/status`, { status });
  }
};

/**
 * Initializes or updates an ambulance profile via backend.
 */
export const createOrUpdateAmbulance = async (driverId, ambulanceData) => {
  await api.post('/api/driver/ambulances', {
    driverId,
    ambulanceData
  });
};

/**
 * Updates real-time driver coordinates via backend.
 */
export const updateDriverLocation = async (driverId, lat, lng, emergencyId = null) => {
  await api.post(`/api/driver/ambulances/${driverId}/location`, {
    latitude: lat,
    longitude: lng,
    emergencyId
  });
};

/**
 * Retrieves all currently available ambulances via backend.
 */
export const getAvailableAmbulances = async () => {
  const res = await api.get('/api/admin/ambulances/available');
  return (res.data || []).map(validateAmbulanceDoc);
};

/**
 * Subscribes to real-time status of all ambulances (real-time listener remains local).
 */
export const subscribeToAllAmbulances = (callback) => {
  const q = collection(db, 'ambulances');
  return onSnapshot(q, (snapshot) => {
    const list = snapshot.docs.map(doc => validateAmbulanceDoc({ id: doc.id, ...doc.data() }));
    callback(list);
  });
};

/**
 * Subscribes to a single ambulance profile updates (real-time listener remains local).
 */
export const subscribeToAmbulance = (driverId, callback) => {
  const ambRef = doc(db, 'ambulances', driverId);
  return onSnapshot(ambRef, (docSnap) => {
    if (docSnap.exists()) {
      callback(validateAmbulanceDoc({ id: docSnap.id, ...docSnap.data() }));
    } else {
      callback(null);
    }
  });
};

/**
 * Releases a driver from an emergency assignment via backend.
 */
export const releaseEmergency = async (emergencyId, driverId) => {
  await api.post(`/api/driver/emergencies/${emergencyId}/release`, { driverId });
};

/**
 * Uploads an accident image to backend, which uploads to Firebase Storage.
 */
export const uploadAccidentImage = async (requestId, file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const res = await api.post(`/api/emergencies/${requestId}/image`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  
  return res.data.imageUrl;
};

/**
 * Links the uploaded accident image URL inside the Firestore emergency document.
 * (Already handled on backend during image upload, so this is a client no-op).
 */
export const linkEmergencyImageUrl = async (_emergencyId, _imageUrl) => {
  return Promise.resolve();
};
