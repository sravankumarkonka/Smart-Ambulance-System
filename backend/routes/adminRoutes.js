import express from 'express';
import { getStats, getAllAmbulances, getAvailableAmbulances } from '../controllers/adminController.js';
import { authMiddleware, checkRole } from '../middleware/authMiddleware.js';

const router = express.Router();

// Apply auth middleware to protect these routes
router.use(authMiddleware);

router.get('/stats', checkRole('admin'), getStats);
router.get('/ambulances', checkRole('admin'), getAllAmbulances);
router.get('/ambulances/available', checkRole(['admin', 'user', 'driver']), getAvailableAmbulances);

export default router;
