import React, { useEffect, useState } from 'react';
import { db } from '../../config/firebase';
import { collection, query, onSnapshot } from 'firebase/firestore';

const AdminHistory = () => {
  const [emergencies, setEmergencies] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [activeTab, setActiveTab] = useState('emergencies'); // 'emergencies' or 'audit'
  const [statusFilter, setStatusFilter] = useState('all');
  const [severityFilter, setSeverityFilter] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');

  // 1. Subscribe to all emergencies
  useEffect(() => {
    const q = query(collection(db, 'emergencies'));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      list.sort((a, b) => new Date(b.createdAt || b.timestamp || 0) - new Date(a.createdAt || a.timestamp || 0));
      setEmergencies(list);
    });
    return () => unsub();
  }, []);

  // 2. Subscribe to audit logs
  useEffect(() => {
    const q = query(collection(db, 'audit_logs'));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
      setAuditLogs(list);
    });
    return () => unsub();
  }, []);

  // Filter emergencies
  const filteredEmergencies = emergencies.filter(e => {
    const statusMatch = statusFilter === 'all' || (e.status || 'pending').toLowerCase() === statusFilter.toLowerCase();
    const severityMatch = severityFilter === 'all' || (e.severityLevel || e.severity || 'medium').toLowerCase() === severityFilter.toLowerCase();
    const searchMatch = !searchTerm ||
      (e.patientName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (e.driverName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (e.emergencyType || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (e.id || '').toLowerCase().includes(searchTerm.toLowerCase());
    return statusMatch && severityMatch && searchMatch;
  });

  return (
    <div className="container mt-4" style={{ maxWidth: '1400px' }} data-testid="admin-history-page">
      <div className="mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '26px', fontWeight: 700 }}>Admin System History & Audit Logs</h2>
          <p className="text-muted" style={{ fontSize: '14px' }}>Complete archive of past emergency dispatches, driver responses, and audit trails.</p>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '12px', borderBottom: '2px solid var(--border)', marginBottom: '24px' }}>
        <button
          className={`btn ${activeTab === 'emergencies' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('emergencies')}
          style={{ borderRadius: '8px 8px 0 0', padding: '10px 24px' }}
        >
          Emergency Dispatch History ({emergencies.length})
        </button>
        <button
          className={`btn ${activeTab === 'audit' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('audit')}
          style={{ borderRadius: '8px 8px 0 0', padding: '10px 24px' }}
        >
          Audit Logs ({auditLogs.length})
        </button>
      </div>

      {activeTab === 'emergencies' && (
        <>
          {/* Filters Bar */}
          <div className="card mb-4" style={{ padding: '16px 20px', display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'center' }}>
            <div style={{ flex: '1 1 240px' }}>
              <input
                type="text"
                className="form-control"
                placeholder="Search patient, driver, or type..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>

            <div style={{ flex: '0 0 180px' }}>
              <select className="form-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="all">All Statuses</option>
                <option value="completed">Completed</option>
                <option value="assigned">Assigned</option>
                <option value="arrived">Arrived</option>
                <option value="waiting">Waiting / Pending</option>
                <option value="cancelled">Cancelled</option>
              </select>
            </div>

            <div style={{ flex: '0 0 180px' }}>
              <select className="form-select" value={severityFilter} onChange={(e) => setSeverityFilter(e.target.value)}>
                <option value="all">All Severities</option>
                <option value="critical">Critical</option>
                <option value="high">High</option>
                <option value="medium">Medium</option>
                <option value="low">Low</option>
              </select>
            </div>
          </div>

          {/* History Table */}
          <div className="card">
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left', color: 'var(--text-muted)' }}>
                  <th style={{ padding: '14px 12px' }}>Request ID</th>
                  <th style={{ padding: '14px 12px' }}>Patient Name</th>
                  <th style={{ padding: '14px 12px' }}>Emergency Type</th>
                  <th style={{ padding: '14px 12px' }}>Severity</th>
                  <th style={{ padding: '14px 12px' }}>Assigned Driver</th>
                  <th style={{ padding: '14px 12px' }}>Status</th>
                  <th style={{ padding: '14px 12px' }}>Created At</th>
                </tr>
              </thead>
              <tbody>
                {filteredEmergencies.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                      No emergency records match your filter criteria.
                    </td>
                  </tr>
                ) : (
                  filteredEmergencies.map((item) => {
                    const statusLower = (item.status || 'pending').toLowerCase();
                    const statusBadgeClass =
                      statusLower === 'completed' ? 'badge-success' :
                      statusLower === 'cancelled' ? 'badge-danger' :
                      statusLower === 'assigned' || statusLower === 'arrived' ? 'badge-primary' : 'badge-warning';

                    const sevLower = (item.severityLevel || item.severity || 'medium').toLowerCase();
                    const sevColor =
                      sevLower === 'critical' ? 'var(--accent-red)' :
                      sevLower === 'high' ? '#e11d48' :
                      sevLower === 'medium' ? '#d97706' : '#16a34a';

                    return (
                      <tr key={item.id} style={{ borderBottom: '1px solid var(--border)' }}>
                        <td style={{ padding: '14px 12px', fontFamily: 'monospace', fontWeight: 600 }}>{item.id?.slice(0, 8)}...</td>
                        <td style={{ padding: '14px 12px', fontWeight: 600 }}>{item.patientName || 'Unknown Patient'}</td>
                        <td style={{ padding: '14px 12px', textTransform: 'capitalize' }}>{item.emergencyType || 'General'}</td>
                        <td style={{ padding: '14px 12px' }}>
                          <span style={{ color: sevColor, fontWeight: 700, textTransform: 'uppercase', fontSize: '12px' }}>
                            {item.severityLevel || item.severity || 'MEDIUM'}
                          </span>
                        </td>
                        <td style={{ padding: '14px 12px' }}>
                          {item.driverName ? (
                            <span style={{ fontWeight: 600 }}>🚑 {item.driverName}</span>
                          ) : (
                            <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Unassigned</span>
                          )}
                        </td>
                        <td style={{ padding: '14px 12px' }}>
                          <span className={`badge ${statusBadgeClass}`} style={{ textTransform: 'uppercase', fontSize: '11px' }}>
                            {item.status || 'pending'}
                          </span>
                        </td>
                        <td style={{ padding: '14px 12px', fontSize: '13px', color: 'var(--text-muted)' }}>
                          {item.createdAt ? new Date(item.createdAt).toLocaleString() : item.timestamp ? new Date(item.timestamp).toLocaleString() : 'N/A'}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </>
      )}

      {activeTab === 'audit' && (
        <div className="card">
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left', color: 'var(--text-muted)' }}>
                <th style={{ padding: '12px' }}>Timestamp</th>
                <th style={{ padding: '12px' }}>Action</th>
                <th style={{ padding: '12px' }}>Performed By (UID)</th>
                <th style={{ padding: '12px' }}>Target User (UID)</th>
                <th style={{ padding: '12px' }}>Log Details</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                    No audit logs recorded yet.
                  </td>
                </tr>
              ) : (
                auditLogs.map((log) => (
                  <tr key={log.id} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ padding: '12px', color: 'var(--text-muted)' }}>{new Date(log.createdAt || 0).toLocaleString()}</td>
                    <td style={{ padding: '12px', fontWeight: 700, textTransform: 'uppercase' }}>{log.action}</td>
                    <td style={{ padding: '12px', fontFamily: 'monospace' }}>{log.performedBy || 'System'}</td>
                    <td style={{ padding: '12px', fontFamily: 'monospace' }}>{log.targetUid || '-'}</td>
                    <td style={{ padding: '12px' }}>{JSON.stringify(log.details || {})}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default AdminHistory;
