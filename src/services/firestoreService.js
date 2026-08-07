import api from './api';
import { db } from '../config/firebase';
import {
  doc,
  collection,
  query,
  where,
  onSnapshot,
  addDoc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc
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
 * Creates or updates a user profile in the 'users' collection via backend with Firestore fallback.
 */
export const createUserProfile = async (uid, profileData) => {
  try {
    await api.post(`/api/auth/profile/${uid}`, profileData);
  } catch (err) {
    console.warn('[firestoreService] API createUserProfile fallback to Firestore:', err.message);
    await setDoc(doc(db, 'users', uid), profileData, { merge: true });
  }
};

/**
 * Fetches user profile from the 'users' collection via backend with Firestore fallback.
 */
export const getUserProfile = async (uid) => {
  try {
    const res = await api.get(`/api/auth/profile/${uid}`);
    return res.data;
  } catch (err) {
    console.warn('[firestoreService] API getUserProfile fallback to Firestore:', err.message);
    const snap = await getDoc(doc(db, 'users', uid));
    if (snap.exists()) {
      return { uid: snap.id, ...snap.data() };
    }
    return null;
  }
};

/**
 * Creates an emergency report in the 'emergencies' collection via backend with Firestore fallback.
 */
export const createEmergency = async (emergencyData) => {
  try {
    const res = await api.post('/api/emergencies', emergencyData);
    return res.data.id || res.data.requestId;
  } catch (err) {
    console.warn('[firestoreService] API createEmergency fallback to direct Firestore write:', err.message);
    const now = new Date().toISOString();
    const docData = {
      patientUid: emergencyData.userId || '',
      userId: emergencyData.userId || '',
      userEmail: emergencyData.userEmail || '',
      patientName: emergencyData.patientName || 'Unknown',
      phone: emergencyData.phone || '',
      emergencyType: emergencyData.emergencyType || 'other',
      description: emergencyData.description || '',
      severity: emergencyData.severityLevel || 'medium',
      severityLevel: emergencyData.severityLevel || 'medium',
      latitude: Number(emergencyData.latitude || 0),
      longitude: Number(emergencyData.longitude || 0),
      status: 'Waiting',
      assignedDriver: null,
      driverId: null,
      hospital: emergencyData.hospitalName || null,
      hospitalName: emergencyData.hospitalName || null,
      hospitalLatitude: emergencyData.hospitalLatitude ? Number(emergencyData.hospitalLatitude) : null,
      hospitalLongitude: emergencyData.hospitalLongitude ? Number(emergencyData.hospitalLongitude) : null,
      imageUrl: null,
      timestamp: now,
      createdAt: now
    };

    const colRef = collection(db, 'emergencies');
    const docRef = await addDoc(colRef, docData);
    await updateDoc(doc(db, 'emergencies', docRef.id), { requestId: docRef.id });
    return docRef.id;
  }
};

/**
 * Fetches a single emergency document by ID via backend with Firestore fallback.
 */
export const getEmergency = async (id) => {
  try {
    const res = await api.get(`/api/emergencies/${id}`);
    return validateEmergencyDoc(res.data);
  } catch (err) {
    console.warn('[firestoreService] API getEmergency fallback to Firestore direct read:', err.message);
    const snap = await getDoc(doc(db, 'emergencies', id));
    if (snap.exists()) {
      return validateEmergencyDoc({ id: snap.id, ...snap.data() });
    }
    return null;
  }
};

/**
 * Subscribes to updates on a specific emergency (real-time listener).
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
 * Fetches the emergency history for a specific user via backend with Firestore fallback.
 */
export const getEmergencyHistory = async (userId) => {
  try {
    const res = await api.get(`/api/emergencies/history/${userId}`);
    return (res.data || []).map(validateEmergencyDoc);
  } catch (err) {
    console.warn('[firestoreService] API getEmergencyHistory fallback to Firestore query:', err.message);
    try {
      const emergenciesMap = new Map();

      // Query 1: by userId
      try {
        const q1 = query(collection(db, 'emergencies'), where('userId', '==', userId));
        const snap1 = await getDocs(q1);
        snap1.forEach(docSnap => {
          emergenciesMap.set(docSnap.id, validateEmergencyDoc({ id: docSnap.id, ...docSnap.data() }));
        });
      } catch (e1) {
        console.warn('[firestoreService] userId query warning:', e1.message);
      }

      // Query 2: by patientUid
      try {
        const q2 = query(collection(db, 'emergencies'), where('patientUid', '==', userId));
        const snap2 = await getDocs(q2);
        snap2.forEach(docSnap => {
          if (!emergenciesMap.has(docSnap.id)) {
            emergenciesMap.set(docSnap.id, validateEmergencyDoc({ id: docSnap.id, ...docSnap.data() }));
          }
        });
      } catch (e2) {
        console.warn('[firestoreService] patientUid query warning:', e2.message);
      }

      const list = Array.from(emergenciesMap.values());
      list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
      return list;
    } catch (fsErr) {
      console.error('[firestoreService] Firestore query error:', fsErr.message);
      return [];
    }
  }
};

/**
 * Subscribes to all pending emergencies (real-time listener).
 */
export const subscribeToPendingEmergencies = (callback) => {
  const q = collection(db, 'emergencies');
  return onSnapshot(q, (querySnapshot) => {
    const list = querySnapshot.docs
      .map(doc => validateEmergencyDoc({ id: doc.id, ...doc.data() }))
      .filter(e => (e.status === 'pending' || e.status === 'Waiting' || e.status === 'waiting') && (!e.driverId || e.driverId === ''));
    list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    callback(list);
  });
};

/**
 * Subscribes to emergencies assigned to a driver (real-time listener).
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
 * Assigns a driver to an emergency via backend with Firestore fallback.
 */
export const assignDriverToEmergency = async (emergencyId, driverId, driverName, driverPhone) => {
  try {
    await api.post(`/api/driver/emergencies/${emergencyId}/assign`, {
      driverId,
      driverName,
      driverPhone
    });
  } catch (err) {
    console.warn('[firestoreService] API assignDriverToEmergency fallback to Firestore direct update:', err.message);
    const now = new Date().toISOString();
    await updateDoc(doc(db, 'emergencies', emergencyId), {
      status: 'assigned',
      assignedDriver: driverId,
      driverId,
      driverName,
      driverPhone,
      assignedAt: now,
      updatedAt: now
    });
    await setDoc(doc(db, 'ambulances', driverId), {
      status: 'busy',
      driverId,
      driverName,
      driverPhone,
      lastUpdated: now
    }, { merge: true });
  }
};

/**
 * Updates an emergency status via backend with Firestore fallback.
 */
export const updateEmergencyStatus = async (emergencyId, status) => {
  try {
    if (status === 'cancelled') {
      await api.post(`/api/emergencies/${emergencyId}/cancel`);
    } else {
      await api.patch(`/api/driver/emergencies/${emergencyId}/status`, { status });
    }
  } catch (err) {
    console.warn('[firestoreService] API updateEmergencyStatus fallback to Firestore direct update:', err.message);
    const now = new Date().toISOString();
    await updateDoc(doc(db, 'emergencies', emergencyId), {
      status,
      updatedAt: now
    });
  }
};

/**
 * Initializes or updates an ambulance profile via backend with Firestore fallback.
 */
export const createOrUpdateAmbulance = async (driverId, ambulanceData) => {
  try {
    await api.post('/api/driver/ambulances', {
      driverId,
      ambulanceData
    });
  } catch (err) {
    console.warn('[firestoreService] API createOrUpdateAmbulance fallback to Firestore direct set:', err.message);
    await setDoc(doc(db, 'ambulances', driverId), {
      driverId,
      lastUpdated: new Date().toISOString(),
      ...ambulanceData
    }, { merge: true });
  }
};

/**
 * Updates real-time driver coordinates via backend with Firestore fallback.
 */
export const updateDriverLocation = async (driverId, lat, lng, emergencyId = null) => {
  try {
    await api.post(`/api/driver/ambulances/${driverId}/location`, {
      latitude: lat,
      longitude: lng,
      emergencyId
    });
  } catch (err) {
    console.warn('[firestoreService] API updateDriverLocation fallback to Firestore direct update:', err.message);
    const now = new Date().toISOString();
    await setDoc(doc(db, 'ambulances', driverId), {
      driverUid: driverId,
      driverId,
      latitude: lat,
      longitude: lng,
      status: 'busy',
      updatedAt: now,
      lastUpdated: now
    }, { merge: true });

    if (emergencyId) {
      await updateDoc(doc(db, 'emergencies', emergencyId), {
        driverLatitude: lat,
        driverLongitude: lng,
        updatedAt: now
      });
    }
  }
};

/**
 * Retrieves all currently available ambulances via backend with Firestore fallback.
 */
export const getAvailableAmbulances = async () => {
  try {
    const res = await api.get('/api/admin/ambulances/available');
    return (res.data || []).map(validateAmbulanceDoc);
  } catch (err) {
    console.warn('[firestoreService] API getAvailableAmbulances fallback to Firestore query:', err.message);
    try {
      const q = query(collection(db, 'ambulances'), where('status', '==', 'available'));
      const snapshot = await getDocs(q);
      const list = [];
      snapshot.forEach(docSnap => {
        list.push(validateAmbulanceDoc({ id: docSnap.id, ...docSnap.data() }));
      });
      return list;
    } catch (fsErr) {
      console.error('[firestoreService] Firestore query error:', fsErr.message);
      return [];
    }
  }
};

/**
 * Subscribes to real-time status of all ambulances (real-time listener).
 */
export const subscribeToAllAmbulances = (callback) => {
  const q = collection(db, 'ambulances');
  return onSnapshot(q, (snapshot) => {
    const list = snapshot.docs.map(doc => validateAmbulanceDoc({ id: doc.id, ...doc.data() }));
    callback(list);
  });
};

/**
 * Subscribes to a single ambulance profile updates (real-time listener).
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
 * Releases a driver from an emergency assignment via backend with Firestore fallback.
 */
export const releaseEmergency = async (emergencyId, driverId) => {
  try {
    await api.post(`/api/driver/emergencies/${emergencyId}/release`, { driverId });
  } catch (err) {
    console.warn('[firestoreService] API releaseEmergency fallback to Firestore direct update:', err.message);
    const now = new Date().toISOString();
    await updateDoc(doc(db, 'emergencies', emergencyId), {
      status: 'pending',
      driverId: null,
      driverName: null,
      driverPhone: null,
      assignedAt: null,
      updatedAt: now
    });
    if (driverId) {
      await setDoc(doc(db, 'ambulances', driverId), {
        status: 'available',
        lastUpdated: now
      }, { merge: true });
    }
  }
};

/**
 * Image upload is disabled — Firebase Storage is not available on the Spark (free) plan.
 * Safe no-op function.
 */
export const uploadAccidentImage = async (_requestId, _file) => {
  console.info('[firestoreService] Image upload is disabled (Firebase Storage not available on Spark plan). Skipping.');
  return null;
};

/**
 * No-op: imageUrl linking is handled server-side on upload.
 * Kept for API compatibility with callers.
 */
export const linkEmergencyImageUrl = async (_emergencyId, _imageUrl) => {
  return Promise.resolve();
};
