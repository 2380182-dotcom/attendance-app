import React, { useCallback, useState } from 'react';
import { Box } from '@mui/material';
import CelebrationOutlinedIcon from '@mui/icons-material/CelebrationOutlined';
import { useQuery, useQueryClient, useIsFetching, keepPreviousData } from '@tanstack/react-query';
import '@fontsource-variable/manrope';
import '@fontsource/ibm-plex-mono/500.css';
import '@fontsource/ibm-plex-mono/600.css';
import '@fontsource/ibm-plex-mono/700.css';
import '../cockpit.css';
import CockpitHeader from '../components/CockpitHeader';
import { Section, SectionNav } from '../components/Sections';
import KpiCard, { KpiGrid, KpiSkeleton } from '../components/KpiCard';
import AlertsPanel from '../components/AlertsPanel';
import Card from '../components/Card';
import { ErrorState } from '../components/States';
import { hrCockpitApi } from '../api';
import { useCockpitFilters } from '../useCockpitFilters';
import { INK, MEANING } from '../tokens';
import { int, shortDate } from '../format';

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'today', label: 'Today' },
  { id: 'trend', label: 'Trend' },
  { id: 'punctuality', label: 'Punctuality' },
  { id: 'agents', label: 'Agents' },
  { id: 'locations', label: 'Locations' },
];

const n = (v, one, many) => (Math.round(v) === 1 ? one : many);

/** Seconds since midnight (Pakistan time) -> "9:42 AM". */
function clock(seconds) {
  if (seconds == null) return '—';
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600) % 24;
  const m = Math.floor((s % 3600) / 60);
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')} ${h >= 12 ? 'PM' : 'AM'}`;
}

/** How each HR card shows its number. */
const CARD_VALUE = {
  checked_in: (c) => (c.secondaryValue == null
    ? [int(c.value), n(c.value, 'agent', 'agents')]
    : [`${int(c.value)} / ${int(c.secondaryValue)}`, n(c.secondaryValue, 'agent', 'agents')]),
  late: (c) => [int(c.value), n(c.value, 'agent', 'agents')],
  absent: (c) => [int(c.value), n(c.value, 'agent', 'agents')],
  off: (c) => [int(c.value), n(c.value, 'agent', 'agents')],
  missed_end_duty: (c) => [int(c.value), n(c.value, 'shift', 'shifts')],
  avg_check_in: (c) => [clock(c.value)],
  face_failures: (c) => [int(c.value), n(c.value, 'failure', 'failures')],
  far_from_mart: (c) => [int(c.value), n(c.value, 'check-in', 'check-ins')],
};

function jumpTo(id) {
  const el = document.getElementById(id);
  if (!el) return;
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
}

/** Placeholder for sections built in the next part — honest, never a fake chart. */
function ComingNext({ what }) {
  return (
    <Card sx={{ bgcolor: '#FAFBFD', border: `1px dashed ${INK.border}`, boxShadow: 'none' }}>
      <Box sx={{ fontSize: 13.5, color: INK.secondary }}>
        <strong style={{ color: INK.primary }}>Coming in the next part:</strong> {what}
      </Box>
    </Card>
  );
}

