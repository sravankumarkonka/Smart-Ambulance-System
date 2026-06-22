import express from 'express';
import { recommendHospital, getHospitalsList } from '../controllers/hospitalController.js';
import { authMiddleware, checkRole } from '../middleware/authMiddleware.js';
import { lookupLimiter } from '../middleware/rateLimitMiddleware.js';
import { recommendHospitalValidators, handleValidationErrors } from '../middleware/validators.js';

const router = express.Router();

// Apply auth middleware to all routes in this router
router.use(authMiddleware);
router.use(checkRole('user'));

// Rate limit all hospital lookups (prevents scraping)
router.use(lookupLimiter);

router.get('/recommend', recommendHospitalValidators, handleValidationErrors, recommendHospital);
router.get('/', getHospitalsList);

export default router;
