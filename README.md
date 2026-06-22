# Smart Ambulance System

The **Smart Ambulance System** is a real-time web application designed to optimize emergency medical dispatch and routing. It allows users to report medical emergencies, automates ambulance assignment using proximity and status logic, and calculates optimal routes to recommended hospitals using a custom Dijkstra routing algorithm.

---

## Features

- **Real-Time Emergency Reporting**: Instant reporting of medical emergencies with description, severity, and GPS coordinates.
- **Auto-Assign Algorithms**: Dynamic matching of drivers and ambulances using location and availability metrics.
- **Intelligent Hospital Recommendation**: Suggests target hospitals based on current capacity and distance.
- **Custom Dijkstra Routing**: Calculates the fastest route from the ambulance's current coordinates to the patient and then to the hospital.
- **Role-Based Access Control (RBAC)**: Secure access levels for `user` (patients), `driver` (paramedics), and `admin` (dispatch managers).
- **Comprehensive Quality Assurance**: Includes end-to-end (E2E) UI testing using Selenium Webdriver and automated DAST security profiling.
- **Performance Load Testing**: Custom-engineered baseline load tester validating high-concurrency capability (up to 10k requests/second).

---

## Tech Stack

### Frontend
- **React** (v19) with **Vite**
- **React Leaflet & Leaflet.js** (Interactive maps & routing visualization)
- **React Router DOM** (v7)
- **Axios** (API requests)
- **Vanilla CSS** (Custom responsive design system)

### Backend
- **Node.js** with **Express**
- **Firebase Admin SDK** & **Firestore** (User profiles, state synchronization)
- **Express Rate Limit** (Anti-abuse and brute-force protection)
- **Express Validator** (Strict request parameter validation)
- **Multer** (Local media uploads)

### Testing Frameworks
- **Selenium WebDriver** (Automated browser testing & screenshots)
- **SheetJS (xlsx) & ExcelJS** (Colour-coded Excel results dashboard generation)
- **Custom Node.js Load Tester** (Baseline load capability assessment)

---

## Installation & Setup

### Prerequisites
- Node.js (v18 or higher)
- npm or yarn

### 1. Clone the Repository
```bash
git clone https://github.com/sravankumarkonka/Smart-Ambulance-System.git
cd Smart-Ambulance-System
```

### 2. Configure Environment Variables
Copy `.env.example` to `.env` in the root directory and configure the variables:
```bash
cp .env.example .env
```
Fill in the Firebase client API keys and configurations. For backend testing, set the path or value of your Firebase Service Account JSON credentials.

### 3. Install Dependencies
Install packages for the frontend, backend, and testing suites:
```bash
# Frontend
npm install

# Backend
cd backend && npm install

# Automated Tests & Security Suite
cd ../automated_test && npm install

# E2E Selenium Tests
cd ../selenium-tests && npm install
```

### 4. Run the Application
Start both the backend server and frontend development server:
```bash
# Run Backend (from /backend folder)
npm run dev

# Run Frontend (from root folder)
npm run dev
```

---

## Environment Variables Required

Refer to the [.env.example](file:///.env.example) template for description of keys:
- `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_PROJECT_ID`, etc. (Firebase client credentials)
- `FIREBASE_SERVICE_ACCOUNT_KEY` (Firebase Admin SDK credentials)
- `PORT` (Defaults to 5000)
- `ALLOWED_ORIGINS` (CORS controls)

---

## API Endpoints Reference

### 🔐 Authentication & Profile (`/api/auth`)
- `POST /api/auth/register` - Register patient accounts
- `POST /api/auth/login` - Authenticate users and retrieve ID Token
- `GET /api/auth/profile/:uid` - Retrieve user profile details
- `POST /api/auth/profile/:uid` - Update user profile details

### 🚨 Emergency Dispatch (`/api/emergencies`)
- `POST /api/emergencies/` - Report a new emergency (requires `user` role)
- `GET /api/emergencies/:id` - Fetch details of a single emergency
- `GET /api/emergencies/history/:userId` - Fetch emergency history list
- `POST /api/emergencies/:id/image` - Upload images associated with the event
- `POST /api/emergencies/:id/cancel` - Cancel a reported emergency

### 🚑 Driver Operations (`/api/driver`)
- `POST /api/driver/emergencies/:id/assign` - Manual assignment to driver
- `POST /api/driver/emergencies/:id/auto-assign` - Auto-assign nearby driver
- `PATCH /api/driver/emergencies/:id/status` - Update travel/patient status
- `POST /api/driver/emergencies/:id/release` - Release driver on completion
- `POST /api/driver/ambulances` - Register or update ambulance details
- `GET /api/driver/ambulances/:driverId` - Get driver's ambulance details
- `POST /api/driver/ambulances/:driverId/location` - Update live GPS coordinates

### 🏥 Hospital Lookups (`/api/hospitals`)
- `GET /api/hospitals/` - Get all registered hospitals
- `GET /api/hospitals/recommend` - Suggest nearest hospital with available bed capacity

### 🗺️ Route Logic (`/api/route`)
- `POST /api/route/` - Calculate shortest route between points using Dijkstra

### 🩺 Health Diagnostics
- `GET /health` - Public API check

---

## Screenshots

*(Place screenshots demonstrating the main dashboards, live Leaflet map tracker, and Dijkstra routing here)*

- **Dispatcher Portal:** `[Placeholder: Dispatcher Dashboard Screenshot]`
- **Driver Navigation view:** `[Placeholder: Live Route Navigation Map]`
- **Report Emergency Page:** `[Placeholder: Emergency Report Panel]`
