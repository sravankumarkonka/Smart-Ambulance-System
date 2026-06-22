import { Builder, By, until } from 'selenium-webdriver';
import chrome from 'selenium-webdriver/chrome.js';
import { spawn, execSync } from 'child_process';
import axios from 'axios';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { generateReport } from './excelReporter.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

let backendProcess = null;
let frontendProcess = null;
let driver = null;

// Helpers to check if port is open
async function isPortOpen(url) {
  try {
    await axios.get(url, { timeout: 1000 });
    return true;
  } catch (err) {
    return false;
  }
}

// Spawns servers if not already running
async function ensureServers() {
  console.log('[TestRunner] Checking if frontend and backend servers are running... ');
  
  console.log('[TestRunner] Terminating any existing server instances on ports 5000 and 5173...');
  try {
    if (process.platform === 'win32') {
      execSync('powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 5000 -ErrorAction SilentlyContinue).OwningProcess -Force -ErrorAction SilentlyContinue"', { stdio: 'ignore' });
      execSync('powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue).OwningProcess -Force -ErrorAction SilentlyContinue"', { stdio: 'ignore' });
    } else {
      execSync('lsof -t -i:5000 | xargs kill -9', { stdio: 'ignore' });
      execSync('lsof -t -i:5173 | xargs kill -9', { stdio: 'ignore' });
    }
    // Give it a brief moment to release ports
    await new Promise(resolve => setTimeout(resolve, 1000));
  } catch (err) {}

  console.log('[TestRunner] Starting backend server...');
  backendProcess = spawn('npm', ['run', 'dev'], {
    cwd: path.join(rootDir, 'backend'),
    shell: true,
    stdio: 'ignore',
    env: { ...process.env, SKIP_RATE_LIMIT: 'true' }
  });

  console.log('[TestRunner] Starting frontend server...');
  frontendProcess = spawn('npm', ['run', 'dev'], {
    cwd: rootDir,
    shell: true,
    stdio: 'ignore'
  });

  // Wait for servers to become active
  let attempts = 20;
  while (attempts > 0) {
    const bUp = await isPortOpen('http://localhost:5000/health');
    const fUp = await isPortOpen('http://localhost:5173');
    if (bUp && fUp) {
      console.log('[TestRunner] Both servers are healthy and running!');
      break;
    }
    console.log(`[TestRunner] Waiting for servers... (${attempts} attempts left)`);
    await new Promise(resolve => setTimeout(resolve, 1500));
    attempts--;
  }

  if (attempts === 0) {
    throw new Error('Servers failed to start in time.');
  }
}

// Bootstrap test accounts and ambulance
async function bootstrapTestUsers() {
  console.log('[TestRunner] Bootstrapping test users and ambulances...');
  
  const users = [
    { name: 'Patient Test Runner', email: 'patient_test_runner@example.com', role: 'user' },
    { name: 'Driver Test Runner', email: 'driver_test_runner@example.com', role: 'driver' },
    { name: 'Admin Test Runner', email: 'admin_test_runner@example.com', role: 'admin' }
  ];

  for (const u of users) {
    let uid = null;
    let idToken = null;
    try {
      // Register
      const res = await axios.post('http://localhost:5000/api/auth/register', {
        name: u.name,
        email: u.email,
        phone: '9876543210',
        password: 'password123'
      });
      uid = res.data.uid;
      idToken = res.data.idToken;
      console.log(`[TestRunner] Registered user ${u.email} with UID: ${uid}`);
    } catch (err) {
      // If already exists, login to get UID & idToken
      try {
        const res = await axios.post('http://localhost:5000/api/auth/login', {
          email: u.email,
          password: 'password123'
        });
        uid = res.data.uid;
        idToken = res.data.idToken;
        console.log(`[TestRunner] User ${u.email} already exists. Logged in to retrieve UID: ${uid}`);
      } catch (loginErr) {
        // Fallback manually computing the mock-uid
        const emailHex = Buffer.from(u.email.trim().toLowerCase()).toString('hex').slice(0, 19);
        uid = `mock-uid-${emailHex}`;
        console.log(`[TestRunner] Computed fallback UID for ${u.email}: ${uid}`);
      }
    }

    // Set user role via set_role_tool.js script
    if (uid) {
      try {
        execSync(`node backend/set_role_tool.js ${uid} ${u.role}`, { cwd: rootDir });
        console.log(`[TestRunner] Role updated to "${u.role}" for user ${u.email}`);
      } catch (roleErr) {
        console.error(`[TestRunner] Failed setting role for ${u.email}:`, roleErr.message);
      }
    }

    // If role is driver, update/register ambulance (using the authenticated idToken)
    if (u.role === 'driver' && uid) {
      try {
        // If idToken is missing, fetch it via login
        if (!idToken) {
          const loginRes = await axios.post('http://localhost:5000/api/auth/login', {
            email: u.email,
            password: 'password123'
          });
          idToken = loginRes.data.idToken;
        }

        await axios.post('http://localhost:5000/api/driver/ambulances', {
          driverId: uid,
          ambulanceData: {
            status: 'available',
            driverName: u.name,
            driverPhone: '9876543210',
            latitude: 12.9716,
            longitude: 77.5946
          }
        }, {
          headers: {
            Authorization: `Bearer ${idToken}`
          }
        });
        console.log(`[TestRunner] Ambulance profile initialized for driver: ${u.email}`);
      } catch (ambErr) {
        console.error('[TestRunner] Failed registering ambulance:', ambErr.response?.data || ambErr.message);
      }
    }
  }
}

