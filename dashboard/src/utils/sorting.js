import { useState } from 'react';

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

/** Sort state + toggle handler for a sortable table: click a column to sort by it, click again to flip direction. */
export function useSort(initialKey, initialDirection = 'desc') {
  const [sort, setSort] = useState({ key: initialKey, direction: initialDirection });
  const onSort = (key) => {
    setSort((prev) =>
      prev.key === key ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' } : { key, direction: 'asc' }
    );
  };
  return [sort, onSort];
}
