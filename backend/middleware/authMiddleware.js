import { auth, db, userCache } from '../config/firebaseAdmin.js';

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

  // 3. Minimum plausible length (prevents trivially crafted micro-tokens)
  if (token.length < 40) {
    return res.status(401).json({ error: 'Unauthorized: Token too short.' });
  }

  try {
    const decodedToken = await auth.verifyIdToken(token);

    // 4. Decoded payload must contain a non-empty, well-formed uid
    const uid = decodedToken?.uid || decodedToken?.user_id || decodedToken?.sub;
    if (!uid || typeof uid !== 'string' || !VALID_UID_RE.test(uid)) {
      return res.status(401).json({ error: 'Unauthorized: Token contains invalid user identity.' });
    }

    // 5. Normalise uid on the decoded object
    decodedToken.uid = uid;
    req.user = decodedToken;

    // 6. Fetch role from Firestore (ground truth — not from JWT payload)
    //    If no profile exists, the token is for an unregistered user → reject
    let role = 'user';
    if (userCache.has(uid)) {
      role = userCache.get(uid).role || 'user';
    } else {
      const userDoc = await db.collection('users').doc(uid).get();
      if (userDoc.exists) {
        const data = userDoc.data();
        role = data.role || 'user';
        userCache.set(uid, data);
      } else {
        return res.status(401).json({ error: 'Unauthorized: User account not found.' });
      }
    }
    req.user.role = role;

    next();
  } catch (error) {
    // All Firebase token verification errors → always 401 (never expose internals)
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
