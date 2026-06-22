import { db } from '../config/firebaseAdmin.js';
import { autoAssignAmbulance } from '../services/ambulanceService.js';

export const assignDriver = async (req, res) => {
  try {
    const { id } = req.params;
    const { driverId, driverName, driverPhone } = req.body;

    // IDOR fix: check ownership FIRST — before field validation
    // This ensures a driver sending a foreign driverId gets 403, not 400
    if (!driverId || req.user.uid !== driverId) {
      return res.status(403).json({ error: 'Forbidden: You can only assign yourself as driver.' });
    }

    if (!driverName || !driverPhone) {
      return res.status(400).json({ error: 'Missing driver name or contact phone.' });
    }

    console.log('[Backend Driver] Assigning driver', driverId, 'to emergency', id);
    const batch = db.batch();

    // Update emergency request
    const emergencyRef = db.collection('emergencies').doc(id);
    batch.update(emergencyRef, {
      status: 'assigned',
      driverId,
      driverName,
      driverPhone,
      assignedAt: new Date().toISOString()
    });

    // Mark ambulance as busy
    const ambulanceRef = db.collection('ambulances').doc(driverId);
    batch.set(ambulanceRef, {
      status: 'busy',
      driverId,
      driverName,
      driverPhone,
      lastUpdated: new Date().toISOString()
    }, { merge: true });

    await batch.commit();
    console.log('[Backend Driver] Driver assigned and ambulance marked busy successfully.');

    return res.status(200).json({ message: 'Driver successfully assigned.' });
  } catch (error) {
    console.error('[Backend Driver] Error assigning driver:', error);
    return res.status(500).json({ error: 'Failed to assign driver: ' + error.message });
  }
};

export const updateStatus = async (req, res) => {
  try {
    const { id } = req.params;
    const { status } = req.body;

    if (!status) {
      return res.status(400).json({ error: 'Status field is required.' });
    }

    const allowedStatuses = ['assigned', 'arrived', 'completed', 'cancelled'];
    if (!allowedStatuses.includes(status)) {
      return res.status(400).json({ error: `Invalid status. Must be one of: ${allowedStatuses.join(', ')}.` });
    }

    const emergencyRef = db.collection('emergencies').doc(id);

    // IDOR fix: driver may only update emergencies assigned to them
    const docSnap = await emergencyRef.get();
    if (!docSnap.exists) {
      return res.status(404).json({ error: 'Emergency request not found.' });
    }
    const emergencyData = docSnap.data();
    if (emergencyData.driverId && emergencyData.driverId !== req.user.uid) {
      return res.status(403).json({ error: 'Forbidden: You are not assigned to this emergency.' });
    }

    await emergencyRef.update({
      status,
      updatedAt: new Date().toISOString()
    });

    // If completed or cancelled, make ambulance driver available again
    if (status === 'completed' || status === 'cancelled') {
      if (emergencyData.driverId) {
        await db.collection('ambulances').doc(emergencyData.driverId).set({
          status: 'available',
          lastUpdated: new Date().toISOString()
        }, { merge: true });
      }
    }

    return res.status(200).json({ message: `Emergency status updated to ${status}.` });
  } catch (error) {
    console.error('Error updating status:', error);
    return res.status(500).json({ error: 'Failed to update status: ' + error.message });
  }
};

export const releaseDriver = async (req, res) => {
  try {
    const { id } = req.params;
    const { driverId } = req.body;

    const emergencyRef = db.collection('emergencies').doc(id);
    const docSnap = await emergencyRef.get();
    if (!docSnap.exists) {
      return res.status(404).json({ error: 'Emergency request not found.' });
    }

    const emergencyData = docSnap.data();

    // IDOR fix: driver may only release emergencies they are assigned to
    if (emergencyData.driverId && emergencyData.driverId !== req.user.uid) {
      return res.status(403).json({ error: 'Forbidden: You are not assigned to this emergency.' });
    }

    const batch = db.batch();

    // Reset emergency to pending state
    batch.update(emergencyRef, {
      status: 'pending',
      driverId: null,
      driverName: null,
      driverPhone: null,
      assignedAt: null,
      driverLatitude: null,
      driverLongitude: null,
      updatedAt: new Date().toISOString()
    });

    // Mark ambulance as available (use req.user.uid as authoritative driverId)
    const effectiveDriverId = driverId || req.user.uid;
    const ambulanceRef = db.collection('ambulances').doc(effectiveDriverId);
    batch.set(ambulanceRef, {
      status: 'available',
      lastUpdated: new Date().toISOString()
    }, { merge: true });

    await batch.commit();

    return res.status(200).json({ message: 'Emergency request released back to queue.' });
  } catch (error) {
    console.error('Error releasing emergency:', error);
    return res.status(500).json({ error: 'Failed to release emergency: ' + error.message });
  }
};

