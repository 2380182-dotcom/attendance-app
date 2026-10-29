import api from './api';

function unwrap(response) {
  return response.data.data; // ApiResponse envelope: { success, message, data }
}

export const productApi = {
  async getPricing() {
    const response = await api.get('/products/pricing');
    return unwrap(response);
  },
  async updatePricing(id, dto) {
    const response = await api.put(`/products/${id}/pricing`, dto);
    return unwrap(response);
  },
  /** Switch a product on (offered to salesmen and agents) or hide it. On needs both prices entered. */
  async setActive(id, active) {
    const response = await api.put(`/products/${id}/active`, { active });
    return unwrap(response);
  },
};
