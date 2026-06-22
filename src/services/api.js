import axios from 'axios';
import { auth } from '../config/firebase';

const api = axios.create({
  baseURL: 'http://localhost:5000'
});

// Interceptor to attach Firebase Auth ID Token to every outgoing request
api.interceptors.request.use(
  async (config) => {
    const mockToken = typeof window !== 'undefined' && localStorage.getItem('mockToken');
    if (mockToken) {
      config.headers.Authorization = `Bearer ${mockToken}`;
      return config;
    }
    const user = auth.currentUser;
    if (user) {
      try {
        // Use cached ID token or refresh if expired
        const token = await user.getIdToken();
        config.headers.Authorization = `Bearer ${token}`;
      } catch (err) {
        console.warn('[API Interceptor] Could not fetch Firebase Auth ID token:', err.message);
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default api;
