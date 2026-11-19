/**
 * Dawn Bread's official price list: product order and family filter
 * buttons, shared by every screen that lists products.
 *
 * ORDER (the most important rule): products are ALWAYS shown in the list's
 * SR No. order (product.sortOrder, set by the server). Products without one
 * come after every placed product, by id. Never by name, category or price.
 *
 * FAMILIES: one filter button per family that has at least one product on
 * the screen, plus "All". Only switched-on products reach the app, so a
 * family with no product switched on has no button, and gets one again as
 * soon as a product in it is switched on. "All" shows every product under
 * family headings; products that are not on the official list come last,
 * under their own heading. Products with no family go under "Other".
 */

export const ALL = 'ALL';
export const OTHER = 'OTHER';
const NOT_LISTED = 'NOT_LISTED';

// Urdu words from Dawn Bread. Family names (and their Urdu) come from the
// server, where an Admin can change them without a new app.
export const FAMILY_STRINGS = {
  all: { en: 'All', ur: 'سب' },
  other: { en: 'Other', ur: 'دیگر' },
  notListed: { en: 'Not on the price list', ur: 'قیمت فہرست میں نہیں' },
};

/** SR No. order: sortOrder (missing = last), then id. */
export function compareProducts(a, b) {
  const sa = a?.sortOrder ?? Number.MAX_SAFE_INTEGER;
  const sb = b?.sortOrder ?? Number.MAX_SAFE_INTEGER;
  if (sa !== sb) return sa - sb;
  return (a?.id ?? 0) - (b?.id ?? 0);
}

export function sortProducts(products) {
  return [...(products || [])].sort(compareProducts);
}

/** Orders rows that carry a product id (stock lines, cart lines ...) by that product's list order. */
export function sortByProductOrder(rows, productOf) {
  return [...(rows || [])].sort((x, y) => compareProducts(productOf(x) || {}, productOf(y) || {}));
}

function familyKey(product, familyById) {
  return product?.familyId != null && familyById.has(product.familyId) ? product.familyId : OTHER;
}

/**
 * The filter buttons for these products: All first, then each family that has products, in the families'
 * button order, then Other if needed. Each button: { key, en, ur, count }.
 */
export function familyButtons(products, families) {
  const familyById = new Map((families || []).map((f) => [f.id, f]));
  const counts = new Map();
  for (const p of products || []) {
    const k = familyKey(p, familyById);
    counts.set(k, (counts.get(k) || 0) + 1);
  }
  const buttons = [{ key: ALL, en: FAMILY_STRINGS.all.en, ur: FAMILY_STRINGS.all.ur, count: (products || []).length }];
  const ordered = [...(families || [])].sort((a, b) => (a.sortOrder - b.sortOrder) || (a.id - b.id));
  for (const f of ordered) {
    if (counts.get(f.id)) buttons.push({ key: f.id, en: f.name, ur: f.nameUr || '', count: counts.get(f.id) });
  }
  if (counts.get(OTHER)) {
    buttons.push({ key: OTHER, en: FAMILY_STRINGS.other.en, ur: FAMILY_STRINGS.other.ur, count: counts.get(OTHER) });
  }
  return buttons;
}

/** Keeps only the selected family's products ("All" keeps everything), in list order. */
export function filterByFamily(products, families, selected) {
  const sorted = sortProducts(products);
  if (selected === ALL || selected == null) return sorted;
  const familyById = new Map((families || []).map((f) => [f.id, f]));
  return sorted.filter((p) => familyKey(p, familyById) === selected);
}

/**
 * Products in list order with a heading row wherever the family changes, for a FlatList:
 * [{ type: 'heading', key, en, ur }, { type: 'item', key, item }, ...]. Products not on the official list
 * come last under one heading; productOf picks the product out of a row (default: the row itself).
 */
export function withFamilyHeadings(rows, families, productOf = (r) => r, keyOf = (r) => productOf(r)?.id) {
  // Families not loaded (offline, old server): the list order still holds, just without headings.
  if (!families || families.length === 0) {
    return sortByProductOrder(rows, productOf).map((r) => ({ type: 'item', key: `item-${keyOf(r)}`, item: r }));
  }
  const familyById = new Map((families || []).map((f) => [f.id, f]));
  const sorted = sortByProductOrder(rows, productOf);
  const listed = sorted.filter((r) => productOf(r)?.onPriceList !== false);
  const notListed = sorted.filter((r) => productOf(r)?.onPriceList === false);
  const out = [];
  let current;
  const push = (group, en, ur) => {
    if (group !== current) {
      out.push({ type: 'heading', key: `heading-${group}-${out.length}`, en, ur });
      current = group;
    }
  };
  for (const r of listed) {
    const k = familyKey(productOf(r), familyById);
    const f = familyById.get(k);
    push(k, f ? f.name : FAMILY_STRINGS.other.en, f ? f.nameUr || '' : FAMILY_STRINGS.other.ur);
    out.push({ type: 'item', key: `item-${keyOf(r)}`, item: r });
  }
  for (const r of notListed) {
    push(NOT_LISTED, FAMILY_STRINGS.notListed.en, FAMILY_STRINGS.notListed.ur);
    out.push({ type: 'item', key: `item-${keyOf(r)}`, item: r });
  }
  return out;
}

/** The list a screen shows: the selected family (no headings), or "All" with headings. */
export function productRows(products, families, selected) {
  const shown = filterByFamily(products, families, selected);
  if (selected === ALL || selected == null) return withFamilyHeadings(shown, families);
  return shown.map((p) => ({ type: 'item', key: `item-${p.id}`, item: p }));
}
