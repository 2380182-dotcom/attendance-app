/** Aggregates a flat list of SaleItemDTO-shaped items into per-product totals, sorted by revenue desc. */
export function aggregateProductMix(items) {
  const byProduct = new Map();
  for (const item of items) {
    const key = item.productId ?? item.productName;
    const existing = byProduct.get(key);
    if (existing) {
      existing.quantity += item.quantity ?? 0;
      existing.revenue += item.totalPrice ?? 0;
    } else {
      byProduct.set(key, {
        productId: item.productId,
        productName: item.productName,
        quantity: item.quantity ?? 0,
        revenue: item.totalPrice ?? 0,
      });
    }
  }
  return Array.from(byProduct.values()).sort((a, b) => b.revenue - a.revenue);
}

/** Flattens sale records (each with an `items` array) into one row per line item, for CSV export. */
export function flattenSalesToLineItems(records, { getAgentName, getEmployeeId } = {}) {
  const rows = [];
  for (const record of records) {
    const agentName = getAgentName ? getAgentName(record) : record.agentName;
    const employeeId = getEmployeeId ? getEmployeeId(record) : record.employeeId;
    const items = record.items && record.items.length > 0 ? record.items : [null];
    for (const item of items) {
      rows.push({
        agentName: agentName ?? '',
        employeeId: employeeId ?? '',
        saleDate: record.saleDate ?? '',
        saleTime: record.saleTime ?? '',
        location: record.location ?? '',
        productName: item?.productName ?? '',
        quantity: item?.quantity ?? '',
        unitPrice: item?.unitPrice ?? '',
        lineTotal: item?.totalPrice ?? '',
        saleTotalAmount: record.totalAmount ?? '',
      });
    }
  }
  return rows;
}