// Clean up child processes on exit
function cleanup() {
  console.log('[TestRunner] Cleaning up processes...');
  if (driver) {
    driver.quit().catch(() => {});
  }
  if (backendProcess) {
    console.log('[TestRunner] Stopping backend process...');
    backendProcess.kill();
  }
  if (frontendProcess) {
    console.log('[TestRunner] Stopping frontend process...');
    frontendProcess.kill();
  }
}

process.on('SIGINT', () => {
  cleanup();
  process.exit(1);
});
process.on('exit', () => {
  cleanup();
});

// Setup Selenium Webdriver
async function setupDriver() {
  const options = new chrome.Options();
  options.addArguments('--headless=new'); // Run in headless mode for speed
  options.addArguments('--no-sandbox');
  options.addArguments('--disable-dev-shm-usage');
  options.addArguments('--disable-gpu');
  options.addArguments('--window-size=1280,1024');
  
  // Enable browser console logs
  options.setLoggingPrefs({ browser: 'ALL' });

  // Disable geolocation permission prompts (2 = Block)
  options.setUserPreferences({
    'profile.default_content_setting_values.geolocation': 2
  });

  driver = await new Builder()
    .forBrowser('chrome')
    .setChromeOptions(options)
    .build();
  
  await driver.manage().setTimeouts({ implicit: 3000 });
}

