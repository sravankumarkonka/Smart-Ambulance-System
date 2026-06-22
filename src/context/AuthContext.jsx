import React, { createContext, useContext, useEffect, useState } from 'react';
import { auth, db } from '../config/firebase';
import { onAuthStateChanged } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import api from '../services/api';

const AuthContext = createContext();

/* eslint-disable-next-line react-refresh/only-export-components */
export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(() => {
    const mockUserStr = localStorage.getItem('mockUser');
    if (mockUserStr) {
      try {
        const parsed = JSON.parse(mockUserStr);
        return {
          ...parsed,
          getIdToken: async () => localStorage.getItem('mockToken') || 'mock-token-dummy'
        };
      } catch (err) {
        console.error('[Mock Auth] Failed to restore mock user:', err);
      }
    }
    return null;
  });
  const [userRole, setUserRole] = useState(() => localStorage.getItem('userRole') || null); // 'user', 'driver', 'admin'
  const [loading, setLoading] = useState(() => {
    const mockUserStr = localStorage.getItem('mockUser');
    return !mockUserStr;
  });

  useEffect(() => {
    const checkMockAuth = () => {
      const mockUserStr = localStorage.getItem('mockUser');
      if (mockUserStr) {
        try {
          const parsed = JSON.parse(mockUserStr);
          const mockUserObj = {
            ...parsed,
            getIdToken: async () => localStorage.getItem('mockToken') || 'mock-token-dummy'
          };
          setCurrentUser(mockUserObj);
          const role = localStorage.getItem('userRole') || 'user';
          setUserRole(role);
          setLoading(false);
          return true;
        } catch (err) {
          console.error('[Mock Auth] Failed to restore mock user:', err);
        }
      }
      return false;
    };

    const handleMockLogin = () => {
      if (!checkMockAuth()) {
        setCurrentUser(null);
        setUserRole(null);
        setLoading(false);
      }
    };

    window.addEventListener('mock-login-changed', handleMockLogin);

    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      if (localStorage.getItem('mockUser')) {
        return;
      }
      if (user) {
        setCurrentUser(user);
        const cachedRole = localStorage.getItem('userRole');
        if (cachedRole) {
          setUserRole(cachedRole);
          setLoading(false);
        } else {
          setLoading(true);
        }

        let role = cachedRole || 'user';

        try {
          // Read role directly from Firestore first
          const userDocRef = doc(db, 'users', user.uid);
          const userDocSnap = await getDoc(userDocRef);
          if (userDocSnap.exists()) {
            role = userDocSnap.data().role || 'user';
            setUserRole(role);
            localStorage.setItem('userRole', role);
          } else {
            // Fallback to profile API if document is not found
            const response = await api.get(`/api/auth/profile/${user.uid}`);
            if (response.data && response.data.role) {
              role = response.data.role;
              setUserRole(role);
              localStorage.setItem('userRole', role);
            } else {
              setUserRole('user');
              localStorage.setItem('userRole', 'user');
            }
          }
        } catch (err) {
          console.error("Error fetching user role from Firestore, trying API fallback:", err);
          try {
            const response = await api.get(`/api/auth/profile/${user.uid}`);
            if (response.data && response.data.role) {
              role = response.data.role;
              setUserRole(role);
              localStorage.setItem('userRole', role);
            }
          } catch (apiErr) {
            console.error("Error fetching user role from API in AuthProvider:", apiErr);
            if (!cachedRole) {
              setUserRole('user');
            }
          }
        }
      } else {
        setCurrentUser(null);
        setUserRole(null);
        localStorage.removeItem('userRole');
      }
      setLoading(false);
    });

    return () => {
      unsubscribe();
      window.removeEventListener('mock-login-changed', handleMockLogin);
    };
  }, []);

  const value = {
    currentUser,
    userRole,
    setUserRole,
    loading
  };

  return (
    <AuthContext.Provider value={value}>
      {loading ? (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100vh',
          backgroundColor: '#F7F9FC',
          fontFamily: "'Inter', sans-serif"
        }}>
          <div className="spinner" style={{ width: '40px', height: '40px', borderWidth: '4px' }}></div>
          <p style={{ marginTop: '16px', color: '#8E8E93', fontSize: '15px', fontWeight: 500 }}>
            Loading authentication...
          </p>
        </div>
      ) : children}
    </AuthContext.Provider>
  );
};

