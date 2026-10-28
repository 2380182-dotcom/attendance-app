import api from './api';

function unwrap(response) {
  return response.data.data;
}

/** Company holidays (Admin edits; HR can read). Dates are plain YYYY-MM-DD Pakistan dates. */
export const holidayApi = {
  async getAll() {
    return unwrap(await api.get('/holidays'));
  },
  async create(dto) {
    return unwrap(await api.post('/holidays', dto));
  },
  async update(id, dto) {
    return unwrap(await api.put(`/holidays/${id}`, dto));
  },
  async remove(id) {
    return unwrap(await api.delete(`/holidays/${id}`));
  },
};

/** Company-wide switches for the caller's company (Admin changes; HR can read). */
export const companySettingsApi = {
  async get() {
    return unwrap(await api.get('/company-settings'));
  },
  async setAbsenceTracking(on) {
    return unwrap(await api.put('/company-settings', { absenceTracking: on }));
  },
};

/** Saved absences (mart agents) for HR history. */
export const absenceApi = {
  async getRange(startDate, endDate, agentId) {
    const params = { startDate, endDate };
    if (agentId) params.agentId = agentId;
    return unwrap(await api.get('/hr/absences', { params }));
  },
};
