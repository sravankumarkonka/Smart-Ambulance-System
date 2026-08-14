import React, { createContext, useContext, useEffect, useState } from 'react';
import { auth, db } from '../config/firebase';
import { onAuthStateChanged, signOut as firebaseSignOut } from 'firebase/auth';
import { doc, getDoc, onSnapshot } from 'firebase/firestore';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [userData, setUserData] = useState(null);
  const [userRole, setUserRole] = useState('user');
  const [userStatus, setUserStatus] = useState(null);
  const [userApproved, setUserApproved] = useState(false);
  const [loading, setLoading] = useState(true);

  const processUserProfile = (data) => {
    if (!data) {
      // Default fallback for authenticated users without a Firestore doc
      setUserRole('user');
      setUserStatus('active');
      setUserApproved(true);
      return;
    }

    const role = data.role || 'user';
    const isUser = role === 'user';
    
    // Status defaults: 'active' for normal users, 'pending' for driver/admin if missing
    const status = data.status || (isUser ? 'active' : 'pending');
    
    // Approved defaults: true for normal users, false for driver/admin if missing
    const approved = data.approved !== undefined ? (data.approved === true) : isUser;

    setUserData(data);
    setUserRole(role);
    setUserStatus(status);
    setUserApproved(approved);
  };

  useEffect(() => {
    let unsubscribeFirestore = null;
    let timeoutId = null;

    const unsubscribeAuth = onAuthStateChanged(auth, async (user) => {
      if (unsubscribeFirestore) {
        unsubscribeFirestore();
        unsubscribeFirestore = null;
      }
      if (timeoutId) {
        clearTimeout(timeoutId);
        timeoutId = null;
      }

      if (user) {
        setCurrentUser(user);

        // Safety timeout: if Firestore doesn't respond within 5 seconds,
        // fallback to default user role so the app doesn't hang
        timeoutId = setTimeout(() => {
          console.warn('[AuthContext] Firestore profile fetch timed out after 5s — releasing loading with defaults');
          setUserRole('user');
          setUserStatus('active');
          setUserApproved(true);
          setLoading(false);
        }, 5000);

        // Fast one-shot getDoc
        try {
          const userDocRef = doc(db, 'users', user.uid);
          const snapshot = await getDoc(userDocRef);
          if (snapshot.exists()) {
            processUserProfile(snapshot.data());
          } else {
            console.warn('[AuthContext] Firestore profile missing for UID:', user.uid);
            setUserData(null);
            processUserProfile(null);
          }
          if (timeoutId) {
            clearTimeout(timeoutId);
            timeoutId = null;
          }
          setLoading(false);
        } catch (error) {
          console.error('[AuthContext] Error fetching user profile via getDoc:', error);
          processUserProfile(null);
          if (timeoutId) {
            clearTimeout(timeoutId);
            timeoutId = null;
          }
          setLoading(false);
        }

        // Real-time listener for subsequent background updates
        try {
          const userDocRef = doc(db, 'users', user.uid);
          unsubscribeFirestore = onSnapshot(userDocRef, (snapshot) => {
            if (snapshot.exists()) {
              processUserProfile(snapshot.data());
            }
          }, (error) => {
            console.error('[AuthContext] Error in user profile listener:', error);
          });
        } catch (error) {
          console.error('[AuthContext] Error setting up onSnapshot listener:', error);
        }

      } else {
        setCurrentUser(null);
        setUserData(null);
        setUserRole('user');
        setUserStatus(null);
        setUserApproved(false);
        setLoading(false);
      }
    });

    return () => {
      unsubscribeAuth();
      if (unsubscribeFirestore) {
        unsubscribeFirestore();
      }
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    };
  }, []);

  const logout = async () => {
    await firebaseSignOut(auth);
  };

  const value = {
    currentUser,
    userData,
    userRole,
    userStatus,
    userApproved,
    loading,
    logout
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
