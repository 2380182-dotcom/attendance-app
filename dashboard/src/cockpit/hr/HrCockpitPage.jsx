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
import { LazySection, Section, SectionNav } from '../components/Sections';
import KpiCard, { KpiGrid, KpiSkeleton } from '../components/KpiCard';
import AlertsPanel from '../components/AlertsPanel';
import { ErrorState } from '../components/States';
import { hrCockpitApi } from '../api';
import { useCockpitFilters } from '../useCockpitFilters';
import { INK, MEANING } from '../tokens';
import { int, shortDate } from '../format';
import Card from '../components/Card';
import { clock } from './hrFormat';
import TodaySection from './TodaySection';
import TrendSection from './TrendSection';
import PunctualitySection from './PunctualitySection';
import AgentsSection from './AgentsSection';
import LocationsSection from './LocationsSection';

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'today', label: 'Today' },
  { id: 'trend', label: 'Trend' },
  { id: 'punctuality', label: 'Punctuality' },
  { id: 'agents', label: 'Agents' },
  { id: 'locations', label: 'Locations' },
];

const n = (v, one, many) => (Math.round(v) === 1 ? one : many);

/** How each HR card shows its number. */
const CARD_VALUE = {
  checked_in: (c) => (c.secondaryValue == null
    ? [int(c.value), n(c.value, 'agent', 'agents')]
    : [`${int(c.value)} / ${int(c.secondaryValue)}`, n(c.secondaryValue, 'agent', 'agents')]),
  late: (c) => [int(c.value), n(c.value, 'agent', 'agents')],
  absent: (c) => (c.value == null ? ['Off'] : [int(c.value), n(c.value, 'agent', 'agents')]),
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
  // Lower sections load one by one as they come near the screen, after the top is done.
  const ready = attention.isSuccess || attention.isError;
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
  const rangeLabel = `${shortDate(filters.startDate)} – ${shortDate(filters.endDate)}`;

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
                    disabled={c.key === 'absent' && c.value == null}
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
            <Box sx={{ fontSize: 13, color: INK.muted }}>
              {highlights.data?.absenceTracking === false
                ? 'Most urgent first · absence streaks are not shown while absence tracking is off'
                : "Most urgent first · absences use saved records + today's live status"}
            </Box>
          </Box>
          {attention.isError && !attention.data
            ? <ErrorState onRetry={() => attention.refetch()} height={140} />
            : <AlertsPanel data={attention.data} loading={!attention.data} onView={jumpTo} />}
        </Box>
      </Section>

      <Section id="today" title="Today" hint={`Live board${today ? ` · ${shortDate(today)}` : ''} · Pakistan time · click a state or a bar to filter the list`}>
        <LazySection>
          {(visible) => <SectionLoader name="today" visible={visible && ready} sectionQuery={sectionQuery}
            render={(q) => <TodaySection query={q} />} />}
        </LazySection>
      </Section>
      <Section id="trend" title="Trend" hint={`${rangeLabel} · ${highlights.data?.absenceTracking === false ? 'late % by day' : 'attendance %, late % and absent % by day'}`}>
        <LazySection>
          {(visible) => <SectionLoader name="trend" visible={visible && ready} sectionQuery={sectionQuery}
            render={(q) => <TrendSection query={q} />} />}
        </LazySection>
      </Section>
      <Section id="punctuality" title="Punctuality" hint={`${rangeLabel} · late = after shift start + grace, Pakistan time`}>
        <LazySection>
          {(visible) => <SectionLoader name="punctuality" visible={visible && ready} sectionQuery={sectionQuery}
            render={(q) => <PunctualitySection query={q} />} />}
        </LazySection>
      </Section>
      <Section id="agents" title="Agent scorecard" hint={`${rangeLabel} · sortable · search · click a name for day-by-day detail`}>
        <LazySection>
          {(visible) => <SectionLoader name="agents" visible={visible && ready} sectionQuery={sectionQuery}
            render={(q) => <AgentsSection query={q} filters={filters} />} />}
        </LazySection>
      </Section>
      <Section id="locations" title="Locations" hint={`${rangeLabel} · check-ins by mart · distance-from-mart outliers`}>
        <LazySection>
          {(visible) => <SectionLoader name="locations" visible={visible && ready} sectionQuery={sectionQuery}
            render={(q) => <LocationsSection query={q} />} />}
        </LazySection>
      </Section>
    </Box>
  );
}

/** Lazy section: its query runs only once the section is near the screen and the page is ready. */
function SectionLoader({ name, visible, sectionQuery, render }) {
  const query = useQuery(sectionQuery(name, visible));
  return render({ ...query, isLoading: !visible || query.isLoading });
}
