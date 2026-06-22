/**
 * validators.js
 * express-validator chains for every writable endpoint.
 * Protects against: SQLi, NoSQLi, XSS, Path Traversal, Command Injection.
 *
 * Usage in routes:
 *   router.post('/login', loginValidators, handleValidationErrors, login);
 */

import { body, param, query, validationResult } from 'express-validator';

// ── Shared helper: send 400 if any validator failed ───────────────────────────
export function handleValidationErrors(req, res, next) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    console.error('[Validation Fail]', errors.array());
    return res.status(400).json({
      error: 'Validation failed.',
      details: errors.array().map(e => ({ field: e.path, message: e.msg })),
    });
  }
  next();
}

// ── Shared sanitizers ────────────────────────────────────────────────────────
// Strips characters used in SQL/NoSQL/shell injection and XSS
const DANGEROUS_CHARS_RE = /[<>"'`;\\${}|&]/;

function noSQLInjection(value) {
  // Reject MongoDB operator objects (e.g. { $gt: '' })
  if (typeof value === 'object' && value !== null) {
    throw new Error('Invalid value type.');
  }
  return true;
}

function noPathTraversal(value) {
  if (typeof value === 'string' && (value.includes('..') || value.includes('/') || value.includes('\\'))) {
    throw new Error('Path traversal detected.');
  }
  return true;
}

// ── Auth: POST /api/auth/register ────────────────────────────────────────────
export const registerValidators = [
  body('name')
    .trim()
    .notEmpty().withMessage('Name is required.')
    .isLength({ min: 1, max: 100 }).withMessage('Name must be 1–100 characters.')
    .matches(/^[A-Za-z\s'\-\.]+$/).withMessage('Name contains invalid characters.')
    .escape(),

  body('email')
    .trim()
    .notEmpty().withMessage('Email is required.')
    .isEmail().withMessage('Must be a valid email address.')
    .matches(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/).withMessage('Must be a valid email address.')
    .isLength({ max: 254 }).withMessage('Email too long.')
    .normalizeEmail({ gmail_remove_dots: false }),

  body('phone')
    .trim()
    .notEmpty().withMessage('Phone is required.')
    .matches(/^[0-9+\-\s()]{7,15}$/).withMessage('Phone must be 7–15 digits.')
    .escape(),

  body('password')
    .notEmpty().withMessage('Password is required.')
    .isLength({ min: 6, max: 128 }).withMessage('Password must be 6–128 characters.')
    .custom((val) => {
      if (val && val.trim().length === 0) {
        throw new Error('Password cannot consist of only spaces.');
      }
      return true;
    }),
    // Do NOT escape password — it will corrupt the hash
];

// ── Auth: POST /api/auth/login ────────────────────────────────────────────────
export const loginValidators = [
  body('email')
    .trim()
    .notEmpty().withMessage('Email is required.')
    .isEmail().withMessage('Must be a valid email address.')
    .isLength({ max: 254 }).withMessage('Email too long.')
    .normalizeEmail({ gmail_remove_dots: false }),

  body('password')
    .notEmpty().withMessage('Password is required.')
    .isLength({ min: 1, max: 128 }).withMessage('Password length invalid.'),
];

// ── Auth: GET/POST /api/auth/profile/:uid ────────────────────────────────────
export const profileUidValidators = [
  param('uid')
    .trim()
    .notEmpty().withMessage('User ID is required.')
    .isLength({ min: 1, max: 128 }).withMessage('User ID length invalid.')
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('User ID contains invalid characters.'),
];

// ── Emergency: POST /api/emergencies ─────────────────────────────────────────
export const createEmergencyValidators = [
  body('userId')
    .trim()
    .notEmpty().withMessage('userId is required.')
    .isLength({ max: 128 }).withMessage('userId too long.')
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('userId contains invalid characters.'),

  body('patientName')
    .trim()
    .notEmpty().withMessage('patientName is required.')
    .isLength({ min: 1, max: 100 }).withMessage('patientName must be 1–100 characters.')
    .matches(/^[A-Za-z0-9\s'\-\.&]+$/).withMessage('patientName contains invalid characters.')
    .escape(),

  body('emergencyType')
    .trim()
    .notEmpty().withMessage('emergencyType is required.')
    .isIn(['accident', 'cardiac', 'respiratory', 'stroke', 'pregnancy', 'other'])
    .withMessage('emergencyType must be one of: accident, cardiac, respiratory, stroke, pregnancy, other.'),

  body('description')
    .trim()
    .notEmpty().withMessage('description is required.')
    .isLength({ min: 1, max: 2000 }).withMessage('description must be 1–2000 characters.')
    .customSanitizer(v => v.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')),

  body('latitude')
    .notEmpty().withMessage('latitude is required.')
    .isFloat({ min: -90, max: 90 }).withMessage('latitude must be between -90 and 90.')
    .toFloat(),

  body('longitude')
    .notEmpty().withMessage('longitude is required.')
    .isFloat({ min: -180, max: 180 }).withMessage('longitude must be between -180 and 180.')
    .toFloat(),

  body('severityLevel')
    .optional()
    .trim()
    .isIn(['low', 'medium', 'high', 'critical']).withMessage('severityLevel must be low, medium, high, or critical.'),

  body('hospitalName')
    .optional()
    .trim()
    .isLength({ max: 200 }).withMessage('hospitalName too long.')
    .escape(),

  body('hospitalLatitude')
    .optional()
    .isFloat({ min: -90, max: 90 }).withMessage('hospitalLatitude must be between -90 and 90.')
    .toFloat(),

  body('hospitalLongitude')
    .optional()
    .isFloat({ min: -180, max: 180 }).withMessage('hospitalLongitude must be between -180 and 180.')
    .toFloat(),
];

// ── Emergency: GET /api/emergencies/:id ──────────────────────────────────────
export const emergencyIdValidators = [
  param('id')
    .trim()
    .notEmpty().withMessage('Emergency ID is required.')
    .isLength({ max: 128 }).withMessage('Emergency ID too long.')
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('Emergency ID contains invalid characters.'),
];

// ── Emergency: GET /api/emergencies/history/:userId ──────────────────────────
export const historyUserIdValidators = [
  param('userId')
    .trim()
    .notEmpty().withMessage('userId is required.')
    .isLength({ max: 128 }).withMessage('userId too long.')
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('userId contains invalid characters.'),
];

// ── Driver: POST /api/driver/emergencies/:id/assign ──────────────────────────
export const assignDriverValidators = [
  param('id')
    .trim()
    .notEmpty().withMessage('Emergency ID is required.')
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('Emergency ID contains invalid characters.'),

  body('driverId')
    .trim()
    .notEmpty().withMessage('driverId is required.')
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('driverId contains invalid characters.'),

  body('driverName')
    .trim()
    .notEmpty().withMessage('driverName is required.')
    .isLength({ min: 1, max: 100 })
    .matches(/^[A-Za-z\s'\-\.]+$/).withMessage('driverName contains invalid characters.')
    .escape(),

  body('driverPhone')
    .trim()
    .notEmpty().withMessage('driverPhone is required.')
    .matches(/^[0-9+\-\s()]{7,15}$/).withMessage('driverPhone must be 7–15 digits.')
    .escape(),
];

// ── Driver: PATCH /api/driver/emergencies/:id/status ─────────────────────────
export const updateStatusValidators = [
  param('id')
    .trim()
    .notEmpty()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/),

  body('status')
    .trim()
    .notEmpty().withMessage('status is required.')
    .isIn(['pending', 'assigned', 'arrived', 'completed', 'cancelled'])
    .withMessage('status must be: pending, assigned, arrived, completed, or cancelled.'),
];

// ── Driver: POST /api/driver/emergencies/:id/auto-assign ─────────────────────
export const autoAssignValidators = [
  param('id')
    .trim()
    .notEmpty()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/),

  body('latitude')
    .notEmpty().withMessage('latitude is required.')
    .isFloat({ min: -90, max: 90 }).withMessage('Invalid latitude.')
    .toFloat(),

  body('longitude')
    .notEmpty().withMessage('longitude is required.')
    .isFloat({ min: -180, max: 180 }).withMessage('Invalid longitude.')
    .toFloat(),
];

// ── Driver: POST /api/driver/ambulances/:driverId/location ───────────────────
export const updateLocationValidators = [
  param('driverId')
    .trim()
    .notEmpty()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/),

  body('latitude')
    .notEmpty().withMessage('latitude is required.')
    .isFloat({ min: -90, max: 90 }).withMessage('Invalid latitude.')
    .toFloat(),

  body('longitude')
    .notEmpty().withMessage('longitude is required.')
    .isFloat({ min: -180, max: 180 }).withMessage('Invalid longitude.')
    .toFloat(),

  body('emergencyId')
    .optional()
    .trim()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]*$/).withMessage('emergencyId contains invalid characters.'),
];

// ── Driver: GET /api/driver/ambulances/:driverId ─────────────────────────────
export const driverIdParamValidators = [
  param('driverId')
    .trim()
    .notEmpty()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('driverId contains invalid characters.'),
];

// ── Driver: POST /api/driver/ambulances (updateAmbulance) ────────────────────
export const updateAmbulanceValidators = [
  body('driverId')
    .trim()
    .notEmpty().withMessage('driverId is required.')
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/).withMessage('driverId contains invalid characters.'),
];

// ── Driver: POST /api/driver/emergencies/:id/release ─────────────────────────
export const releaseDriverValidators = [
  param('id')
    .trim()
    .notEmpty()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]+$/),

  body('driverId')
    .optional()
    .trim()
    .isLength({ max: 128 })
    .custom(noPathTraversal)
    .matches(/^[A-Za-z0-9_\-]*$/),
];