// Generate the 300 test case datasets
function generateTestData() {
  const loginCases = [];
  const registerCases = [];
  const emergencyCases = [];

  // --- 1. Login Cases (1 to 100) ---
  loginCases.push({
    id: 1,
    email: 'patient_test_runner@example.com',
    password: 'password123',
    description: 'Success login for patient role',
    expected: 'Redirect to /user/dashboard or /dashboard',
    expectSuccess: true
  });
  loginCases.push({
    id: 2,
    email: 'driver_test_runner@example.com',
    password: 'password123',
    description: 'Success login for driver role',
    expected: 'Redirect to /driver/dashboard',
    expectSuccess: true
  });
  loginCases.push({
    id: 3,
    email: 'admin_test_runner@example.com',
    password: 'password123',
    description: 'Success login for admin role',
    expected: 'Redirect to /admin/dashboard',
    expectSuccess: true
  });

  loginCases.push({
    id: 4,
    email: 'patient_test_runner@example.com',
    password: 'wrongpassword',
    description: 'Login failure with incorrect password',
    expected: 'Error badge shows "Invalid email or password."',
    expectSuccess: false
  });
  loginCases.push({
    id: 5,
    email: '',
    password: 'password123',
    description: 'Login failure with empty email',
    expected: 'HTML5 validation blocks submission',
    expectSuccess: false
  });
  loginCases.push({
    id: 6,
    email: 'patient_test_runner@example.com',
    password: '',
    description: 'Login failure with empty password',
    expected: 'HTML5 validation blocks submission',
    expectSuccess: false
  });

  // 44 Invalid email formats
  for (let i = 7; i <= 50; i++) {
    const invalidEmail = `invalidemail_${i}@` + (i % 2 === 0 ? '' : 'domain');
    loginCases.push({
      id: i,
      email: invalidEmail,
      password: 'password123',
      description: `Login failure - Invalid email pattern format case #${i}`,
      expected: 'Validation error banner or HTML5 blocks submission',
      expectSuccess: false
    });
  }

  // 25 SQL Injection payload cases
  const sqlPayloads = [
    "' OR '1'='1", "admin'--", "' UNION SELECT NULL--", "admin' #", "' OR 1=1--",
    "admin'/*", "' or ''='", "' OR 'x'='x", "' AND 1=0 UNION SELECT", "benchmark(1000000,md5(1))",
    "'; WAITFOR DELAY '0:0:5'--", "1'; DROP TABLE users--", "admin' AND 1=1--", "' or 'a'='a",
    "' or '1'='1'--", "' or '1'='1'/*", "' or 1=1 or ''='", "admin') or ('1'='1", "1' or '1'='1",
    "1' or 1=1--", "1' or 1=1/*", "1' or 1=1 #", "1' or '1'='1'--", "1' or '1'='1'/*"
  ];
  for (let i = 51; i <= 75; i++) {
    const payload = sqlPayloads[i - 51] || "' OR 1=1--";
    loginCases.push({
      id: i,
      email: `${payload}@example.com`,
      password: 'password123',
      description: `Security testing - SQL Injection payload in email field: ${payload}`,
      expected: 'Login rejected with credentials error',
      expectSuccess: false
    });
  }

  // 25 Extreme length input cases
  for (let i = 76; i <= 100; i++) {
    const longEmail = 'a'.repeat(i * 10) + '@example.com';
    loginCases.push({
      id: i,
      email: longEmail,
      password: 'password123',
      description: `Boundary testing - Extremely long email input of ${longEmail.length} chars`,
      expected: 'Error badge shows "Invalid email or password." or validation failure',
      expectSuccess: false
    });
  }

  // --- 2. Register Cases (101 to 200) ---
  const uniqueRegEmail = `reg_valid_${Date.now()}@example.com`;
  registerCases.push({
    id: 101,
    name: 'New Test Patient',
    phone: '9876543210',
    email: uniqueRegEmail,
    password: 'password123',
    description: 'Success registration of new standard user',
    expected: 'Success redirect to user dashboard',
    expectSuccess: true
  });
  registerCases.push({
    id: 102,
    name: 'Duplicate Patient',
    phone: '9876543210',
    email: 'patient_test_runner@example.com',
    password: 'password123',
    description: 'Registration failure with duplicate email',
    expected: 'Error badge shows "This email is already in use."',
    expectSuccess: false
  });
  registerCases.push({
    id: 103,
    name: 'Short PW User',
    phone: '9876543210',
    email: `short_pw_${Date.now()}@example.com`,
    password: '123',
    description: 'Registration failure with short password (<6 chars)',
    expected: 'Error badge shows "Password should be at least 6 characters."',
    expectSuccess: false
  });

  // 30 Phone formats that are strictly invalid under the regex: /^[0-9+\-\s()]{7,15}$/
  // (e.g. contains letters, invalid symbols, or length outside 7-15 chars)
  const badPhones = [
    'abc', 'phone123', '9876abc123', '987654321a', // letters
    '!@#$%^&*', '987654321!', '987654321_', '@9876543', '98765#43', // symbols
    '1', '12', '123', '1234', '12345', '123456', // too short (<7 chars)
    '1234567890123456', '12345678901234567', '123456789012345678', // too long (>15 chars)
    '12345678901234567890', '123456789012345678901', '1234567890123456789012', // very long
    'bad', 'no-phone', 'number_one', 'digits!', 'ph-number', '12345a', '9876543210x', '0' // misc
  ];
  for (let i = 104; i <= 133; i++) {
    const phone = badPhones[i - 104] || '123';
    registerCases.push({
      id: i,
      name: 'Bad Phone User',
      phone: phone,
      email: `bad_phone_${i}_${Date.now()}@example.com`,
      password: 'password123',
      description: `Registration failure - Invalid phone format: ${phone}`,
      expected: 'Error banner displayed or HTML5 blocks submission',
      expectSuccess: false
    });
  }

  // 33 Invalid email patterns in registration
  for (let i = 134; i <= 166; i++) {
    const invalidEmail = `reg_invalid_${i}_` + (i % 2 === 0 ? 'no_domain' : '@no_dot_com');
    registerCases.push({
      id: i,
      name: 'Bad Email User',
      phone: '9876543210',
      email: invalidEmail,
      password: 'password123',
      description: `Registration failure - Invalid email pattern format: ${invalidEmail}`,
      expected: 'Error banner displayed or HTML5 blocks submission',
      expectSuccess: false
    });
  }

  // 34 Empty inputs or extreme length strings in registration
  for (let i = 167; i <= 200; i++) {
    const isNameLong = i % 2 === 0;
    const nameInput = isNameLong ? 'A'.repeat(250) : 'Test User';
    const emailInput = !isNameLong ? 'B'.repeat(200) + '@example.com' : `long_in_${i}_${Date.now()}@example.com`;
    registerCases.push({
      id: i,
      name: nameInput,
      phone: '9876543210',
      email: emailInput,
      password: 'password123',
      description: `Registration boundary - Extreme lengths (Name: ${nameInput.length} chars, Email: ${emailInput.length} chars)`,
      expected: 'Rejected by validation or form limits length',
      expectSuccess: false
    });
  }

  // --- 3. Report Emergency Cases (201 to 300) ---
  emergencyCases.push({
    id: 201,
    patientName: 'John Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident reported at standard central location.',
    lat: '12.9716',
    lng: '77.5946',
    hospitalId: 'hos-1',
    descriptionText: 'Valid E2E submission with correct parameters',
    expected: 'Success notification or redirect to tracking page',
    expectSuccess: true
  });
  emergencyCases.push({
    id: 202,
    patientName: '',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with empty patient name.',
    lat: '12.9716',
    lng: '77.5946',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with empty patient name',
    expected: 'HTML5 validation blocks submission',
    expectSuccess: false
  });
  emergencyCases.push({
    id: 203,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: '',
    lat: '12.9716',
    lng: '77.5946',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with empty description text',
    expected: 'HTML5 validation blocks submission',
    expectSuccess: false
  });
  emergencyCases.push({
    id: 204,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with empty latitude.',
    lat: '',
    lng: '77.5946',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with empty latitude coordinate',
    expected: 'HTML5 validation blocks submission',
    expectSuccess: false
  });
  emergencyCases.push({
    id: 205,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with empty longitude.',
    lat: '12.9716',
    lng: '',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with empty longitude coordinate',
    expected: 'HTML5 validation blocks submission',
    expectSuccess: false
  });
  
  emergencyCases.push({
    id: 206,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with latitude out of bounds (> 90).',
    lat: '95.0',
    lng: '77.5946',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with latitude out of bounds (95.0)',
    expected: 'Error badge shows "Latitude must be between -90 and 90." or client blocks',
    expectSuccess: false
  });
  emergencyCases.push({
    id: 207,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with latitude out of bounds (< -90).',
    lat: '-95.0',
    lng: '77.5946',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with latitude out of bounds (-95.0)',
    expected: 'Error badge shows "Latitude must be between -90 and 90." or client blocks',
    expectSuccess: false
  });
  emergencyCases.push({
    id: 208,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with longitude out of bounds (> 180).',
    lat: '12.9716',
    lng: '185.0',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with longitude out of bounds (185.0)',
    expected: 'Error badge shows "Longitude must be between -180 and 180." or client blocks',
    expectSuccess: false
  });
  emergencyCases.push({
    id: 209,
    patientName: 'Jane Doe Test',
    type: 'accident',
    severity: 'medium',
    description: 'Accident report with longitude out of bounds (< -180).',
    lat: '12.9716',
    lng: '-185.0',
    hospitalId: 'hos-1',
    descriptionText: 'Reporting failure with longitude out of bounds (-185.0)',
    expected: 'Error badge shows "Longitude must be between -180 and 180." or client blocks',
    expectSuccess: false
  });

  // 40 Form combination cases
  const severities = ['low', 'medium', 'high', 'critical'];
  const types = ['accident', 'cardiac', 'respiratory', 'stroke', 'pregnancy', 'other'];
  for (let i = 210; i <= 249; i++) {
    const sev = severities[i % severities.length];
    const typ = types[i % types.length];
    emergencyCases.push({
      id: i,
      patientName: `Combination Patient ${i}`,
      type: typ,
      severity: sev,
      description: `Simulation of ${typ} with severity ${sev} at index ${i}`,
      lat: (12.9716 + (i - 230) * 0.001).toFixed(4),
      lng: (77.5946 + (i - 230) * 0.001).toFixed(4),
      hospitalId: 'hos-1',
      descriptionText: `Reporting success - combination of type: ${typ}, severity: ${sev}`,
      expected: 'Success notification or redirect to tracking page',
      expectSuccess: true
    });
  }

  // 40 Hospital selection cases
  const hospitals = ['hos-1', 'hos-2', 'hos-3', 'hos-4', 'hos-5'];
  for (let i = 250; i <= 289; i++) {
    const hos = hospitals[i % hospitals.length];
    emergencyCases.push({
      id: i,
      patientName: `Hospital Route Patient ${i}`,
      type: 'cardiac',
      severity: 'high',
      description: `Hospital routing test case index ${i} targeting hospital ${hos}`,
      lat: (12.9716 - (i - 270) * 0.0005).toFixed(4),
      lng: (77.5946 - (i - 270) * 0.0005).toFixed(4),
      hospitalId: hos,
      descriptionText: `Reporting success - targeting hospital: ${hos}`,
      expected: 'Success notification or redirect to tracking page',
      expectSuccess: true
    });
  }

  // 11 Polar and boundary coordinate cases
  for (let i = 290; i <= 300; i++) {
    const isPolar = i % 2 === 0;
    const latVal = isPolar ? '89.9' : '0.0001';
    const lngVal = isPolar ? '179.9' : '0.0001';
    emergencyCases.push({
      id: i,
      patientName: `Extreme Coord Patient ${i}`,
      type: 'other',
      severity: 'low',
      description: `Extreme coordinate validation test case index ${i} at coords ${latVal}, ${lngVal}`,
      lat: latVal,
      lng: lngVal,
      hospitalId: 'hos-1',
      descriptionText: `Reporting success - coordinate boundaries: lat ${latVal}, lng ${lngVal}`,
      expected: 'Success notification or redirect to tracking page',
      expectSuccess: true
    });
  }

  return { loginCases, registerCases, emergencyCases };
}

