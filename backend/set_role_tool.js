import { db } from './config/firebaseAdmin.js';
import process from 'process';

const uid = process.argv[2];
const role = process.argv[3];

if (!uid || !role) {
  console.error('Usage: node set_role_tool.js <uid> <role>');
  process.exit(1);
}

db.collection('users').doc(uid).set({ role }, { merge: true })
  .then(() => {
    console.log(`Role ${role} successfully set for ${uid}`);
    process.exit(0);
  })
  .catch(err => {
    console.error('Error setting role:', err);
    process.exit(1);
  });
