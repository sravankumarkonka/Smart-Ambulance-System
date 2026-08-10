import { db } from '../config/firebaseAdmin.js';

const REQUIRED_COLLECTIONS = [
  'users',
  'drivers',
  'admins',
  'ambulances',
  'emergencies',
  'notifications',
  'messages',
  'settings',
  'activityLogs',
  'audit_logs'
];

export async function initializeCollections() {
  try {
    console.log('[CollectionInitializer] Checking and initializing required Firestore collections in parallel...');

    const now = new Date().toISOString();
    await Promise.all(REQUIRED_COLLECTIONS.map(async (collectionName) => {
      const colRef = db.collection(collectionName);
      const snapshot = await colRef.limit(1).get();

      if (snapshot.empty) {
        console.log(`[CollectionInitializer] Initializing collection '${collectionName}' with default schema marker...`);

        let initialDocData = {
          _schemaVersion: '1.0.0',
          createdAt: now,
          updatedAt: now,
          isSystemPlaceholder: true
        };

        if (collectionName === 'settings') {
          initialDocData = {
            systemName: 'Smart Ambulance System',
            version: '1.0.0',
            maintenanceMode: false,
            autoDispatchEnabled: true,
            maxSearchRadiusKm: 25,
            createdAt: now,
            updatedAt: now
          };
          await colRef.doc('system_config').set(initialDocData);
        } else if (collectionName === 'admins') {
          initialDocData = {
            uid: 'system-admin-default',
            email: 'admin@smartambulance.com',
            displayName: 'System Admin',
            role: 'admin',
            status: 'active',
            createdAt: now,
            updatedAt: now
          };
          await colRef.doc('system-admin-default').set(initialDocData);
        } else {
          await colRef.doc('_placeholder').set(initialDocData);
        }
      }
    }));
    console.log('[CollectionInitializer] All Firestore collections successfully verified.');
  } catch (error) {
    console.warn('[CollectionInitializer] Warning during collection initialization:', error.message);
  }
}
