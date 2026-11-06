import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useQuery, useQueryClient, useIsFetching, keepPreviousData } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody, TableSortLabel,
  CircularProgress, Alert, Stack, MenuItem, Select, InputLabel, FormControl, TextField, Chip, TablePagination,
  Tabs, Tab, Button, ToggleButton, ToggleButtonGroup, LinearProgress,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import { routesApi } from '../../services/routesApi';

const MAX_DAYS = 92;
const PAGE_SIZE = 50;
const TYPE_LABEL = { LOCAL: 'Local', LMT: 'LMT' };
const REASON_LABEL = { NO_SALESMAN: 'No salesman', SALESMAN_INACTIVE: 'Salesman inactive', NOT_A_SALESMAN: 'Not a salesman account' };
const PRESETS = [
  { key: 'today', label: 'Today' },
  { key: 'yesterday', label: 'Yesterday' },
  { key: 'week', label: 'This week' },
  { key: 'month', label: 'This month' },
  { key: 'custom', label: 'Custom' },
];

/** Pakistan "today" in the browser, whatever the viewer's own timezone (en-CA formats as YYYY-MM-DD). */
function pakistanToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Karachi' }).format(new Date());
}

/** Weeks start on Monday. */
function presetRange(key, today) {
  const t = dayjs(today);
  if (key === 'yesterday') {
    const y = t.subtract(1, 'day').format('YYYY-MM-DD');
    return { startDate: y, endDate: y };
  }
  if (key === 'week') return { startDate: t.subtract((t.day() + 6) % 7, 'day').format('YYYY-MM-DD'), endDate: today };
  if (key === 'month') return { startDate: t.startOf('month').format('YYYY-MM-DD'), endDate: today };
  return { startDate: today, endDate: today };
}

const fmtDate = (d) => (d ? dayjs(d).format('DD MMM YYYY') : 'Never');
const fmtSince = (n) => (n == null ? 'Never' : n === 0 ? 'Today' : `${n} day${n === 1 ? '' : 's'}`);
const fmtPct = (p) => (p == null ? '—' : `${p.toFixed(1)}%`);

