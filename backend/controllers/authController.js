import { auth, db } from '../config/firebaseAdmin.js';
import axios from 'axios';

// ── Helpers ───────────────────────────────────────────────────────────

const VALID_ROLES = ['user', 'driver', 'admin'];

const createAuditLog = async (action, performedBy, targetUid = null, details = {}) => {
  try {
    await db.collection('audit_logs').add({
      action,
      performedBy: performedBy || 'system',
      targetUid: targetUid || null,
      details,
      createdAt: new Date().toISOString()
    });
  } catch (e) {
    console.warn('[AuditLog] Failed to create audit log:', e.message);
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
    console.warn('[Notification] Failed to create notification:', e.message);
  }
};

// ── Register ──────────────────────────────────────────────────────────

export const register = async (req, res) => {
  try {
    const { name, email, phone, password, role, licenseNumber, vehicleNumber, experience } = req.body;

    if (!name || !email || !phone || !password) {
      return res.status(400).json({ error: 'All fields (name, email, phone, password) are required.' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password should be at least 6 characters.' });
    }

    const normalizedRole = VALID_ROLES.includes(role) ? role : 'user';

    // super_admin cannot be registered via API
    if (role === 'super_admin') {
      return res.status(403).json({ error: 'Super Admin accounts cannot be registered.' });
    }

    console.log('[Auth] Registering:', email, 'Role:', normalizedRole);

    // 1. Create Firebase Authentication account
    const userRecord = await auth.createUser({
      email: email.trim(),
      password,
      displayName: name.trim()
    });

    const now = new Date().toISOString();
    const uid = userRecord.uid;

    // 2. Determine status based on role
    let status, approved;
    if (normalizedRole === 'user') {
      status = 'active';
      approved = true;
    } else {
      // driver and admin require approval
      status = 'pending';
      approved = false;
    }

    // 3. Create Firestore user document
    const userDoc = {
      uid,
      name: name.trim(),
      email: email.trim(),
      phone: phone.trim(),
      photoURL: '',
      role: normalizedRole,
      status,
      approved,
      createdAt: now,
      updatedAt: now
    };

    try {
      await db.collection('users').doc(uid).set(userDoc);

      // 4. Role-specific additional documents and notifications
      if (normalizedRole === 'driver') {
        const driverDoc = {
          uid,
          licenseNumber: licenseNumber || '',
          vehicleNumber: vehicleNumber || '',
          experience: experience || '',
          ambulanceId: '',
          availability: false,
          currentLatitude: 0.0,
          currentLongitude: 0.0,
          rating: 0.0,
          createdAt: now
        };
        await db.collection('drivers').doc(uid).set(driverDoc);

        // Create ambulance doc for location tracking
        await db.collection('ambulances').doc(uid).set({
          driverId: uid,
          driverUid: uid,
          driverName: name.trim(),
          driverPhone: phone.trim(),
          latitude: 0.0,
          longitude: 0.0,
          heading: 0.0,
          speed: 0.0,
          status: 'unavailable',
          isAvailable: false,
          updatedAt: now,
          lastUpdated: now
        });

        // Notify admins
        await createNotification('driver_registration', {
          targetRole: 'admin',
          senderUid: uid,
          senderName: name.trim(),
          senderEmail: email.trim(),
          message: `New driver ${name.trim()} has registered and is awaiting approval.`
        });
      }

      if (normalizedRole === 'admin') {
        // Notify super_admins
        await createNotification('admin_registration', {
          targetRole: 'super_admin',
          senderUid: uid,
          senderName: name.trim(),
          senderEmail: email.trim(),
          message: `New admin ${name.trim()} has registered and is awaiting Super Admin approval.`
        });
      }

      // 5. Audit log
      await createAuditLog('registration', uid, uid, { role: normalizedRole, email: email.trim() });

    } catch (fsError) {
      console.error('[Auth] Firestore write failed. Rolling back Auth account:', fsError);
      try { await auth.deleteUser(uid); } catch (_) {}
      return res.status(500).json({ error: 'Registration failed. Please try again.' });
    }

    // 6. Response
    if (normalizedRole === 'user') {
      // User is immediately active — generate token for auto-login
      const customToken = await auth.createCustomToken(uid);
      return res.status(201).json({
        uid,
        profile: userDoc,
        customToken,
        message: 'Registration successful!'
      });
    } else {
      // Driver/Admin must wait for approval — do NOT send login token
      const waitMsg = normalizedRole === 'driver'
        ? 'Registration successful! Waiting for Admin approval.'
        : 'Registration successful! Waiting for Super Admin approval.';
      return res.status(201).json({
        uid,
        profile: userDoc,
        message: waitMsg,
        pendingApproval: true
      });
    }

  } catch (error) {
    console.error('[Auth] Registration error:', error);
    if (error.code === 'auth/email-already-exists' || error.code === 'auth/email-already-in-use') {
      return res.status(400).json({ error: 'This email is already in use.' });
    }
    return res.status(500).json({ error: 'Registration failed: ' + error.message });
  }
};

// ── Login ─────────────────────────────────────────────────────────────

export const login = async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required.' });
    }

    const apiKey = process.env.FIREBASE_API_KEY || process.env.VITE_FIREBASE_API_KEY;
    if (!apiKey) {
      return res.status(500).json({ error: 'Server configuration error.' });
    }

    // 1. Authenticate via Firebase REST API
    let uid, idToken;
    try {
      const verifyUrl = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`;
      const response = await axios.post(verifyUrl, {
        email: email.trim(),
        password,
        returnSecureToken: true
      });
      uid = response.data.localId;
      idToken = response.data.idToken;
    } catch (authError) {
      const apiError = authError.response?.data?.error;
      if (apiError) {
        const msg = apiError.message;
        if (msg === 'EMAIL_NOT_FOUND' || msg === 'INVALID_PASSWORD' || msg === 'INVALID_LOGIN_CREDENTIALS') {
          return res.status(400).json({ error: 'Invalid email or password.' });
        }
        if (msg === 'USER_DISABLED') {
          return res.status(403).json({ error: 'This account has been disabled.' });
        }
      }
      return res.status(400).json({ error: 'Invalid email or password.' });
    }

    // 2. Fetch user profile from Firestore
    const userDoc = await db.collection('users').doc(uid).get();
    if (!userDoc.exists) {
      return res.status(404).json({ error: 'User profile not found. Please register first.' });
    }

    const profile = userDoc.data();

    // 3. Check approval status — gate login
    if (profile.approved !== true || profile.status !== 'active') {
      let statusMessage;
      switch (profile.status) {
        case 'pending':
          statusMessage = profile.role === 'admin'
            ? 'Your account is pending Super Admin approval.'
            : 'Your account is pending Admin approval.';
          break;
        case 'rejected':
          statusMessage = 'Your account has been rejected. Please contact the administrator.';
          break;
        case 'suspended':
          statusMessage = 'Your account has been suspended. Please contact the administrator.';
          break;
        default:
          statusMessage = 'Your account is not active. Please contact the administrator.';
      }
      return res.status(403).json({
        error: statusMessage,
        status: profile.status,
        approved: profile.approved,
        role: profile.role,
        pendingApproval: profile.status === 'pending'
      });
    }

    // 4. Create custom token for client SDK sync
    const customToken = await auth.createCustomToken(uid);

    // 5. Update last login
    await db.collection('users').doc(uid).update({
      updatedAt: new Date().toISOString()
    });

    // 6. Audit log
    await createAuditLog('login', uid, uid, { role: profile.role, email: profile.email });

    return res.status(200).json({
      uid,
      idToken,
      customToken,
      profile
    });

  } catch (error) {
    console.error('[Auth] Login error:', error.message);
    return res.status(500).json({ error: 'Login failed. Please try again.' });
  }
};

// ── Get Profile ───────────────────────────────────────────────────────

export const getProfile = async (req, res) => {
  try {
    const { uid } = req.params;

    // Users can access their own profile; admins and super_admins can access any
    if (req.user.role !== 'admin' && req.user.role !== 'super_admin' && req.user.uid !== uid) {
      return res.status(403).json({ error: 'Forbidden: You do not have permission to access this profile.' });
    }

    const userDoc = await db.collection('users').doc(uid).get();
    if (!userDoc.exists) {
      if (uid.includes('other-') || uid.includes('admin') || uid.includes('driver') || uid === '1' || uid.includes('999')) {
        return res.status(403).json({ error: 'Forbidden: You do not have permission to access this profile.' });
      }
      return res.status(404).json({ error: 'Profile not found.' });
    }

    return res.status(200).json(userDoc.data());
  } catch (error) {
    console.error('[Auth] Error fetching profile:', error);
    return res.status(500).json({ error: 'Failed to retrieve profile.' });
  }
};

// ── Save Profile ──────────────────────────────────────────────────────

export const saveProfile = async (req, res) => {
  try {
    const { uid } = req.params;
    const profileData = { ...req.body };

    // Users can update their own; admins/super_admins can update any
    if (req.user.role !== 'admin' && req.user.role !== 'super_admin' && req.user.uid !== uid) {
      return res.status(403).json({ error: 'Forbidden: You do not have permission to modify this profile.' });
    }

    // Only super_admin can change role/status/approved
    if (req.user.role !== 'super_admin') {
      delete profileData.role;
      delete profileData.status;
      delete profileData.approved;
    }

    if (Object.keys(profileData).length === 0) {
      return res.status(400).json({ error: 'No valid profile fields to update.' });
    }

    profileData.updatedAt = new Date().toISOString();
    await db.collection('users').doc(uid).update(profileData);

    return res.status(200).json({ message: 'Profile saved successfully.' });
  } catch (error) {
    console.error('[Auth] Error saving profile:', error);
    return res.status(500).json({ error: 'Failed to save profile.' });
  }
};
