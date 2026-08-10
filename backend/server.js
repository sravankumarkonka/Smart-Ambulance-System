import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import helmet from 'helmet';

// Load environmental variables from root .env file
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

// Import router modules
import authRoutes from './routes/authRoutes.js';
import emergencyRoutes from './routes/emergencyRoutes.js';
import driverRoutes from './routes/driverRoutes.js';
import approvalRoutes from './routes/approvalRoutes.js';
import adminRoutes from './routes/adminRoutes.js';
import hospitalRoutes from './routes/hospitalRoutes.js';
import routingRoutes from './routes/routingRoutes.js';
import { globalLimiter } from './middleware/rateLimitMiddleware.js';

import { initializeCollections } from './services/collectionInitializer.js';

// Startup Environment Check
if (process.env.NODE_ENV === 'production' && !process.env.FIREBASE_SERVICE_ACCOUNT_KEY) {
  console.error('CRITICAL ERROR: FIREBASE_SERVICE_ACCOUNT_KEY is missing in production. Refusing startup.');
  process.exit(1);
}

// Auto-initialize required Firestore collections
initializeCollections().catch(err => console.warn('[Server Boot] Collection init warning:', err.message));

const app = express();
app.set('trust proxy', 1);
const PORT = process.env.PORT || 5000;

// Enable Helmet for security headers with cross-origin support
app.use(helmet({
  crossOriginResourcePolicy: { policy: "cross-origin" }
}));

// Enable CORS and parsing requests
const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : ['http://localhost:5173', 'http://127.0.0.1:5173', 'http://localhost:5000'];

const corsOptions = {
  origin: function (origin, callback) {
    if (!origin) return callback(null, true);
    if (allowedOrigins.indexOf(origin) !== -1 || process.env.NODE_ENV !== 'production') {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true
};

app.use(cors(corsOptions));
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true, limit: '1mb' }));

// Global rate limiter — catch-all safety net (200 req/min per IP)
app.use(globalLimiter);

// Register routes
app.use('/api/auth', authRoutes);
app.use('/api/approval', approvalRoutes);
app.use('/api/emergencies', emergencyRoutes);
app.use('/api/driver', driverRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/route', routingRoutes);
app.use('/api/hospitals', hospitalRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', timestamp: new Date().toISOString() });
});

// Global Error Handler — never expose stack traces to clients
app.use((err, req, res, _next) => {
  console.error('[Global Error]', err.stack);
  const isProd = process.env.NODE_ENV === 'production';
  res.status(err.status || 500).json({
    error: isProd ? 'Internal server error.' : 'Internal server error: ' + err.message,
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Server] Smart Ambulance API server running on http://0.0.0.0:${PORT}`);
});

export default app;
