import React from 'react';
import { Box, Tooltip as MuiTooltip } from '@mui/material';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import ChartCard, { Legend, TooltipBox } from '../components/ChartCard';
import { axisProps, gridProps, reducedMotion } from '../components/chartStyle';
import usePrintMode, { PRINT_WIDTH } from '../components/usePrintMode';
import { Grid } from '../components/Sections';
import { INK, MEANING } from '../tokens';
import { compact, int, pct, rs, shortDate } from '../format';

/**
 * A ranked list of return rates. Each row: name, a bar for return % (the
 * alert line from LMT Settings is drawn on every bar) and the % itself.
 * Rows above the alert line get a red "Above x%" mark, so the state never
 * depends on colour alone.
 */
function RateList({ rows, alertPercent, onDrill, emptyText }) {
  if (!rows.length) {
    return <Box sx={{ color: INK.secondary, fontSize: 13, py: 3, textAlign: 'center' }}>{emptyText}</Box>;
  }
  const max = Math.max(alertPercent * 1.5, ...rows.map((r) => r.returnPercent), 1);
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map((r) => {
        const above = r.returnPercent > alertPercent;
        return (
          <MuiTooltip key={r.id} placement="top"
            title={`Returns ${rs(r.returnValue)} of sales ${rs(r.saleValue)} · ${int(r.returnedUnits)} units returned`}>
            <Box role="button" tabIndex={0} className="cockpit-clickable"
              onClick={() => onDrill(r)} onKeyDown={(e) => { if (e.key === 'Enter') onDrill(r); }}
              sx={{
                display: 'grid', gridTemplateColumns: 'minmax(0, 1.2fr) minmax(0, 1fr) 64px', gap: 1.25,
                alignItems: 'center', py: 0.8, px: 1, borderRadius: '10px', cursor: 'pointer',
                '&:hover': { bgcolor: '#F4F6FA' },
              }}>
              <Box sx={{ minWidth: 0 }}>
                <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {r.name}
                </Box>
                {above && (
                  <Box sx={{ fontSize: 10.5, fontWeight: 800, letterSpacing: '0.06em', textTransform: 'uppercase', color: MEANING.alert }}>
                    Above {pct(alertPercent, 0)}
                  </Box>
                )}
              </Box>
              <Box sx={{ position: 'relative', height: 10, bgcolor: '#EEF1F6', borderRadius: 999 }}>
                <Box sx={{
                  height: '100%', width: `${Math.min((r.returnPercent / max) * 100, 100)}%`, borderRadius: 999,
                  bgcolor: MEANING.returns, transition: 'width 600ms ease',
                }} />
                <Box aria-hidden sx={{
                  position: 'absolute', top: -3, bottom: -3, width: 2, borderRadius: 1, bgcolor: MEANING.alert,
                  left: `${(alertPercent / max) * 100}%`,
                }} />
              </Box>
              <Box className="cockpit-num" sx={{ fontSize: 13, fontWeight: 700, textAlign: 'right', color: INK.primary }}>
                {pct(r.returnPercent)}
              </Box>
            </Box>
          </MuiTooltip>
        );
      })}
    </Box>
  );
}

function ReturnsOverTime({ daily, onDrill }) {
  const printing = usePrintMode();
  return (
    <ResponsiveContainer width={printing ? PRINT_WIDTH.full : '100%'} height={260}>
      <BarChart data={daily} margin={{ top: 12, right: 8, left: 4, bottom: 0 }} barGap={2}>
        <CartesianGrid {...gridProps} />
        <XAxis dataKey="date" tickFormatter={shortDate} {...axisProps} minTickGap={18} />
        <YAxis tickFormatter={compact} {...axisProps} axisLine={false} width={40} allowDecimals={false} />
        <Tooltip
          cursor={{ fill: 'rgba(14,42,99,0.05)' }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const d = payload[0].payload;
            return (
              <TooltipBox
                title={shortDate(d.date)}
                rows={[
                  { label: 'Expired returns', value: `${int(d.returnedUnits)} units`, color: MEANING.returns },
                  { label: 'Return before expiry', value: `${int(d.rbeUnits)} units`, color: MEANING.rbe },
                  { label: 'Return value', value: rs(d.returnValue) },
                ]}
                footer="Click for this day's vouchers"
              />
            );
          }}
        />
        <Bar dataKey="returnedUnits" name="Expired returns" fill={MEANING.returns} maxBarSize={18} radius={[4, 4, 0, 0]}
          isAnimationActive={!reducedMotion} onClick={(d) => onDrill(d.payload || d)} />
        <Bar dataKey="rbeUnits" name="Return before expiry" fill={MEANING.rbe} maxBarSize={18} radius={[4, 4, 0, 0]}
          isAnimationActive={!reducedMotion} onClick={(d) => onDrill(d.payload || d)} />
      </BarChart>
    </ResponsiveContainer>
  );
}

export default function ReturnsSection({ query, onDrill }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();
  const alert = d?.alertPercent ?? 15;
  const hasDaily = d?.daily?.some((x) => x.returnedUnits > 0 || x.rbeUnits > 0);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <ChartCard
        title={d ? `Returns are ${pct(d.returnPercent)} of sales` : 'Returns over time'}
        hint={d ? `Returns ${rs(d.returnValue)} of sales ${rs(d.saleValue)} · units per day · return before expiry never changes money totals · click a day for its vouchers` : ''}
        loading={loading} error={error} onRetry={retry} height={260}
        empty={!hasDaily} emptyTitle="No returns in this period" emptyMessage="Nothing was returned or replaced."
        legend={<Legend items={[{ label: 'Expired returns', color: MEANING.returns }, { label: 'Return before expiry (replaced)', color: MEANING.rbe }]} />}
      >
        {d && (
          <ReturnsOverTime daily={d.daily} onDrill={(x) => onDrill({
            title: shortDate(x.date), subtitle: `${int(x.returnedUnits)} units returned · ${int(x.rbeUnits)} replaced`,
            extra: { startDate: x.date, endDate: x.date },
          })} />
        )}
      </ChartCard>

      <Grid cols={3} from="lg" className="cockpit-grid-print-2">
        {[
          { key: 'byShop', title: 'Return % by shop', hint: `Worst 15 shops · the red line is your ${pct(alert, 0)} alert · click a shop`, extra: (r) => ({ shopId: r.id }) },
          { key: 'bySalesman', title: 'Return % by salesman', hint: 'Worst 15 salesmen · click a name', extra: (r) => ({ agentId: r.id }) },
          { key: 'byProduct', title: 'Return % by product', hint: 'Worst 15 products · click a product', extra: (r) => ({ productId: r.id }) },
        ].map((c) => (
          <ChartCard key={c.key} title={c.title} hint={c.hint} loading={loading} error={error} onRetry={retry} height={300}
            empty={false}>
            {d && (
              <RateList rows={d[c.key]} alertPercent={alert} emptyText="No returns in this period."
                onDrill={(r) => onDrill({ title: r.name, subtitle: `Returns ${pct(r.returnPercent)} · ${rs(r.returnValue)}`, extra: c.extra(r) })} />
            )}
          </ChartCard>
        ))}
      </Grid>
    </Box>
  );
}
