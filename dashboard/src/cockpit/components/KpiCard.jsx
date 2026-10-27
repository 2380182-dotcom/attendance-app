import React, { useId } from 'react';
import { Box, Skeleton } from '@mui/material';
import { Area, AreaChart, ResponsiveContainer, Tooltip, YAxis } from 'recharts';
import Card, { Eyebrow } from './Card';
import { INK, MEANING } from '../tokens';
import { shortDate } from '../format';
import usePrintMode from './usePrintMode';

function DeltaBadge({ delta, higherIsBetter, label, unit = '%' }) {
  if (delta === null || delta === undefined || !Number.isFinite(delta)) {
    return (
      <Box sx={badgeSx('#EEF1F6', INK.secondary)}>No comparison yet</Box>
    );
  }
  const up = delta > 0;
  const flat = Math.abs(delta) < 0.5;
  let tone = 'neutral';
  if (!flat && higherIsBetter !== null && higherIsBetter !== undefined) {
    tone = up === higherIsBetter ? 'good' : 'bad';
  }
  const colors = {
    good: ['#E7F6EC', '#126B33'],
    bad: ['#FBEAEA', '#A51F1F'],
    neutral: ['#EEF1F6', INK.secondary],
  }[tone];
  const arrow = flat ? '▬' : up ? '▲' : '▼';
  return (
    <Box className="cockpit-delta" sx={badgeSx(colors[0], colors[1])} title={label}>
      <span aria-hidden>{arrow}</span>
      <span className="cockpit-num">{Math.abs(delta).toFixed(0)}{unit}</span>
      <Box component="span" sx={{ fontWeight: 600, opacity: 0.85 }}>{label}</Box>
    </Box>
  );
}

function badgeSx(bg, fg) {
  return {
    display: 'inline-flex', alignItems: 'center', gap: 0.5, bgcolor: bg, color: fg,
    fontSize: 11.5, fontWeight: 700, px: 1, py: 0.25, borderRadius: '999px', whiteSpace: 'nowrap',
    maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis',
  };
}

function Sparkline({ data, color, format, fromZero = true }) {
  const id = useId().replace(/:/g, '');
  const printing = usePrintMode();
  if (!data?.length) return <Box sx={{ height: 44 }} />;
  return (
    <Box sx={{ height: 44, mx: -0.5 }} aria-hidden>
      <ResponsiveContainer width={printing ? 140 : '100%'} height="100%">
        <AreaChart data={data} margin={{ top: 6, right: 4, bottom: 2, left: 4 }}>
          <defs>
            <linearGradient id={`sp-${id}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity={0.22} />
              <stop offset="100%" stopColor={color} stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <YAxis hide domain={fromZero ? [0, 'dataMax'] : ['dataMin', 'dataMax']} />
          <Tooltip
            cursor={{ stroke: INK.grid, strokeWidth: 1 }}
            content={({ active, payload }) => (active && payload?.length ? (
              <Box sx={{ bgcolor: '#0F172A', color: '#fff', fontSize: 11.5, px: 1, py: 0.5, borderRadius: 1 }}>
                {shortDate(payload[0].payload.date)} · <span className="cockpit-num">{format(payload[0].value)}</span>
              </Box>
            ) : null)}
          />
          <Area
            type="monotone" dataKey="value" stroke={color} strokeWidth={2} fill={`url(#sp-${id})`}
            dot={false} activeDot={{ r: 4, stroke: '#fff', strokeWidth: 2 }} animationDuration={700}
          />
        </AreaChart>
      </ResponsiveContainer>
    </Box>
  );
}

const MEANING_COLOR = {
  sales: MEANING.sales, returns: MEANING.returns, rbe: MEANING.rbe,
  alert: MEANING.alert, ok: MEANING.ok, neutral: MEANING.neutral, late: MEANING.late,
};

/**
 * Highlight card: big number, one context line, 14-day sparkline and a
 * coloured "vs last week / previous period" badge.
 */
export default function KpiCard({ card, value, unit, onClick, sparkFormat, sparkFromZero = true }) {
  const color = MEANING_COLOR[card.meaning] || MEANING.neutral;
  return (
    <Card accent={color} onClick={onClick} sx={{ display: 'flex', flexDirection: 'column', gap: 1, minHeight: 186 }}
      aria-label={`${card.label}: ${value}${unit ? ` ${unit}` : ''}. ${card.context}`}>
      <Eyebrow>{card.label}</Eyebrow>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75, flexWrap: 'wrap', minWidth: 0 }}>
        <Box className="cockpit-num cockpit-kpi-value" sx={{ fontSize: { xs: 28, sm: 30 }, fontWeight: 700, color: INK.primary, lineHeight: 1.1 }}>
          {value}
        </Box>
        {unit && <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.secondary }}>{unit}</Box>}
      </Box>
      <Box className="cockpit-kpi-context" sx={{ fontSize: 12.5, color: INK.secondary, lineHeight: 1.4, minHeight: 35 }}>{card.context}</Box>
      <Box sx={{ mt: 'auto' }}>
        <Sparkline data={card.sparkline} color={color} format={sparkFormat} fromZero={sparkFromZero} />
        <Box sx={{ mt: 0.75 }}>
          <DeltaBadge delta={card.deltaPercent} higherIsBetter={card.higherIsBetter} label={card.deltaLabel} unit={card.deltaUnit} />
        </Box>
      </Box>
    </Card>
  );
}

export function KpiSkeleton() {
  return (
    <Card sx={{ minHeight: 186 }}>
      <Skeleton animation="wave" width="55%" height={16} />
      <Skeleton animation="wave" width="70%" height={44} />
      <Skeleton animation="wave" width="90%" height={16} />
      <Skeleton animation="wave" variant="rounded" height={44} sx={{ mt: 1.5, borderRadius: 2 }} />
      <Skeleton animation="wave" width="45%" height={22} sx={{ borderRadius: 999 }} />
    </Card>
  );
}

/** 5 per row on large screens, 3 on laptops, 2 on tablets, 1 on phones. */
export function KpiGrid({ children, xlColumns = 5 }) {
  return (
    <Box className="cockpit-cards-print" sx={{
      display: 'grid', gap: 2,
      gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0,1fr))', md: 'repeat(3, minmax(0,1fr))', xl: `repeat(${xlColumns}, minmax(0,1fr))` },
    }}>
      {children}
    </Box>
  );
}
