import { useEffect, useMemo, useState } from 'react';
import { apiService } from '../services/api';
import { cachedFetch } from '../services/apiCache';
import { ALL, familyButtons, productRows } from '../utils/productFamilies';

const FAMILIES_CACHE_KEY = 'product-families';
const FAMILIES_CACHE_TTL_MS = 5 * 60 * 1000; // same as the product catalog's cache

/**
 * The product families (filter buttons), shared by every product list.
 * If they cannot be loaded (offline, an older server), the screen still
 * works: products stay in the list's order, without buttons or headings.
 */
export function useProductFamilies() {
  const [families, setFamilies] = useState([]);
  useEffect(() => {
    let alive = true;
    cachedFetch(FAMILIES_CACHE_KEY, () => apiService.sales.getProductFamilies(), FAMILIES_CACHE_TTL_MS)
      .then((data) => { if (alive) setFamilies(Array.isArray(data) ? data : []); })
      .catch(() => { if (alive) setFamilies([]); });
    return () => { alive = false; };
  }, []);
  return families;
}

/**
 * Everything a product-entry list needs: the families, the selected button (All by default), the buttons
 * with counts (of the switched-on products this screen lists, before any search) and the rows to render
 * (search applied inside the selected family; "All" with family headings).
 */
export function useFamilyFilter(products, searchQuery = '') {
  const families = useProductFamilies();
  const [selected, setSelected] = useState(ALL);
  const buttons = useMemo(() => familyButtons(products, families), [products, families]);
  // A family whose button disappeared (its products were switched off) falls back to All.
  const active = buttons.some((b) => b.key === selected) ? selected : ALL;
  const rows = useMemo(() => {
    const q = (searchQuery || '').trim().toLowerCase();
    const searched = q ? (products || []).filter((p) => (p.name || '').toLowerCase().includes(q)) : products;
    return productRows(searched, families, active);
  }, [products, families, active, searchQuery]);
  return { families, buttons, selected: active, setSelected, rows };
}
