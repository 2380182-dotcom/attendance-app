const nf0 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });

/** (A no-break space keeps "Rs" and the number together everywhere.)
 * Full rupees with separators: "Rs 1,234,567". */
export function rs(value) {
  return `Rs\u00A0${nf0.format(Math.round(value || 0))}`;
}

/** Compact rupees for cards and axis ticks: Rs 3.4M, Rs 12.5K. */
export function rsCompact(value) {
  const v = value || 0;
  const abs = Math.abs(v);
  if (abs >= 1e6) return `Rs\u00A0${nf1.format(v / 1e6)}M`;
  if (abs >= 1e3) return `Rs\u00A0${nf1.format(v / 1e3)}K`;
  return `Rs\u00A0${nf0.format(v)}`;
}

export function compact(value) {
  const v = value || 0;
  const abs = Math.abs(v);
  if (abs >= 1e6) return `${nf1.format(v / 1e6)}M`;
  if (abs >= 1e3) return `${nf1.format(v / 1e3)}K`;
  return nf0.format(v);
}

export function int(value) {
  return nf0.format(Math.round(value || 0));
}

export function pct(value, digits = 1) {
  return `${(value || 0).toFixed(digits)}%`;
}

/** "2026-10-25" -> "25 Oct". */
export function shortDate(iso) {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

/** Server times are already Pakistan local time ("2026-10-25T14:05:31.2"). */
export function pkTime(isoLocal) {
  if (!isoLocal) return '';
  const [date, time] = isoLocal.split('T');
  const [h, min] = time.split(':').map(Number);
  const ampm = h >= 12 ? 'PM' : 'AM';
  const h12 = ((h + 11) % 12) + 1;
  return `${shortDate(date)}, ${h12}:${String(min).padStart(2, '0')} ${ampm}`;
}
