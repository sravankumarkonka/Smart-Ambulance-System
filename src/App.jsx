import React from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar from './components/layout/Navbar';
import ErrorBoundary from './components/common/ErrorBoundary';

// Pages
import Home from './pages/Home';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';

// User Pages
import UserDashboard from './pages/user/UserDashboard';
import ReportAccident from './pages/user/ReportAccident';
import TrackAmbulance from './pages/user/TrackAmbulance';
import EmergencyHistory from './pages/user/EmergencyHistory';

// Driver Pages
import DriverDashboard from './pages/driver/DriverDashboard';
import ActiveEmergency from './pages/driver/ActiveEmergency';
import DriverHistory from './pages/driver/DriverHistory';

// Admin Pages
import AdminDashboard from './pages/admin/AdminDashboard';
import LiveMap from './pages/admin/LiveMap';
import AdminHistory from './pages/admin/AdminHistory';

// Super Admin Pages
import SuperAdminDashboard from './pages/superadmin/SuperAdminDashboard';

// Pending / Suspended Account View Component
const PendingAccountNotice = ({ userRole, userStatus, logout }) => {
  const isDriver = userRole === 'driver';
  const isAdmin = userRole === 'admin';

  let title = 'Account Pending Approval';
  let message = 'Your account registration was received and is awaiting administrator verification. Once approved, full access will be granted.';

  if (isDriver) {
    message = 'Your Ambulance Driver registration has been submitted and is awaiting Admin review and vehicle verification.';
  } else if (isAdmin) {
    message = 'Your Admin registration has been submitted and is awaiting Super Admin verification.';
  }

  if (userStatus === 'rejected') {
    title = 'Account Registration Declined';
    message = 'Your account registration was declined by an administrator. Please contact support if you believe this was an error.';
  } else if (userStatus === 'suspended') {
    title = 'Account Suspended';
    message = 'Your account access has been temporarily suspended by an administrator.';
  }

  return (
    <div className="container mt-5" style={{ maxWidth: '560px' }}>
      <div className="card text-center" style={{ padding: '36px 24px', borderRadius: '16px', boxShadow: 'var(--shadow-md)' }}>
        <div style={{ fontSize: '48px', marginBottom: '16px' }}>
          {userStatus === 'rejected' || userStatus === 'suspended' ? '⚠️' : '⏳'}
        </div>
        <h2 style={{ marginBottom: '12px', fontWeight: 700 }}>{title}</h2>
        <p style={{ color: 'var(--text-muted)', lineHeight: '1.6', marginBottom: '24px', fontSize: '15px' }}>
          {message}
        </p>
        <button
          onClick={logout}
          className="btn btn-outline"
          style={{ padding: '12px 24px', borderRadius: '8px', fontWeight: 600 }}
        >
          Sign Out & Return to Login
        </button>
      </div>
    </div>
  );
};

// Protected Route Component with Role & Approval Checks
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { currentUser, userRole, userStatus, userApproved, loading, logout } = useAuth();
  
  if (loading) return <div className="container mt-5 text-center">Loading dispatch session...</div>;
  
  if (!currentUser) {
    return <Navigate to="/login" replace />;
  }

  // Patients (userRole === 'user') are always approved by default unless explicitly suspended/rejected
  const isPatientUser = userRole === 'user';
  const isActive = isPatientUser
    ? (userStatus !== 'suspended' && userStatus !== 'rejected')
    : (userApproved === true && userStatus === 'active');

  if (!isActive) {
    return <PendingAccountNotice userRole={userRole} userStatus={userStatus} logout={logout} />;
  }

  if (allowedRoles && !allowedRoles.includes(userRole)) {
    if (userRole === 'super_admin') return <Navigate to="/super-admin/dashboard" replace />;
    if (userRole === 'admin') return <Navigate to="/admin/dashboard" replace />;
    if (userRole === 'driver') return <Navigate to="/driver/dashboard" replace />;
    return <Navigate to="/user/dashboard" replace />;
  }
  
  return children;
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <div className="page-wrapper">
          <Navbar />
          <main className="main-content">
            <ErrorBoundary>
              <Routes>
                {/* Public Routes */}
                <Route path="/" element={<Home />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />

                {/* User Routes */}
                <Route path="/user/dashboard" element={<ProtectedRoute allowedRoles={['user']}><UserDashboard /></ProtectedRoute>} />
                <Route path="/user/report" element={<ProtectedRoute allowedRoles={['user']}><ReportAccident /></ProtectedRoute>} />
                <Route path="/user/track/:id" element={<ProtectedRoute allowedRoles={['user']}><TrackAmbulance /></ProtectedRoute>} />
                <Route path="/user/history" element={<ProtectedRoute allowedRoles={['user']}><EmergencyHistory /></ProtectedRoute>} />
                <Route path="/dashboard" element={<ProtectedRoute allowedRoles={['user']}><UserDashboard /></ProtectedRoute>} />
                <Route path="/report-emergency" element={<ProtectedRoute allowedRoles={['user']}><ReportAccident /></ProtectedRoute>} />
                <Route path="/emergency-history" element={<ProtectedRoute allowedRoles={['user']}><EmergencyHistory /></ProtectedRoute>} />
                <Route path="/live-tracking/:id" element={<ProtectedRoute allowedRoles={['user']}><TrackAmbulance /></ProtectedRoute>} />
                <Route path="/live-tracking" element={<ProtectedRoute allowedRoles={['user']}><TrackAmbulance /></ProtectedRoute>} />

                {/* Driver Routes */}
                <Route path="/driver/dashboard" element={<ProtectedRoute allowedRoles={['driver']}><DriverDashboard /></ProtectedRoute>} />
                <Route path="/driver/active/:id" element={<ProtectedRoute allowedRoles={['driver']}><ActiveEmergency /></ProtectedRoute>} />
                <Route path="/driver/history" element={<ProtectedRoute allowedRoles={['driver']}><DriverHistory /></ProtectedRoute>} />

                {/* Admin Routes (accessible by admin & super_admin) */}
                <Route path="/admin/dashboard" element={<ProtectedRoute allowedRoles={['admin', 'super_admin']}><AdminDashboard /></ProtectedRoute>} />
                <Route path="/admin/live-map" element={<ProtectedRoute allowedRoles={['admin', 'super_admin']}><LiveMap /></ProtectedRoute>} />
                <Route path="/admin/history" element={<ProtectedRoute allowedRoles={['admin', 'super_admin']}><AdminHistory /></ProtectedRoute>} />

                {/* Super Admin Routes */}
                <Route path="/super-admin/dashboard" element={<ProtectedRoute allowedRoles={['super_admin']}><SuperAdminDashboard /></ProtectedRoute>} />
              </Routes>
            </ErrorBoundary>
          </main>
        </div>
      </Router>
    </AuthProvider>
  );
}

export default App;
