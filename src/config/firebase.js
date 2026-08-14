import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { getMessaging, getToken, onMessage } from 'firebase/messaging';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyBLp0H5GzoriDPGSIuK-Ey0Ml_9Xn4NAEc",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "smart-ambulance-system-599d2.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "smart-ambulance-system-599d2",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "smart-ambulance-system-599d2.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "686610895218",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:686610895218:web:667b86b0d2074d0398cdeb"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);

// Initialize Services
export const auth = getAuth(app);

// Use standard getFirestore instead of initializeFirestore with persistentLocalCache.
// The persistentLocalCache with persistentMultipleTabManager can cause the Firestore
// instance to block/hang indefinitely when there's a stale IndexedDB, tab conflicts,
// or when the browser doesn't fully support the persistence APIs — preventing login
// and registration from ever completing.
export const db = getFirestore(app);

// Messaging may not be supported in all environments (e.g. some browsers without notification support)
let messaging;
try {
  messaging = getMessaging(app);
} catch (error) {
  console.log('Firebase Messaging is not supported in this environment.', error);
}

export { messaging, getToken, onMessage };
export default app;