function StatCard({ label, value, hint, color }) {
  return (
    <Paper sx={{ p: 2.5, flex: '1 1 160px', minWidth: 150 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold" color={color} sx={{ fontVariantNumeric: 'tabular-nums' }}>{value ?? '—'}</Typography>
      {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
    </Paper>
  );
}

function CoverageBar({ value }) {
  if (value == null) return <Typography variant="body2" color="text.secondary">—</Typography>;
  const color = value >= 80 ? 'success' : value >= 50 ? 'warning' : 'error';
  return (
    <Stack direction="row" spacing={1} sx={{ minWidth: 140, alignItems: 'center' }}>
      <LinearProgress variant="determinate" value={Math.min(value, 100)} color={color} sx={{ flex: 1, height: 6, borderRadius: 3 }} />
      <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', width: 52, textAlign: 'right' }}>{fmtPct(value)}</Typography>
    </Stack>
  );
}

function Sortable({ label, sortKey, sort, onSort, align }) {
  return (
    <TableCell align={align} sortDirection={sort.key === sortKey ? sort.dir : false}>
      <TableSortLabel active={sort.key === sortKey} direction={sort.key === sortKey ? sort.dir : 'asc'} onClick={() => onSort(sortKey)}>
        {label}
      </TableSortLabel>
    </TableCell>
  );
}

/**
 * Sales > Routes. Every active shop assigned to an active salesman is due on
 * each of that salesman's working days, except company holidays. "Visited" =
 * a successful QR scan or a feedback by the shop's own salesman that day
 * (Pakistan date), the same rule as the Sales cockpit. Shops with no salesman
 * (or an inactive one) are listed separately under Unassigned.
 *
 * The server caches each company + date range for 90 seconds; Refresh asks
 * again (like the cockpit), and "Updated at" shows when the numbers were made.
 */
export default function RoutesPage() {
  const queryClient = useQueryClient();
  const today = pakistanToday();
  const [preset, setPreset] = useState('today');
  const [custom, setCustom] = useState({ startDate: dayjs(today).subtract(6, 'day').format('YYYY-MM-DD'), endDate: today });
  const [tab, setTab] = useState('salesmen');
  const [agentId, setAgentId] = useState('');
  const [type, setType] = useState('ALL');
  const [area, setArea] = useState('');
  const [minDays, setMinDays] = useState('');
  const [sort, setSort] = useState({ key: null, dir: 'asc' });
  const [page, setPage] = useState(0);
  const [salesmanSort, setSalesmanSort] = useState({ key: 'salesman', dir: 'asc' });
  const [unassignedPage, setUnassignedPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  const range = preset === 'custom' ? custom : presetRange(preset, today);
  const rangeDays = dayjs(range.endDate).diff(dayjs(range.startDate), 'day') + 1;
  const rangeError = preset !== 'custom' ? ''
    : !dayjs(range.startDate).isValid() || !dayjs(range.endDate).isValid() ? 'Choose both dates.'
      : rangeDays < 1 ? 'The start date must be on or before the end date.'
        : range.endDate > today ? 'The end date cannot be after today.'
          : rangeDays > MAX_DAYS ? `Choose at most ${MAX_DAYS} days (this range is ${rangeDays}).` : '';
  const ready = !rangeError;
  const single = range.startDate === range.endDate;

  const filters = { ...range, agentId, type, area, minDaysSince: minDays, sort: sort.key, dir: sort.dir };
  const resetPage = (fn) => (v) => { fn(v); setPage(0); };

  const summary = useQuery({
    queryKey: ['routes', 'summary', range.startDate, range.endDate],
    queryFn: () => routesApi.summary(range.startDate, range.endDate),
    enabled: ready,
  });
  const notVisited = useQuery({
    queryKey: ['routes', 'not-visited', filters, page],
    queryFn: () => routesApi.notVisited(filters, page, PAGE_SIZE),
    enabled: ready && tab === 'not-visited',
    placeholderData: keepPreviousData,
  });
  const unassigned = useQuery({
    queryKey: ['routes', 'unassigned', range.startDate, range.endDate],
    queryFn: () => routesApi.unassigned(range.startDate, range.endDate),
    enabled: ready && tab === 'unassigned',
  });
  const fetching = useIsFetching({ queryKey: ['routes'] }) > 0;
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['routes'] });

  const s = summary.data;
  const salesmen = useMemo(() => {
    const rows = [...(s?.salesmen || [])];
    const { key, dir } = salesmanSort;
    rows.sort((a, b) => {
      const av = a[key], bv = b[key];
      const c = typeof av === 'string' || typeof bv === 'string'
        ? String(av ?? '').localeCompare(String(bv ?? ''))
        : (av ?? -1) - (bv ?? -1);
      return dir === 'asc' ? c : -c;
    });
    return rows;
  }, [s, salesmanSort]);
  const salesmanOptions = (s?.salesmanOptions || []).filter((o) => type === 'ALL' || o.type === type);

  const onSort = (key) => {
    setSort((cur) => (cur.key === key ? { key, dir: cur.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }));
    setPage(0);
  };
  const onSalesmanSort = (key) => setSalesmanSort((cur) => (cur.key === key ? { key, dir: cur.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }));

  const openSalesman = (row) => {
    setAgentId(row.agentId);
    setType('ALL');
    setArea('');
    setMinDays('');
    setPage(0);
    setTab('not-visited');
  };

  const changePreset = (key) => {
    if (!key) return;
    setPreset(key);
    setPage(0);
    setUnassignedPage(0);
  };

  const exportExcel = async () => {
    setExporting(true);
    setExportError('');
    try {
      const { blob, fileName } = await routesApi.exportNotVisited(filters);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      setExportError('The Excel file could not be made. Try again in a few seconds.');
    } finally {
      setExporting(false);
    }
  };

  const total = s?.total;
  const holidayToday = single && s?.holidays?.length > 0 ? s.holidays[0] : null;
  const unit = single ? 'shops' : 'shop-days';

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mb: 2, justifyContent: 'space-between', alignItems: { md: 'flex-start' } }}>
          <Box>
            <Typography variant="h5" gutterBottom>Routes</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 760 }}>
              Every active shop is due on each working day of its salesman, except company holidays. A shop counts as
              visited when its own salesman made a successful QR scan or left feedback there that day (Pakistan time),
              the same rule as the Sales cockpit.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1.5} sx={{ flexShrink: 0, alignItems: 'center', alignSelf: { xs: 'flex-start', md: 'auto' } }}>
            {s?.updatedAt && (
              <Typography variant="body2" color="text.secondary" data-testid="routes-updated-at">
                Updated at {dayjs(s.updatedAt).format('HH:mm')}
              </Typography>
            )}
            <Button variant="outlined" startIcon={fetching ? <CircularProgress size={16} /> : <RefreshIcon />} onClick={refresh} disabled={fetching || !ready}>
              Refresh
            </Button>
          </Stack>
        </Stack>

        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} useFlexGap sx={{ alignItems: { md: 'center' }, flexWrap: 'wrap' }}>
            <ToggleButtonGroup size="small" exclusive value={preset} onChange={(e, v) => changePreset(v)} aria-label="Dates" sx={{ flexWrap: 'wrap' }}>
              {PRESETS.map((p) => <ToggleButton key={p.key} value={p.key}>{p.label}</ToggleButton>)}
            </ToggleButtonGroup>
            {preset === 'custom' ? (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <DatePicker label="From" value={dayjs(custom.startDate)} maxDate={dayjs(today)} format="DD MMM YYYY"
                  onChange={(v) => { setCustom((c) => ({ ...c, startDate: v?.isValid() ? v.format('YYYY-MM-DD') : '' })); setPage(0); }}
                  slotProps={{ textField: { size: 'small' } }} />
                <DatePicker label="To" value={dayjs(custom.endDate)} maxDate={dayjs(today)} format="DD MMM YYYY"
                  onChange={(v) => { setCustom((c) => ({ ...c, endDate: v?.isValid() ? v.format('YYYY-MM-DD') : '' })); setPage(0); }}
                  slotProps={{ textField: { size: 'small' } }} />
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {single ? fmtDate(range.startDate) : `${fmtDate(range.startDate)} – ${fmtDate(range.endDate)} · ${rangeDays} days`}
              </Typography>
            )}
          </Stack>
          {rangeError && <Alert severity="warning" sx={{ mt: 2 }}>{rangeError}</Alert>}
        </Paper>

        {summary.isError && <Alert severity="error" sx={{ mb: 2 }}>The routes could not be loaded. Press Refresh to try again.</Alert>}
        {summary.isLoading && ready && <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}><CircularProgress /></Box>}

        {s && (
          <>
            {holidayToday && (
              <Alert severity="info" icon={<EventBusyIcon />} sx={{ mb: 2 }}>
                Company holiday: <strong>{holidayToday.name}</strong>. No shops are due on {fmtDate(holidayToday.date)}.
              </Alert>
            )}
            {!single && s.holidays.length > 0 && (
              <Alert severity="info" sx={{ mb: 2 }}>
                Company holidays skipped: {s.holidays.map((h) => `${fmtDate(h.date)} (${h.name})`).join(', ')}.
              </Alert>
            )}

            <Stack direction="row" spacing={2} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}>
              <StatCard label="Assigned shops" value={total.assignedShops} hint={`${s.salesmen.length} salesmen`} />
              <StatCard label={single ? 'Visited' : 'Visited shop-days'} value={total.visited} color="success.main"
                hint={single ? undefined : `of ${total.due} due`} />
              <StatCard label={single ? 'Not visited' : 'Not visited shop-days'} value={total.notVisited} color="error.main"
                hint={single ? undefined : `${total.shopsNotVisited} shops not visited at all`} />
              <StatCard label="Coverage" value={fmtPct(total.coveragePercent)} hint={total.dayOff ? 'Nothing due' : `visited ÷ due ${unit}`} />
              <StatCard label="Unassigned shops" value={s.unassigned.shops} color="warning.main"
                hint={`${s.unassigned.notVisited} not visited`} />
            </Stack>

            <Tabs value={tab} onChange={(e, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
              <Tab value="salesmen" label="Salesmen" />
              <Tab value="not-visited" label="Not visited" />
              <Tab value="unassigned" label={`Unassigned (${s.unassigned.shops})`} />
            </Tabs>

            {tab === 'salesmen' && (
              <TableContainer component={Paper}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <Sortable label="Salesman" sortKey="salesman" sort={salesmanSort} onSort={onSalesmanSort} />
                      <Sortable label="Type" sortKey="type" sort={salesmanSort} onSort={onSalesmanSort} />
                      <Sortable label="Assigned shops" sortKey="assignedShops" sort={salesmanSort} onSort={onSalesmanSort} align="right" />
                      {!single && <Sortable label="Due shop-days" sortKey="due" sort={salesmanSort} onSort={onSalesmanSort} align="right" />}
                      <Sortable label="Visited" sortKey="visited" sort={salesmanSort} onSort={onSalesmanSort} align="right" />
                      <Sortable label="Not visited" sortKey="notVisited" sort={salesmanSort} onSort={onSalesmanSort} align="right" />
                      <Sortable label="Coverage" sortKey="coveragePercent" sort={salesmanSort} onSort={onSalesmanSort} />
                      {!single && <Sortable label="Shops not visited at all" sortKey="shopsNotVisited" sort={salesmanSort} onSort={onSalesmanSort} align="right" />}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {salesmen.length === 0 && (
                      <TableRow><TableCell colSpan={8} align="center" sx={{ py: 4, color: 'text.secondary' }}>No shops are assigned to active salesmen.</TableCell></TableRow>
                    )}
                    {salesmen.map((r) => (
                      <TableRow key={r.agentId} hover sx={{ cursor: 'pointer' }} onClick={() => openSalesman(r)}
                        title={`Show ${r.salesman}'s shops that were not visited`}>
                        <TableCell sx={{ fontWeight: 500 }}>{r.salesman}</TableCell>
                        <TableCell><Chip size="small" variant="outlined" label={TYPE_LABEL[r.type]} /></TableCell>
                        <TableCell align="right">{r.assignedShops}</TableCell>
                        {!single && <TableCell align="right">{r.due}</TableCell>}
                        <TableCell align="right">{r.dayOff ? '—' : r.visited}</TableCell>
                        <TableCell align="right">{r.dayOff ? '—' : r.notVisited}</TableCell>
                        <TableCell>{r.dayOff ? <Chip size="small" label="Day off" /> : <CoverageBar value={r.coveragePercent} />}</TableCell>
                        {!single && <TableCell align="right">{r.shopsNotVisited}</TableCell>}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}

            {tab === 'not-visited' && (
              <Paper>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} useFlexGap sx={{ p: 2, alignItems: { md: 'center' }, flexWrap: 'wrap' }}>
                  <FormControl size="small" sx={{ minWidth: 120 }}>
                    <InputLabel id="routes-type">Type</InputLabel>
                    <Select labelId="routes-type" label="Type" value={type} onChange={(e) => {
                      const v = e.target.value;
                      setType(v);
                      const chosen = s.salesmanOptions.find((o) => o.id === agentId);
                      if (chosen && v !== 'ALL' && chosen.type !== v) setAgentId('');
                      setPage(0);
                    }}>
                      <MenuItem value="ALL">All</MenuItem>
                      <MenuItem value="LOCAL">Local</MenuItem>
                      <MenuItem value="LMT">LMT</MenuItem>
                    </Select>
                  </FormControl>
                  <FormControl size="small" sx={{ minWidth: 200 }}>
                    <InputLabel id="routes-salesman">Salesman</InputLabel>
                    <Select labelId="routes-salesman" label="Salesman" value={agentId} onChange={(e) => resetPage(setAgentId)(e.target.value)}>
                      <MenuItem value="">All salesmen</MenuItem>
                      {salesmanOptions.map((o) => <MenuItem key={o.id} value={o.id}>{o.name} · {TYPE_LABEL[o.type]}</MenuItem>)}
                    </Select>
                  </FormControl>
                  <FormControl size="small" sx={{ minWidth: 180 }}>
                    <InputLabel id="routes-area">Area</InputLabel>
                    <Select labelId="routes-area" label="Area" value={area} onChange={(e) => resetPage(setArea)(e.target.value)}>
                      <MenuItem value="">All areas</MenuItem>
                      {s.areas.map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
                    </Select>
                  </FormControl>
                  <TextField size="small" type="number" label="Not visited for (days)" placeholder="any" value={minDays} sx={{ width: 190 }}
                    slotProps={{ htmlInput: { min: 0 } }}
                    helperText={minDays !== '' ? `${minDays}+ days or never` : ' '}
                    onChange={(e) => resetPage(setMinDays)(e.target.value === '' ? '' : String(Math.max(0, Math.floor(Number(e.target.value)))))} />
                  <Box sx={{ flex: 1 }} />
                  <Button variant="contained" startIcon={exporting ? <CircularProgress size={16} color="inherit" /> : <FileDownloadIcon />}
                    onClick={exportExcel} disabled={exporting || !notVisited.data || notVisited.data.totalElements === 0}>
                    Export Excel
                  </Button>
                </Stack>
                {exportError && <Alert severity="error" sx={{ mx: 2, mb: 2 }}>{exportError}</Alert>}
                {notVisited.isError && <Alert severity="error" sx={{ mx: 2, mb: 2 }}>The list could not be loaded. Press Refresh to try again.</Alert>}
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <Sortable label="Shop" sortKey="shopName" sort={sort} onSort={onSort} />
                        <Sortable label="Code" sortKey="shopCode" sort={sort} onSort={onSort} />
                        <Sortable label="Area" sortKey="area" sort={sort} onSort={onSort} />
                        <Sortable label="Salesman" sortKey="salesman" sort={sort} onSort={onSort} />
                        <Sortable label="Type" sortKey="type" sort={sort} onSort={onSort} />
                        <Sortable label="Last visit" sortKey="lastVisit" sort={sort} onSort={onSort} />
                        <Sortable label="Days since last visit" sortKey="daysSinceLastVisit" sort={sort} onSort={onSort} align="right" />
                        {!single && <Sortable label="Visited / due days" sortKey="visitedDays" sort={sort} onSort={onSort} align="right" />}
                        {!single && <Sortable label="Not visited days" sortKey="missedDays" sort={sort} onSort={onSort} align="right" />}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {notVisited.isLoading && (
                        <TableRow><TableCell colSpan={9} align="center" sx={{ py: 4 }}><CircularProgress size={28} /></TableCell></TableRow>
                      )}
                      {notVisited.data?.content.length === 0 && (
                        <TableRow><TableCell colSpan={9} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                          {total.dayOff ? 'Nothing was due on these dates.' : 'Every shop matching these filters was visited.'}
                        </TableCell></TableRow>
                      )}
                      {notVisited.data?.content.map((r) => (
                        <TableRow key={r.shopId} hover>
                          <TableCell sx={{ fontWeight: 500 }}>{r.shopName}</TableCell>
                          <TableCell sx={{ fontFamily: 'monospace' }}>{r.shopCode}</TableCell>
                          <TableCell>{r.area}</TableCell>
                          <TableCell>{r.salesman}</TableCell>
                          <TableCell>{TYPE_LABEL[r.type]}</TableCell>
                          <TableCell>{fmtDate(r.lastVisit)}</TableCell>
                          <TableCell align="right">
                            {r.daysSinceLastVisit == null ? <Chip size="small" color="error" variant="outlined" label="Never" /> : fmtSince(r.daysSinceLastVisit)}
                          </TableCell>
                          {!single && <TableCell align="right">{r.visitedDays} / {r.dueDays}</TableCell>}
                          {!single && <TableCell align="right">{r.missedDays}</TableCell>}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                <TablePagination component="div" count={notVisited.data?.totalElements || 0} page={page} rowsPerPage={PAGE_SIZE}
                  rowsPerPageOptions={[PAGE_SIZE]} onPageChange={(e, p) => setPage(p)} />
              </Paper>
            )}

            {tab === 'unassigned' && (
              <Paper>
                <Typography variant="body2" color="text.secondary" sx={{ p: 2, pb: 0 }}>
                  Active shops with no active salesman: {s.unassigned.noSalesman} with no salesman, {s.unassigned.salesmanInactive} whose
                  salesman is inactive{s.unassigned.notASalesman > 0 ? `, ${s.unassigned.notASalesman} assigned to an account that is not a salesman` : ''}.
                  Here a visit by any salesman counts.
                </Typography>
                {unassigned.isError && <Alert severity="error" sx={{ m: 2 }}>The list could not be loaded. Press Refresh to try again.</Alert>}
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Shop</TableCell>
                        <TableCell>Code</TableCell>
                        <TableCell>Area</TableCell>
                        <TableCell>Why unassigned</TableCell>
                        <TableCell>{single ? 'Visited' : 'Visited / due days'}</TableCell>
                        <TableCell>Last visit</TableCell>
                        <TableCell align="right">Days since last visit</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {unassigned.isLoading && (
                        <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4 }}><CircularProgress size={28} /></TableCell></TableRow>
                      )}
                      {unassigned.data?.content.length === 0 && (
                        <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>Every active shop has an active salesman.</TableCell></TableRow>
                      )}
                      {unassigned.data?.content.slice(unassignedPage * PAGE_SIZE, (unassignedPage + 1) * PAGE_SIZE).map((r) => (
                        <TableRow key={r.shopId} hover>
                          <TableCell sx={{ fontWeight: 500 }}>{r.shopName}</TableCell>
                          <TableCell sx={{ fontFamily: 'monospace' }}>{r.shopCode}</TableCell>
                          <TableCell>{r.area}</TableCell>
                          <TableCell>
                            <Chip size="small" variant="outlined" color={r.reason === 'NO_SALESMAN' ? 'warning' : 'default'}
                              label={r.assignedName ? `${REASON_LABEL[r.reason]}: ${r.assignedName}` : REASON_LABEL[r.reason]} />
                          </TableCell>
                          <TableCell>
                            {r.dueDays === 0 ? '—' : single
                              ? (r.visitedDays > 0 ? <Chip size="small" color="success" label="Yes" /> : <Chip size="small" color="error" variant="outlined" label="No" />)
                              : `${r.visitedDays} / ${r.dueDays}`}
                          </TableCell>
                          <TableCell>{fmtDate(r.lastVisit)}</TableCell>
                          <TableCell align="right">{fmtSince(r.daysSinceLastVisit)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                <TablePagination component="div" count={unassigned.data?.content.length || 0} page={unassignedPage} rowsPerPage={PAGE_SIZE}
                  rowsPerPageOptions={[PAGE_SIZE]} onPageChange={(e, p) => setUnassignedPage(p)} />
              </Paper>
            )}
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}
