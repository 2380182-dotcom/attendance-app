import api from './api';

function unwrap(response) {
  return response.data.data; // ApiResponse envelope: { success, message, data }
}

/** Only set filters are sent, so the server never receives empty values. */
export function routesParams({ startDate, endDate, agentId, type, area, minDaysSince, sort, dir }) {
  const params = { startDate, endDate };
  if (agentId) params.agentId = agentId;
  if (type && type !== 'ALL') params.type = type;
  if (area) params.area = area;
  if (minDaysSince !== '' && minDaysSince != null) params.minDaysSince = minDaysSince;
  if (sort) {
    params.sort = sort;
    params.dir = dir || 'asc';
  }
  return params;
}

/** Sales > Routes: assigned-shop coverage for a day or a range (at most 92 days). */
export const routesApi = {
  async summary(startDate, endDate) {
    return unwrap(await api.get('/routes/summary', { params: { startDate, endDate }, timeout: 30000 }));
  },
  async notVisited(filters, page, size) {
    return unwrap(await api.get('/routes/not-visited', { params: { ...routesParams(filters), page, size }, timeout: 30000 }));
  },
  async unassigned(startDate, endDate) {
    return unwrap(await api.get('/routes/unassigned', { params: { startDate, endDate }, timeout: 30000 }));
  },
  /**
   * The current filtered + sorted not-visited list as an .xlsx Blob. The file name is built here (same as
   * the server's): the dashboard is on another domain, where the Content-Disposition header is not readable.
   */
  async exportNotVisited(filters) {
    const response = await api.get('/routes/not-visited/export', {
      params: routesParams(filters), responseType: 'blob', timeout: 60000,
    });
    const { startDate, endDate } = filters;
    const fileName = `routes-not-visited-${startDate}${startDate === endDate ? '' : `_to_${endDate}`}.xlsx`;
    return { blob: response.data, fileName };
  },
};