// Run E2E Login Suite
async function runLoginSuite(cases, results) {
  console.log('\n--- Running Login Test Suite (Cases 1 - 100) ---');
  
  for (const tc of cases) {
    const startTime = Date.now();
    let status = 'FAIL';
    let actual = '';

    try {
      // Re-navigate to reset page state and clear loaders
      await driver.get('http://localhost:5173/login');
      
      const emailInput = await driver.wait(
        until.elementLocated(By.css('[data-testid="login-email-input"]')),
        4000
      );
      await driver.wait(until.elementIsEnabled(emailInput), 3000);
      
      const passwordInput = await driver.findElement(By.css('[data-testid="login-password-input"]'));
      const submitBtn = await driver.findElement(By.css('[data-testid="login-submit-btn"]'));

      // Fill values
      await emailInput.clear();
      if (tc.email) await emailInput.sendKeys(tc.email);

      await passwordInput.clear();
      if (tc.password) await passwordInput.sendKeys(tc.password);

      // Check if HTML5 validation blocks it
      const isHtml5Blocked = await driver.executeScript(() => {
        const form = document.querySelector('form');
        return form ? !form.checkValidity() : false;
      });

      // Submit
      await submitBtn.click();

      let successRedirect = false;
      let errorText = '';

      if (isHtml5Blocked) {
        status = 'PASS';
        actual = 'Submission blocked by browser HTML5 input validation.';
      } else {
        // Dynamic Wait for redirection or error message (up to 12 seconds to prevent cold start failures)
        try {
          await driver.wait(async () => {
            const url = await driver.getCurrentUrl();
            if (url.includes('dashboard') || url.includes('live-map')) {
              successRedirect = true;
              return true;
            }
            const errorBadges = await driver.findElements(By.css('[data-testid="login-error-badge"]'));
            if (errorBadges.length > 0) {
              errorText = await errorBadges[0].getText();
              return true;
            }
            return false;
          }, 12000);
        } catch (waitErr) {
          // Timeout
        }

        const currentUrl = await driver.getCurrentUrl();

        if (tc.expectSuccess) {
          if (successRedirect) {
            status = 'PASS';
            actual = `Successfully redirected to dashboard: ${currentUrl}`;
            
            // Reset session
            await driver.executeScript(() => {
              localStorage.clear();
              sessionStorage.clear();
              window.dispatchEvent(new Event('mock-login-changed'));
            });
          } else {
            actual = errorText ? `Failed to redirect. Error shown: "${errorText}"` : `Failed to redirect. Stayed on: ${currentUrl}`;
          }
        } else {
          if (errorText) {
            status = 'PASS';
            actual = `Received validation error banner: "${errorText}"`;
          } else if (currentUrl.includes('login')) {
            status = 'PASS';
            actual = 'Login rejected. Kept on login page.';
          } else {
            actual = `Incorrectly bypassed login! Redirected to: ${currentUrl}`;
          }
        }
      }
    } catch (err) {
      actual = `Exception occurred: ${err.message}`;
    }

    const durationMs = Date.now() - startTime;
    results.push({
      id: tc.id,
      suite: 'Login Suite',
      description: tc.description,
      inputs: `Email: "${tc.email || '(empty)'}", Password: "${tc.password ? '******' : '(empty)'}"`,
      expected: tc.expected,
      actual: actual,
      status: status,
      durationMs: durationMs
    });

    // Diagnostically print console logs on E2E failures
    if (status === 'FAIL') {
      console.log(`\n  [FAIL DIAGNOSTIC] Case ${tc.id} failed. Console Output:`);
      try {
        const consoleLogs = await driver.manage().logs().get('browser');
        console.log(consoleLogs);
      } catch (logErr) {}
    }

    if (tc.id % 20 === 0 || tc.id <= 3) {
      console.log(`  [Case ${tc.id}] ${tc.description} -> ${status} (${durationMs}ms)`);
    }
  }
}

