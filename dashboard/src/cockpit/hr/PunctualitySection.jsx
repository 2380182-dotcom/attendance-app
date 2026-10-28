import React from 'react';
import { Box, Tooltip as MuiTooltip } from '@mui/material';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import ChartCard, { Legend, TooltipBox } from '../components/ChartCard';
import { axisProps, gridProps, reducedMotion } from '../components/chartStyle';
import usePrintMode, { PRINT_WIDTH } from '../components/usePrintMode';
import { Grid } from '../components/Sections';
import { INK, MEANING } from '../tokens';
import { int, pct } from '../format';
import { hhmm } from './hrFormat';

/** Ranked list: late check-ins per agent (bar), with days present and average minutes late. */
function LateList({ rows }) {
  const max = Math.max(1, ...rows.map((r) => r.lateCount));
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map((r) => (
        <MuiTooltip key={r.agentId} placement="top"
          title={`${r.lateCount} late of ${r.daysPresent} days present · on average ${r.avgMinutesLate ?? 0} min late · ${r.department}`}>
          <Box tabIndex={0} sx={{
            display: 'grid', gridTemplateColumns: 'minmax(0, 1.1fr) minmax(0, 1fr) 96px', gap: 1.25,
            alignItems: 'center', py: 0.75, px: 1, borderRadius: '10px', '&:hover': { bgcolor: '#F4F6FA' },
          }}>
            <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {r.name}
            </Box>
            <Box sx={{ height: 10, bgcolor: '#EEF1F6', borderRadius: 999 }}>
              <Box sx={{ height: '100%', width: `${(r.lateCount / max) * 100}%`, bgcolor: MEANING.late, borderRadius: 999, transition: 'width 600ms ease' }} />
            </Box>
            <Box className="cockpit-num" sx={{ fontSize: 13, textAlign: 'right', color: INK.primary }}>
              <strong>{r.lateCount}×</strong>
              <Box component="span" sx={{ color: INK.secondary }}> · {Math.round(r.avgMinutesLate ?? 0)}m</Box>
            </Box>
          </Box>
        </MuiTooltip>
      ))}
    </Box>
  );
}

function Distribution({ slots }) {
  const printing = usePrintMode();
  return (
    <ResponsiveContainer width={printing ? PRINT_WIDTH.half : '100%'} height={280}>
      <BarChart data={slots.map((x) => ({ ...x, key: x.until ? `gap ${x.time}` : x.time }))} margin={{ top: 10, right: 8, left: 0, bottom: 0 }} barCategoryGap={2}>
        <CartesianGrid {...gridProps} />
        <XAxis dataKey="key" tickFormatter={(k) => (k.startsWith('gap') ? '⋯' : hhmm(k))} {...axisProps} minTickGap={20} />
        <YAxis {...axisProps} axisLine={false} width={36} allowDecimals={false} />
        <Tooltip
          cursor={{ fill: 'rgba(14,42,99,0.05)' }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const x = payload[0].payload;
            if (x.until) return <TooltipBox title="No check-ins" rows={[]} footer={`${hhmm(x.time)} – ${hhmm(x.until)}`} />;
            const [h, m] = x.time.split(':').map(Number);
            const end = `${String(h + (m === 45 ? 1 : 0)).padStart(2, '0')}:${String((m + 15) % 60).padStart(2, '0')}`;
            return (
              <TooltipBox title={`${hhmm(x.time)} – ${hhmm(end)}`} rows={[
                { label: 'On time', value: int(x.onTime), color: MEANING.ok },
                { label: 'Late', value: int(x.late), color: MEANING.late },
              ]} />
            );
          }}
        />
        {/* White strokes keep a 2px gap between the stacked segments. */}
        <Bar dataKey="onTime" stackId="t" name="On time" fill={MEANING.ok} stroke="#fff" strokeWidth={1}
          maxBarSize={22} isAnimationActive={!reducedMotion} />
        <Bar dataKey="late" stackId="t" name="Late" fill={MEANING.late} stroke="#fff" strokeWidth={1}
          maxBarSize={22} radius={[4, 4, 0, 0]} isAnimationActive={!reducedMotion} />
      </BarChart>
    </ResponsiveContainer>
  );
}

export default function PunctualitySection({ query }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();

  return (
    <Grid cols={2} from="lg" className="cockpit-grid-print-2">
      <ChartCard
        title="Late check-ins by agent"
        hint={d ? `Top ${d.lateByAgent.length} · ${int(d.lateCheckIns)} late of ${int(d.checkIns)} check-ins · late = after shift start + grace, Pakistan time` : ''}
        loading={loading} error={error} onRetry={retry} height={300}
        empty={!d || d.lateByAgent.length === 0} emptyTitle="Nobody was late" emptyMessage="Every check-in in this period was on time."
      >
        {d && <LateList rows={d.lateByAgent} />}
      </ChartCard>
      <ChartCard
        title={d?.onTimePct != null ? `${pct(d.onTimePct)} of check-ins on time` : 'Check-in times'}
        hint={d?.avgMinutesLate != null ? `Check-ins per 15 minutes (⋯ = hours without check-ins) · late ones were ${Math.round(d.avgMinutesLate)} min late on average` : 'Check-ins per 15 minutes, Pakistan time'}
        loading={loading} error={error} onRetry={retry} height={280}
        empty={!d || d.distribution.length === 0} emptyTitle="No check-ins in this period"
        legend={<Legend items={[{ label: 'On time', color: MEANING.ok }, { label: 'Late', color: MEANING.late }]} />}
      >
        {d && <Distribution slots={d.distribution} />}
      </ChartCard>
    </Grid>
  );
}
