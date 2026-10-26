import React from 'react';
import { Box } from '@mui/material';
import Card from './Card';
import { ChartSkeleton, EmptyState, ErrorState } from './States';
import { INK } from '../tokens';

/**
 * A chart's frame: title + hint, then exactly one of skeleton / error /
 * friendly empty state / the chart, then its legend underneath.
 */
export default function ChartCard({
  title, hint, loading, error, onRetry, empty, emptyTitle, emptyMessage, height = 280, legend, children, sx,
}) {
  let body;
  if (loading) body = <ChartSkeleton height={height} />;
  else if (error) body = <ErrorState height={height} onRetry={onRetry} />;
  else if (empty) body = <EmptyState height={height} title={emptyTitle} message={emptyMessage} />;
  else body = <Box sx={{ width: '100%', minHeight: height }}>{children}</Box>;

  return (
    <Card sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, ...sx }}>
      <Box>
        <Box component="h3" sx={{ m: 0, fontSize: 15, fontWeight: 800, color: INK.primary }}>{title}</Box>
        {hint && <Box sx={{ fontSize: 12.5, color: INK.muted, mt: 0.25 }}>{hint}</Box>}
      </Box>
      {body}
      {!loading && !error && !empty && legend}
    </Card>
  );
}

/** Legend under a chart: a coloured key beside text in text colour (never coloured text). */
export function Legend({ items }) {
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'center', fontSize: 12.5, color: INK.secondary }}>
      {items.map((it) => (
        <Box key={it.label} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
          {it.line ? (
            <Box component="span" sx={{ width: 16, height: 2, bgcolor: it.color, borderRadius: 2 }} />
          ) : (
            <Box component="span" sx={{ width: 10, height: 10, bgcolor: it.color, borderRadius: '3px' }} />
          )}
          <span>{it.label}</span>
          {it.value && <Box component="span" className="cockpit-num" sx={{ color: INK.primary, fontWeight: 600 }}>{it.value}</Box>}
        </Box>
      ))}
    </Box>
  );
}

/** Dark tooltip used by every chart. rows: [{ label, value, color }] */
export function TooltipBox({ title, rows, footer }) {
  return (
    <Box sx={{
      bgcolor: '#0F172A', color: '#fff', borderRadius: '10px', px: 1.5, py: 1, fontSize: 12.5,
      boxShadow: '0 8px 24px rgba(15,23,42,0.25)', minWidth: 160,
    }}>
      {title && <Box sx={{ fontWeight: 700, mb: 0.5 }}>{title}</Box>}
      {rows.map((r) => (
        <Box key={r.label} sx={{ display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'space-between' }}>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, color: 'rgba(255,255,255,0.78)' }}>
            {r.color && <Box component="span" sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: r.color }} />}
            {r.label}
          </Box>
          <span className="cockpit-num">{r.value}</span>
        </Box>
      ))}
      {footer && <Box sx={{ mt: 0.75, color: 'rgba(255,255,255,0.6)', fontSize: 11.5 }}>{footer}</Box>}
    </Box>
  );
}
