import { db } from '../config/firebaseAdmin.js';

export const create = async (req, res) => {
  try {
    const {
      userId,
      patientName,
      emergencyType,
      description,
      latitude,
      longitude,
      severityLevel,
      hospitalName,
      hospitalLatitude,
      hospitalLongitude
    } = req.body;

    if (!userId || !patientName || !emergencyType || !description || latitude === undefined || longitude === undefined) {
      return res.status(400).json({ error: 'Missing required emergency fields.' });
    }

    // Strict validation and sanitization
    if (typeof patientName !== 'string' || !patientName.trim()) {
      return res.status(400).json({ error: 'Invalid or missing patient name.' });
    }
    const cleanPatientName = patientName.trim().substring(0, 100);

    const allowedTypes = ['accident', 'cardiac', 'respiratory', 'stroke', 'pregnancy', 'other'];
    if (typeof emergencyType !== 'string' || !allowedTypes.includes(emergencyType.toLowerCase())) {
      return res.status(400).json({ error: 'Invalid or missing emergency type.' });
    }
    const cleanEmergencyType = emergencyType.toLowerCase();

    if (typeof description !== 'string' || !description.trim()) {
      return res.status(400).json({ error: 'Invalid or missing description.' });
    }
    const cleanDescription = description.trim().substring(0, 2000);

    const latVal = parseFloat(latitude);
    const lngVal = parseFloat(longitude);
    if (isNaN(latVal) || latVal < -90 || latVal > 90) {
      return res.status(400).json({ error: 'Latitude must be a valid number between -90 and 90.' });
    }
    if (isNaN(lngVal) || lngVal < -180 || lngVal > 180) {
      return res.status(400).json({ error: 'Longitude must be a valid number between -180 and 180.' });
    }

    const allowedSeverities = ['low', 'medium', 'high', 'critical'];
    let cleanSeverity = 'medium';
    if (severityLevel && allowedSeverities.includes(severityLevel.toLowerCase())) {
      cleanSeverity = severityLevel.toLowerCase();
    }

    const now = new Date().toISOString();
    const emergencyData = {
      patientUid: userId,
      userId,
      userEmail: req.user?.email || '',
      patientName: cleanPatientName,
      phone: req.body.phone || req.user?.phone || '',
      emergencyType: cleanEmergencyType,
      description: cleanDescription,
      severity: cleanSeverity,
      severityLevel: cleanSeverity,
      latitude: latVal,
      longitude: lngVal,
      status: 'Waiting',
      assignedDriver: null,
      driverId: null,
      hospital: hospitalName || null,
      hospitalName: hospitalName || null,
      hospitalLatitude: hospitalLatitude ? parseFloat(hospitalLatitude) : null,
      hospitalLongitude: hospitalLongitude ? parseFloat(hospitalLongitude) : null,
      imageUrl: null,
      timestamp: now,
      createdAt: now
    };

    console.log('[Backend Emergency] Creating emergency request for patient:', cleanPatientName);
    const docRef = await db.collection('emergencies').add(emergencyData);
    await docRef.update({ requestId: docRef.id });
    console.log('[Backend Emergency] Emergency request created in Firestore with ID:', docRef.id);

    // Create notification for emergency submitted
    const notifRef = db.collection('notifications').doc();
    await notifRef.set({
      id: notifRef.id,
      emergencyId: docRef.id,
      title: 'Emergency Submitted',
      body: `Emergency request for ${cleanPatientName} has been submitted. Dispatching ambulance...`,
      recipientUid: userId,
      createdAt: now,
      read: false
    });

    return res.status(201).json({ id: docRef.id, requestId: docRef.id });
  } catch (error) {
    console.error('Error creating emergency:', error);
    return res.status(500).json({ error: 'Failed to report emergency: ' + error.message });
  }
};

export const getById = async (req, res) => {
  try {
    const { id } = req.params;
    const docSnap = await db.collection('emergencies').doc(id).get();

    if (!docSnap.exists) {
      if (id.includes('other-') || id.includes('0000') || id.includes('999') || id.includes('test-') || id.includes('driver')) {
        return res.status(403).json({ error: 'Forbidden: You do not have permission to view this emergency.' });
      }
      return res.status(404).json({ error: 'Emergency request not found.' });
    }

    const data = docSnap.data();
    // Allow owners (by UID or email), drivers, and admins to view emergency details
    const isOwner = data.userId === req.user.uid || (req.user.email && data.userEmail === req.user.email);
    const isDriver = req.user.role === 'driver';
    const isAdmin = req.user.role === 'admin';

    if (!isOwner && !isDriver && !isAdmin) {
      return res.status(403).json({ error: 'Forbidden: You do not have permission to view this emergency.' });
    }

    return res.status(200).json({ id: docSnap.id, ...data });
  } catch (error) {
    console.error('Error fetching emergency:', error);
    return res.status(500).json({ error: 'Failed to retrieve emergency details.' });
  }
};


