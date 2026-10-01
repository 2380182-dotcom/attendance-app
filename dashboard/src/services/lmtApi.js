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
  /** Task 3: assign/reassign (agentId) or unassign (agentId: null) a shop to an LMT salesman. Separate from update() — its "null means leave as-is" convention can't express an explicit unassign. */
  async assign(id, agentId) {
    const response = await api.put(`/lmt/customer-shops/${id}/assign`, { agentId });
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
  /** Explicit per-shop prices only — products absent from the result use the global salesman price. */
  async getProductPrices(shopId) {
    const response = await api.get(`/lmt/customer-shops/${shopId}/product-prices`);
    return unwrap(response);
  },
  /** entries: [{ productId, price }] — a null price removes that product's shop price. */
  async applyProductPrices(shopId, entries) {
    const response = await api.put(`/lmt/customer-shops/${shopId}/product-prices`, { prices: entries });
    return unwrap(response);
  },
};

export const lmtStockApi = {
  /** Management-only reconciliation report: one record per LMT per day, items carry sold/returned/unsold/missing + returnsByShop. */
  async getReconciliation(startDate, endDate, agentId) {
    const params = { startDate, endDate };
    if (agentId) params.agentId = agentId;
    const response = await api.get('/lmt/stock/reconciliation', { params });
    return unwrap(response);
  },
};

export const shopVisitApi = {
  /**
   * Task 3 — server-side paginated + filtered "QR / Shop Visits" report.
   * Admin-only now (was ADMIN/HR/SALES). Returns a PageResponse shape:
   * { content, page, size, totalElements, totalPages, hasNext }.
   */
  async getReport(startDate, endDate, { agentId, role, shopSearch, failedOnly, page = 0, size = 25 } = {}) {
    const params = { startDate, endDate, page, size };
    if (agentId) params.agentId = agentId;
    if (role) params.role = role;
    if (shopSearch) params.shopSearch = shopSearch;
    if (failedOnly) params.failedOnly = true;
    const response = await api.get('/lmt/shop-visits', { params });
    return unwrap(response);
  },
  /** One salesman's visits for one day, plus the total/successful/failed/unique-shops counts. */
  async getDaySummary(agentId, date) {
    const params = { agentId };
    if (date) params.date = date;
    const response = await api.get('/lmt/shop-visits/summary', { params });
    return unwrap(response);
  },
  /** Task 3 "Not Visited" tab, paginated. role is required: 'SALESMAN_LMT' (assigned outlets not scanned) or 'SALESMAN_LOCAL' (every active unscanned shop). */
  async getNotVisited(date, role, { agentId, shopSearch, page = 0, size = 25 } = {}) {
    const params = { date, role, page, size };
    if (agentId) params.agentId = agentId;
    if (shopSearch) params.shopSearch = shopSearch;
    const response = await api.get('/lmt/shop-visits/not-visited', { params });
    return unwrap(response);
  },
  /** Task 4 correction: active shops with no salesman assigned at all, not scanned on this date — not role-scoped, same list under both Local and LMT tabs. */
  async getUnassignedNotScanned(date, { shopSearch, page = 0, size = 25 } = {}) {
    const params = { date, page, size };
    if (shopSearch) params.shopSearch = shopSearch;
    const response = await api.get('/lmt/shop-visits/unassigned-not-scanned', { params });
    return unwrap(response);
  },
  /** Task 3 summary counts (Total Shops / Visited / Not Visited / Voucher-Without-Scan) for one date + role. */
  async getSummaryCounts(date, role, agentId) {
    const params = { date, role };
    if (agentId) params.agentId = agentId;
    const response = await api.get('/lmt/shop-visits/summary-counts', { params });
    return unwrap(response);
  },
  /** Task 3 "voucher without scan": a SalesRecord exists for this agent/shop/day but no successful QR scan does. */
  async getVouchersWithoutScan(startDate, endDate, { agentId, role, page = 0, size = 25 } = {}) {
    const params = { startDate, endDate, page, size };
    if (agentId) params.agentId = agentId;
    if (role) params.role = role;
    const response = await api.get('/lmt/shop-visits/vouchers-without-scan', { params });
    return unwrap(response);
  },
};

export const salesVoucherApi = {
  /** Task 4 — section is 'local' or 'lmt'. Shop-list page: every total is a database aggregate. */
  async getShops(section, { shopSearch, page = 0, size = 25 } = {}) {
    const params = { page, size };
    if (shopSearch) params.shopSearch = shopSearch;
    const response = await api.get(`/sales/vouchers/${section}/shops`, { params });
    return unwrap(response);
  },
  /** A shop's paginated voucher list within a section. */
  async getVouchersForShop(section, shopId, { page = 0, size = 25 } = {}) {
    const params = { page, size };
    const response = await api.get(`/sales/vouchers/${section}/shops/${shopId}`, { params });
    return unwrap(response);
  },
  /** Single voucher detail, independent of section (the id alone is enough; tenant-checked server-side). */
  async getVoucherDetail(voucherId) {
    const response = await api.get(`/sales/vouchers/${voucherId}`);
    return unwrap(response);
  },
  /** Downloads the server-generated PDF as a Blob for the caller to save/open. */
  async getVoucherPdfBlob(voucherId) {
    const response = await api.get(`/sales/vouchers/${voucherId}/pdf`, { responseType: 'blob' });
    return response.data;
  },
};

export const lmtSettingsApi = {
  async get() {
    const response = await api.get('/lmt/settings');
    return unwrap(response);
  },
  /** dto: { geofenceBufferMeters, geofenceMode?, qrMode? } — geofenceMode/qrMode are "PER_SHOP" | "FORCE_ON" | "FORCE_OFF"; omit to leave a mode unchanged. */
  async update(dto) {
    const response = await api.put('/lmt/settings', dto);
    return unwrap(response);
  },
};
