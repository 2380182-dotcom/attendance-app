import api from './api';

function unwrap(response) {
  return response.data.data; // ApiResponse envelope: { success, message, data }
}

export const hierarchyPersonApi = {
  /** Admin-only, includes inactive — management screens need to see and reactivate deactivated people. */
  async getAll() {
    const response = await api.get('/lmt/hierarchy-persons/all');
    return unwrap(response);
  },
  async create(dto) {
    const response = await api.post('/lmt/hierarchy-persons', dto);
    return unwrap(response);
  },
  async update(id, dto) {
    const response = await api.put(`/lmt/hierarchy-persons/${id}`, dto);
    return unwrap(response);
  },
  async deactivate(id) {
    const response = await api.delete(`/lmt/hierarchy-persons/${id}`);
    return unwrap(response);
  },
  async reactivate(id) {
    const response = await api.patch(`/lmt/hierarchy-persons/${id}/reactivate`);
    return unwrap(response);
  },
};

export const areaApi = {
  async getAll() {
    const response = await api.get('/lmt/areas/all');
    return unwrap(response);
  },
  async create(dto) {
    const response = await api.post('/lmt/areas', dto);
    return unwrap(response);
  },
  async update(id, dto) {
    const response = await api.put(`/lmt/areas/${id}`, dto);
    return unwrap(response);
  },
  async deactivate(id) {
    const response = await api.delete(`/lmt/areas/${id}`);
    return unwrap(response);
  },
  async reactivate(id) {
    const response = await api.patch(`/lmt/areas/${id}/reactivate`);
    return unwrap(response);
  },
};

export const customerShopApi = {
  async getAll() {
    const response = await api.get('/lmt/customer-shops/all');
    return unwrap(response);
  },
  async create(dto) {
    const response = await api.post('/lmt/customer-shops', dto);
    return unwrap(response);
  },
  async update(id, dto) {
    const response = await api.put(`/lmt/customer-shops/${id}`, dto);
    return unwrap(response);
  },
  async deactivate(id) {
    const response = await api.delete(`/lmt/customer-shops/${id}`);
    return unwrap(response);
  },
  async reactivate(id) {
    const response = await api.patch(`/lmt/customer-shops/${id}/reactivate`);
    return unwrap(response);
  },
  async getProductDiscounts(shopId) {
    const response = await api.get(`/lmt/customer-shops/${shopId}/product-discounts`);
    return unwrap(response);
  },
  async upsertProductDiscount(shopId, productId, discountPercent) {
    const response = await api.put(`/lmt/customer-shops/${shopId}/product-discounts/${productId}`, { discountPercent });
    return unwrap(response);
  },
  async removeProductDiscount(shopId, productId) {
    const response = await api.delete(`/lmt/customer-shops/${shopId}/product-discounts/${productId}`);
    return unwrap(response);
  },
};

export const lmtSettingsApi = {
  async get() {
    const response = await api.get('/lmt/settings');
    return unwrap(response);
  },
  async update(geofenceBufferMeters) {
    const response = await api.put('/lmt/settings', { geofenceBufferMeters });
    return unwrap(response);
  },
};
