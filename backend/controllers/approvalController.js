import { auth, db } from '../config/firebaseAdmin.js';

// ── Helpers ───────────────────────────────────────────────────────────

const createAuditLog = async (action, performedBy, targetUid = null, details = {}) => {
  try {
    await db.collection('audit_logs').add({
      action,
      performedBy,
      targetUid,
      details,
      createdAt: new Date().toISOString()
    });
  } catch (e) {
    console.warn('[AuditLog] Failed:', e.message);
  }
};

const createNotification = async (type, data) => {
  try {
    await db.collection('notifications').add({
      type,
      ...data,
      read: false,
      createdAt: new Date().toISOString()
    });
  } catch (e) {
    console.warn('[Notification] Failed:', e.message);
  }
};

// ══════════════════════════════════════════════════════════════════════
// SUPER ADMIN ENDPOINTS — Manage Admins
// ══════════════════════════════════════════════════════════════════════

export const getPendingAdmins = async (req, res) => {
  try {
    const snapshot = await db.collection('users')
      .where('role', '==', 'admin')
      .where('status', '==', 'pending')
      .get();
    const list = [];
    snapshot.forEach(doc => list.push({ uid: doc.id, ...doc.data() }));
    return res.status(200).json(list);
  } catch (error) {
    console.error('[Approval] Error fetching pending admins:', error);
    return res.status(500).json({ error: 'Failed to retrieve pending admins.' });
  }
};

export const approveAdmin = async (req, res) => {
  try {
    const { uid } = req.params;
    const now = new Date().toISOString();

    await db.collection('users').doc(uid).update({
      status: 'active',
      approved: true,
      updatedAt: now
    });

    await createNotification('admin_approved', {
      targetRole: 'admin',
      targetUid: uid,
      message: 'Your admin account has been approved! You can now log in.',
      performedBy: req.user.uid
    });

    await createAuditLog('approval', req.user.uid, uid, { role: 'admin', action: 'approved' });

    return res.status(200).json({ message: 'Admin approved successfully.' });
  } catch (error) {
    console.error('[Approval] Error approving admin:', error);
    return res.status(500).json({ error: 'Failed to approve admin.' });
  }
};

export const rejectAdmin = async (req, res) => {
  try {
    const { uid } = req.params;
    const now = new Date().toISOString();

    await db.collection('users').doc(uid).update({
      status: 'rejected',
      approved: false,
      updatedAt: now
    });

    await createNotification('admin_rejected', {
      targetRole: 'admin',
      targetUid: uid,
      message: 'Your admin account request has been rejected.',
      performedBy: req.user.uid
    });

    await createAuditLog('rejection', req.user.uid, uid, { role: 'admin', action: 'rejected' });

    return res.status(200).json({ message: 'Admin rejected.' });
  } catch (error) {
    console.error('[Approval] Error rejecting admin:', error);
    return res.status(500).json({ error: 'Failed to reject admin.' });
  }
};

export const suspendAdmin = async (req, res) => {
  try {
    const { uid } = req.params;
    const now = new Date().toISOString();

    await db.collection('users').doc(uid).update({
      status: 'suspended',
      updatedAt: now
    });

    await createAuditLog('suspension', req.user.uid, uid, { role: 'admin', action: 'suspended' });

    return res.status(200).json({ message: 'Admin suspended.' });
  } catch (error) {
    console.error('[Approval] Error suspending admin:', error);
    return res.status(500).json({ error: 'Failed to suspend admin.' });
  }
};

export const deleteAdmin = async (req, res) => {
  try {
    const { uid } = req.params;

    // Delete Firestore user doc
    await db.collection('users').doc(uid).delete();

    // Delete Firebase Auth account
    try {
      await auth.deleteUser(uid);
    } catch (authErr) {
      console.warn('[Approval] Could not delete Auth account:', authErr.message);
    }

    await createAuditLog('deletion', req.user.uid, uid, { role: 'admin', action: 'deleted' });

    return res.status(200).json({ message: 'Admin account deleted.' });
  } catch (error) {
    console.error('[Approval] Error deleting admin:', error);
    return res.status(500).json({ error: 'Failed to delete admin.' });
  }
};

// ══════════════════════════════════════════════════════════════════════
// ADMIN + SUPER ADMIN ENDPOINTS — Manage Drivers
// ══════════════════════════════════════════════════════════════════════

export const getPendingDrivers = async (req, res) => {
  try {
    const snapshot = await db.collection('users')
      .where('role', '==', 'driver')
      .where('status', '==', 'pending')
      .get();
    const list = [];
    snapshot.forEach(doc => list.push({ uid: doc.id, ...doc.data() }));

    // Enrich with driver collection data
    for (const user of list) {
      const driverDoc = await db.collection('drivers').doc(user.uid).get();
      if (driverDoc.exists) {
        user.driverDetails = driverDoc.data();
      }
    }

    return res.status(200).json(list);
  } catch (error) {
    console.error('[Approval] Error fetching pending drivers:', error);
    return res.status(500).json({ error: 'Failed to retrieve pending drivers.' });
  }
};

