/** Generic array sort by a key path, stable, ascending or descending. Non-mutating. */
export function sortRows(rows, key, direction = 'asc') {
  const sign = direction === 'desc' ? -1 : 1;
  return [...rows].sort((a, b) => {
    const av = a[key];
    const bv = b[key];
    if (av == null && bv == null) return 0;
    if (av == null) return -1 * sign;
    if (bv == null) return 1 * sign;
    if (typeof av === 'string') return sign * av.localeCompare(bv);
    return sign * (av - bv);
  });
}
