import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
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

// Admin Pages
import AdminDashboard from './pages/admin/AdminDashboard';
import LiveMap from './pages/admin/LiveMap';

// Protected Route Component
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { currentUser, userRole, loading } = useAuth();
  
  if (loading) return <div>Loading...</div>;
  
  if (!currentUser) {
    return <Navigate to="/login" replace />;
  }
  
  if (allowedRoles && !allowedRoles.includes(userRole)) {
    // Redirect based on role if unauthorized
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

                {/* User Primary Routes */}
                <Route path="/dashboard" element={<ProtectedRoute allowedRoles={['user']}><UserDashboard /></ProtectedRoute>} />
                <Route path="/report-emergency" element={<ProtectedRoute allowedRoles={['user']}><ReportAccident /></ProtectedRoute>} />
                <Route path="/emergency-history" element={<ProtectedRoute allowedRoles={['user']}><EmergencyHistory /></ProtectedRoute>} />
                <Route path="/live-tracking/:id" element={<ProtectedRoute allowedRoles={['user']}><TrackAmbulance /></ProtectedRoute>} />
                <Route path="/live-tracking" element={<ProtectedRoute allowedRoles={['user']}><TrackAmbulance /></ProtectedRoute>} />

                {/* User Legacy Routes */}
                <Route path="/user/dashboard" element={<ProtectedRoute allowedRoles={['user']}><UserDashboard /></ProtectedRoute>} />
                <Route path="/user/report" element={<ProtectedRoute allowedRoles={['user']}><ReportAccident /></ProtectedRoute>} />
                <Route path="/user/track/:id" element={<ProtectedRoute allowedRoles={['user']}><TrackAmbulance /></ProtectedRoute>} />
                <Route path="/user/history" element={<ProtectedRoute allowedRoles={['user']}><EmergencyHistory /></ProtectedRoute>} />

                {/* Driver Routes */}
                <Route path="/driver/dashboard" element={<ProtectedRoute allowedRoles={['driver']}><DriverDashboard /></ProtectedRoute>} />
                <Route path="/driver/active/:id" element={<ProtectedRoute allowedRoles={['driver']}><ActiveEmergency /></ProtectedRoute>} />

                {/* Admin Routes */}
                <Route path="/admin/dashboard" element={<ProtectedRoute allowedRoles={['admin']}><AdminDashboard /></ProtectedRoute>} />
                <Route path="/admin/live-map" element={<ProtectedRoute allowedRoles={['admin']}><LiveMap /></ProtectedRoute>} />
              </Routes>
            </ErrorBoundary>
          </main>
        </div>
      </Router>
    </AuthProvider>
  );
}

export default App;
