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
        discountPercent: item?.discountPercent ?? '',
        lineTotal: item?.totalPrice ?? '',
        saleTotalAmount: record.totalAmount ?? '',
      });
    }
  }
  return rows;
}

/**
 * Groups LMT shop-visit records (SalesDTO-shaped, each with a customerShop*
 * and an `items` array of SALE/RETURN/UNSOLD lines) into one row per shop,
 * each carrying a per-product breakdown. Discount PKR is what the shop
 * saved on SALE lines: (unitPrice x qty) - totalPrice. UNSOLD lines are
 * day-level, not per-shop, so they're skipped here.
 */
export function aggregateByShop(records) {
  const shops = new Map();
  for (const record of records) {
    if (record.customerShopId == null) continue;
    let shop = shops.get(record.customerShopId);
    if (!shop) {
      shop = {
        shopId: record.customerShopId,
        shopCode: record.customerShopCode,
        shopName: record.customerShopName,
        visits: 0,
        unitsSold: 0,
        unitsReturned: 0,
        discountAmount: 0,
        revenue: 0,
        productMap: new Map(),
      };
      shops.set(record.customerShopId, shop);
    }
    shop.visits += 1;
    for (const item of record.items || []) {
      const type = item.transactionType || 'SALE';
      if (type === 'UNSOLD') continue;
      const key = item.productId ?? item.productName;
      let product = shop.productMap.get(key);
      if (!product) {
        product = { productId: item.productId, productName: item.productName, sold: 0, returned: 0, discountPercent: 0, discountAmount: 0, revenue: 0 };
        shop.productMap.set(key, product);
      }
      const qty = item.quantity ?? 0;
      if (type === 'RETURN') {
        product.returned += qty;
        shop.unitsReturned += qty;
      } else {
        const lineTotal = item.totalPrice ?? 0;
        const discount = Math.max(0, (item.unitPrice ?? 0) * qty - lineTotal);
        product.sold += qty;
        product.revenue += lineTotal;
        product.discountAmount += discount;
        if (item.discountPercent) product.discountPercent = item.discountPercent;
        shop.unitsSold += qty;
        shop.revenue += lineTotal;
        shop.discountAmount += discount;
      }
    }
  }
  return Array.from(shops.values()).map(({ productMap, ...shop }) => ({
    ...shop,
    products: Array.from(productMap.values()).sort((a, b) => b.revenue - a.revenue),
  }));
}
