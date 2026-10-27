import React, { useCallback, useMemo, useState } from 'react';
import { Box } from '@mui/material';
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
import DrillDrawer from '../components/DrillDrawer';
import { ErrorState } from '../components/States';
import SalesSection from './SalesSection';
import ReturnsSection from './ReturnsSection';
import CoverageSection from './CoverageSection';
import ScorecardSection from './ScorecardSection';
import LmtStockSection from './LmtStockSection';
import ProductsSection from './ProductsSection';
import { salesCockpitApi } from '../api';
import { useCockpitFilters } from '../useCockpitFilters';
import { INK } from '../tokens';
import { int, rsCompact, shortDate } from '../format';

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'sales', label: 'Sales' },
  { id: 'returns', label: 'Returns' },
  { id: 'coverage', label: 'Shop coverage' },
  { id: 'salesmen', label: 'Salesmen' },
  { id: 'lmt', label: 'LMT stock' },
  { id: 'products', label: 'Products' },
];

const SEGMENTS = [
  { value: 'ALL', label: 'All' },
  { value: 'LOCAL', label: 'Local' },
  { value: 'LMT', label: 'LMT' },
];

const n = (v, one, many) => (Math.round(v) === 1 ? one : many);

/** How each highlight card shows its number. */
const CARD_VALUE = {
  net_sales: (c) => [rsCompact(c.value)],
  sale_return: (c) => [rsCompact(c.value), 'sales'],
  rbe: (c) => [int(c.value), n(c.value, 'unit', 'units')],
  visits_today: (c) => [`${int(c.value)} / ${int(c.secondaryValue)}`, n(c.secondaryValue, 'shop', 'shops')],
  not_visited_today: (c) => [int(c.value), n(c.value, 'shop', 'shops')],
  off_location: (c) => [int(c.value), n(c.value, 'attempt', 'attempts')],
  without_scan: (c) => [int(c.value), n(c.value, 'voucher', 'vouchers')],
  feedback_today: (c) => [int(c.value), n(c.value, 'visit', 'visits')],
  lmt_missing: (c) => [int(c.value), n(c.value, 'unit', 'units')],
  active_salesmen: (c) => [`${int(c.value)} / ${int(c.secondaryValue)}`],
};
const MONEY_CARDS = new Set(['net_sales', 'sale_return']);

function jumpTo(id) {
  const el = document.getElementById(id);
  if (!el) return;
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
}

