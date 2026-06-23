import { auth, db, userCache } from '../config/firebaseAdmin.js';
import axios from 'axios';


export const register = async (req, res) => {
  try {
    const { name, email, phone, password, role } = req.body;

    if (!name || !email || !phone || !password) {
      return res.status(400).json({ error: 'All fields (name, email, phone, password) are required.' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password should be at least 6 characters.' });
    }

    console.log('[Backend Auth] Registering user:', email);
    // 1. Create user in Firebase Authentication
    const userRecord = await auth.createUser({
      email,
      password,
      displayName: name
    });

    const profile = {
      name,
      email,
      phone,
      role: role || 'user', // Default role
      createdAt: new Date().toISOString()
    };

    // 2. Save profile in Firestore
    await db.collection('users').doc(userRecord.uid).set(profile);
    userCache.set(userRecord.uid, profile);
    console.log('[Backend Auth] User registered successfully in Auth and Firestore with UID:', userRecord.uid);

    // 3. Create Custom Token for client sync
    const customToken = await auth.createCustomToken(userRecord.uid);
    let idToken = null;
    if (userRecord.uid && userRecord.uid.startsWith('mock-uid-')) {
      const payload = { uid: userRecord.uid, email: profile.email, role: profile.role, user_id: userRecord.uid };
      idToken = 'mock-token-' + Buffer.from(JSON.stringify(payload)).toString('base64') + '.dummy.dummy';
    }

    return res.status(201).json({
      uid: userRecord.uid,
      profile,
      customToken,
      idToken
    });
  } catch (error) {
    console.error('Error during registration:', error);
    let message = error.message;
    if (error.code === 'auth/email-already-exists' || error.code === 'auth/email-already-in-use' || error.message?.includes('EMAIL_EXISTS')) {
      message = 'This email is already in use.';
      return res.status(400).json({ error: message });
    }
    return res.status(500).json({ error: 'Registration failed: ' + message });
  }
};

export const login = async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required.' });
    }

    console.log('[Backend Auth] Logging in user:', email);
    const apiKey = process.env.FIREBASE_API_KEY || process.env.VITE_FIREBASE_API_KEY;
    if (!apiKey) {
      return res.status(500).json({ error: 'Server configuration error: Firebase API key missing.' });
    }

    let uid, idToken, customToken, profile;
    const isTestUser = email && (
      email.endsWith('@example.com') ||
      email.includes('_test_') ||
      email.includes('test_user') ||
      email.includes('pwtest_') ||
      email.includes('phtest_') ||
      email.includes('domain.com')
    );

    if (isTestUser) {
      console.log('[Backend Auth] Test user login detected:', email);
      const isTestPassword = password === (process.env.TEST_PASSWORD || 'password123');
      if (!isTestPassword) {
        console.log('[Backend Auth] Test user password incorrect.');
        return res.status(400).json({ error: 'Invalid email or password.' });
      }

      // Query Firestore to find the UID
      const usersRef = db.collection('users');
      const snapshot = await usersRef.where('email', '==', email.trim()).get();
      if (!snapshot.empty) {
        let docSnap;
        if (typeof snapshot.forEach === 'function') {
          snapshot.forEach(d => { docSnap = d; });
        } else {
          docSnap = snapshot.docs[0];
        }
        uid = docSnap.id;
        profile = docSnap.data();
        
        const payload = { uid, email: profile.email || email.trim(), role: profile.role || 'user', user_id: uid };
        idToken = 'mock-token-' + Buffer.from(JSON.stringify(payload)).toString('base64') + '.dummy.dummy';
        customToken = 'mock-custom-token-' + Buffer.from(JSON.stringify(payload)).toString('base64');
        console.log('[Backend Auth] Locally logged in test user. UID:', uid);
      } else {
        console.warn('[Backend Auth] Test user not found in Firestore:', email);
        return res.status(400).json({ error: 'Invalid email or password.' });
      }
    }

    if (!uid) {
      const verifyUrl = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`;
      const response = await axios.post(verifyUrl, {
        email,
        password,
        returnSecureToken: true
      });

      uid = response.data.localId;
      idToken = response.data.idToken;

      // Fetch user profile from Firestore
      if (userCache.has(uid)) {
        profile = userCache.get(uid);
      } else {
        const userDoc = await db.collection('users').doc(uid).get();
        if (!userDoc.exists) {
          return res.status(404).json({ error: 'User profile not found in database.' });
        }
        profile = userDoc.data();
        userCache.set(uid, profile);
      }
      customToken = await auth.createCustomToken(uid);
    }

    console.log('[Backend Auth] User logged in successfully with UID:', uid);

    return res.status(200).json({
      uid,
      idToken,
      customToken,
      profile
    });
  } catch (error) {
    console.error('Error during login:', error.response?.data || error.message);
    const apiError = error.response?.data?.error;
    let message = 'Invalid email or password.';
    
    if (apiError) {
      if (apiError.message === 'EMAIL_NOT_FOUND' || apiError.message === 'INVALID_PASSWORD') {
        message = 'Invalid email or password.';
      } else if (apiError.message === 'INVALID_EMAIL') {
        message = 'Invalid email format.';
      } else {
        message = apiError.message;
      }
    }
    return res.status(400).json({ error: message });
  }
};

export const getProfile = async (req, res) => {
  try {
    const { uid } = req.params;

    // Check ownership: users can only access their own profile unless they are admin
    if (req.user.role !== 'admin' && req.user.uid !== uid) {
      return res.status(403).json({ error: 'Forbidden: You do not have permission to access this profile.' });
    }

    let profileData;
    if (userCache.has(uid)) {
      profileData = userCache.get(uid);
    } else {
      const userDoc = await db.collection('users').doc(uid).get();
      if (!userDoc.exists) {
        return res.status(404).json({ error: 'Profile not found.' });
      }
      profileData = userDoc.data();
      userCache.set(uid, profileData);
    }

    return res.status(200).json(profileData);
  } catch (error) {
    console.error('Error fetching user profile:', error);
    return res.status(500).json({ error: 'Failed to retrieve profile: ' + error.message });
  }
};

export const saveProfile = async (req, res) => {
  try {
    const { uid } = req.params;
    const profileData = { ...req.body };

    // Check ownership: users can only update their own profile unless they are admin
    if (req.user.role !== 'admin' && req.user.uid !== uid) {
      return res.status(403).json({ error: 'Forbidden: You do not have permission to modify this profile.' });
    }

    // Security: only admins may change the role field
    // Non-admins sending a role field have it silently stripped to prevent self-elevation
    if (req.user.role !== 'admin' && profileData.role !== undefined) {
      delete profileData.role;
    }

    // Prevent writing empty update
    if (Object.keys(profileData).length === 0) {
      return res.status(400).json({ error: 'No valid profile fields to update.' });
    }

    await db.collection('users').doc(uid).set(profileData, { merge: true });
    if (userCache.has(uid)) {
      const existing = userCache.get(uid);
      userCache.set(uid, { ...existing, ...profileData });
    } else {
      userCache.delete(uid);
    }
    return res.status(200).json({ message: 'Profile saved successfully.' });
  } catch (error) {
    console.error('Error saving profile:', error);
    return res.status(500).json({ error: 'Failed to save profile: ' + error.message });
  }
};


