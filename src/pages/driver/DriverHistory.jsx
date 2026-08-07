import React, { useEffect, useState } from 'react';
import { db } from '../../config/firebase';
import { collection, query, where, onSnapshot } from 'firebase/firestore';
import { useAuth } from '../../context/AuthContext';

const DriverHistory = () => {
  const { currentUser } = useAuth();
  const [history, setHistory] = useState([]);
  const [statusFilter, setStatusFilter] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    if (!currentUser?.uid) return;

    // Fetch emergencies assigned to this driver
    const q = query(collection(db, 'emergencies'), where('driverId', '==', currentUser.uid));
    const unsub = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      list.sort((a, b) => new Date(b.createdAt || b.timestamp || 0) - new Date(a.createdAt || a.timestamp || 0));
      setHistory(list);
    });

    return () => unsub();
  }, [currentUser]);

  const filteredHistory = history.filter(item => {
    const statusMatch = statusFilter === 'all' || (item.status || 'pending').toLowerCase() === statusFilter.toLowerCase();
    const searchMatch = !searchTerm ||
      (item.patientName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (item.emergencyType || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (item.hospitalName || item.hospital || '').toLowerCase().includes(searchTerm.toLowerCase());
    return statusMatch && searchMatch;
  });

  const totalCompleted = history.filter(i => i.status === 'completed').length;
  const totalAssigned = history.filter(i => i.status === 'assigned' || i.status === 'arrived').length;

  return (
    <div className="container mt-4" style={{ maxWidth: '1200px' }} data-testid="driver-history-page">
      <div className="mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '26px', fontWeight: 700 }}>Driver Dispatch Response History</h2>
          <p className="text-muted" style={{ fontSize: '14px' }}>Log of all emergency responses, patient pick-ups, and completed hospital transfers.</p>
        </div>
      </div>

      {/* Summary Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <div className="card" style={{ borderLeft: '6px solid var(--primary)', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Total Calls Responded</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: 'var(--primary)' }}>{history.length}</div>
        </div>

        <div className="card" style={{ borderLeft: '6px solid var(--accent-green)', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Successfully Completed</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: 'var(--accent-green)' }}>{totalCompleted}</div>
        </div>

        <div className="card" style={{ borderLeft: '6px solid var(--accent-yellow)', padding: '18px' }}>
          <span className="text-muted" style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' }}>Active In Progress</span>
          <div style={{ fontSize: '32px', fontWeight: 800, marginTop: '6px', color: '#b45309' }}>{totalAssigned}</div>
        </div>
      </div>

      {/* Filters Bar */}
      <div className="card mb-4" style={{ padding: '16px 20px', display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ flex: '1 1 240px' }}>
          <input
            type="text"
            className="form-control"
            placeholder="Search patient name, type, or hospital..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div style={{ flex: '0 0 200px' }}>
          <select className="form-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="all">All Call Statuses</option>
            <option value="completed">Completed</option>
            <option value="assigned">Active / Assigned</option>
            <option value="arrived">Arrived at Scene</option>
            <option value="cancelled">Cancelled</option>
          </select>
        </div>
      </div>

      {/* History List */}
      <div className="card">
        {filteredHistory.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
            No emergency response history found matching your search.
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left', color: 'var(--text-muted)' }}>
                <th style={{ padding: '14px 12px' }}>Call ID</th>
                <th style={{ padding: '14px 12px' }}>Patient Name</th>
                <th style={{ padding: '14px 12px' }}>Emergency Type</th>
                <th style={{ padding: '14px 12px' }}>Destination Hospital</th>
                <th style={{ padding: '14px 12px' }}>Severity</th>
                <th style={{ padding: '14px 12px' }}>Call Status</th>
                <th style={{ padding: '14px 12px' }}>Accepted At</th>
              </tr>
            </thead>
            <tbody>
              {filteredHistory.map((item) => {
                const statusLower = (item.status || 'pending').toLowerCase();
                const statusBadgeClass =
                  statusLower === 'completed' ? 'badge-success' :
                  statusLower === 'cancelled' ? 'badge-danger' : 'badge-warning';

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
                      {item.hospitalName || item.hospital ? (
                        <span>🏥 {item.hospitalName || item.hospital}</span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>Not specified</span>
                      )}
                    </td>
                    <td style={{ padding: '14px 12px' }}>
                      <span style={{ color: sevColor, fontWeight: 700, textTransform: 'uppercase', fontSize: '12px' }}>
                        {item.severityLevel || item.severity || 'MEDIUM'}
                      </span>
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
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default DriverHistory;