// Run E2E Register Suite
async function runRegisterSuite(cases, results) {
  console.log('\n--- Running Registration Test Suite (Cases 101 - 200) ---');

  for (const tc of cases) {
    const startTime = Date.now();
    let status = 'FAIL';
    let actual = '';

    try {
      // Re-navigate to reset page state and clear loaders
      await driver.get('http://localhost:5173/register');
      
      const nameInput = await driver.wait(
        until.elementLocated(By.css('[data-testid="register-name-input"]')),
        4000
      );
      await driver.wait(until.elementIsEnabled(nameInput), 3000);

      const phoneInput = await driver.findElement(By.css('[data-testid="register-phone-input"]'));
      const emailInput = await driver.findElement(By.css('[data-testid="register-email-input"]'));
      const passwordInput = await driver.findElement(By.css('[data-testid="register-password-input"]'));
      const submitBtn = await driver.findElement(By.css('[data-testid="register-submit-btn"]'));

      await nameInput.clear();
      if (tc.name) await nameInput.sendKeys(tc.name);

      await phoneInput.clear();
      if (tc.phone) await phoneInput.sendKeys(tc.phone);

      await emailInput.clear();
      if (tc.email) await emailInput.sendKeys(tc.email);

      await passwordInput.clear();
      if (tc.password) await passwordInput.sendKeys(tc.password);

      // Check if HTML5 validation blocks it
      const isHtml5Blocked = await driver.executeScript(() => {
        const form = document.querySelector('form');
        return form ? !form.checkValidity() : false;
      });

      await submitBtn.click();
      
      let successRedirect = false;
      let errorText = '';

      if (isHtml5Blocked) {
        status = 'PASS';
        actual = 'Submission blocked by browser HTML5 input validation.';
      } else {
        // Dynamic Wait (up to 12 seconds)
        try {
          await driver.wait(async () => {
            const url = await driver.getCurrentUrl();
            if (url.includes('dashboard') || url.includes('history')) {
              successRedirect = true;
              return true;
            }
            const errorBadges = await driver.findElements(By.css('.badge-danger'));
            if (errorBadges.length > 0) {
              errorText = await errorBadges[0].getText();
              return true;
            }
            return false;
          }, 12000);
        } catch (waitErr) {
          // Timeout
        }

        const currentUrl = await driver.getCurrentUrl();

        if (tc.expectSuccess) {
          if (successRedirect) {
            status = 'PASS';
            actual = `Successfully registered and redirected: ${currentUrl}`;
            
            await driver.executeScript(() => {
              localStorage.clear();
              sessionStorage.clear();
              window.dispatchEvent(new Event('mock-login-changed'));
            });
          } else {
            actual = errorText ? `Failed registration with error: "${errorText}"` : `Stayed on page: ${currentUrl}`;
          }
        } else {
          if (errorText) {
            status = 'PASS';
            actual = `Validation caught successfully: "${errorText}"`;
          } else if (currentUrl.includes('register')) {
            status = 'PASS';
            actual = 'Registration rejected. Remained on register page.';
          } else {
            actual = `Incorrectly bypassed registration and redirected to: ${currentUrl}`;
          }
        }
      }
    } catch (err) {
      actual = `Exception occurred: ${err.message}`;
    }

    const durationMs = Date.now() - startTime;
    results.push({
      id: tc.id,
      suite: 'Registration Suite',
      description: tc.description,
      inputs: `Name: "${tc.name}", Phone: "${tc.phone}", Email: "${tc.email}"`,
      expected: tc.expected,
      actual: actual,
      status: status,
      durationMs: durationMs
    });

    // Diagnostically print console logs on E2E failures
    if (status === 'FAIL') {
      console.log(`\n  [FAIL DIAGNOSTIC] Case ${tc.id} failed. Console Output:`);
      try {
        const consoleLogs = await driver.manage().logs().get('browser');
        console.log(consoleLogs);
      } catch (logErr) {}
    }

    if (tc.id % 20 === 0 || tc.id === 101 || tc.id === 102) {
      console.log(`  [Case ${tc.id}] ${tc.description} -> ${status} (${durationMs}ms)`);
    }
  }
}

