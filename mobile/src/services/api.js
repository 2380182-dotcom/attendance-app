import axios from 'axios';
import { Platform, Alert } from 'react-native';
import { storage } from '../utils/storage';
import config from '../config';
import { navigate } from '../navigation/NavigationService';

// ✅ API URL - Centralized in config.js with dynamic fallback
let dynamicBaseUrl = config.API_URL;

storage.getServerUrl().then(url => {
  if (url) {
    dynamicBaseUrl = url;
  }
});

const api = axios.create({
  baseURL: dynamicBaseUrl,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  }
});

// Request Interceptor: Attach JWT Token and dynamic baseURL
api.interceptors.request.use(
  async (axiosConfig) => {
    const savedUrl = await storage.getServerUrl();
    if (savedUrl) {
      dynamicBaseUrl = savedUrl;
      axiosConfig.baseURL = savedUrl;
    } else {
      dynamicBaseUrl = config.API_URL;
      axiosConfig.baseURL = config.API_URL;
    }
    const token = await storage.getToken();
    if (token) {
      axiosConfig.headers.Authorization = `Bearer ${token}`;
    }
    return axiosConfig;
  },
  (error) => {
    return Promise.reject(error);
  }
);

let alertShowing = false;

// Set by AuthContext on mount so this module (outside the React tree) can
// clear auth state when the backend reports the token is no longer valid.
let sessionExpiredHandler = null;
export function setSessionExpiredHandler(handler) {
  sessionExpiredHandler = handler;
}

let sessionExpiredAlertShowing = false;

// Response Interceptor: Handle network errors, expired sessions, and route to settings
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // Check if error is due to network connection issues
    const isNetworkError = !error.response && (error.message === 'Network Error' || error.code === 'ECONNABORTED');
    if (isNetworkError && !alertShowing) {
      alertShowing = true;
      Alert.alert(
        'Server Connection Failed',
        `Unable to reach the server at:\n${error.config?.baseURL || dynamicBaseUrl}\n\nIf you switched Wi-Fi networks, the server IP address might have changed. Would you like to update your Server Settings?`,
        [
          { text: 'Cancel', style: 'cancel', onPress: () => { alertShowing = false; } },
          {
            text: 'Server Settings',
            onPress: () => {
              alertShowing = false;
              navigate('ServerSettings');
            }
          }
        ],
        { cancelable: true }
      );
    }

    // Session expiration: a 401 on an already-authenticated request means the
    // token expired/was revoked server-side. Exclude the login/register calls
    // themselves — a 401 there just means "wrong password", not "session expired".
    const requestUrl = error.config?.url || '';
    const isAuthEndpoint = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register');
    if (error.response?.status === 401 && !isAuthEndpoint) {
      await storage.clearAll();
      if (sessionExpiredHandler) {
        sessionExpiredHandler();
      }
      if (!sessionExpiredAlertShowing) {
        sessionExpiredAlertShowing = true;
        Alert.alert(
          'Session Expired',
          'Your session has expired. Please log in again.',
          [{ text: 'OK', onPress: () => { sessionExpiredAlertShowing = false; } }],
          { cancelable: false }
        );
      }
    }

    return Promise.reject(error);
  }
);

// Helper to extract nested backend response data or return readable errors
const handleResponse = (response) => {
  if (response.data && response.data.success) {
    return response.data.data;
  }
  throw new Error(response.data?.message || 'Server error occurred');
};

const handleApiError = (error) => {
  const message = error.response?.data?.message || error.message || 'API request failed';
  throw new Error(message);
};