export const approveDriver = async (req, res) => {
  try {
    const { uid } = req.params;
    const now = new Date().toISOString();

    await db.collection('users').doc(uid).update({
      status: 'active',
      approved: true,
      updatedAt: now
    });

    // Update driver availability
    await db.collection('drivers').doc(uid).update({
      availability: true
    });

    // Update ambulance status
    await db.collection('ambulances').doc(uid).update({
      status: 'available',
      isAvailable: true,
      updatedAt: now
    });

    await createNotification('driver_approved', {
      targetRole: 'driver',
      targetUid: uid,
      message: 'Your driver account has been approved! You can now log in.',
      performedBy: req.user.uid
    });

    await createAuditLog('approval', req.user.uid, uid, { role: 'driver', action: 'approved' });

    return res.status(200).json({ message: 'Driver approved successfully.' });
  } catch (error) {
    console.error('[Approval] Error approving driver:', error);
    return res.status(500).json({ error: 'Failed to approve driver.' });
  }
};

export const rejectDriver = async (req, res) => {
  try {
    const { uid } = req.params;
    const now = new Date().toISOString();

    await db.collection('users').doc(uid).update({
      status: 'rejected',
      approved: false,
      updatedAt: now
    });

    await createNotification('driver_rejected', {
      targetRole: 'driver',
      targetUid: uid,
      message: 'Your driver account request has been rejected.',
      performedBy: req.user.uid
    });

    await createAuditLog('rejection', req.user.uid, uid, { role: 'driver', action: 'rejected' });

    return res.status(200).json({ message: 'Driver rejected.' });
  } catch (error) {
    console.error('[Approval] Error rejecting driver:', error);
    return res.status(500).json({ error: 'Failed to reject driver.' });
  }
};

export const suspendDriver = async (req, res) => {
  try {
    const { uid } = req.params;
    const now = new Date().toISOString();

    await db.collection('users').doc(uid).update({
      status: 'suspended',
      updatedAt: now
    });

    await createAuditLog('suspension', req.user.uid, uid, { role: 'driver', action: 'suspended' });

    return res.status(200).json({ message: 'Driver suspended.' });
  } catch (error) {
    console.error('[Approval] Error suspending driver:', error);
    return res.status(500).json({ error: 'Failed to suspend driver.' });
  }
};

export const deleteDriver = async (req, res) => {
  try {
    const { uid } = req.params;

    // Delete all related docs
    await db.collection('users').doc(uid).delete();
    try { await db.collection('drivers').doc(uid).delete(); } catch (_) {}
    try { await db.collection('ambulances').doc(uid).delete(); } catch (_) {}

    // Delete Firebase Auth account
    try {
      await auth.deleteUser(uid);
    } catch (authErr) {
      console.warn('[Approval] Could not delete Auth account:', authErr.message);
    }

    await createAuditLog('deletion', req.user.uid, uid, { role: 'driver', action: 'deleted' });

    return res.status(200).json({ message: 'Driver account deleted.' });
  } catch (error) {
    console.error('[Approval] Error deleting driver:', error);
    return res.status(500).json({ error: 'Failed to delete driver.' });
  }
};

// ══════════════════════════════════════════════════════════════════════
// DATA LISTING ENDPOINTS
// ══════════════════════════════════════════════════════════════════════

export const getAllUsers = async (req, res) => {
  try {
    const snapshot = await db.collection('users').get();
    const list = [];
    snapshot.forEach(doc => list.push({ uid: doc.id, ...doc.data() }));
    return res.status(200).json(list);
  } catch (error) {
    console.error('[Approval] Error fetching all users:', error);
    return res.status(500).json({ error: 'Failed to retrieve users.' });
  }
};

export const getAllDrivers = async (req, res) => {
  try {
    const usersSnap = await db.collection('users')
      .where('role', '==', 'driver')
      .get();
    const list = [];
    for (const userDoc of usersSnap.docs) {
      const userData = { uid: userDoc.id, ...userDoc.data() };
      const driverDoc = await db.collection('drivers').doc(userDoc.id).get();
      if (driverDoc.exists) {
        userData.driverDetails = driverDoc.data();
      }
      list.push(userData);
    }
    return res.status(200).json(list);
  } catch (error) {
    console.error('[Approval] Error fetching all drivers:', error);
    return res.status(500).json({ error: 'Failed to retrieve drivers.' });
  }
};

export const getAuditLogs = async (req, res) => {
  try {
    const snapshot = await db.collection('audit_logs')
      .orderBy('createdAt', 'desc')
      .limit(200)
      .get();
    const list = [];
    snapshot.forEach(doc => list.push({ id: doc.id, ...doc.data() }));
    return res.status(200).json(list);
  } catch (error) {
    console.error('[Approval] Error fetching audit logs:', error);
    return res.status(500).json({ error: 'Failed to retrieve audit logs.' });
  }
};

export const getNotifications = async (req, res) => {
  try {
    const { role } = req.user;
    const snapshot = await db.collection('notifications')
      .where('targetRole', '==', role)
      .orderBy('createdAt', 'desc')
      .limit(50)
      .get();
    const list = [];
    snapshot.forEach(doc => list.push({ id: doc.id, ...doc.data() }));
    return res.status(200).json(list);
  } catch (error) {
    console.error('[Approval] Error fetching notifications:', error);
    return res.status(500).json({ error: 'Failed to retrieve notifications.' });
  }
};
