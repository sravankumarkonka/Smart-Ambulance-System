import React, { createContext, useContext, useEffect, useState } from 'react';
import { auth, db } from '../config/firebase';
import { onAuthStateChanged, signOut as firebaseSignOut } from 'firebase/auth';
import { doc, onSnapshot } from 'firebase/firestore';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [userData, setUserData] = useState(null);
  const [userRole, setUserRole] = useState('user');
  const [userStatus, setUserStatus] = useState(null);
  const [userApproved, setUserApproved] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let unsubscribeFirestore = null;

    const unsubscribeAuth = onAuthStateChanged(auth, (user) => {
      if (unsubscribeFirestore) {
        unsubscribeFirestore();
        unsubscribeFirestore = null;
      }

      if (user) {
        setCurrentUser(user);

        // Real-time listener for the user's Firestore profile
        const userDocRef = doc(db, 'users', user.uid);
        unsubscribeFirestore = onSnapshot(userDocRef, (snapshot) => {
          if (snapshot.exists()) {
            const data = snapshot.data();
            setUserData(data);
            setUserRole(data.role || 'user');
            setUserStatus(data.status || 'pending');
            setUserApproved(data.approved === true);
          } else {
            console.warn('[AuthContext] Firestore profile missing for UID:', user.uid);
            setUserData(null);
            setUserRole('user');
            setUserStatus('pending');
            setUserApproved(false);
          }
          setLoading(false);
        }, (error) => {
          console.error('[AuthContext] Error listening to user profile:', error);
          setLoading(false);
        });

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