// Run E2E Emergency Report Suite
async function runEmergencySuite(cases, results) {
  console.log('\n--- Running Emergency Reporting Test Suite (Cases 201 - 300) ---');
  
  // Clear session first to ensure clean login page
  await driver.get('http://localhost:5173/login');
  await driver.executeScript(() => {
    localStorage.clear();
    sessionStorage.clear();
    window.dispatchEvent(new Event('mock-login-changed'));
  });
  await driver.get('http://localhost:5173/login');
  
  const loginEmail = await driver.wait(
    until.elementLocated(By.css('[data-testid="login-email-input"]')),
    4000
  );
  const loginPass = await driver.findElement(By.css('[data-testid="login-password-input"]'));
  const loginSubmit = await driver.findElement(By.css('[data-testid="login-submit-btn"]'));
  
  await loginEmail.clear();
  await loginEmail.sendKeys('patient_test_runner@example.com');
  await loginPass.clear();
  await loginPass.sendKeys('password123');
  await loginSubmit.click();
  
  // Wait for redirect to dashboard
  await driver.wait(until.urlContains('dashboard'), 12000);
  console.log('[TestRunner] Successfully authenticated patient.');

  for (const tc of cases) {
    const startTime = Date.now();
    let status = 'FAIL';
    let actual = '';

    try {
      // Re-navigate to form page to reset loader states
      await driver.get('http://localhost:5173/report-emergency');
      
      const patientInput = await driver.wait(
        until.elementLocated(By.css('[data-testid="patient-name-input"]')),
        4000
      );
      await driver.wait(until.elementIsEnabled(patientInput), 3000);

      const typeSelect = await driver.findElement(By.css('[data-testid="emergency-type-select"]'));
      const severitySelect = await driver.findElement(By.css('[data-testid="severity-level-select"]'));
      const descTextarea = await driver.findElement(By.css('[data-testid="report-description"]'));
      const latInput = await driver.findElement(By.css('[data-testid="latitude-input"]'));
      const lngInput = await driver.findElement(By.css('[data-testid="longitude-input"]'));
      const hospSelect = await driver.findElement(By.css('[data-testid="hospital-select"]'));
      const submitBtn = await driver.findElement(By.css('[data-testid="report-submit"]'));

      // Clear & Fill
      await patientInput.clear();
      if (tc.patientName) await patientInput.sendKeys(tc.patientName);

      await typeSelect.sendKeys(tc.type);
      await severitySelect.sendKeys(tc.severity);

      await descTextarea.clear();
      if (tc.description) await descTextarea.sendKeys(tc.description);

      await latInput.clear();
      if (tc.lat) await latInput.sendKeys(tc.lat);

      await lngInput.clear();
      if (tc.lng) await lngInput.sendKeys(tc.lng);

      await hospSelect.sendKeys(tc.hospitalId);

      // Check if HTML5 validation blocks it
      const isHtml5Blocked = await driver.executeScript(() => {
        const form = document.querySelector('form');
        return form ? !form.checkValidity() : false;
      });

      // Submit E2E report
      await submitBtn.click();
      
      let successRedirect = false;
      let successText = '';
      let errorText = '';

      if (isHtml5Blocked) {
        status = 'PASS';
        actual = 'Submission blocked by browser HTML5 input validation.';
      } else {
        // Dynamic Wait (up to 12 seconds to ensure slow database writes complete)
        try {
          await driver.wait(async () => {
            const url = await driver.getCurrentUrl();
            if (url.includes('track') || url.includes('history') || url.includes('dashboard')) {
              successRedirect = true;
              return true;
            }
            const successBadges = await driver.findElements(By.css('[data-testid="report-success-badge"]'));
            if (successBadges.length > 0) {
              successText = await successBadges[0].getText();
              return true;
            }
            const errorBadges = await driver.findElements(By.css('[data-testid="report-error-badge"]'));
            if (errorBadges.length > 0) {
              errorText = await errorBadges[0].getText();
              return true;
            }
            return false;
          }, 12000);
        } catch (waitErr) {
          // Timeout
        }

        const currentUrl = await driver.getCurrentUrl();

        if (tc.expectSuccess) {
          if (successRedirect) {
            status = 'PASS';
            actual = `Successfully reported emergency. Directed to: ${currentUrl}`;
          } else if (successText) {
            status = 'PASS';
            actual = `Successfully reported. Banner: "${successText}"`;
          } else {
            actual = errorText ? `Unexpected submission failure: "${errorText}"` : `Remained on page: ${currentUrl}`;
          }
        } else {
          if (errorText) {
            status = 'PASS';
            actual = `Error captured successfully: "${errorText}"`;
          } else if (currentUrl.includes('report-emergency')) {
            status = 'PASS';
            actual = 'Submission rejected. Remained on emergency form page.';
          } else {
            actual = `Incorrectly bypassed boundaries and redirected to: ${currentUrl}`;
          }
        }
      }
    } catch (err) {
      actual = `Exception occurred: ${err.message}`;
    }

    const durationMs = Date.now() - startTime;
    results.push({
      id: tc.id,
      suite: 'Emergency Suite',
      description: tc.descriptionText,
      inputs: `Patient: "${tc.patientName}", Type: "${tc.type}", Severity: "${tc.severity}", Coords: "${tc.lat}, ${tc.lng}", Hospital: "${tc.hospitalId}"`,
      expected: tc.expected,
      actual: actual,
      status: status,
      durationMs: durationMs
    });

    // Diagnostically print console logs on E2E failures
    if (status === 'FAIL') {
      console.log(`\n  [FAIL DIAGNOSTIC] Case ${tc.id} failed. Console Output:`);
      try {
        const consoleLogs = await driver.manage().logs().get('browser');
        console.log(consoleLogs);
      } catch (logErr) {}
    }

    if (tc.id % 20 === 0 || tc.id === 201 || tc.id === 202) {
      console.log(`  [Case ${tc.id}] ${tc.descriptionText} -> ${status} (${durationMs}ms)`);
    }
  }
}

