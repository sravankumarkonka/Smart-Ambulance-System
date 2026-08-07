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

// Protected Route Component with Status & Approval Checks
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { currentUser, userRole, userStatus, userApproved, loading } = useAuth();
  
  if (loading) return <div className="container mt-4 text-center">Loading session...</div>;
  
  if (!currentUser) {
    return <Navigate to="/login" replace />;
  }

  if (userApproved !== true || userStatus !== 'active') {
    return <Navigate to="/login" replace />;
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
