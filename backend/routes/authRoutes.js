import express from 'express';
import { register, login, getProfile, saveProfile } from '../controllers/authController.js';
import { verifyToken } from '../middleware/authMiddleware.js';
import { authLimiter, profileLimiter } from '../middleware/rateLimitMiddleware.js';
import {
  registerValidators,
  loginValidators,
  profileUidValidators,
  handleValidationErrors,
} from '../middleware/validators.js';

const router = express.Router();

// Public — strict rate limit + input validation
router.post('/register',
  authLimiter,
  registerValidators,
  handleValidationErrors,
  register,
);

router.post('/login',
  authLimiter,
  loginValidators,
  handleValidationErrors,
  login,
);

// Authenticated — moderate rate limit + param validation
router.get('/profile/:uid',
  profileLimiter,
  verifyToken,
  profileUidValidators,
  handleValidationErrors,
  getProfile,
);

router.post('/profile/:uid',
  profileLimiter,
  verifyToken,
  profileUidValidators,
  handleValidationErrors,
  saveProfile,
);

export default router;
