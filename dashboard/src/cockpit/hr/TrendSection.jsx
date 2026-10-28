import React from 'react';
import { Box } from '@mui/material';
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import ChartCard, { Legend, TooltipBox } from '../components/ChartCard';
import { axisProps, gridProps, reducedMotion } from '../components/chartStyle';
import usePrintMode, { PRINT_WIDTH } from '../components/usePrintMode';
import { INK, MEANING } from '../tokens';
import { int, pct, shortDate } from '../format';
import { TrackingOffNote } from './hrUi';

const SERIES = [
  { key: 'attendancePct', label: 'Attendance %', color: MEANING.ok, hint: 'present of expected' },
  { key: 'latePct', label: 'Late %', color: MEANING.late, hint: 'late of present' },
  { key: 'absentPct', label: 'Absent %', color: MEANING.alert, hint: 'absent of expected' },
];

/** Absent % and attendance % (present of expected) need absence tracking; late % does not. */
const seriesFor = (d) => (d?.absenceTracking === false ? SERIES.filter((s) => s.key === 'latePct') : SERIES);

/** One headline per series, so the period's result reads before the chart. */
function Summary({ d }) {
  return (
    <Box sx={{ display: 'flex', gap: { xs: 2, sm: 4 }, flexWrap: 'wrap' }}>
      {seriesFor(d).map((s) => (
        <Box key={s.key}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, fontSize: 12, fontWeight: 700, color: INK.secondary, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            <Box component="span" sx={{ width: 14, height: 2, bgcolor: s.color, borderRadius: 2 }} />
            {s.label}
          </Box>
          <Box className="cockpit-num" sx={{ fontSize: 24, fontWeight: 700, color: INK.primary }}>
            {d[s.key] == null ? '—' : pct(d[s.key])}
          </Box>
          <Box sx={{ fontSize: 12, color: INK.muted }}>{s.hint}, whole period</Box>
        </Box>
      ))}
    </Box>
  );
}

export default function TrendSection({ query }) {
  const printing = usePrintMode();
  const d = query.data;
  const days = d?.days || [];
  const empty = !days.some((x) => x.expected > 0 || x.present > 0);
  const holidays = days.filter((x) => x.holiday);
  const series = seriesFor(d);
  const tracking = d?.absenceTracking !== false;

  return (
    <ChartCard
      title={tracking ? 'Attendance, late and absent by day' : 'Late check-ins by day'}
      hint={tracking
        ? 'Expected = working day for the agent, not a holiday, already joined · absent = expected but no check-in · today counts only agents past shift start + grace'
        : 'Late % = late agents of those who checked in · attendance % and absent % need absence tracking'}
      loading={query.isLoading || (!d && query.isFetching)} error={query.isError && !d} onRetry={() => query.refetch()}
      empty={!d || empty} emptyTitle="No attendance in this period" emptyMessage="Nobody was expected and nobody checked in."
      height={300}
      legend={<Legend items={[...series.map((s) => ({ label: s.label, color: s.color, line: true })),
        ...(holidays.length ? [{ label: 'Company holiday', color: INK.muted, line: true }] : [])]} />}
    >
      {d && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {!tracking && <TrackingOffNote />}
          <Summary d={d} />
          <ResponsiveContainer width={printing ? PRINT_WIDTH.full : '100%'} height={280}>
            <LineChart data={days} margin={{ top: 10, right: 12, left: 0, bottom: 0 }}>
              <CartesianGrid {...gridProps} />
              <XAxis dataKey="date" tickFormatter={shortDate} {...axisProps} minTickGap={22} />
              <YAxis domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={(v) => `${v}%`} {...axisProps} axisLine={false} width={44} />
              {holidays.map((h) => (
                <ReferenceLine key={h.date} x={h.date} stroke={INK.muted} strokeDasharray="3 3" />
              ))}
              <Tooltip
                cursor={{ stroke: INK.grid, strokeWidth: 1 }}
                content={({ active, payload }) => {
                  if (!active || !payload?.length) return null;
                  const x = payload[0].payload;
                  return (
                    <TooltipBox
                      title={`${shortDate(x.date)}${x.holiday ? ` · ${x.holiday}` : ''}`}
                      rows={tracking ? [
                        { label: 'Attendance', value: x.attendancePct == null ? '—' : `${pct(x.attendancePct)} (${int(x.present)} of ${int(x.expected)})`, color: MEANING.ok },
                        { label: 'Late', value: x.latePct == null ? '—' : `${pct(x.latePct)} (${int(x.late)})`, color: MEANING.late },
                        { label: 'Absent', value: x.absentPct == null ? '—' : `${pct(x.absentPct)} (${int(x.absent)})`, color: MEANING.alert },
                      ] : [
                        { label: 'Checked in', value: int(x.present) },
                        { label: 'Late', value: x.latePct == null ? '—' : `${pct(x.latePct)} (${int(x.late)})`, color: MEANING.late },
                      ]}
                      footer={tracking && x.expected === 0 ? 'Nobody was expected this day' : undefined}
                    />
                  );
                }}
              />
              {series.map((s) => (
                <Line key={s.key} type="monotone" dataKey={s.key} name={s.label} stroke={s.color} strokeWidth={2}
                  dot={days.length <= 14 ? { r: 3, strokeWidth: 0, fill: s.color } : false}
                  activeDot={{ r: 5, stroke: '#fff', strokeWidth: 2 }} connectNulls={false}
                  isAnimationActive={!reducedMotion} />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </Box>
      )}
    </ChartCard>
  );
}