export const apiService = {
  // Authentication APIs
  auth: {
    async login(companyCode, agentId, password) {
      try {
        const response = await api.post('/auth/login', { companyCode, agentId, password });
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async register(agentData) {
      try {
        const response = await api.post('/auth/register', agentData);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  },

  // Marts APIs
  marts: {
    async getAll() {
      try {
        const response = await api.get('/marts');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  },

  // Attendance APIs
  attendance: {
    async checkIn(agentId, martId, latitude, longitude, faceVerified = false) {
      try {
        const response = await api.post('/attendance/checkin', {
          agentId,
          martId,
          latitude,
          longitude,
          faceVerified,
        });
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async checkOut(agentId, latitude, longitude, faceVerified = false) {
      try {
        const response = await api.post('/attendance/checkout', {
          agentId,
          latitude,
          longitude,
          faceVerified,
        });
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getHistory(agentId) {
      try {
        const response = await api.get(`/attendance/agent/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getToday(agentId) {
      try {
        const response = await api.get(`/attendance/agent/${agentId}/today`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async isCheckedIn(agentId) {
      try {
        const response = await api.get(`/attendance/agent/${agentId}/is-checked-in`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getCurrentCheckIn(agentId) {
      try {
        const response = await api.get(`/attendance/agent/${agentId}/current`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async submitFaceResult(result) {
      try {
        const response = await api.post('/attendance/face-result', result);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async verifyMidDay(agentId) {
      try {
        const response = await api.post(`/attendance/verify-midday/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getTodayReport() {
      try {
        const response = await api.get('/attendance/report/today');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getVerificationStatus(agentId) {
      try {
        const response = await api.get(`/attendance/verification-status/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  },

  // Face verification APIs (on-device ML Kit)
  face: {
    async getStatus(agentId) {
      try {
        const response = await api.get(`/face/status/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getEmbedding(agentId) {
      try {
        const response = await api.get(`/face/embedding/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async saveEmbedding(agentId, embedding) {
      try {
        const response = await api.post('/face/embedding', { agentId, embedding });
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async submitResult(result) {
      try {
        const response = await api.post('/attendance/face-result', result);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getThresholdConfig() {
      try {
        const response = await api.get('/face/config/threshold');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
  },

  // Geo-Fencing APIs
  geoFence: {
    async check(agentId, latitude, longitude) {
      try {
        const response = await api.post('/geo-fence/check', { agentId, latitude, longitude });
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getLogs(agentId) {
      try {
        const response = await api.get(`/geo-fence/logs/agent/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getAllLogs() {
      try {
        const response = await api.get('/geo-fence/logs');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  },

  // Admin APIs
  admin: {
    async getStats() {
      try {
        const response = await api.get('/admin/statistics');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getMarts() {
      try {
        const response = await api.get('/admin/marts');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getMart(id) {
      try {
        const response = await api.get(`/admin/marts/${id}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async createMart(mart) {
      try {
        const response = await api.post('/admin/marts', mart);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async updateMart(id, mart) {
      try {
        const response = await api.put(`/admin/marts/${id}`, mart);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async deleteMart(id) {
      try {
        const response = await api.delete(`/admin/marts/${id}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async reactivateMart(id) {
      try {
        const response = await api.patch(`/admin/marts/${id}/reactivate`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async toggleGeoFence(id, enabled) {
      try {
        const response = await api.patch(`/admin/marts/${id}/toggle-geofence?enabled=${enabled}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  },

  // Notifications APIs
  // getSales/getHR/getAgent return a page envelope: { content, page, size,
  // totalElements, totalPages, hasNext } — not a raw array. Pass page/size to
  // fetch further pages (defaults: page 0, size 20).
  notifications: {
    async getSales(page = 0, size = 20) {
      try {
        const response = await api.get(`/notifications/sales?page=${page}&size=${size}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getHR(page = 0, size = 20) {
      try {
        const response = await api.get(`/notifications/hr?page=${page}&size=${size}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getAgent(agentId, page = 0, size = 20) {
      try {
        const response = await api.get(`/notifications/agent/${agentId}?page=${page}&size=${size}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async markAsRead(id) {
      try {
        const response = await api.patch(`/notifications/${id}/read`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async delete(id) {
      try {
        const response = await api.delete(`/notifications/${id}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  },

  // Reports APIs
  // NOTE: these return relative API paths (not full URLs) — they're meant to be passed
  // to downloadAndShareFile() (utils/downloadAndShareFile.js), which fetches them through
  // the authenticated axios instance. Do NOT pass these to Linking.openURL() — that opens
  // the system browser with no Authorization header and the backend will reject it.
  reports: {
    getExportPath(date, agentId, year, month) {
      const params = [];
      if (date) params.push(`date=${date}`);
      if (agentId) params.push(`agentId=${agentId}`);
      if (year) params.push(`year=${year}`);
      if (month) params.push(`month=${month}`);
      return '/reports/export' + (params.length > 0 ? '?' + params.join('&') : '');
    },
    getAgentExportPath(agentId, startDate, endDate) {
      const params = [];
      if (startDate) params.push(`startDate=${startDate}`);
      if (endDate) params.push(`endDate=${endDate}`);
      return `/reports/export/agent/${agentId}` + (params.length > 0 ? '?' + params.join('&') : '');
    },
    getHrAgentAttendanceCsvPath(agentId, from, to) {
      return `/reports/hr/agent-attendance-csv?agentId=${agentId}&from=${from}&to=${to}`;
    },
    getSalesAgentSalesCsvPath(agentId, from, to) {
      return `/reports/sales/agent-sales-csv?agentId=${agentId}&from=${from}&to=${to}`;
    }
  },

  // Sales & HR Dashboard APIs
  sales: {
    async getProducts() {
      try {
        const response = await api.get('/sales/products-with-images');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async submitSales(salesRequest) {
      try {
        const response = await api.post('/sales/entry-with-images', salesRequest);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getAgentSales(agentId) {
      try {
        const response = await api.get(`/sales/agent-sales/${agentId}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getRealtimeDashboard() {
      try {
        const response = await api.get('/sales/dashboard/realtime');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getDailyReport(date) {
      try {
        const response = await api.get(`/sales/daily-report?date=${date}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getWeeklyReport(date) {
      try {
        const response = await api.get(`/sales/weekly-report?date=${date}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getMonthlyReport(date) {
      try {
        const response = await api.get(`/sales/monthly-report?date=${date}`);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getHRDashboard() {
      try {
        const response = await api.get('/hr/dashboard/attendance-sales');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getHRCompliance() {
      try {
        const response = await api.get('/hr/dashboard/compliance');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async getHRTopPerformers() {
      try {
        const response = await api.get('/hr/dashboard/top-performers');
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    },
    async overrideSales(saleId, request, reason, username) {
      try {
        const response = await api.put(`/sales/${saleId}/override?reason=${encodeURIComponent(reason)}&username=${encodeURIComponent(username)}`, request);
        return handleResponse(response);
      } catch (error) {
        return handleApiError(error);
      }
    }
  }
};

export default api;
