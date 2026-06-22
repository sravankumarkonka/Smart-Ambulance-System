import express from 'express';
import { getRoute } from '../controllers/routingController.js';
import { authMiddleware, checkRole } from '../middleware/authMiddleware.js';
import { lookupLimiter } from '../middleware/rateLimitMiddleware.js';
import { getRouteValidators, handleValidationErrors } from '../middleware/validators.js';

const router = express.Router();

// Apply auth middleware to all routes in this router
router.use(authMiddleware);
router.use(checkRole(['user', 'driver', 'admin']));

// Rate limit routing lookups (prevents scraping / API abuse)
router.post('/', lookupLimiter, getRouteValidators, handleValidationErrors, getRoute);

export default router;
