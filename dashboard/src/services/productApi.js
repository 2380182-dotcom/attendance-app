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
  /** The product families (the app's filter buttons), in button order. */
  async getFamilies() {
    return unwrap(await api.get('/sales/product-families'));
  },
  /** Admin only: { familyId | null, sortOrder | null, onPriceList }. sortOrder = the price list's SR No. */
  async setPlacement(id, placement) {
    return unwrap(await api.put(`/products/${id}/placement`, placement));
  },
  /** Admin only: add (no id) or change a family: { name, nameUr, sortOrder }. */
  async saveFamily(id, family) {
    return unwrap(id ? await api.put(`/products/families/${id}`, family) : await api.post('/products/families', family));
  },
  /** Admin only; refused while the family still has products. */
  async deleteFamily(id) {
    return unwrap(await api.delete(`/products/families/${id}`));
  },
};
