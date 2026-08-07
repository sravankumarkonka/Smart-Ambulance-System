import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth, db } from '../../config/firebase';
import { createUserWithEmailAndPassword, updateProfile } from 'firebase/auth';
import { doc, setDoc, addDoc, collection } from 'firebase/firestore';

const Register = () => {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    role: 'user', // 'user', 'driver', 'admin'
    licenseNumber: '',
    vehicleNumber: '',
    experience: ''
  });

  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleRoleSelect = (role) => {
    setFormData({ ...formData, role });
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    if (formData.password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    if (formData.role === 'driver' && (!formData.licenseNumber || !formData.vehicleNumber)) {
      setError('Driver license number and vehicle number are required.');
      return;
    }

    setLoading(true);

    try {
      // 1. Create Firebase Auth user
      const userCredential = await createUserWithEmailAndPassword(auth, formData.email.trim(), formData.password);
      const uid = userCredential.user.uid;

      // Update Auth Display Name
      await updateProfile(userCredential.user, { displayName: formData.name.trim() });

      const now = new Date().toISOString();
      const isUser = formData.role === 'user';
      const status = isUser ? 'active' : 'pending';
      const approved = isUser;

      // 2. Save User Document in Firestore
      const userDocData = {
        uid,
        name: formData.name.trim(),
        email: formData.email.trim(),
        phone: formData.phone.trim(),
        photoURL: '',
        role: formData.role,
        status,
        approved,
        createdAt: now,
        updatedAt: now
      };

      await setDoc(doc(db, 'users', uid), userDocData);

      // 3. Driver specific document
      if (formData.role === 'driver') {
        await setDoc(doc(db, 'drivers', uid), {
          uid,
          licenseNumber: formData.licenseNumber.trim(),
          vehicleNumber: formData.vehicleNumber.trim(),
          experience: formData.experience.trim(),
          ambulanceId: '',
          availability: false,
          currentLatitude: 0.0,
          currentLongitude: 0.0,
          rating: 0.0,
          createdAt: now
        });

        await setDoc(doc(db, 'ambulances', uid), {
          driverId: uid,
          driverUid: uid,
          driverName: formData.name.trim(),
          driverPhone: formData.phone.trim(),
          latitude: 0.0,
          longitude: 0.0,
          heading: 0.0,
          speed: 0.0,
          status: 'unavailable',
          isAvailable: false,
          updatedAt: now,
          lastUpdated: now
        });

        // Driver notification for admins
        await addDoc(collection(db, 'notifications'), {
          type: 'driver_registration',
          targetRole: 'admin',
          senderUid: uid,
          senderName: formData.name.trim(),
          senderEmail: formData.email.trim(),
          message: `New driver ${formData.name.trim()} registered and requires approval.`,
          read: false,
          createdAt: now
        });
      }

      // 4. Admin specific notification for super admin
      if (formData.role === 'admin') {
        await addDoc(collection(db, 'notifications'), {
          type: 'admin_registration',
          targetRole: 'super_admin',
          senderUid: uid,
          senderName: formData.name.trim(),
          senderEmail: formData.email.trim(),
          message: `New admin ${formData.name.trim()} registered and requires Super Admin approval.`,
          read: false,
          createdAt: now
        });
      }

      // 5. Audit Log
      await addDoc(collection(db, 'audit_logs'), {
        action: 'registration',
        performedBy: uid,
        targetUid: uid,
        details: { role: formData.role, email: formData.email.trim() },
        createdAt: now
      });

      // 6. Navigation / Message handling
      if (isUser) {
        setSuccessMsg('Account created successfully! Redirecting...');
        setTimeout(() => {
          navigate('/user/dashboard');
        }, 1200);
      } else {
        const waitMsg = formData.role === 'driver'
          ? 'Registration submitted! Your driver account is awaiting Admin approval.'
          : 'Registration submitted! Your admin account is awaiting Super Admin approval.';
        
        setSuccessMsg(waitMsg);
        await auth.signOut();
      }

    } catch (err) {
      console.error('[Register] Registration error:', err);
      if (err.code === 'auth/email-already-in-use') {
        setError('An account with this email address already exists.');
      } else if (err.code === 'auth/weak-password') {
        setError('Password must be at least 6 characters.');
      } else {
        setError(err.message || 'Registration failed.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container" data-testid="register-container">
      <div className="auth-card" style={{ maxWidth: '520px' }}>
        <div className="auth-header">
          <div style={{ fontSize: '36px', marginBottom: '8px' }}>🚑</div>
          <h2>Create Account</h2>
          <p>Join the Smart Ambulance Emergency Network</p>
        </div>

        {error && (
          <div className="badge badge-danger mb-3" style={{ display: 'block', padding: '12px 16px', borderRadius: 'var(--radius-md)', fontSize: '13px', textAlign: 'center', lineHeight: '1.4' }}>
            {error}
          </div>
        )}

        {successMsg && (
          <div className="badge badge-success mb-3" style={{ display: 'block', padding: '12px 16px', borderRadius: 'var(--radius-md)', fontSize: '13px', textAlign: 'center', lineHeight: '1.4' }}>
            {successMsg}
          </div>
        )}

        <form onSubmit={handleRegister}>
          <div className="form-group">
            <label className="form-label">I am registering as:</label>
            <div className="auth-role-group">
              <div
                className={`auth-role-card ${formData.role === 'user' ? 'active' : ''}`}
                onClick={() => handleRoleSelect('user')}
                data-testid="role-user"
              >
                <div style={{ fontSize: '20px', marginBottom: '4px' }}>🧑‍🦽</div>
                Patient / User
              </div>

              <div
                className={`auth-role-card ${formData.role === 'driver' ? 'active' : ''}`}
                onClick={() => handleRoleSelect('driver')}
                data-testid="role-driver"
              >
                <div style={{ fontSize: '20px', marginBottom: '4px' }}>🚑</div>
                Ambulance Driver
              </div>

              <div
                className={`auth-role-card ${formData.role === 'admin' ? 'active' : ''}`}
                onClick={() => handleRoleSelect('admin')}
                data-testid="role-admin"
              >
                <div style={{ fontSize: '20px', marginBottom: '4px' }}>👨‍💼</div>
                Administrator
              </div>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Full Name</label>
            <input
              type="text"
              name="name"
              className="form-control"
              value={formData.name}
              onChange={handleChange}
              placeholder="e.g. Sravan Kumar"
              required
              data-testid="register-name-input"
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input
                type="email"
                name="email"
                className="form-control"
                value={formData.email}
                onChange={handleChange}
                placeholder="name@example.com"
                required
                data-testid="register-email-input"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Phone Number</label>
              <input
                type="tel"
                name="phone"
                className="form-control"
                value={formData.phone}
                onChange={handleChange}
                placeholder="+1 234 567 8900"
                required
                data-testid="register-phone-input"
              />
            </div>
          </div>

          {formData.role === 'driver' && (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <div className="form-group">
                  <label className="form-label">Driving License No.</label>
                  <input
                    type="text"
                    name="licenseNumber"
                    className="form-control"
                    value={formData.licenseNumber}
                    onChange={handleChange}
                    placeholder="DL-123456789"
                    required
                    data-testid="license-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Vehicle Registration</label>
                  <input
                    type="text"
                    name="vehicleNumber"
                    className="form-control"
                    value={formData.vehicleNumber}
                    onChange={handleChange}
                    placeholder="KA-01-EA-1234"
                    required
                    data-testid="vehicle-input"
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Years of Experience (Optional)</label>
                <input
                  type="text"
                  name="experience"
                  className="form-control"
                  value={formData.experience}
                  onChange={handleChange}
                  placeholder="e.g. 5 years"
                />
              </div>
            </>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                type="password"
                name="password"
                className="form-control"
                value={formData.password}
                onChange={handleChange}
                placeholder="••••••••"
                required
                data-testid="register-password-input"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Confirm Password</label>
              <input
                type="password"
                name="confirmPassword"
                className="form-control"
                value={formData.confirmPassword}
                onChange={handleChange}
                placeholder="••••••••"
                required
                data-testid="confirm-password-input"
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary w-100 mt-2"
            disabled={loading}
            style={{ width: '100%', padding: '14px', borderRadius: 'var(--radius-md)', fontSize: '16px', fontWeight: '700' }}
            data-testid="register-submit-btn"
          >
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                <span className="spinner"></span> Creating Account...
              </span>
            ) : 'Register Account'}
          </button>
        </form>

        <div className="text-center mt-4" style={{ fontSize: '14px', color: 'var(--text-muted)' }}>
          Already have an account? <Link to="/login" style={{ fontWeight: '600', color: 'var(--primary)' }}>Sign in</Link>
        </div>
      </div>
    </div>
  );
};

export default Register;
