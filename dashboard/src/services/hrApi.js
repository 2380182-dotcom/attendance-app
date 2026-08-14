import api from './api';

function unwrap(response) {
  return response.data.data; // ApiResponse envelope: { success, message, data }
}

export const hrApi = {
  /** Today's snapshot: checked-in/late/absent counts+percents, compliance tiers, top performers. */
  async getDashboard() {
    const response = await api.get('/hr/dashboard/attendance-sales');
    return unwrap(response);
  },

  async getAgent(agentId) {
    const response = await api.get(`/agents/${agentId}`);
    return unwrap(response);
  },

  async getFaceStatus(agentId) {
    const response = await api.get(`/face/status/${agentId}`);
    return unwrap(response);
  },
};
