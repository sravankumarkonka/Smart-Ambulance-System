import express from 'express';
import { assignDriver, updateStatus, releaseDriver, updateAmbulance, updateLocation, autoAssign, getAmbulance } from '../controllers/driverController.js';
import { authMiddleware, checkRole } from '../middleware/authMiddleware.js';
import {
  assignDriverValidators,
  updateStatusValidators,
  releaseDriverValidators,
  updateAmbulanceValidators,
  updateLocationValidators,
  autoAssignValidators,
  driverIdParamValidators,
  emergencyIdValidators,
  handleValidationErrors,
} from '../middleware/validators.js';

const router = express.Router();

// Apply auth middleware to all routes in this router
router.use(authMiddleware);
router.use(checkRole('driver'));

router.post('/emergencies/:id/assign',
  assignDriverValidators,
  handleValidationErrors,
  assignDriver,
);

router.post('/emergencies/:id/auto-assign',
  autoAssignValidators,
  handleValidationErrors,
  autoAssign,
);

router.patch('/emergencies/:id/status',
  updateStatusValidators,
  handleValidationErrors,
  updateStatus,
);

router.post('/emergencies/:id/release',
  releaseDriverValidators,
  handleValidationErrors,
  releaseDriver,
);

router.post('/ambulances',
  updateAmbulanceValidators,
  handleValidationErrors,
  updateAmbulance,
);

router.get('/ambulances/:driverId',
  driverIdParamValidators,
  handleValidationErrors,
  getAmbulance,
);

router.post('/ambulances/:driverId/location',
  updateLocationValidators,
  handleValidationErrors,
  updateLocation,
);

export default router;
