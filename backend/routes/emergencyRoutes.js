import express from 'express';
import { create, getById, getHistory, cancel } from '../controllers/emergencyController.js';
import { authMiddleware, checkRole } from '../middleware/authMiddleware.js';
import { emergencyLimiter } from '../middleware/rateLimitMiddleware.js';
import {
  createEmergencyValidators,
  emergencyIdValidators,
  historyUserIdValidators,
  handleValidationErrors,
} from '../middleware/validators.js';

const router = express.Router();

// Apply auth middleware to all routes in this router
router.use(authMiddleware);
router.use(checkRole(['user', 'driver', 'admin']));

// POST /api/emergencies — rate limited + full body validation (user role only)
router.post('/',
  checkRole('user'),
  emergencyLimiter,
  createEmergencyValidators,
  handleValidationErrors,
  create,
);

// GET /api/emergencies/:id — param validation
router.get('/:id',
  emergencyIdValidators,
  handleValidationErrors,
  getById,
);

// GET /api/emergencies/history/:userId — param validation
router.get('/history/:userId',
  historyUserIdValidators,
  handleValidationErrors,
  getHistory,
);

// POST /api/emergencies/:id/cancel — param validation
router.post('/:id/cancel',
  emergencyIdValidators,
  handleValidationErrors,
  cancel,
);

export default router;