// Main execution flow
async function main() {
  console.log('====================================================');
  console.log('  Smart Ambulance System - 300 E2E Selenium Test Suite');
  console.log('====================================================\n');
  
  const results = [];
  const testData = generateTestData();

  try {
    // 1. Ensure servers are running
    await ensureServers();

    // 2. Bootstrap test users in Firestore and set roles
    await bootstrapTestUsers();

    // 3. Setup Selenium WebDriver
    await setupDriver();

    // 4. Execute suites
    await runLoginSuite(testData.loginCases, results);
    await runRegisterSuite(testData.registerCases, results);
    await runEmergencySuite(testData.emergencyCases, results);

    // 5. Generate report (with EBUSY lock fallback)
    const reportPath = path.join(rootDir, 'selenium-tests', 'E2E_Test_Report.xlsx');
    try {
      await generateReport(results, reportPath);
    } catch (excelErr) {
      if (excelErr.code === 'EBUSY') {
        const fallbackPath = path.join(rootDir, 'selenium-tests', `E2E_Test_Report_${Date.now()}.xlsx`);
        console.warn(`[TestRunner] Primary Excel file is locked/busy. Saving to fallback path: ${fallbackPath}`);
        await generateReport(results, fallbackPath);
      } else {
        throw excelErr;
      }
    }

    console.log('\n====================================================');
    console.log('  TESTING COMPLETE!');
    console.log(`  Total Executed: ${results.length}`);
    console.log(`  Passed: ${results.filter(r => r.status === 'PASS').length}`);
    console.log(`  Failed: ${results.filter(r => r.status === 'FAIL').length}`);
    console.log('====================================================\n');

  } catch (error) {
    console.error('[TestRunner] Critical Failure in test execution:', error);
  } finally {
    cleanup();
  }
}

main();
