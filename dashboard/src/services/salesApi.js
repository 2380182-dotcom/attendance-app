import api from './api';

function unwrap(response) {
  return response.data.data; // ApiResponse envelope: { success, message, data }
}

export const salesApi = {
  /** Revenue, units, agents-checked-in-today, top products, per-agent leaderboard, revenue trend — one call. */
  async getRealtimeDashboard() {
    const response = await api.get('/sales/dashboard/realtime');
    return unwrap(response);
  },

  /** Full sales history for one agent (no server-side date filter — filter client-side). */
  async getAgentSales(agentId) {
    const response = await api.get(`/sales/agent-sales/${agentId}`);
    return unwrap(response);
  },

  /** Company-wide report anchored at a single date, spanning that day/week/month. */
  async getReport(period, date) {
    const response = await api.get(`/sales/reports/${period}`, { params: date ? { date } : {} });
    return unwrap(response);
  },
};
