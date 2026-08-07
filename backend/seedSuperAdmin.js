import { auth, db } from './config/firebaseAdmin.js';
import readline from 'readline';

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

const prompt = (query) => new Promise((resolve) => rl.question(query, resolve));

async function seedSuperAdmin() {
  console.log('--------------------------------------------------');
  console.log('   Smart Ambulance System — Super Admin Seeder');
  console.log('--------------------------------------------------');

  try {
    const email = await prompt('Enter Super Admin Email: ');
    const password = await prompt('Enter Super Admin Password: ');
    const name = await prompt('Enter Super Admin Full Name (default: Super Admin): ') || 'Super Admin';

    if (!email || !password) {
      console.error('ERROR: Email and password are required.');
      process.exit(1);
    }

    if (password.length < 6) {
      console.error('ERROR: Password must be at least 6 characters long.');
      process.exit(1);
    }

    console.log(`\nCreating Super Admin account for: ${email.trim()} ...`);

    let uid;
    try {
      const userRecord = await auth.createUser({
        email: email.trim(),
        password: password,
        displayName: name.trim()
      });
      uid = userRecord.uid;
      console.log(`✅ Firebase Auth account created. UID: ${uid}`);
    } catch (authErr) {
      if (authErr.code === 'auth/email-already-exists') {
        console.log('⚠️ User already exists in Auth. Fetching existing UID...');
        const userRecord = await auth.getUserByEmail(email.trim());
        uid = userRecord.uid;
        console.log(`✅ Found existing user UID: ${uid}`);
      } else {
        throw authErr;
      }
    }

    const now = new Date().toISOString();
    const superAdminDoc = {
      uid,
      name: name.trim(),
      email: email.trim(),
      phone: '+10000000000',
      photoURL: '',
      role: 'super_admin',
      status: 'active',
      approved: true,
      createdAt: now,
      updatedAt: now
    };

    await db.collection('users').doc(uid).set(superAdminDoc, { merge: true });
    console.log(`✅ Firestore user profile set for Super Admin.`);

    await db.collection('audit_logs').add({
      action: 'super_admin_seed',
      performedBy: 'system_seed_script',
      targetUid: uid,
      details: { email: email.trim() },
      createdAt: now
    });
    console.log(`✅ Audit log created.`);

    console.log('--------------------------------------------------');
    console.log('🎉 Super Admin creation complete!');
    console.log(`   Email:    ${email.trim()}`);
    console.log(`   Role:     super_admin`);
    console.log(`   Status:   active (approved: true)`);
    console.log('--------------------------------------------------');

  } catch (error) {
    console.error('❌ Seeding failed:', error.message);
  } finally {
    rl.close();
    process.exit(0);
  }
}

seedSuperAdmin();
