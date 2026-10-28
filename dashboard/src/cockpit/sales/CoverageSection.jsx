import React, { useState } from 'react';
import dayjs from 'dayjs';
import { Box, IconButton, Skeleton, Tooltip as MuiTooltip } from '@mui/material';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import {
  Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import ChartCard, { Legend, TooltipBox } from '../components/ChartCard';
import Card from '../components/Card';
import { axisProps, gridProps, reducedMotion } from '../components/chartStyle';
import usePrintMode, { PRINT_WIDTH } from '../components/usePrintMode';
import { Grid } from '../components/Sections';
import { EmptyState, ErrorState } from '../components/States';
import { salesCockpitApi } from '../api';
import { CELL, INK, MEANING, REASON, REASON_LABEL, SEGMENT_LABEL } from '../tokens';
import { int, pct, shortDate } from '../format';

const segOfRole = (role) => (role === 'SALESMAN_LOCAL' ? 'LOCAL' : role === 'SALESMAN_LMT' ? 'LMT' : 'OTHER');

/** Visited vs not visited per salesman (lowest coverage first). Click a row for its vouchers. */
function SalesmanCoverage({ rows, onDrill }) {
  if (!rows.length) return <EmptyState height={200} title="No assigned shops" message="No shops are assigned to salesmen for these filters." />;
  const VISIBLE = 7; // rows that fit before the list scrolls
  return (
    <Box>
    <Box sx={{ maxHeight: 340, overflowY: 'auto', pr: 0.5 }}>
      {rows.map((r) => (
        <MuiTooltip key={r.agentId} placement="top"
          title={`${int(r.visitedShops)} of ${int(r.assignedShops)} assigned shops visited at least once`}>
          <Box role="button" tabIndex={0} className="cockpit-clickable"
            onClick={() => onDrill(r)} onKeyDown={(e) => { if (e.key === 'Enter') onDrill(r); }}
            sx={{
              display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.2fr) 56px', gap: 1.25, alignItems: 'center',
              py: 0.75, px: 1, borderRadius: '10px', cursor: 'pointer', '&:hover': { bgcolor: '#F4F6FA' },
            }}>
            <Box sx={{ minWidth: 0 }}>
              <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.name}</Box>
              <Box sx={{ fontSize: 11, color: INK.muted }}>{SEGMENT_LABEL[segOfRole(r.role)]} · {int(r.visitedShops)}/{int(r.assignedShops)} shops</Box>
            </Box>
            <Box sx={{ display: 'flex', height: 10, borderRadius: 999, overflow: 'hidden', gap: '2px', bgcolor: '#fff' }}>
              <Box sx={{ width: `${r.coveragePercent}%`, bgcolor: MEANING.ok }} />
              <Box sx={{ flex: 1, bgcolor: '#F3C9C9' }} />
            </Box>
            <Box className="cockpit-num" sx={{ fontSize: 13, fontWeight: 700, textAlign: 'right', color: INK.primary }}>{pct(r.coveragePercent, 0)}</Box>
          </Box>
        </MuiTooltip>
      ))}
    </Box>
    {rows.length > VISIBLE && (
      <Box className="cockpit-no-print" sx={{ mt: 1, fontSize: 12, color: INK.secondary, textAlign: 'center' }}>
        {int(rows.length)} salesmen · lowest coverage first · scroll the list for the rest
      </Box>
    )}
    </Box>
  );
}

