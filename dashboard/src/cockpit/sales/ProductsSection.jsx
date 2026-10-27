import React from 'react';
import { Box, Tooltip as MuiTooltip } from '@mui/material';
import ChartCard from '../components/ChartCard';
import { INK, MEANING } from '../tokens';
import { int, pct, rs, rsCompact, shortDate } from '../format';

const ABC = {
  A: { bg: '#E1EDFB', fg: '#0B3D7A', hint: 'Top 80% of sales value' },
  B: { bg: '#EEF1F6', fg: INK.primary, hint: 'Next 15%' },
  C: { bg: '#F7F9FC', fg: INK.secondary, hint: 'Last 5%' },
};

function AbcChip({ cls }) {
  const s = ABC[cls];
  return (
    <MuiTooltip title={`Class ${cls}: ${s.hint}`}>
      <Box component="span" sx={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 22, height: 22, borderRadius: '6px',
        bgcolor: s.bg, color: s.fg, fontSize: 11.5, fontWeight: 800, flexShrink: 0,
      }}>{cls}</Box>
    </MuiTooltip>
  );
}

/** Product mix as a ranked share list (works for 40 products where a donut can't), with ABC class + running total. */
function ProductMix({ rows, onDrill }) {
  const max = Math.max(...rows.map((r) => r.sharePercent), 1);
  return (
    <Box sx={{ maxHeight: 420, overflowY: 'auto', pr: 0.5 }}>
      {rows.map((r) => (
        <MuiTooltip key={r.productId} placement="top" title={`${int(r.soldUnits)} units sold · ${rs(r.saleValue)} · running total ${pct(r.cumulativePercent)}`}>
          <Box role="button" tabIndex={0} className="cockpit-clickable"
            onClick={() => onDrill(r)} onKeyDown={(e) => { if (e.key === 'Enter') onDrill(r); }}
            sx={{
              display: 'grid', gridTemplateColumns: '22px minmax(0, 1.1fr) minmax(0, 1fr) 70px 56px', gap: 1.25, alignItems: 'center',
              py: 0.7, px: 1, borderRadius: '10px', cursor: 'pointer', '&:hover': { bgcolor: '#F4F6FA' },
            }}>
            <AbcChip cls={r.abcClass} />
            <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.name}</Box>
            <Box sx={{ height: 10, bgcolor: '#EEF1F6', borderRadius: 999 }}>
              <Box sx={{ height: '100%', width: `${(r.sharePercent / max) * 100}%`, bgcolor: MEANING.sales, borderRadius: 999, transition: 'width 600ms ease' }} />
            </Box>
            <Box className="cockpit-num" sx={{ fontSize: 12.5, textAlign: 'right', color: INK.secondary }}>{rsCompact(r.saleValue)}</Box>
            <Box className="cockpit-num" sx={{ fontSize: 13, fontWeight: 700, textAlign: 'right', color: INK.primary }}>{pct(r.sharePercent)}</Box>
          </Box>
        </MuiTooltip>
      ))}
    </Box>
  );
}

function SlowList({ rows }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map((r) => (
        <Box key={r.productId} sx={{
          display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto', gap: 1, alignItems: 'center', py: 0.9, px: 1,
          '&:not(:last-of-type)': { borderBottom: `1px solid ${INK.grid}` },
        }}>
          <Box sx={{ minWidth: 0 }}>
            <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.name}</Box>
            <Box sx={{ fontSize: 11.5, color: r.lastSold ? INK.muted : MEANING.alert, fontWeight: r.lastSold ? 400 : 700 }}>
              {r.lastSold ? `Last sold ${shortDate(r.lastSold)}` : 'Never sold'}
            </Box>
          </Box>
          <Box className="cockpit-num" sx={{ fontSize: 13, fontWeight: 700, textAlign: 'right', color: INK.primary }}>
            {int(r.soldUnits)} <Box component="span" sx={{ fontSize: 11.5, color: INK.muted, fontWeight: 500 }}>units</Box>
          </Box>
        </Box>
      ))}
    </Box>
  );
}

export default function ProductsSection({ query, onDrill }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();
  const counts = d ? ['A', 'B', 'C'].map((c) => d.products.filter((p) => p.abcClass === c).length) : [0, 0, 0];

  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 2fr) minmax(0, 1fr)' } }} className="cockpit-grid-print-2">
      <ChartCard
        title="Product mix and ABC classes"
        hint={d && d.products.length ? `Share of ${rs(d.totalSaleValue)} sold · A: ${counts[0]} products make the top 80% · B: ${counts[1]} · C: ${counts[2]} · click for vouchers` : 'Share of sales value'}
        loading={loading} error={error} onRetry={retry} height={300}
        empty={!d?.products?.length} emptyTitle="No products sold" emptyMessage="Nothing was sold in this period."
      >
        {d && <ProductMix rows={d.products} onDrill={(r) => onDrill({ title: r.name, subtitle: `${pct(r.sharePercent)} of sales · class ${r.abcClass}`, extra: { productId: r.productId } })} />}
      </ChartCard>
      <ChartCard
        title="Slow products"
        hint="Active products with the fewest units sold in this period"
        loading={loading} error={error} onRetry={retry} height={300}
        empty={!d?.slowProducts?.length} emptyTitle="No active products" emptyMessage="There are no active products."
      >
        {d && <SlowList rows={d.slowProducts} />}
      </ChartCard>
    </Box>
  );
}
