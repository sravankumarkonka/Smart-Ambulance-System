import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth, db } from '../../config/firebase';
import { signInWithEmailAndPassword } from 'firebase/auth';
import { doc, getDoc, addDoc, collection } from 'firebase/firestore';
import { useAuth } from '../../context/AuthContext';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const { currentUser, userRole, userStatus, userApproved } = useAuth();

  // If user is ALREADY logged in and active, auto-redirect to their dashboard
  useEffect(() => {
    if (currentUser && userApproved && userStatus === 'active') {
      if (userRole === 'super_admin') {
        navigate('/super-admin/dashboard', { replace: true });
      } else if (userRole === 'admin') {
        navigate('/admin/dashboard', { replace: true });
      } else if (userRole === 'driver') {
        navigate('/driver/dashboard', { replace: true });
      } else {
        navigate('/user/dashboard', { replace: true });
      }
    }
  }, [currentUser, userRole, userStatus, userApproved, navigate]);

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      // 1. Authenticate via Firebase Auth
      const userCredential = await signInWithEmailAndPassword(auth, email.trim(), password);
      const uid = userCredential.user.uid;

      // 2. Fetch user profile from Firestore
      const userDocRef = doc(db, 'users', uid);
      const userSnap = await getDoc(userDocRef);

      if (!userSnap.exists()) {
        // Fallback for user docs not yet created in Firestore — allow Patient login by default
        console.warn('[Login] User profile not found in Firestore — defaulting to Patient user.');
      } else {
        const profile = userSnap.data();

        // 3. Gate check: Status and approval
        const isPatientUser = !profile.role || profile.role === 'user';
        const isApproved = isPatientUser ? (profile.approved !== false) : (profile.approved === true);
        const isActive = isPatientUser ? (profile.status !== 'suspended' && profile.status !== 'rejected') : (profile.status === 'active');

        if (!isApproved || !isActive) {
          let statusMsg = 'Your account is not active.';
          if (profile.status === 'pending') {
            statusMsg = profile.role === 'admin'
              ? 'Your admin account is pending Super Admin approval.'
              : 'Your driver account is pending Admin approval.';
          } else if (profile.status === 'rejected') {
            statusMsg = 'Your account request was rejected by an administrator.';
          } else if (profile.status === 'suspended') {
            statusMsg = 'Your account has been suspended.';
          }

          setError(statusMsg);
          await auth.signOut();
          setLoading(false);
          return;
        }

        // 4. Create Audit Log entry (non-blocking)
        try {
          await addDoc(collection(db, 'audit_logs'), {
            action: 'login',
            performedBy: uid,
            targetUid: uid,
            details: { email: profile.email || email.trim(), role: profile.role || 'user' },
            createdAt: new Date().toISOString()
          });
        } catch (logErr) {
          console.warn('[Login] Audit log write failed:', logErr.message);
        }

        // 5. Navigate based on role
        const role = profile.role || 'user';
        if (role === 'super_admin') {
          navigate('/super-admin/dashboard', { replace: true });
        } else if (role === 'admin') {
          navigate('/admin/dashboard', { replace: true });
        } else if (role === 'driver') {
          navigate('/driver/dashboard', { replace: true });
        } else {
          navigate('/user/dashboard', { replace: true });
        }
        return;
      }

      // Default navigation if no profile doc exists
      navigate('/user/dashboard', { replace: true });

    } catch (err) {
      console.error('[Login] Auth error:', err);
      if (err.code === 'auth/invalid-credential' || err.code === 'auth/user-not-found' || err.code === 'auth/wrong-password' || err.code === 'auth/invalid-email') {
        setError('Invalid email address or password.');
      } else if (err.code === 'auth/too-many-requests') {
        setError('Too many failed attempts. Please try again later.');
      } else {
        setError(err.message || 'Login failed.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container" data-testid="login-container">
      <div className="auth-card">
        <div className="auth-header">
          <div style={{ fontSize: '36px', marginBottom: '8px' }}>🚑</div>
          <h2>Smart Ambulance System</h2>
          <p>Sign in to access your dispatch command center</p>
        </div>

        {error && (
          <div className="badge badge-danger mb-3" style={{ display: 'block', padding: '12px 16px', borderRadius: 'var(--radius-md)', fontSize: '13px', textAlign: 'center', lineHeight: '1.4' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input
              type="email"
              className="form-control"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              required
              data-testid="login-email-input"
            />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              type="password"
              className="form-control"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              data-testid="login-password-input"
            />
          </div>

          <button
            type="submit"
            className="btn btn-primary w-100 mt-2"
            disabled={loading}
            style={{ width: '100%', padding: '14px', borderRadius: 'var(--radius-md)', fontSize: '16px', fontWeight: '700' }}
            data-testid="login-submit-btn"
          >
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                <span className="spinner"></span> Authenticating...
              </span>
            ) : 'Sign In'}
          </button>
        </form>

        <div className="text-center mt-4" style={{ fontSize: '14px', color: 'var(--text-muted)' }}>
          Don't have an account? <Link to="/register" style={{ fontWeight: '600', color: 'var(--primary)' }}>Create an account</Link>
        </div>
      </div>
    </div>
  );
};

export default Login;
