import React from 'react';
import { Box } from '@mui/material';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import ChartCard, { Legend, TooltipBox } from '../components/ChartCard';
import Card from '../components/Card';
import { reducedMotion } from '../components/chartStyle';
import usePrintMode, { PRINT_WIDTH } from '../components/usePrintMode';
import { EmptyState } from '../components/States';
import { INK, MEANING, STOCK } from '../tokens';
import { int, pct } from '../format';

const PARTS = [
  { key: 'sold', label: 'Sold', color: STOCK.sold },
  { key: 'returned', label: 'Returned', color: STOCK.returned },
  { key: 'unsold', label: 'Unsold', color: STOCK.unsold },
  { key: 'replaced', label: 'Replaced (RBE)', color: STOCK.replaced },
  { key: 'missing', label: 'Missing', color: STOCK.missing },
];

/** Where each LMT salesman's opening stock went, over reconciled days. */
function StockBars({ rows }) {
  const printing = usePrintMode();
  return (
    <ResponsiveContainer width={printing ? PRINT_WIDTH.full : '100%'} height={Math.max(rows.length * 34 + 20, 160)}>
      <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }} barCategoryGap={8}>
        <XAxis type="number" hide />
        <YAxis type="category" dataKey="name" width={150} tickLine={false} axisLine={false} interval={0}
          tick={({ x, y, payload }) => (
            <text x={x - 6} y={y} dy={4} textAnchor="end" fill={INK.primary} fontSize={12}>
              {payload.value && payload.value.length > 20 ? `${payload.value.slice(0, 19)}…` : payload.value}
            </text>
          )} />
        <Tooltip cursor={{ fill: 'rgba(14,42,99,0.05)' }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const r = payload[0].payload;
            return (
              <TooltipBox title={r.name}
                rows={[{ label: 'Opening stock', value: `${int(r.opening)} units` },
                  ...PARTS.map((p) => ({ label: p.label, value: `${int(r[p.key])} · ${pct(r.opening ? (r[p.key] * 100) / r.opening : 0, 0)}`, color: p.color }))]}
                footer={`${int(r.stockDays)} reconciled day${r.stockDays === 1 ? '' : 's'}`} />
            );
          }} />
        {PARTS.map((p, i) => (
          <Bar key={p.key} dataKey={p.key} stackId="s" fill={p.color} maxBarSize={18} stroke="#fff" strokeWidth={1}
            radius={i === PARTS.length - 1 ? [0, 4, 4, 0] : 0} isAnimationActive={!reducedMotion} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}

/** One 100% bar: stock days that were OK, short, or never reconciled. */
function HealthBar({ health }) {
  const total = health.okDays + health.shortageDays + health.openDays;
  const parts = [
    { key: 'okDays', label: 'OK', color: MEANING.ok, value: health.okDays },
    { key: 'shortageDays', label: 'Shortage', color: MEANING.alert, value: health.shortageDays },
    { key: 'openDays', label: 'Not reconciled', color: '#D97706', value: health.openDays },
  ];
  if (!total) return <EmptyState height={120} title="No stock days" message="No LMT stock was entered in this period." />;
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', height: 18, borderRadius: 999, overflow: 'hidden', gap: '2px' }} role="img"
        aria-label={parts.map((p) => `${p.label} ${p.value}`).join(', ')}>
        {parts.filter((p) => p.value > 0).map((p) => (
          <Box key={p.key} sx={{ width: `${(p.value / total) * 100}%`, bgcolor: p.color }} />
        ))}
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1 }}>
        {parts.map((p) => (
          <Box key={p.key}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, fontSize: 12, color: INK.secondary }}>
              <Box component="span" sx={{ width: 10, height: 10, borderRadius: '3px', bgcolor: p.color }} />{p.label}
            </Box>
            <Box className="cockpit-num" sx={{ fontSize: 20, fontWeight: 700, color: INK.primary }}>
              {int(p.value)} <Box component="span" sx={{ fontSize: 12, color: INK.muted, fontWeight: 500 }}>day{p.value === 1 ? '' : 's'} · {pct((p.value * 100) / total, 0)}</Box>
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

export default function LmtStockSection({ query }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();

  if (d && !d.applicable) {
    return (
      <Card>
        <EmptyState height={140} title="Not used by Local salesmen" message="LMT stock only applies to LMT salesmen. Switch the filter to All or LMT." />
      </Card>
    );
  }
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 2fr) minmax(0, 1fr)' } }} className="cockpit-grid-print-2">
      <ChartCard
        title="Where the opening stock went"
        hint="Per LMT salesman, reconciled days · most missing first · hover for units and %"
        loading={loading} error={error} onRetry={retry} height={260}
        empty={!d?.bySalesman?.length} emptyTitle="No reconciled stock" emptyMessage="No LMT stock day was reconciled in this period."
        legend={<Legend items={PARTS.map((p) => ({ label: p.label, color: p.color }))} />}
      >
        {d && <StockBars rows={d.bySalesman} />}
      </ChartCard>
      <ChartCard title="Stock-day health" hint="Each salesman-day of LMT stock · today's open day is not counted"
        loading={loading} error={error} onRetry={retry} height={140} empty={false}>
        {d && <HealthBar health={d.health} />}
      </ChartCard>
    </Box>
  );
}
