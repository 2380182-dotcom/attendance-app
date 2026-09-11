import api from './api';

function unwrap(response) {
  return response.data.data; // ApiResponse envelope: { success, message, data }
}

export const attendanceApi = {
  /** Rich per-agent report (status, lateMinutes, faceVerified) for one day. Omit date for today. */
  async getDailyReport(date) {
    const response = await api.get('/attendance/daily-report', { params: date ? { date } : {} });
    return unwrap(response);
  },

  /** Plain attendance records across a date range, every agent. */
  async getDateRange(startDate, endDate) {
    const response = await api.get('/attendance/date-range', { params: { startDate, endDate } });
    return unwrap(response);
  },

  /** Plain attendance records across a date range, one agent. */
  async getAgentDateRange(agentId, startDate, endDate) {
    const response = await api.get(`/attendance/agent/${agentId}/date-range`, { params: { startDate, endDate } });
    return unwrap(response);
  },
};

export const agentApi = {
  /** Agents with attendance activity in the last 7 days — NOT the full roster, despite the name. */
  async getActive() {
    const response = await api.get('/agents/active');
    return unwrap(response);
  },

  /** Every agent row (all roles, all active states) — callers filter down to the roster they need. */
  async getAll() {
    const response = await api.get('/agents');
    return unwrap(response);
  },
};
