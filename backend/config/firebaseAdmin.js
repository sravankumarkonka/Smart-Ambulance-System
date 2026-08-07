import admin from 'firebase-admin';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

// ── Initialize Admin SDK with Service Account ─────────────────────────

const serviceAccountKeyPath = process.env.FIREBASE_SERVICE_ACCOUNT_KEY;
if (!serviceAccountKeyPath) {
  console.error('[Firebase Admin] CRITICAL: FIREBASE_SERVICE_ACCOUNT_KEY is not set.');
  process.exit(1);
}

let serviceAccount;
const keyValue = serviceAccountKeyPath.trim();
if (keyValue.startsWith('{')) {
  serviceAccount = JSON.parse(keyValue);
} else {
  const resolvedPath = path.resolve(__dirname, '..', keyValue);
  if (!fs.existsSync(resolvedPath)) {
    console.error(`[Firebase Admin] CRITICAL: Service account file not found at ${resolvedPath}`);
    process.exit(1);
  }
  serviceAccount = JSON.parse(fs.readFileSync(resolvedPath, 'utf8'));
}

const adminApp = admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

console.log('[Firebase Admin] Initialized successfully with Service Account.');

// ── Export Admin Services ─────────────────────────────────────────────

const db = admin.firestore();
const auth = admin.auth();

export { admin, db, auth };
export default adminApp;
