import api from '../services/api';

function unwrap(response) {
  return response.data.data;
}

/** Only set filters are sent, so the server never receives empty values. */
export function filterParams(filters) {
  const params = { startDate: filters.startDate, endDate: filters.endDate, segment: filters.segment };
  if (filters.areaId) params.areaId = filters.areaId;
  if (filters.agentId) params.agentId = filters.agentId;
  return params;
}

export const salesCockpitApi = {
  async section(name, filters) {
    const response = await api.get(`/cockpit/sales/${name}`, { params: filterParams(filters), timeout: 30000 });
    return unwrap(response);
  },
  async filterOptions() {
    return unwrap(await api.get('/cockpit/sales/filter-options'));
  },
  /** Drill-down: the vouchers behind a clicked chart element. extra: { productId, noArea, page, size, startDate, endDate, segment, agentId, areaId } */
  async vouchers(filters, extra = {}) {
    const params = { ...filterParams({ ...filters, ...extra }), page: extra.page || 0, size: extra.size || 25 };
    if (extra.productId) params.productId = extra.productId;
    if (extra.noArea) params.noArea = true;
    return unwrap(await api.get('/cockpit/sales/vouchers', { params, timeout: 30000 }));
  },
};
