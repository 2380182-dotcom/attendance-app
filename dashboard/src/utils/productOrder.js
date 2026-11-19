import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { productApi } from '../services/productApi';

/**
 * Dawn Bread's official price list order, for every product list and report
 * on the dashboard: the list's SR No. (product.sortOrder); products without
 * one come after it, by id. Never by name, category or price. Rankings
 * (top products, return %, slow products) keep their own value order.
 */
export function compareProducts(a, b) {
  const sa = a?.sortOrder ?? Number.MAX_SAFE_INTEGER;
  const sb = b?.sortOrder ?? Number.MAX_SAFE_INTEGER;
  if (sa !== sb) return sa - sb;
  return (a?.id ?? Number.MAX_SAFE_INTEGER) - (b?.id ?? Number.MAX_SAFE_INTEGER);
}

/**
 * Products (already in list order) with a heading wherever the family changes:
 * [{ type: 'heading', key, name }, { type: 'item', key, item }]. Products that are not on the
 * official list come last under one heading. No families = no headings.
 */
export function withFamilyHeadings(products, families) {
  const rows = [...(products || [])].sort(compareProducts);
  if (!families || families.length === 0) return rows.map((p) => ({ type: 'item', key: `p-${p.id}`, item: p }));
  const names = new Map(families.map((f) => [f.id, f.name]));
  const listed = rows.filter((p) => p.onPriceList !== false);
  const notListed = rows.filter((p) => p.onPriceList === false);
  const out = [];
  let current;
  const heading = (group, name) => {
    if (group !== current) {
      out.push({ type: 'heading', key: `h-${group}-${out.length}`, name });
      current = group;
    }
  };
  for (const p of listed) {
    const group = names.has(p.familyId) ? p.familyId : 'OTHER';
    heading(group, names.get(p.familyId) || 'Other');
    out.push({ type: 'item', key: `p-${p.id}`, item: p });
  }
  for (const p of notListed) {
    heading('NOT_LISTED', 'Not on the price list');
    out.push({ type: 'item', key: `p-${p.id}`, item: p });
  }
  return out;
}

/**
 * For report rows that only carry a product id and/or name (sales history, LMT reconciliation ...): a
 * comparator putting them in list order. Rows whose product is unknown go last, in their original order.
 * Uses the pricing list (Admin and Sales, the only dashboard users who see these reports).
 */
export function useProductOrder() {
  const pricing = useQuery({ queryKey: ['product-pricing'], queryFn: productApi.getPricing, staleTime: 5 * 60 * 1000 });
  return useMemo(() => {
    const rankById = new Map();
    const rankByName = new Map();
    (pricing.data || []).forEach((p, i) => {
      // The server sends the pricing list in list order already.
      rankById.set(p.id, i);
      if (p.name) rankByName.set(p.name.trim().toLowerCase(), i);
    });
    const rank = (productId, productName) => {
      if (productId != null && rankById.has(productId)) return rankById.get(productId);
      const n = (productName || '').trim().toLowerCase();
      return rankByName.has(n) ? rankByName.get(n) : Number.MAX_SAFE_INTEGER;
    };
    return {
      ready: pricing.isSuccess,
      rank,
      /** Sorts a copy of rows in list order; productOf(row) -> { id, name }. Stable for unknown products. */
      sort: (rows, productOf) => (rows || [])
        .map((r, i) => ({ r, i, k: rank(productOf(r)?.id, productOf(r)?.name) }))
        .sort((a, b) => (a.k - b.k) || (a.i - b.i))
        .map((x) => x.r),
    };
  }, [pricing.data, pricing.isSuccess]);
}
