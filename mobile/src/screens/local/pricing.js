/**
 * Display-only price maths for the local salesman's entry screen. The
 * server (SalesService.submitShopVisit) independently computes the real
 * amounts at save time and is the only authority — these numbers exist so
 * the salesman sees the same figures he will be charged, live, as he taps.
 * They mirror the server's rules exactly:
 *   base price  = this shop's price for the product, else the global salesmanPrice
 *   discount %  = this shop's per-product override, else the shop's overall %, else 0
 *   SALE line   = base x qty x (1 - discount%)
 *   RETURN line = base x qty, never discounted
 */

export function getBasePrice(product, shopPrices) {
  const override = (shopPrices || []).find((p) => p.productId === product.id);
  return override ? override.price : product.salesmanPrice ?? 0;
}

export function getDiscountPercent(product, shop, shopDiscounts) {
  const override = (shopDiscounts || []).find((d) => d.productId === product.id);
  if (override) return override.discountPercent ?? 0;
  return shop?.discountPercent ?? 0;
}

/** The price shown next to each bread: after discount for a sale, plain for a return. */
export function getFinalUnitPrice(product, mode, shop, shopPrices, shopDiscounts) {
  const base = getBasePrice(product, shopPrices);
  if (mode === 'RETURN') return base;
  return base * (1 - getDiscountPercent(product, shop, shopDiscounts) / 100);
}

/** { totalBreads, totalRs, lines: [{ product, quantity, unitPrice, lineTotal }] } for every product with quantity > 0. */
export function computeTotals(products, quantities, mode, shop, shopPrices, shopDiscounts) {
  const lines = [];
  let totalBreads = 0;
  let totalRs = 0;
  (products || []).forEach((product) => {
    const quantity = quantities[product.id] || 0;
    if (quantity <= 0) return;
    const unitPrice = getFinalUnitPrice(product, mode, shop, shopPrices, shopDiscounts);
    const lineTotal = unitPrice * quantity;
    lines.push({ product, quantity, unitPrice, lineTotal });
    totalBreads += quantity;
    totalRs += lineTotal;
  });
  return { totalBreads, totalRs, lines };
}

/** "90", "90.5", "1,250" — whole numbers without decimals, otherwise up to 2. */
export function formatRs(amount) {
  const rounded = Math.round((amount + Number.EPSILON) * 100) / 100;
  return Number.isInteger(rounded)
    ? rounded.toLocaleString('en-US')
    : rounded.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