export default function HrCockpitPage() {
  const state = useCockpitFilters();
  const { filters: dateFilters, autoRefresh } = state;
  const [department, setDepartment] = useState('');
  const [martId, setMartId] = useState('');
  const filters = { ...dateFilters, department, martId };
  const queryClient = useQueryClient();
  const refetchInterval = autoRefresh > 0 ? autoRefresh * 60 * 1000 : false;

  const sectionQuery = (name, enabled = true) => ({
    queryKey: ['cockpit', 'hr', name, filters],
    queryFn: () => hrCockpitApi.section(name, filters),
    enabled,
    refetchInterval,
    staleTime: 60 * 1000,
    placeholderData: keepPreviousData,
  });

  const options = useQuery({ queryKey: ['cockpit', 'hr', 'options'], queryFn: hrCockpitApi.filterOptions, staleTime: 5 * 60 * 1000 });
  const highlights = useQuery(sectionQuery('highlights'));
  // One heavy request at a time: Needs attention waits for Highlights.
  const attention = useQuery(sectionQuery('attention', highlights.isSuccess || highlights.isError));
  const fetching = useIsFetching({ queryKey: ['cockpit', 'hr'] }) > 0;

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['cockpit', 'hr'] });
  }, [queryClient]);

  const print = useCallback(() => {
    window.dispatchEvent(new Event('cockpit:load-all'));
    const started = Date.now();
    const timer = setInterval(() => {
      if (queryClient.isFetching({ queryKey: ['cockpit', 'hr'] }) === 0 || Date.now() - started > 15000) {
        clearInterval(timer);
        setTimeout(() => window.print(), 600);
      }
    }, 250);
  }, [queryClient]);

  const departments = (options.data?.departments || []).map((d) => ({ id: d.name, name: d.name }));
  const holiday = highlights.data?.holiday;
  const today = highlights.data?.today;

  return (
    <Box className="cockpit-root" sx={{ bgcolor: INK.page, mx: { xs: -2, sm: -3 }, mt: { xs: -2, sm: -3 }, px: { xs: 2, sm: 3 }, pt: { xs: 2, sm: 3 }, pb: 6, minHeight: '100%' }}>
      <CockpitHeader
        title="HR Cockpit"
        subtitle={`Mart agents' attendance, on one page${today ? ` · today ${shortDate(today)}` : ''}`}
        state={state}
        segmentOptions={[]}
        placeFilter={{ label: 'Mart', value: martId, onChange: setMartId, options: options.data?.marts || [] }}
        personFilter={{ label: 'Department', value: department, onChange: setDepartment, options: departments }}
        onRefresh={refresh}
        refreshing={fetching}
        updatedAt={highlights.data?.generatedAt}
        onPrint={print}
      />

      <SectionNav sections={SECTIONS} />

      <Section id="overview" title="Highlights" hint='Today, Pakistan time · "missed end duty" is yesterday · click a card to jump to its section'>
        {holiday && (
          <Card accent={MEANING.ok} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2, py: 1.5 }}>
            <CelebrationOutlinedIcon sx={{ color: MEANING.ok }} />
            <Box sx={{ fontSize: 14, color: INK.primary }}>
              <strong>Company holiday today: {holiday}.</strong> Nobody is marked absent; every agent counts as off.
            </Box>
          </Card>
        )}
        {highlights.isError && !highlights.data ? (
          <ErrorState onRetry={() => highlights.refetch()} />
        ) : (
          // 8 cards: 4 per row on large screens gives two full rows (Sales uses 5 for its 10).
          <KpiGrid xlColumns={4}>
            {highlights.data
              ? highlights.data.cards.map((c) => {
                const [value, unit] = (CARD_VALUE[c.key] || ((x) => [int(x.value)]))(c);
                const card = { ...c, deltaPercent: c.delta };
                return (
                  <KpiCard
                    key={c.key} card={card} value={value} unit={unit}
                    sparkFormat={c.kind === 'time' ? clock : int}
                    sparkFromZero={c.kind !== 'time'}
                    onClick={() => jumpTo(c.section)}
                  />
                );
              })
              : Array.from({ length: 8 }).map((_, i) => <KpiSkeleton key={i} />)}
          </KpiGrid>
        )}

        <Box sx={{ mt: 3 }}>
          <Box className="cockpit-attention-head" sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1, mb: 1.5 }}>
            <Box component="h2" sx={{ m: 0, fontSize: 17, fontWeight: 800, color: INK.primary }}>Needs attention</Box>
            <Box sx={{ fontSize: 13, color: INK.muted }}>Most urgent first · absences use saved records + today's live status</Box>
          </Box>
          {attention.isError && !attention.data
            ? <ErrorState onRetry={() => attention.refetch()} height={140} />
            : <AlertsPanel data={attention.data} loading={!attention.data} onView={jumpTo} />}
        </Box>
      </Section>

      <Section id="today" title="Today" hint="Live board: in / late / absent, by department and mart">
        <ComingNext what="the live board of who is in, late or absent, grouped by department and mart." />
      </Section>
      <Section id="trend" title="Trend" hint="Attendance %, late % and absent % by day">
        <ComingNext what="attendance %, late % and absent % per day for the selected period." />
      </Section>
      <Section id="punctuality" title="Punctuality" hint="Late count by agent · check-in time distribution">
        <ComingNext what="late count by agent and the distribution of check-in times." />
      </Section>
      <Section id="agents" title="Agent scorecard" hint="Days present, late, absent, missed end duty, average hours">
        <ComingNext what="the sortable agent scorecard with a day-by-day panel per agent." />
      </Section>
      <Section id="locations" title="Locations" hint="Check-ins by mart · distance-from-mart outliers">
        <ComingNext what="check-ins by mart and the distance-from-mart outliers." />
      </Section>
    </Box>
  );
}
