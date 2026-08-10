import express from 'express';
import { getStats, getAllAmbulances, getAvailableAmbulances, getAllEmergencies } from '../controllers/adminController.js';
import { authMiddleware, checkRole } from '../middleware/authMiddleware.js';

const router = express.Router();

// Apply auth middleware to protect these routes
router.use(authMiddleware);
router.get('/stats', checkRole(['admin', 'super_admin']), getStats);
router.get('/ambulances', checkRole(['admin', 'super_admin']), getAllAmbulances);
router.get('/ambulances/available', checkRole(['admin', 'user', 'driver', 'super_admin']), getAvailableAmbulances);
router.get('/emergencies', checkRole(['admin', 'super_admin']), getAllEmergencies);

export default router;
