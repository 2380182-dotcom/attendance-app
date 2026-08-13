import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Mirrors the mobile app's pattern (mobile/src/context/AuthContext.js): a 401
// from any authenticated request — not just an explicit logout — clears the
// session, since the token is no longer valid regardless of why.
let sessionExpiredHandler = null;
export function setSessionExpiredHandler(handler) {
  sessionExpiredHandler = handler;
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      sessionExpiredHandler?.();
    }
    return Promise.reject(error);
  }
);

export const apiService = {
  auth: {
    async login(companyCode, agentId, password) {
      const response = await api.post('/auth/login', { companyCode, agentId, password });
      return response.data.data; // ApiResponse envelope: { success, message, data }
    },
  },
};

export default api;
