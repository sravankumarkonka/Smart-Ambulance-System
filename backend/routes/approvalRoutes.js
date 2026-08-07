import express from 'express';
import { verifyToken, checkRole } from '../middleware/authMiddleware.js';
import {
  getPendingAdmins,
  approveAdmin,
  rejectAdmin,
  suspendAdmin,
  deleteAdmin,
  getPendingDrivers,
  approveDriver,
  rejectDriver,
  suspendDriver,
  deleteDriver,
  getAllUsers,
  getAllDrivers,
  getAuditLogs,
  getNotifications
} from '../controllers/approvalController.js';

const router = express.Router();

// ── Super Admin Only — Manage Admins ──────────────────────────────────
router.get('/pending-admins', verifyToken, checkRole(['super_admin']), getPendingAdmins);
router.post('/admin/:uid/approve', verifyToken, checkRole(['super_admin']), approveAdmin);
router.post('/admin/:uid/reject', verifyToken, checkRole(['super_admin']), rejectAdmin);
router.post('/admin/:uid/suspend', verifyToken, checkRole(['super_admin']), suspendAdmin);
router.delete('/admin/:uid', verifyToken, checkRole(['super_admin']), deleteAdmin);

// ── Admin + Super Admin — Manage Drivers ──────────────────────────────
router.get('/pending-drivers', verifyToken, checkRole(['admin', 'super_admin']), getPendingDrivers);
router.post('/driver/:uid/approve', verifyToken, checkRole(['admin', 'super_admin']), approveDriver);
router.post('/driver/:uid/reject', verifyToken, checkRole(['admin', 'super_admin']), rejectDriver);
router.post('/driver/:uid/suspend', verifyToken, checkRole(['admin', 'super_admin']), suspendDriver);
router.delete('/driver/:uid', verifyToken, checkRole(['admin', 'super_admin']), deleteDriver);

// ── Data Listing (Admin + Super Admin) ────────────────────────────────
router.get('/all-users', verifyToken, checkRole(['admin', 'super_admin']), getAllUsers);
router.get('/all-drivers', verifyToken, checkRole(['admin', 'super_admin']), getAllDrivers);
router.get('/audit-logs', verifyToken, checkRole(['admin', 'super_admin']), getAuditLogs);
router.get('/notifications', verifyToken, checkRole(['admin', 'super_admin']), getNotifications);

export default router;
