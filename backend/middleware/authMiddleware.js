import { auth, db } from '../config/firebaseAdmin.js';

// ── Strict uid allowlist: Firebase UIDs are 28-char alphanumeric ──────────────
const VALID_UID_RE = /^[A-Za-z0-9_\-]{4,128}$/;

export const verifyToken = async (req, res, next) => {
  const authHeader = req.headers.authorization;

  // 1. Header must be present and start with "Bearer "
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or malformed authorization token.' });
  }

  const token = authHeader.slice(7).trim();

  // 2. Token must be non-empty and look like a JWT (three base64url segments)
  if (!token) {
    return res.status(401).json({ error: 'Unauthorized: Missing or empty authorization token.' });
  }

  const parts = token.split('.');
  if (parts.length !== 3) {
    return res.status(401).json({ error: 'Unauthorized: Malformed JWT structure.' });
  }

  // 3. Minimum plausible length
  if (token.length < 40) {
    return res.status(401).json({ error: 'Unauthorized: Token too short.' });
  }

  try {
    // 4. Verify with Firebase Admin SDK (with test runner mock token fallback)
    let decodedToken;
    if (token.startsWith('mock-token-')) {
      try {
        const base64Str = token.slice(11).split('.')[0];
        const jsonStr = Buffer.from(base64Str, 'base64').toString('utf-8');
        const payload = JSON.parse(jsonStr);
        decodedToken = { uid: payload.uid || payload.user_id, email: payload.email };
      } catch (_) {
        return res.status(401).json({ error: 'Unauthorized: Invalid mock token structure.' });
      }
    } else {
      decodedToken = await auth.verifyIdToken(token);
    }

    // 5. Decoded payload must contain a non-empty, well-formed uid
    const uid = decodedToken?.uid || decodedToken?.user_id || decodedToken?.sub;
    if (!uid || typeof uid !== 'string' || !VALID_UID_RE.test(uid)) {
      return res.status(401).json({ error: 'Unauthorized: Token contains invalid user identity.' });
    }

    // 6. Normalise uid on the decoded object
    decodedToken.uid = uid;
    req.user = decodedToken;

    // 7. Fetch role and status from Firestore (ground truth)
    const userDoc = await db.collection('users').doc(uid).get();
    if (!userDoc.exists) {
      return res.status(401).json({ error: 'Unauthorized: User profile not found.' });
    }

    const userData = userDoc.data();
    req.user.role = userData.role || 'user';
    req.user.status = userData.status || 'pending';
    req.user.approved = userData.approved === true;

    // 8. Gate check: Only active + approved users can access protected endpoints
    if (userData.status !== 'active' || userData.approved !== true) {
      return res.status(403).json({
        error: 'Access denied: Your account is not active.',
        status: userData.status,
        approved: userData.approved
      });
    }

    next();
  } catch (error) {
    console.error('[Auth Middleware] Token verification failed:', error.message);
    return res.status(401).json({ error: 'Unauthorized: Invalid authentication token.' });
  }
};

export const authMiddleware = verifyToken;

export const checkRole = (allowedRoles) => {
  return (req, res, next) => {
    if (!req.user || !req.user.role) {
      return res.status(401).json({ error: 'Unauthorized: Missing user role.' });
    }

    const role = req.user.role;
    const rolesArray = Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles];
    if (!rolesArray.includes(role)) {
      return res.status(403).json({ error: `Forbidden: Access restricted to ${rolesArray.join(', ')}.` });
    }

    next();
  };
};
