import React from 'react';
import { Box, Tooltip as MuiTooltip } from '@mui/material';
import {
  Bar, BarChart, Cell, ComposedChart, LabelList, Line, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import ChartCard, { Legend, TooltipBox } from '../components/ChartCard';
import { axisProps, gridProps, reducedMotion } from '../components/chartStyle';
import usePrintMode, { PRINT_WIDTH } from '../components/usePrintMode';
import { Grid } from '../components/Sections';
import { INK, MEANING, SEGMENT, SEGMENT_LABEL } from '../tokens';
import { compact, int, pct, rs, rsCompact, shortDate } from '../format';

const BAR = { maxBarSize: 24, radius: [4, 4, 0, 0] };
const HBAR = { maxBarSize: 18, radius: [0, 4, 4, 0] };
const AVG_LINE = '#0B2350';
const segOfRole = (role) => (role === 'SALESMAN_LOCAL' ? 'LOCAL' : role === 'SALESMAN_LMT' ? 'LMT' : 'OTHER');

function DailyChart({ daily, onDrill }) {
  const labelBars = daily.length <= 14;
  const printing = usePrintMode();
  return (
    <ResponsiveContainer width={printing ? PRINT_WIDTH.full : '100%'} height={300}>
      <ComposedChart data={daily} margin={{ top: 22, right: 8, left: 4, bottom: 0 }} barGap={2}>
        <CartesianGrid {...gridProps} />
        <XAxis dataKey="date" tickFormatter={shortDate} {...axisProps} minTickGap={18} />
        <YAxis tickFormatter={(v) => compact(v)} {...axisProps} axisLine={false} width={48} />
        <Tooltip
          cursor={{ fill: 'rgba(14,42,99,0.05)' }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const d = payload[0].payload;
            return (
              <TooltipBox
                title={shortDate(d.date)}
                rows={[
                  { label: 'Sales', value: rs(d.saleValue), color: MEANING.sales },
                  { label: 'Returns', value: rs(d.returnValue), color: MEANING.returns },
                  { label: 'Net', value: rs(d.netValue) },
                  { label: '7-day avg sales', value: rs(d.saleAvg7), color: AVG_LINE },
                ]}
                footer="Click for this day's vouchers"
              />
            );
          }}
        />
        <Bar dataKey="saleValue" name="Sales" fill={MEANING.sales} {...BAR} isAnimationActive={!reducedMotion}
          onClick={(d) => onDrill(d.payload || d)}>
          {labelBars && <LabelList dataKey="saleValue" position="top" formatter={compact} style={{ fill: INK.secondary, fontSize: 11 }} />}
        </Bar>
        <Bar dataKey="returnValue" name="Returns" fill={MEANING.returns} {...BAR} isAnimationActive={!reducedMotion}
          onClick={(d) => onDrill(d.payload || d)} />
        <Line type="monotone" dataKey="saleAvg7" name="7-day average" stroke={AVG_LINE} strokeWidth={2} dot={false}
          activeDot={{ r: 4, stroke: '#fff', strokeWidth: 2 }} isAnimationActive={!reducedMotion} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

function SegmentDonut({ rows, onDrill }) {
  const total = rows.reduce((s, r) => s + Math.max(r.netValue, 0), 0);
  const printing = usePrintMode();
  return (
    <Box sx={{ position: 'relative', height: 260 }}>
      <ResponsiveContainer width={printing ? PRINT_WIDTH.half : '100%'} height="100%">
        <PieChart>
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const r = payload[0].payload;
              return (
                <TooltipBox
                  title={SEGMENT_LABEL[r.segment]}
                  rows={[
                    { label: 'Net', value: rs(r.netValue), color: SEGMENT[r.segment] },
                    { label: 'Sales', value: rs(r.saleValue) },
                    { label: 'Returns', value: rs(r.returnValue) },
                    { label: 'Sold units', value: int(r.soldUnits) },
                  ]}
                  footer="Click for these vouchers"
                />
              );
            }}
          />
          <Pie
            data={rows} dataKey="netValue" nameKey="segment" innerRadius="62%" outerRadius="88%"
            paddingAngle={1.5} stroke="#fff" strokeWidth={2} isAnimationActive={!reducedMotion}
            onClick={(d) => onDrill(d.payload || d)}
          >
            {rows.map((r) => <Cell key={r.segment} fill={SEGMENT[r.segment]} />)}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <Box sx={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none' }}>
        <Box sx={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', color: INK.secondary }}>Net</Box>
        <Box className="cockpit-num" sx={{ fontSize: 22, fontWeight: 700, color: INK.primary }}>{rsCompact(total)}</Box>
      </Box>
    </Box>
  );
}

function HorizontalBars({ rows, labelKey, valueKey, colorOf, onDrill, tooltipRows, height }) {
  const printing = usePrintMode();
  return (
    <ResponsiveContainer width={printing ? PRINT_WIDTH.half : '100%'} height={height || Math.max(rows.length * 34 + 16, 140)}>
      <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 8, left: 0, bottom: 0 }}>
        {/* Headroom so the value label at the tip of the longest bar never clips (more on narrow print cards). */}
        <XAxis type="number" hide domain={[0, (max) => max * (printing ? 1.6 : 1.22)]} />
        <YAxis type="category" dataKey={labelKey} width={150} tickLine={false} axisLine={false} interval={0}
          tick={({ x, y, payload }) => (
            <text x={x - 6} y={y} dy={4} textAnchor="end" fill={INK.primary} fontSize={12}>
              <title>{payload.value}</title>
              {payload.value && payload.value.length > 20 ? `${payload.value.slice(0, 19)}…` : payload.value}
            </text>
          )} />
        <Tooltip
          cursor={{ fill: 'rgba(14,42,99,0.05)' }}
          content={({ active, payload }) => (active && payload?.length ? (
            <TooltipBox title={payload[0].payload[labelKey]} rows={tooltipRows(payload[0].payload)} footer="Click for vouchers" />
          ) : null)}
        />
        <Bar dataKey={valueKey} {...HBAR} isAnimationActive={!reducedMotion} onClick={(d) => onDrill(d.payload || d)}>
          {rows.map((r, i) => <Cell key={i} fill={colorOf(r)} />)}
          <LabelList dataKey={valueKey} position="right" formatter={rsCompact} style={{ fill: INK.secondary, fontSize: 11.5, fontFamily: 'IBM Plex Mono, monospace' }} />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Areas as a bar-table: the hierarchy (TSE / SR TSE / ASM) needs text room a plain axis can't give. */
function AreaTable({ rows, onDrill }) {
  const max = Math.max(...rows.map((r) => Math.max(r.netValue, 0)), 1);
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map((r) => (
        <MuiTooltip
          key={r.areaId ?? 'none'}
          placement="top"
          title={`Sales ${rs(r.saleValue)} · Returns ${rs(r.returnValue)} · ${int(r.soldUnits)} sold units`}
        >
          <Box
            role="button" tabIndex={0} className="cockpit-clickable"
            onClick={() => onDrill(r)} onKeyDown={(e) => { if (e.key === 'Enter') onDrill(r); }}
            sx={{
              display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1.4fr) minmax(0, 0.8fr) auto', md: 'minmax(0, 1.6fr) minmax(0, 1fr) auto' }, gap: 1.5, alignItems: 'center',
              py: 1.1, px: 1, borderRadius: '10px', cursor: 'pointer', '&:hover': { bgcolor: '#F4F6FA' },
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Box sx={{ fontWeight: 700, fontSize: 13.5, color: INK.primary }}>{r.name}</Box>
              <Box sx={{ fontSize: 11.5, color: INK.muted, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {r.areaId == null ? 'Vouchers with no shop' : `TSE ${r.tse || '—'} · SR TSE ${r.srTse || '—'} · ASM ${r.asm || '—'}`}
              </Box>
            </Box>
            <Box sx={{ height: 10, bgcolor: '#EEF1F6', borderRadius: 999, overflow: 'hidden' }}>
              <Box sx={{ height: '100%', width: `${(Math.max(r.netValue, 0) / max) * 100}%`, bgcolor: MEANING.sales, borderRadius: 999, transition: 'width 600ms ease' }} />
            </Box>
            <Box className="cockpit-num" sx={{ fontSize: 13, fontWeight: 700, color: INK.primary, minWidth: 76, textAlign: 'right' }}>
              {rsCompact(r.netValue)}
            </Box>
          </Box>
        </MuiTooltip>
      ))}
    </Box>
  );
}

export default function SalesSection({ query, filters, onDrill }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();
  const hasSales = d?.daily?.some((x) => x.saleValue > 0 || x.returnValue > 0);
  const rangeHint = `${shortDate(filters.startDate)} – ${shortDate(filters.endDate)}`;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <ChartCard
        title="Daily sales and returns"
        hint={`${rangeHint} · bars are sales and returns, the line is the 7-day average of sales · click a bar for that day's vouchers`}
        loading={loading} error={error} onRetry={retry} height={300}
        empty={!hasSales} emptyTitle="No sales in this period" emptyMessage="Try a longer date range or clear the filters."
        legend={<Legend items={[
          { label: 'Sales', color: MEANING.sales },
          { label: 'Returns', color: MEANING.returns },
          { label: '7-day average (sales)', color: AVG_LINE, line: true },
        ]} />}
      >
        {d && (
          <DailyChart daily={d.daily} onDrill={(x) => onDrill({
            title: shortDate(x.date), subtitle: `Net ${rs(x.netValue)} · sales ${rs(x.saleValue)}`,
            extra: { startDate: x.date, endDate: x.date },
          })} />
        )}
      </ChartCard>

      <Grid cols={2} from="lg" className="cockpit-grid-print-2">
        <ChartCard
          title="Local vs LMT"
          hint="Net sales by salesman type at the time of sale · click a slice"
          loading={loading} error={error} onRetry={retry} height={260}
          empty={!d?.bySegment?.length} emptyTitle="No sales yet" emptyMessage="Nothing was sold in this period."
          legend={d && <Legend items={d.bySegment.map((s) => {
            const total = d.bySegment.reduce((a, b) => a + Math.max(b.netValue, 0), 0);
            return {
              label: SEGMENT_LABEL[s.segment], color: SEGMENT[s.segment],
              value: `${rsCompact(s.netValue)} · ${pct(total ? (Math.max(s.netValue, 0) * 100) / total : 0, 0)}`,
            };
          })} />}
        >
          {d && (
            <SegmentDonut rows={d.bySegment} onDrill={(s) => {
              // Old mart-agent vouchers have no segment filter of their own, so that slice is hover-only.
              if (s.segment === 'OTHER') return;
              onDrill({ title: SEGMENT_LABEL[s.segment], subtitle: `Net ${rs(s.netValue)}`, extra: { segment: s.segment } });
            }} />
          )}
        </ChartCard>

        <ChartCard
          title="Sales by salesman"
          hint="Net sales (sales minus returns) · click a bar"
          loading={loading} error={error} onRetry={retry} height={260}
          empty={!d?.bySalesman?.length} emptyTitle="No salesman sales yet" emptyMessage="Nothing was sold in this period."
          legend={d && <Legend items={['LOCAL', 'LMT', 'OTHER']
            .filter((seg) => d.bySalesman.slice(0, 15).some((r) => segOfRole(r.role) === seg))
            .map((seg) => ({ label: SEGMENT_LABEL[seg], color: SEGMENT[seg] }))} />}
        >
          {d && (
            <HorizontalBars
              rows={d.bySalesman.slice(0, 15)} labelKey="name" valueKey="netValue"
              colorOf={(r) => SEGMENT[segOfRole(r.role)]}
              tooltipRows={(r) => [
                { label: 'Net', value: rs(r.netValue) },
                { label: 'Sales', value: rs(r.saleValue), color: MEANING.sales },
                { label: 'Returns', value: rs(r.returnValue), color: MEANING.returns },
                { label: 'Vouchers', value: int(r.vouchers) },
                { label: 'Sold units', value: int(r.soldUnits) },
              ]}
              onDrill={(r) => onDrill({ title: r.name, subtitle: `Net ${rs(r.netValue)} · ${int(r.vouchers)} vouchers`, extra: { agentId: r.agentId } })}
            />
          )}
        </ChartCard>

        <ChartCard
          title="Sales by area"
          hint="Net sales per area with its TSE, SR TSE and ASM · click a row"
          loading={loading} error={error} onRetry={retry} height={260}
          empty={!d?.byArea?.length} emptyTitle="No area sales yet" emptyMessage="Nothing was sold in this period."
        >
          {d && (
            <AreaTable rows={d.byArea} onDrill={(r) => onDrill({
              title: r.name, subtitle: `Net ${rs(r.netValue)}`,
              extra: r.areaId == null ? { noArea: true } : { areaId: r.areaId },
            })} />
          )}
        </ChartCard>

        <ChartCard
          title="Top 15 products"
          hint="By sold value · click a bar for vouchers containing that product"
          loading={loading} error={error} onRetry={retry} height={260}
          empty={!d?.topProducts?.length} emptyTitle="No products sold yet" emptyMessage="Nothing was sold in this period."
        >
          {d && (
            <HorizontalBars
              rows={d.topProducts} labelKey="name" valueKey="saleValue" colorOf={() => MEANING.sales}
              tooltipRows={(r) => [
                { label: 'Sold value', value: rs(r.saleValue), color: MEANING.sales },
                { label: 'Sold units', value: int(r.soldUnits) },
                { label: 'Returns', value: rs(r.returnValue), color: MEANING.returns },
              ]}
              onDrill={(r) => onDrill({ title: r.name, subtitle: `${int(r.soldUnits)} units sold · ${rs(r.saleValue)}`, extra: { productId: r.productId } })}
            />
          )}
        </ChartCard>
      </Grid>
    </Box>
  );
}
