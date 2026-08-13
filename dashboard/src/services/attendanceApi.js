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
  async getActive() {
    const response = await api.get('/agents/active');
    return unwrap(response);
  },
};