function CoverageTrend({ trend }) {
  const printing = usePrintMode();
  return (
    <ResponsiveContainer width={printing ? PRINT_WIDTH.half : '100%'} height={240}>
      <AreaChart data={trend} margin={{ top: 12, right: 8, left: 4, bottom: 0 }}>
        <defs>
          <linearGradient id="cov-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={MEANING.ok} stopOpacity={0.18} />
            <stop offset="100%" stopColor={MEANING.ok} stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid {...gridProps} />
        <XAxis dataKey="date" tickFormatter={shortDate} {...axisProps} minTickGap={18} />
        <YAxis domain={[0, 100]} tickFormatter={(v) => `${v}%`} {...axisProps} axisLine={false} width={40} />
        <Tooltip
          content={({ active, payload }) => (active && payload?.length ? (
            <TooltipBox title={shortDate(payload[0].payload.date)} rows={[
              { label: 'Coverage', value: pct(payload[0].payload.coveragePercent), color: MEANING.ok },
              { label: 'Shops visited', value: `${int(payload[0].payload.visitedShops)} of ${int(payload[0].payload.assignedShops)}` },
            ]} />
          ) : null)}
        />
        <Area type="monotone" dataKey="coveragePercent" stroke={MEANING.ok} strokeWidth={2} fill="url(#cov-fill)"
          dot={false} activeDot={{ r: 4, stroke: '#fff', strokeWidth: 2 }} isAnimationActive={!reducedMotion} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

function ReasonsDonut({ reasons }) {
  const printing = usePrintMode();
  const total = reasons.reduce((s, r) => s + r.count, 0);
  return (
    <Box sx={{ position: 'relative', height: 220 }}>
      <ResponsiveContainer width={printing ? PRINT_WIDTH.half : '100%'} height="100%">
        <PieChart>
          <Tooltip content={({ active, payload }) => (active && payload?.length ? (
            <TooltipBox title={REASON_LABEL[payload[0].payload.reason] || payload[0].payload.reason}
              rows={[{ label: 'Visits', value: int(payload[0].payload.count), color: REASON[payload[0].payload.reason] }]} />
          ) : null)} />
          <Pie data={reasons} dataKey="count" nameKey="reason" innerRadius="60%" outerRadius="86%" paddingAngle={1.5}
            stroke="#fff" strokeWidth={2} isAnimationActive={!reducedMotion}>
            {reasons.map((r) => <Cell key={r.reason} fill={REASON[r.reason] || MEANING.neutral} />)}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <Box sx={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none' }}>
        <Box sx={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', color: INK.secondary }}>Feedback</Box>
        <Box className="cockpit-num" sx={{ fontSize: 22, fontWeight: 700, color: INK.primary }}>{int(total)}</Box>
      </Box>
    </Box>
  );
}

const CELL_LABEL = { SCAN: 'Visited (QR scan)', FEEDBACK: 'Visited (feedback, no sale)', NONE: 'Not visited', FUTURE: 'Not yet' };

/** Shop x day grid for one week, 20 shops per page, least-visited first. */
function Heatmap({ filters, enabled, onDrill }) {
  const [weekOf, setWeekOf] = useState(null);
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['cockpit', 'sales', 'heatmap', filters, weekOf, page],
    queryFn: () => salesCockpitApi.heatmap(filters, weekOf, page),
    enabled,
    staleTime: 60 * 1000,
    placeholderData: keepPreviousData,
  });
  const h = query.data;
  const start = h ? dayjs(h.weekStart) : null;
  const pages = h ? Math.max(1, Math.ceil(h.totalShops / h.size)) : 1;
  const moveWeek = (deltaDays) => { setWeekOf(start.add(deltaDays, 'day').format('YYYY-MM-DD')); setPage(0); };

  return (
    <Card sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <Box>
          <Box component="h3" sx={{ m: 0, fontSize: 15, fontWeight: 800, color: INK.primary }}>Shop × day</Box>
          <Box sx={{ fontSize: 12.5, color: INK.muted, mt: 0.25 }}>
            {h ? `${shortDate(h.days[0])} – ${shortDate(h.days[6])}` : 'Selected week'} · least-visited shops first · click a shop for its vouchers
          </Box>
        </Box>
        <Box className="cockpit-no-print" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <IconButton size="small" aria-label="Previous week" disabled={!h} onClick={() => moveWeek(-7)}><ChevronLeftRoundedIcon /></IconButton>
          <Box sx={{ fontSize: 12.5, color: INK.secondary, minWidth: 64, textAlign: 'center' }}>Week</Box>
          <IconButton size="small" aria-label="Next week" disabled={!h} onClick={() => moveWeek(7)}><ChevronRightRoundedIcon /></IconButton>
        </Box>
      </Box>

      {(!h && (query.isLoading || !enabled)) && <Skeleton animation="wave" variant="rounded" height={320} sx={{ borderRadius: 3 }} />}
      {query.isError && !h && <ErrorState onRetry={() => query.refetch()} />}
      {h && h.rows.length === 0 && <EmptyState title="No assigned shops" message="No shops are assigned to salesmen for these filters." />}
      {h && h.rows.length > 0 && (
        <Box sx={{ overflowX: 'auto' }}>
          <Box role="table" aria-label="Shop visits by day" sx={{ minWidth: 560 }}>
            <Box role="row" sx={{ display: 'grid', gridTemplateColumns: 'minmax(170px, 1.6fr) repeat(7, minmax(34px, 1fr)) 44px', gap: '3px', mb: 0.5 }}>
              <Box role="columnheader" sx={{ fontSize: 11, color: INK.muted }}>Shop · salesman</Box>
              {h.days.map((d) => (
                <Box role="columnheader" key={d} sx={{ fontSize: 11, color: INK.muted, textAlign: 'center' }}>
                  {dayjs(d).format('dd')}<br />{dayjs(d).format('D')}
                </Box>
              ))}
              <Box role="columnheader" sx={{ fontSize: 11, color: INK.muted, textAlign: 'right' }}>Days</Box>
            </Box>
            {h.rows.map((r) => (
              <Box role="row" key={r.shopId} sx={{ display: 'grid', gridTemplateColumns: 'minmax(170px, 1.6fr) repeat(7, minmax(34px, 1fr)) 44px', gap: '3px', alignItems: 'center', mb: '3px' }}>
                <Box role="rowheader" className="cockpit-clickable" tabIndex={0}
                  onClick={() => onDrill(r, h)} onKeyDown={(e) => { if (e.key === 'Enter') onDrill(r, h); }}
                  sx={{ minWidth: 0, cursor: 'pointer', '&:hover .shop': { textDecoration: 'underline' } }}>
                  <Box className="shop" sx={{ fontSize: 12.5, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.shopName}</Box>
                  <Box sx={{ fontSize: 11, color: INK.muted, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.salesman}</Box>
                </Box>
                {r.days.map((state, i) => (
                  <MuiTooltip key={i} title={`${shortDate(h.days[i])}: ${CELL_LABEL[state]}`}>
                    <Box role="cell" aria-label={`${shortDate(h.days[i])}: ${CELL_LABEL[state]}`}
                      sx={{ height: 26, borderRadius: '6px', bgcolor: CELL[state], border: state === 'NONE' ? '1px solid #F3C9C9' : 'none',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 800,
                        color: state === 'SCAN' ? '#fff' : state === 'FEEDBACK' ? '#14532D' : 'transparent' }}>
                      {state === 'SCAN' ? '✓' : state === 'FEEDBACK' ? 'F' : ''}
                    </Box>
                  </MuiTooltip>
                ))}
                <Box className="cockpit-num" sx={{ fontSize: 12.5, fontWeight: 700, textAlign: 'right', color: INK.primary }}>{r.visitedDays}</Box>
              </Box>
            ))}
          </Box>
        </Box>
      )}
      {h && h.rows.length > 0 && (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
          <Legend items={[
            { label: 'QR scan ✓', color: CELL.SCAN }, { label: 'Feedback F', color: CELL.FEEDBACK },
            { label: 'Not visited', color: '#F3C9C9' }, { label: 'Not yet', color: CELL.FUTURE },
          ]} />
          <Box className="cockpit-no-print" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, fontSize: 12.5, color: INK.secondary }}>
            <IconButton size="small" aria-label="Previous shops" disabled={page === 0} onClick={() => setPage((p) => p - 1)}><ChevronLeftRoundedIcon /></IconButton>
            <span className="cockpit-num">{page * h.size + 1}–{Math.min((page + 1) * h.size, h.totalShops)} of {int(h.totalShops)}</span>
            <IconButton size="small" aria-label="More shops" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}><ChevronRightRoundedIcon /></IconButton>
          </Box>
        </Box>
      )}
    </Card>
  );
}

export default function CoverageSection({ query, filters, visible, onDrill }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Grid cols={3} from="lg" className="cockpit-grid-print-2">
        <ChartCard
          title={d ? `${pct(d.coveragePercent, 0)} of assigned shops visited` : 'Visited vs not visited'}
          hint="Per salesman, at least one scan or feedback in the period · lowest first · click for vouchers"
          loading={loading} error={error} onRetry={retry} height={300} empty={false}
          legend={<Legend items={[{ label: 'Visited', color: MEANING.ok }, { label: 'Not visited', color: '#F3C9C9' }]} />}
        >
          {d && <SalesmanCoverage rows={d.bySalesman} onDrill={(r) => onDrill({ title: r.name, subtitle: `${int(r.visitedShops)} of ${int(r.assignedShops)} shops visited`, extra: { agentId: r.agentId } })} />}
        </ChartCard>
        <ChartCard
          title="Coverage % per day"
          hint="Assigned shops visited by their salesman that day (today's assignments)"
          loading={loading} error={error} onRetry={retry} height={240}
          empty={!d?.trend?.length} emptyTitle="No days in range" emptyMessage="Pick a range that includes past days."
        >
          {d && <CoverageTrend trend={d.trend} />}
        </ChartCard>
        <ChartCard
          title="Feedback reasons"
          hint="Visits where no sale was made"
          loading={loading} error={error} onRetry={retry} height={220}
          empty={!d?.feedbackReasons?.length} emptyTitle="No feedback yet" emptyMessage="No visits ended without a sale in this period."
          legend={d && <Legend items={d.feedbackReasons.map((r) => ({ label: REASON_LABEL[r.reason] || r.reason, color: REASON[r.reason] || MEANING.neutral, value: int(r.count) }))} />}
        >
          {d && <ReasonsDonut reasons={d.feedbackReasons} />}
        </ChartCard>
      </Grid>
      <Heatmap filters={filters} enabled={visible} onDrill={(r, h) => onDrill({
        title: r.shopName, subtitle: `Week of ${shortDate(h.days[0])}`,
        extra: { shopId: r.shopId, startDate: h.days[0], endDate: h.days[6] },
      })} />
    </Box>
  );
}