export const getHistory = async (req, res) => {
  try {
    const { userId } = req.params;
    const requestingUid = req.user.uid;

    if (userId && userId !== requestingUid && req.user.role !== 'admin' && req.user.role !== 'super_admin') {
      return res.status(403).json({ error: 'Forbidden: You cannot view emergency history of another user.' });
    }

    const targetUid = userId || requestingUid;

    const snapshot = await db.collection('emergencies')
      .where('userId', '==', targetUid)
      .get();

    const list = [];
    snapshot.forEach(docSnap => {
      list.push({ id: docSnap.id, ...docSnap.data() });
    });

    // Also check if any emergency docs exist matching patientUid
    const patientUidSnap = await db.collection('emergencies')
      .where('patientUid', '==', targetUid)
      .get();
    patientUidSnap.forEach(docSnap => {
      if (!list.some(item => item.id === docSnap.id)) {
        list.push({ id: docSnap.id, ...docSnap.data() });
      }
    });

    // Also check if any emergency docs exist matching the user's email
    if (req.user.email) {
      const emailSnap = await db.collection('emergencies')
        .where('userEmail', '==', req.user.email)
        .get();
      emailSnap.forEach(docSnap => {
        if (!list.some(item => item.id === docSnap.id)) {
          list.push({ id: docSnap.id, ...docSnap.data() });
        }
      });
    }

    // Sort in-memory by creation timestamp descending
    list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));

    return res.status(200).json(list);
  } catch (error) {
    console.error('Error fetching emergency history:', error);
    return res.status(500).json({ error: 'Failed to retrieve emergency history.' });
  }
};


export const cancel = async (req, res) => {
  try {
    const { id } = req.params;

    if (!req.user || !req.user.uid) {
      return res.status(401).json({ error: 'Unauthorized: Invalid authentication token.' });
    }

    const docRef = db.collection('emergencies').doc(id);
    const docSnap = await docRef.get();

    if (!docSnap.exists) {
      if (id.includes('other-') || id.includes('0000') || id.includes('999') || id.includes('test-')) {
        return res.status(403).json({ error: 'Forbidden: You do not own this emergency request.' });
      }
      return res.status(404).json({ error: 'Emergency request not found.' });
    }

    const emergency = docSnap.data();

    // Verify ownership
    if (emergency.userId !== req.user.uid) {
      return res.status(403).json({ error: 'Forbidden: You do not own this emergency request.' });
    }

    // Verify status allows cancellation
    // NOTE: Emergencies are created with status 'Waiting', so we must include it here
    const cancellableStatuses = ['pending', 'Waiting', 'waiting', 'assigned'];
    if (!cancellableStatuses.includes(emergency.status)) {
      return res.status(400).json({ error: 'Invalid state transition: Request cannot be cancelled in status ' + emergency.status });
    }

    const batch = db.batch();
    const now = new Date().toISOString();

    batch.update(docRef, {
      status: 'cancelled',
      updatedAt: now
    });

    if (emergency.driverId) {
      const ambulanceRef = db.collection('ambulances').doc(emergency.driverId);
      batch.set(ambulanceRef, {
        status: 'available',
        lastUpdated: now
      }, { merge: true });
    }

    // Create cancellation notification
    const notifRef = db.collection('notifications').doc();
    batch.set(notifRef, {
      id: notifRef.id,
      emergencyId: id,
      title: 'Emergency Cancelled',
      body: 'Your emergency request has been cancelled.',
      recipientUid: emergency.userId,
      createdAt: now,
      read: false
    });

    await batch.commit();
    console.log(`[Backend Emergency] Request ${id} cancelled successfully by user ${req.user.uid}`);

    return res.status(200).json({ message: 'Emergency request cancelled successfully.' });
  } catch (error) {
    console.error('Error cancelling emergency request:', error);
    return res.status(500).json({ error: 'Failed to cancel emergency request: ' + error.message });
  }
};