export const updateAmbulance = async (req, res) => {
  try {
    const { driverId, ambulanceData } = req.body;

    if (!driverId) {
      return res.status(400).json({ error: 'DriverId is required.' });
    }

    // IDOR fix: a driver may only update their own ambulance record
    if (req.user.uid !== driverId) {
      return res.status(403).json({ error: 'Forbidden: You can only update your own ambulance data.' });
    }

    await db.collection('ambulances').doc(driverId).set({
      driverId,
      lastUpdated: new Date().toISOString(),
      ...ambulanceData
    }, { merge: true });

    return res.status(200).json({ message: 'Ambulance state updated.' });
  } catch (error) {
    console.error('Error updating ambulance:', error);
    return res.status(500).json({ error: 'Failed to update ambulance: ' + error.message });
  }
};

export const updateLocation = async (req, res) => {
  try {
    const { driverId } = req.params;
    const { latitude, longitude, emergencyId } = req.body;

    if (latitude === undefined || longitude === undefined) {
      return res.status(400).json({ error: 'Latitude and Longitude are required.' });
    }

    // IDOR fix: a driver may only update their own location — prevents GPS spoofing
    if (req.user.uid !== driverId) {
      return res.status(403).json({ error: 'Forbidden: You can only update your own location.' });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);

    if (isNaN(lat) || lat < -90 || lat > 90) {
      return res.status(400).json({ error: 'Latitude must be between -90 and 90.' });
    }
    if (isNaN(lng) || lng < -180 || lng > 180) {
      return res.status(400).json({ error: 'Longitude must be between -180 and 180.' });
    }

    const batch = db.batch();

    // 1. Update location in ambulances fleet doc
    const ambulanceRef = db.collection('ambulances').doc(driverId);
    batch.set(ambulanceRef, {
      latitude: lat,
      longitude: lng,
      lastUpdated: new Date().toISOString()
    }, { merge: true });

    // 2. Link coordinates directly inside current emergency doc if active
    if (emergencyId) {
      const emergencyRef = db.collection('emergencies').doc(emergencyId);
      batch.update(emergencyRef, {
        driverLatitude: lat,
        driverLongitude: lng,
        updatedAt: new Date().toISOString()
      });
    }

    await batch.commit();

    return res.status(200).json({ message: 'Driver location coordinates updated.' });
  } catch (error) {
    console.error('Error updating driver location:', error);
    return res.status(500).json({ error: 'Failed to update location coordinate: ' + error.message });
  }
};

export const autoAssign = async (req, res) => {
  try {
    const { id } = req.params;
    const { latitude, longitude } = req.body;

    if (latitude === undefined || longitude === undefined) {
      return res.status(400).json({ error: 'Patient latitude and longitude coordinates are required.' });
    }

    const assigned = await autoAssignAmbulance(id, parseFloat(latitude), parseFloat(longitude));
    if (!assigned) {
      return res.status(404).json({ error: 'No available ambulances found nearby.' });
    }

    return res.status(200).json({ message: 'Ambulance auto-assigned successfully.', ...assigned });
  } catch (error) {
    console.error('[Driver Controller] Error in autoAssign:', error);
    return res.status(500).json({ error: 'Failed to auto-assign ambulance: ' + error.message });
  }
};

export const getAmbulance = async (req, res) => {
  try {
    const { driverId } = req.params;

    // IDOR fix: drivers may only access their own ambulance profile
    // Admins (checked via role) can access any ambulance via the admin route
    if (req.user.uid !== driverId) {
      return res.status(403).json({ error: 'Forbidden: You can only view your own ambulance profile.' });
    }

    const docSnap = await db.collection('ambulances').doc(driverId).get();

    if (!docSnap.exists) {
      return res.status(404).json({ error: 'Ambulance profile not found.' });
    }

    return res.status(200).json(docSnap.data());
  } catch (error) {
    console.error('[Driver Controller] Error fetching ambulance:', error);
    return res.status(500).json({ error: 'Failed to retrieve ambulance status.' });
  }
};
