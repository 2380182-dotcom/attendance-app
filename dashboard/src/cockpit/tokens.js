/**
 * Cockpit design tokens — one fixed colour per MEANING, used identically in
 * cards, charts and legends. Chart hues were checked with the dataviz
 * palette validator (lightness band, chroma, colour-blind separation,
 * contrast vs. white): sales/returns/RBE and Local/LMT both pass.
 *
 * BRAND_RED is the mobile app's top-bar colour (mobile/src/theme.js
 * `primary`), so web and app match. ALERT red is deliberately a different
 * red — the app does the same, so "brand" and "something failed" never
 * look identical.
 */
export const BRAND_RED = '#E30613';

export const MEANING = {
  sales: '#0057B8',    // brand blue
  returns: '#E8710A',
  rbe: '#7C3AED',      // return before expiry
  alert: '#C62828',    // missing / needs action
  ok: '#16A34A',       // healthy
  neutral: '#64748B',
};

export const SEGMENT = {
  LOCAL: '#009C8E',
  LMT: '#4338CA',
  OTHER: '#94A3B8',    // old mart-agent vouchers
};

export const SEGMENT_LABEL = { LOCAL: 'Local', LMT: 'LMT', OTHER: 'Mart agents' };

export const INK = {
  primary: '#0F172A',
  secondary: '#5B6577',
  muted: '#8A93A6',
  grid: '#E9EDF3',
  surface: '#FFFFFF',
  page: '#F3F5F9',
  border: '#E3E8F0',
};

export const FONT_UI = '"Manrope Variable", "Segoe UI", system-ui, sans-serif';
export const FONT_NUM = '"IBM Plex Mono", ui-monospace, "SFMono-Regular", Menlo, monospace';

export const HEADER_GRADIENT = 'linear-gradient(120deg, #081634 0%, #0E2A63 45%, #1A4FAE 100%)';

export const CARD = {
  radius: 16,
  shadow: '0 1px 2px rgba(15, 23, 42, 0.04), 0 6px 18px rgba(15, 23, 42, 0.06)',
};

/** Sticky offsets: the app bar is 64px (56px on phones). */
export const APPBAR = { xs: 56, sm: 64 };

/** Feedback reasons (categorical — always shown with a labelled legend and counts). */
export const REASON = {
  SHOP_CLOSED: '#475569',
  STOCK_FULL: '#0891B2',
  OWNER_NOT_AVAILABLE: '#D97706',
  OTHER: '#DB2777',
};
export const REASON_LABEL = {
  SHOP_CLOSED: 'Shop closed',
  STOCK_FULL: 'Stock full',
  OWNER_NOT_AVAILABLE: 'Owner not available',
  OTHER: 'Other',
};

/** LMT stock outcomes — same meaning colours as everywhere; unsold is neutral. */
export const STOCK = {
  sold: '#0057B8',
  returned: '#E8710A',
  unsold: '#94A3B8',
  replaced: '#7C3AED',
  missing: '#C62828',
};

/** Heatmap cell states. */
export const CELL = {
  SCAN: '#16A34A',
  FEEDBACK: '#86EFAC',
  NONE: '#FBEAEA',
  FUTURE: '#F1F5F9',
};
