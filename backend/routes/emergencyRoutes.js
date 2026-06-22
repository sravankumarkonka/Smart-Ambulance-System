import express from 'express';
import { create, getById, getHistory, uploadImage, upload, cancel } from '../controllers/emergencyController.js';
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
router.use(checkRole('user'));

// POST /api/emergencies — rate limited + full body validation
router.post('/',
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

const handleMulterUpload = (req, res, next) => {
  upload.single('file')(req, res, (err) => {
    if (err) {
      console.error('[Multer Error]', err.message);
      return res.status(400).json({ error: 'File upload failed: ' + err.message });
    }
    next();
  });
};

// POST /api/emergencies/:id/image — param validation (file handled by multer wrapper)
router.post('/:id/image',
  emergencyIdValidators,
  handleValidationErrors,
  handleMulterUpload,
  uploadImage,
);

// POST /api/emergencies/:id/cancel — param validation
router.post('/:id/cancel',
  emergencyIdValidators,
  handleValidationErrors,
  cancel,
);

export default router;
