import React from 'react';
import { Box } from '@mui/material';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { INK, MEANING } from '../tokens';
import { STATE } from './hrFormat';

/** Shown wherever absence numbers would be while the company's absence tracking is off. */
export function TrackingOffNote({ children }) {
  return (
    <Box role="note" sx={{
      display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, borderRadius: '10px',
      bgcolor: '#F1F4F9', border: `1px solid ${INK.border}`, fontSize: 13, color: INK.secondary,
    }}>
      <InfoOutlinedIcon sx={{ fontSize: 18, color: INK.muted }} />
      <span>
        <strong style={{ color: INK.primary }}>Absence tracking is off</strong>
        {children ? <> · {children}</> : <> · an administrator can turn it on in Admin &gt; Holidays</>}
      </span>
    </Box>
  );
}

/** A state pill: coloured dot + label in text colour (never colour alone). */
export function StatePill({ state, children }) {
  const s = STATE[state] || { label: state, color: INK.muted };
  return (
    <Box component="span" sx={{
      display: 'inline-flex', alignItems: 'center', gap: 0.75, px: 1, py: 0.25, borderRadius: 999,
      bgcolor: `${s.color}1F`, color: INK.primary, fontSize: 12, fontWeight: 700, whiteSpace: 'nowrap',
    }}>
      <Box component="span" sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: s.color, flexShrink: 0 }} />
      {children || s.label}
    </Box>
  );
}

/**
 * One horizontal stacked bar for a group (department / mart): segments in
 * a fixed state order with 2px gaps, total on the right.
 */
export function StackRow({ name, counts, order, max, onClick, selected }) {
  const total = order.reduce((n, k) => n + (counts[k] || 0), 0);
  const tip = order.filter((k) => counts[k]).map((k) => `${STATE[k].label} ${counts[k]}`).join(' · ');
  return (
    <Box role={onClick ? 'button' : undefined} tabIndex={onClick ? 0 : undefined}
      onClick={onClick} onKeyDown={(e) => { if (onClick && e.key === 'Enter') onClick(); }}
      title={tip}
      aria-label={`${name}: ${tip || 'nobody'}`}
      sx={{
        display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.6fr) 36px', gap: 1.25, alignItems: 'center',
        py: 0.75, px: 1, borderRadius: '10px', cursor: onClick ? 'pointer' : 'default',
        bgcolor: selected ? '#EEF3FB' : 'transparent', '&:hover': onClick ? { bgcolor: '#F4F6FA' } : undefined,
        '&:focus-visible': { outline: `2px solid ${MEANING.sales}`, outlineOffset: 1 },
      }}>
      <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
        {name}
      </Box>
      <Box sx={{ display: 'flex', gap: '2px', height: 12, width: `${max ? Math.max((total / max) * 100, 2) : 0}%`, transition: 'width 500ms ease' }}>
        {order.map((k) => (counts[k] ? (
          <Box key={k} sx={{ flex: counts[k], bgcolor: STATE[k].color, borderRadius: '3px', minWidth: 3 }} />
        ) : null))}
      </Box>
      <Box className="cockpit-num" sx={{ fontSize: 13, fontWeight: 700, textAlign: 'right', color: INK.primary }}>{total}</Box>
    </Box>
  );
}