// ── Hospital: GET /api/hospitals/recommend ───────────────────────────────────
export const recommendHospitalValidators = [
  query('latitude')
    .notEmpty().withMessage('latitude is required.')
    .isFloat({ min: -90, max: 90 }).withMessage('latitude must be between -90 and 90.')
    .toFloat(),
  query('longitude')
    .notEmpty().withMessage('longitude is required.')
    .isFloat({ min: -180, max: 180 }).withMessage('longitude must be between -180 and 180.')
    .toFloat(),
  query('severityLevel')
    .optional()
    .trim()
    .isIn(['low', 'medium', 'high', 'critical']).withMessage('severityLevel must be low, medium, high, or critical.'),
];

// ── Routing: POST /api/route ─────────────────────────────────────────────────
export const getRouteValidators = [
  body('waypoints')
    .notEmpty().withMessage('waypoints is required.')
    .isArray({ min: 2 }).withMessage('waypoints must be an array of at least 2 points.')
    .custom((value) => {
      for (const pt of value) {
        if (!Array.isArray(pt) || pt.length !== 2) {
          throw new Error('Each waypoint must be an array of [latitude, longitude].');
        }
        const lat = Number(pt[0]);
        const lng = Number(pt[1]);
        if (isNaN(lat) || lat < -90 || lat > 90) {
          throw new Error('Each waypoint latitude must be between -90 and 90.');
        }
        if (isNaN(lng) || lng < -180 || lng > 180) {
          throw new Error('Each waypoint longitude must be between -180 and 180.');
        }
      }
      return true;
    }),
  body('useAstar')
    .optional()
    .isBoolean().withMessage('useAstar must be a boolean.'),
];