export default function SalesCockpitPage() {
  const state = useCockpitFilters();
  const { filters, segment, areaId, setAreaId, agentId, setAgentId, autoRefresh } = state;
  const queryClient = useQueryClient();
  const [drill, setDrill] = useState(null);
  const refetchInterval = autoRefresh > 0 ? autoRefresh * 60 * 1000 : false;

  const sectionQuery = (name, enabled = true) => ({
    queryKey: ['cockpit', 'sales', name, filters],
    queryFn: () => salesCockpitApi.section(name, filters),
    enabled,
    refetchInterval,
    staleTime: 60 * 1000,
    placeholderData: keepPreviousData,
  });

  const options = useQuery({ queryKey: ['cockpit', 'sales', 'options'], queryFn: salesCockpitApi.filterOptions, staleTime: 5 * 60 * 1000 });
  const highlights = useQuery(sectionQuery('highlights'));
  // Load one heavy section at a time: Needs attention starts only after
  // Highlights has answered (the server also caps concurrent cockpit work).
  const attention = useQuery(sectionQuery('attention', highlights.isSuccess || highlights.isError));
  const ready = attention.isSuccess || attention.isError;
  const fetching = useIsFetching({ queryKey: ['cockpit', 'sales'] }) > 0;

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['cockpit', 'sales'] });
  }, [queryClient]);

  // Load every lazy section, wait for the data, then print.
  const print = useCallback(() => {
    window.dispatchEvent(new Event('cockpit:load-all'));
    const started = Date.now();
    const timer = setInterval(() => {
      if (queryClient.isFetching({ queryKey: ['cockpit', 'sales'] }) === 0 || Date.now() - started > 15000) {
        clearInterval(timer);
        setTimeout(() => window.print(), 600);
      }
    }, 250);
  }, [queryClient]);

  const salesmen = useMemo(() => (options.data?.salesmen || [])
    .filter((s) => segment === 'ALL' || s.extra === segment)
    .map((s) => ({ id: s.id, name: s.name, hint: s.extra === 'LOCAL' ? 'Local' : 'LMT' })), [options.data, segment]);

  const setSegment = (v) => {
    state.setSegment(v);
    const chosen = options.data?.salesmen?.find((s) => s.id === agentId);
    if (chosen && v !== 'ALL' && chosen.extra !== v) setAgentId('');
  };

  const rangeLabel = filters.startDate === filters.endDate
    ? shortDate(filters.endDate)
    : `${shortDate(filters.startDate)} – ${shortDate(filters.endDate)}`;

  return (
    <Box className="cockpit-root" sx={{ bgcolor: INK.page, mx: { xs: -2, sm: -3 }, mt: { xs: -2, sm: -3 }, px: { xs: 2, sm: 3 }, pt: { xs: 2, sm: 3 }, pb: 6, minHeight: '100%' }}>
      <CockpitHeader
        title="Sales Cockpit"
        subtitle={`Everything that matters today, on one page · ${rangeLabel}`}
        state={{ ...state, setSegment }}
        segmentOptions={SEGMENTS}
        placeFilter={{ label: 'Area', value: areaId, onChange: setAreaId, options: options.data?.areas || [] }}
        personFilter={{ label: 'Salesman', value: agentId, onChange: setAgentId, options: salesmen }}
        onRefresh={refresh}
        refreshing={fetching}
        updatedAt={highlights.data?.generatedAt}
        onPrint={print}
      />

      <SectionNav sections={SECTIONS} />

      <Section id="overview" title="Highlights" hint={`${rangeLabel} · "today" cards use Pakistan time · click a card to jump to its section`}>
        {highlights.isError && !highlights.data ? (
          <ErrorState onRetry={() => highlights.refetch()} />
        ) : (
          <KpiGrid>
            {highlights.data
              ? highlights.data.cards.map((c) => {
                const [value, unit] = (CARD_VALUE[c.key] || ((x) => [int(x.value)]))(c);
                return (
                  <KpiCard
                    key={c.key} card={c} value={value} unit={unit}
                    sparkFormat={MONEY_CARDS.has(c.key) ? rsCompact : int}
                    onClick={() => jumpTo(c.section)}
                  />
                );
              })
              : Array.from({ length: 10 }).map((_, i) => <KpiSkeleton key={i} />)}
          </KpiGrid>
        )}

        <Box sx={{ mt: 3 }}>
          <Box className="cockpit-attention-head" sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1, mb: 1.5 }}>
            <Box component="h2" sx={{ m: 0, fontSize: 17, fontWeight: 800, color: INK.primary }}>Needs attention</Box>
            <Box sx={{ fontSize: 13, color: INK.muted }}>
              Most urgent first · return alert above {attention.data ? `${attention.data.returnAlertPercent}%` : '…'} (LMT Settings)
            </Box>
          </Box>
          {attention.isError && !attention.data
            ? <ErrorState onRetry={() => attention.refetch()} height={140} />
            : <AlertsPanel data={attention.data} loading={!attention.data} onView={jumpTo} />}
        </Box>
      </Section>

      <Section id="sales" title="Sales" hint={`${rangeLabel} · net = sales minus returns · click any bar, slice or row for its vouchers`}>
        <LazySection>
          {(visible) => <SalesSectionLoader visible={visible && ready} sectionQuery={sectionQuery} filters={filters} onDrill={setDrill} />}
        </LazySection>
      </Section>

      <Section id="returns" title="Returns" hint={`${rangeLabel} · return % by shop, salesman and product · expired returns vs return before expiry`}>
        <LazySection>
          {(visible) => <SectionLoader name="returns" visible={visible && ready} sectionQuery={sectionQuery}
            render={(query) => <ReturnsSection query={query} onDrill={setDrill} />} />}
        </LazySection>
      </Section>
      <Section id="coverage" title="Shop coverage" hint={`${rangeLabel} · visited = QR scan or feedback by the assigned salesman`}>
        <LazySection>
          {(visible) => <SectionLoader name="coverage" visible={visible && ready} sectionQuery={sectionQuery}
            render={(query, on) => <CoverageSection query={query} filters={filters} visible={on} onDrill={setDrill} />} />}
        </LazySection>
      </Section>
      <Section id="salesmen" title="Salesman scorecard" hint={`${rangeLabel} · sortable · search · click a name for day-by-day detail`}>
        <LazySection>
          {(visible) => <SectionLoader name="salesmen" visible={visible && ready} sectionQuery={sectionQuery}
            render={(query) => <ScorecardSection query={query} filters={filters} />} />}
        </LazySection>
      </Section>
      <Section id="lmt" title="LMT stock" hint={`${rangeLabel} · opening stock vs sold / returned / unsold / replaced / missing`}>
        <LazySection>
          {(visible) => <SectionLoader name="lmt-stock" visible={visible && ready} sectionQuery={sectionQuery}
            render={(query) => <LmtStockSection query={query} />} />}
        </LazySection>
      </Section>
      <Section id="products" title="Products" hint={`${rangeLabel} · product mix · ABC analysis (A = top 80% of sales value) · slow products`}>
        <LazySection>
          {(visible) => <SectionLoader name="products" visible={visible && ready} sectionQuery={sectionQuery}
            render={(query) => <ProductsSection query={query} onDrill={setDrill} />} />}
        </LazySection>
      </Section>

      <DrillDrawer drill={drill} filters={filters} onClose={() => setDrill(null)} />
    </Box>
  );
}

function SalesSectionLoader({ visible, sectionQuery, filters, onDrill }) {
  const query = useQuery(sectionQuery('sales', visible));
  return <SalesSection query={{ ...query, isLoading: !visible || query.isLoading }} filters={filters} onDrill={onDrill} />;
}

/** Generic lazy section: its query runs only once the section is near the screen and the page is ready. */
function SectionLoader({ name, visible, sectionQuery, render }) {
  const query = useQuery(sectionQuery(name, visible));
  return render({ ...query, isLoading: !visible || query.isLoading }, visible);
}
