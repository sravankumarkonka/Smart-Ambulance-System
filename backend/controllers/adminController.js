import { db } from '../config/firebaseAdmin.js';

export const getStats = async (req, res) => {
  try {
    const emergenciesSnap = await db.collection('emergencies').get();
    const ambulancesSnap = await db.collection('ambulances').get();

    const emergencies = [];
    emergenciesSnap.forEach(doc => {
      emergencies.push({ id: doc.id, ...doc.data() });
    });

    const ambulances = [];
    ambulancesSnap.forEach(doc => {
      ambulances.push({ id: doc.id, ...doc.data() });
    });

    const activeCount = emergencies.filter(e => ['pending', 'assigned', 'arrived'].includes(e.status)).length;
    const criticalCount = emergencies.filter(e => ['pending', 'assigned', 'arrived'].includes(e.status) && e.severityLevel === 'critical').length;
    const availableAmbs = ambulances.filter(a => a.status === 'available').length;
    const busyAmbs = ambulances.filter(a => a.status === 'busy').length;

    return res.status(200).json({
      activeCount,
      criticalCount,
      availableCount: availableAmbs,
      busyCount: busyAmbs
    });
  } catch (error) {
    console.error('Error fetching admin stats:', error);
    return res.status(500).json({ error: 'Failed to retrieve admin stats: ' + error.message });
  }
};

export const getAllAmbulances = async (req, res) => {
  try {
    const snapshot = await db.collection('ambulances').get();
    const list = [];
    snapshot.forEach(doc => {
      list.push({ id: doc.id, ...doc.data() });
    });
    return res.status(200).json(list);
  } catch (error) {
    console.error('Error fetching ambulances list:', error);
    return res.status(500).json({ error: 'Failed to retrieve ambulance fleet.' });
  }
};

export const getAvailableAmbulances = async (req, res) => {
  try {
    const snapshot = await db.collection('ambulances')
      .where('status', '==', 'available')
      .get();
    
    const list = [];
    snapshot.forEach(doc => {
      list.push({ id: doc.id, ...doc.data() });
    });
    return res.status(200).json(list);
  } catch (error) {
    console.error('Error fetching available ambulances:', error);
    return res.status(500).json({ error: 'Failed to retrieve available fleet.' });
  }
};
