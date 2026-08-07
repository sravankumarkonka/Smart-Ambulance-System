import React, { useEffect, useState } from 'react';
import { db } from '../../config/firebase';
import { collection, query, where, onSnapshot, doc, updateDoc, deleteDoc, addDoc } from 'firebase/firestore';
import { useAuth } from '../../context/AuthContext';

const SuperAdminDashboard = () => {
  const { currentUser } = useAuth();
  const [pendingAdmins, setPendingAdmins] = useState([]);
  const [allUsers, setAllUsers] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [activeTab, setActiveTab] = useState('pending'); // 'pending', 'users', 'logs'
  const [actionLoading, setActionLoading] = useState(false);

  // 1. Real-time Pending Admins
  useEffect(() => {
    const q = query(collection(db, 'users'), where('role', '==', 'admin'), where('status', '==', 'pending'));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(d => ({ uid: d.id, ...d.data() }));
      setPendingAdmins(list);
    });
    return () => unsub();
  }, []);

  // 2. Real-time All Users
  useEffect(() => {
    const q = query(collection(db, 'users'));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(d => ({ uid: d.id, ...d.data() }));
      setAllUsers(list);
    });
    return () => unsub();
  }, []);

  // 3. Real-time Audit Logs
  useEffect(() => {
    const q = query(collection(db, 'audit_logs'));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
      setAuditLogs(list.slice(0, 100));
    });
    return () => unsub();
  }, []);

  // 4. Real-time Notifications
  useEffect(() => {
    const q = query(collection(db, 'notifications'), where('targetRole', '==', 'super_admin'));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
      setNotifications(list);
    });
    return () => unsub();
  }, []);

  const handleApproveAdmin = async (targetUid) => {
    if (actionLoading) return;
    setActionLoading(true);
    try {
      const now = new Date().toISOString();
      await updateDoc(doc(db, 'users', targetUid), {
        status: 'active',
        approved: true,
        updatedAt: now
      });

      await addDoc(collection(db, 'audit_logs'), {
        action: 'approval',
        performedBy: currentUser.uid,
        targetUid,
        details: { role: 'admin', action: 'approved' },
        createdAt: now
      });

      await addDoc(collection(db, 'notifications'), {
        type: 'admin_approved',
        targetRole: 'admin',
        targetUid,
        message: 'Your admin account has been approved by Super Admin!',
        read: false,
        createdAt: now
      });

    } catch (err) {
      console.error('[SuperAdmin] Approve error:', err);
      alert('Failed to approve admin: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectAdmin = async (targetUid) => {
    if (actionLoading) return;
    setActionLoading(true);
    try {
      const now = new Date().toISOString();
      await updateDoc(doc(db, 'users', targetUid), {
        status: 'rejected',
        approved: false,
        updatedAt: now
      });

      await addDoc(collection(db, 'audit_logs'), {
        action: 'rejection',
        performedBy: currentUser.uid,
        targetUid,
        details: { role: 'admin', action: 'rejected' },
        createdAt: now
      });

    } catch (err) {
      console.error('[SuperAdmin] Reject error:', err);
      alert('Failed to reject admin: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleSuspendUser = async (targetUid) => {
    if (actionLoading) return;
    setActionLoading(true);
    try {
      const now = new Date().toISOString();
      await updateDoc(doc(db, 'users', targetUid), {
        status: 'suspended',
        updatedAt: now
      });

      await addDoc(collection(db, 'audit_logs'), {
        action: 'suspension',
        performedBy: currentUser.uid,
        targetUid,
        details: { action: 'suspended' },
        createdAt: now
      });

    } catch (err) {
      console.error('[SuperAdmin] Suspend error:', err);
      alert('Failed to suspend user: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleActivateUser = async (targetUid) => {
    if (actionLoading) return;
    setActionLoading(true);
    try {
      const now = new Date().toISOString();
      await updateDoc(doc(db, 'users', targetUid), {
        status: 'active',
        approved: true,
        updatedAt: now
      });

      await addDoc(collection(db, 'audit_logs'), {
        action: 'activation',
        performedBy: currentUser.uid,
        targetUid,
        details: { action: 'activated' },
        createdAt: now
      });

    } catch (err) {
      console.error('[SuperAdmin] Activate error:', err);
      alert('Failed to activate user: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (targetUid) => {
    if (!window.confirm('Are you sure you want to delete this user document?')) return;
    if (actionLoading) return;
    setActionLoading(true);
    try {
      await deleteDoc(doc(db, 'users', targetUid));
      try { await deleteDoc(doc(db, 'drivers', targetUid)); } catch (_) {}
      try { await deleteDoc(doc(db, 'ambulances', targetUid)); } catch (_) {}

      await addDoc(collection(db, 'audit_logs'), {
        action: 'deletion',
        performedBy: currentUser.uid,
        targetUid,
        details: { action: 'deleted' },
        createdAt: new Date().toISOString()
      });

    } catch (err) {
      console.error('[SuperAdmin] Delete error:', err);
      alert('Failed to delete user: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const totalAdmins = allUsers.filter(u => u.role === 'admin' && u.status === 'active').length;
  const totalDrivers = allUsers.filter(u => u.role === 'driver' && u.status === 'active').length;
  const totalUsers = allUsers.filter(u => u.role === 'user').length;
  const pendingCount = pendingAdmins.length;

  return (
    <div className="container mt-4" style={{ maxWidth: '1400px' }} data-testid="super-admin-dashboard">
      <div className="mb-4">
        <h2>Super Admin Control Center</h2>
        <p className="text-muted" style={{ fontSize: '14px' }}>System-wide moderation, admin approvals, user management, and audit logs.</p>
      </div>

      {/* Analytics Summary */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <div className="card" style={{ borderLeft: '6px solid var(--accent-yellow)', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Pending Admin Approvals</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: '#b45309' }}>{pendingCount}</div>
        </div>

        <div className="card" style={{ borderLeft: '6px solid var(--primary)', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Active Admins</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: 'var(--primary)' }}>{totalAdmins}</div>
        </div>

        <div className="card" style={{ borderLeft: '6px solid var(--accent-green)', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Active Drivers</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: 'var(--accent-green)' }}>{totalDrivers}</div>
        </div>

        <div className="card" style={{ borderLeft: '6px solid #7c3aed', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Registered Patients</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: '#7c3aed' }}>{totalUsers}</div>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '12px', borderBottom: '2px solid var(--border)', marginBottom: '20px' }}>
        <button
          className={`btn ${activeTab === 'pending' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('pending')}
          style={{ borderRadius: '8px 8px 0 0', padding: '10px 20px' }}
        >
          Pending Admin Approvals ({pendingCount})
        </button>
        <button
          className={`btn ${activeTab === 'users' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('users')}
          style={{ borderRadius: '8px 8px 0 0', padding: '10px 20px' }}
        >
          All System Users ({allUsers.length})
        </button>
        <button
          className={`btn ${activeTab === 'logs' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('logs')}
          style={{ borderRadius: '8px 8px 0 0', padding: '10px 20px' }}
        >
          Audit Logs ({auditLogs.length})
        </button>
      </div>

      {/* Tab 1: Pending Admin Approvals */}
      {activeTab === 'pending' && (
        <div className="card">
          <h3 style={{ marginBottom: '16px' }}>Pending Administrator Requests</h3>
          {pendingAdmins.length === 0 ? (
            <p className="text-muted" style={{ textAlign: 'center', padding: '32px' }}>No pending admin registration requests.</p>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                  <th style={{ padding: '12px' }}>Name</th>
                  <th style={{ padding: '12px' }}>Email</th>
                  <th style={{ padding: '12px' }}>Phone</th>
                  <th style={{ padding: '12px' }}>Requested At</th>
                  <th style={{ padding: '12px' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {pendingAdmins.map((admin) => (
                  <tr key={admin.uid} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ padding: '12px', fontWeight: 600 }}>{admin.name}</td>
                    <td style={{ padding: '12px' }}>{admin.email}</td>
                    <td style={{ padding: '12px' }}>{admin.phone}</td>
                    <td style={{ padding: '12px' }}>{new Date(admin.createdAt || 0).toLocaleString()}</td>
                    <td style={{ padding: '12px', display: 'flex', gap: '8px' }}>
                      <button
                        className="btn btn-primary"
                        style={{ padding: '4px 12px', fontSize: '12px' }}
                        disabled={actionLoading}
                        onClick={() => handleApproveAdmin(admin.uid)}
                      >
                        Approve
                      </button>
                      <button
                        className="btn btn-outline"
                        style={{ padding: '4px 12px', fontSize: '12px', color: 'var(--accent-red)', borderColor: 'var(--accent-red)' }}
                        disabled={actionLoading}
                        onClick={() => handleRejectAdmin(admin.uid)}
                      >
                        Reject
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Tab 2: All System Users */}
      {activeTab === 'users' && (
        <div className="card">
          <h3 style={{ marginBottom: '16px' }}>All Users in Directory</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '10px' }}>User</th>
                <th style={{ padding: '10px' }}>Email</th>
                <th style={{ padding: '10px' }}>Role</th>
                <th style={{ padding: '10px' }}>Status</th>
                <th style={{ padding: '10px' }}>Approved</th>
                <th style={{ padding: '10px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {allUsers.map((u) => (
                <tr key={u.uid} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '10px', fontWeight: 600 }}>{u.name || u.displayName}</td>
                  <td style={{ padding: '10px' }}>{u.email}</td>
                  <td style={{ padding: '10px' }}>
                    <span className="badge" style={{ background: u.role === 'super_admin' ? '#7c3aed' : u.role === 'admin' ? 'var(--primary)' : u.role === 'driver' ? '#b45309' : '#10b981', color: 'white', fontSize: '11px', textTransform: 'uppercase' }}>
                      {u.role}
                    </span>
                  </td>
                  <td style={{ padding: '10px' }}>
                    <span className="badge" style={{ background: u.status === 'active' ? 'var(--accent-green-light)' : u.status === 'pending' ? 'var(--accent-yellow-light)' : '#fee2e2', color: u.status === 'active' ? '#248A3D' : u.status === 'pending' ? '#B28E00' : 'var(--accent-red)', fontSize: '11px' }}>
                      {(u.status || 'unknown').toUpperCase()}
                    </span>
                  </td>
                  <td style={{ padding: '10px' }}>{u.approved ? '✅ Yes' : '❌ No'}</td>
                  <td style={{ padding: '10px', display: 'flex', gap: '6px' }}>
                    {u.role !== 'super_admin' && (
                      <>
                        {u.status === 'suspended' ? (
                          <button className="btn btn-outline" style={{ padding: '3px 8px', fontSize: '11px' }} onClick={() => handleActivateUser(u.uid)}>
                            Activate
                          </button>
                        ) : (
                          <button className="btn btn-outline" style={{ padding: '3px 8px', fontSize: '11px', color: '#b45309', borderColor: '#b45309' }} onClick={() => handleSuspendUser(u.uid)}>
                            Suspend
                          </button>
                        )}
                        <button className="btn btn-outline" style={{ padding: '3px 8px', fontSize: '11px', color: 'var(--accent-red)', borderColor: 'var(--accent-red)' }} onClick={() => handleDeleteUser(u.uid)}>
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab 3: Audit Logs */}
      {activeTab === 'logs' && (
        <div className="card">
          <h3 style={{ marginBottom: '16px' }}>System Audit Trail</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '8px' }}>Timestamp</th>
                <th style={{ padding: '8px' }}>Action</th>
                <th style={{ padding: '8px' }}>Performed By (UID)</th>
                <th style={{ padding: '8px' }}>Target (UID)</th>
                <th style={{ padding: '8px' }}>Details</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.map((log) => (
                <tr key={log.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '8px' }}>{new Date(log.createdAt || 0).toLocaleString()}</td>
                  <td style={{ padding: '8px', fontWeight: 600, textTransform: 'uppercase' }}>{log.action}</td>
                  <td style={{ padding: '8px', fontFamily: 'monospace' }}>{log.performedBy}</td>
                  <td style={{ padding: '8px', fontFamily: 'monospace' }}>{log.targetUid || '-'}</td>
                  <td style={{ padding: '8px' }}>{JSON.stringify(log.details || {})}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

    </div>
  );
};

export default SuperAdminDashboard;
