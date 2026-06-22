import { db } from '../config/firebaseAdmin.js';
import { calculateHaversineDistance } from '../utils/haversine.js';

/**
 * Finds the nearest available ambulance to a given set of client coordinates.
 *
 * @param {number} patientLat Latitude of the patient
 * @param {number} patientLng Longitude of the patient
 * @returns {Promise<{nearest: object|null, allAvailable: Array}>}
 */
export async function findNearestAmbulance(patientLat, patientLng) {
  try {
    const snapshot = await db.collection('ambulances')
      .where('status', '==', 'available')
      .get();

    let nearestAmbulance = null;
    let minDistance = Infinity;
    const list = [];

    snapshot.forEach(doc => {
      const amb = { id: doc.id, ...doc.data() };
      if (amb.latitude !== undefined && amb.longitude !== undefined) {
        const lat = parseFloat(amb.latitude);
        const lng = parseFloat(amb.longitude);
        if (!isNaN(lat) && !isNaN(lng)) {
          const dist = calculateHaversineDistance(patientLat, patientLng, lat, lng);
          amb.distanceKm = parseFloat(dist.toFixed(2));
          list.push(amb);
          if (dist < minDistance) {
            minDistance = dist;
            nearestAmbulance = amb;
          }
        }
      }
    });

    // Sort the list of available ambulances by proximity
    list.sort((a, b) => a.distanceKm - b.distanceKm);

    return {
      nearest: nearestAmbulance,
      allAvailable: list
    };
  } catch (error) {
    console.error('[Ambulance Service] Error finding nearest ambulance:', error);
    throw new Error('Failed to search available ambulances: ' + error.message, { cause: error });
  }
}

/**
 * Auto-assigns the nearest available ambulance to an emergency.
 *
 * @param {string} emergencyId The document ID of the emergency request
 * @param {number} patientLat Latitude of the emergency
 * @param {number} patientLng Longitude of the emergency
 * @returns {Promise<object|null>} The assigned ambulance object, or null if none available
 */
export async function autoAssignAmbulance(emergencyId, patientLat, patientLng) {
  try {
    const { nearest } = await findNearestAmbulance(patientLat, patientLng);

    if (!nearest) {
      console.log(`[Ambulance Service] No available ambulance found for emergency request: ${emergencyId}`);
      return null;
    }

    const driverId = nearest.driverId || nearest.id;
    const driverName = nearest.driverName || 'Closest Responder';
    const driverPhone = nearest.driverPhone || 'N/A';

    const batch = db.batch();

    // 1. Update the emergency request document
    const emergencyRef = db.collection('emergencies').doc(emergencyId);
    batch.update(emergencyRef, {
      status: 'assigned',
      driverId,
      driverName,
      driverPhone,
      assignedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    });

    // 2. Mark the selected ambulance as busy
    const ambulanceRef = db.collection('ambulances').doc(driverId);
    batch.set(ambulanceRef, {
      status: 'busy',
      lastUpdated: new Date().toISOString()
    }, { merge: true });

    await batch.commit();

    console.log(`[Ambulance Service] Auto-assigned ambulance ${driverId} to emergency ${emergencyId}`);
    return {
      driverId,
      driverName,
      driverPhone,
      distanceKm: nearest.distanceKm
    };
  } catch (error) {
    console.error('[Ambulance Service] Error during auto-assignment:', error);
    throw new Error('Failed to auto-assign ambulance: ' + error.message, { cause: error });
  }
}
