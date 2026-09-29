import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, Grid, MenuItem, Select, InputLabel, FormControl,
  TextField, InputAdornment, Button, Chip, TablePagination, FormControlLabel, Switch,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import DownloadIcon from '@mui/icons-material/Download';
import { shopVisitApi } from '../../services/lmtApi';
import { agentApi } from '../../services/attendanceApi';
import { sortRows, useSort } from '../../utils/sorting';
import { usePagination } from '../../utils/pagination';
import { toCsv, downloadCsv } from '../../utils/csvExport';
import SortableHeader from '../../components/SortableHeader';

const ALL = 'ALL';
const STATUS_COLOR = { SUCCESS: 'success', INVALID_CODE: 'error', SHOP_INACTIVE: 'error', OUTSIDE_GEOFENCE: 'warning' };
const STATUS_LABEL = { SUCCESS: 'Verified', INVALID_CODE: 'Invalid Code', SHOP_INACTIVE: 'Shop Inactive', OUTSIDE_GEOFENCE: 'Outside Geofence' };
const GEOFENCE_LABEL = { INSIDE: 'Inside', OUTSIDE: 'Outside', NOT_EVALUATED: '—' };

function StatCard({ label, value, color }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold" color={color}>{value}</Typography>
    </Paper>
  );
}

/**
 * QR shop-visit flow (Q4) — the admin's "QR / Shop Visits" report from the
 * spec's example table. Mirrors LmtReconciliationPage's structure (filter
 * bar, stat cards, sortable/paginated table, CSV export) for visual
 * consistency. Salesman names are joined client-side from the roster
 * (GET /lmt/shop-visits itself only carries agentId) — the same pattern
 * LmtReconciliationPage already established. Shop code/name, City and Area
 * filtering happen client-side over the fetched date-range list, the same
 * "backend gives a date range, browser narrows further" split already used
 * there — the backend surface stays small and proven.
 */
export default function QrShopVisitsPage() {
  const navigate = useNavigate();
  const [selectedSalesman, setSelectedSalesman] = useState(ALL);
  const [rangeStart, setRangeStart] = useState(dayjs());
  const [rangeEnd, setRangeEnd] = useState(dayjs());
  const [search, setSearch] = useState('');
  const [city, setCity] = useState(ALL);
  const [area, setArea] = useState(ALL);
  const [failedOnly, setFailedOnly] = useState(false);
  const [sort, onSort] = useSort('scanDate', 'desc');

  const agents = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const salesmen = useMemo(
    () => (agents.data || []).filter((a) => a.role === 'SALESMAN_LMT' || a.role === 'SALESMAN_LOCAL'),
    [agents.data]
  );
  const nameById = useMemo(() => Object.fromEntries(salesmen.map((a) => [a.id, a.name])), [salesmen]);

  const startStr = rangeStart.format('YYYY-MM-DD');
  const endStr = rangeEnd.format('YYYY-MM-DD');
  const report = useQuery({
    queryKey: ['shop-visits', startStr, endStr, selectedSalesman],
    queryFn: () => shopVisitApi.getReport(startStr, endStr, selectedSalesman === ALL ? null : selectedSalesman),
  });

  const rows = useMemo(
    () => (report.data || []).map((r) => ({ ...r, agentName: nameById[r.agentId] || `Salesman #${r.agentId}` })),
    [report.data, nameById]
  );

  const cities = useMemo(() => Array.from(new Set(rows.map((r) => r.city).filter(Boolean))).sort(), [rows]);
  const areas = useMemo(() => Array.from(new Set(rows.map((r) => r.areaName).filter(Boolean))).sort(), [rows]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (failedOnly && r.visitStatus === 'SUCCESS') return false;
      if (city !== ALL && r.city !== city) return false;
      if (area !== ALL && r.areaName !== area) return false;
      if (!q) return true;
      return (
        (r.shopCode || '').toLowerCase().includes(q) ||
        (r.shopName || '').toLowerCase().includes(q) ||
        (r.agentName || '').toLowerCase().includes(q) ||
        (r.scannedCode || '').toLowerCase().includes(q)
      );
    });
  }, [rows, search, city, area, failedOnly]);

  const sorted = useMemo(() => sortRows(filtered, sort.key, sort.direction), [filtered, sort]);
  const { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage, resetPage } = usePagination(sorted, 25);

  const totals = useMemo(() => {
    const successful = filtered.filter((r) => r.visitStatus === 'SUCCESS');
    const uniqueShops = new Set(filtered.filter((r) => r.shopId != null).map((r) => r.shopId));
    return {
      total: filtered.length,
      successful: successful.length,
      failed: filtered.length - successful.length,
      uniqueShops: uniqueShops.size,
    };
  }, [filtered]);

  const handleExport = () => {
    const csv = toCsv(sorted, [
      { key: 'scanDate', label: 'Date' },
      { key: 'scanTime', label: 'Time' },
      { key: 'agentName', label: 'Salesman' },
      { key: 'shopCode', label: 'Shop Code' },
      { key: 'shopName', label: 'Shop' },
      { key: 'areaName', label: 'Area' },
      { key: 'city', label: 'City' },
      { key: 'distanceMeters', label: 'Distance (m)' },
      { key: 'geofenceStatus', label: 'GPS Status' },
      { key: 'visitStatus', label: 'Result' },
    ]);
    downloadCsv(`shop-visits-${startStr}-to-${endStr}.csv`, csv);
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>QR / Shop Visits</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Every QR scan a salesman attempted, pass or fail — the physical proof they were at a shop before recording
          sales or returns there.
        </Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="visit-salesman-label">Salesman</InputLabel>
            <Select
              labelId="visit-salesman-label"
              label="Salesman"
              value={selectedSalesman}
              onChange={(e) => { setSelectedSalesman(e.target.value); resetPage(); }}
            >
              <MenuItem value={ALL}>All Salesmen</MenuItem>
              {salesmen.map((a) => <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}
            </Select>
          </FormControl>
          <DatePicker
            label="From"
            value={rangeStart}
            onChange={(v) => { if (v) { setRangeStart(v); resetPage(); } }}
            maxDate={rangeEnd}
            slotProps={{ textField: { size: 'small' } }}
          />
          <DatePicker
            label="To"
            value={rangeEnd}
            onChange={(v) => { if (v) { setRangeEnd(v); resetPage(); } }}
            minDate={rangeStart}
            maxDate={dayjs()}
            slotProps={{ textField: { size: 'small' } }}
          />
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel id="visit-city-label">City</InputLabel>
            <Select labelId="visit-city-label" label="City" value={city} onChange={(e) => { setCity(e.target.value); resetPage(); }}>
              <MenuItem value={ALL}>All Cities</MenuItem>
              {cities.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel id="visit-area-label">Area</InputLabel>
            <Select labelId="visit-area-label" label="Area" value={area} onChange={(e) => { setArea(e.target.value); resetPage(); }}>
              <MenuItem value={ALL}>All Areas</MenuItem>
              {areas.map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField
            size="small"
            placeholder="Search shop, code, or salesman…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); resetPage(); }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
            sx={{ minWidth: 220 }}
          />
          <FormControlLabel
            control={<Switch checked={failedOnly} onChange={(e) => { setFailedOnly(e.target.checked); resetPage(); }} />}
            label="Failed only"
          />
          <Button startIcon={<DownloadIcon />} variant="outlined" size="small" onClick={handleExport} disabled={sorted.length === 0}>
            Export CSV
          </Button>
        </Stack>

        {report.isLoading || agents.isLoading ? (
          <CircularProgress />
        ) : report.isError || agents.isError ? (
          <Alert severity="error">Failed to load shop visits. Try refreshing.</Alert>
        ) : (
          <>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Total Scans" value={totals.total} />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Successful" value={totals.successful} color="success.main" />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Failed" value={totals.failed} color={totals.failed > 0 ? 'error.main' : undefined} />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Unique Shops Visited" value={totals.uniqueShops} />
              </Grid>
            </Grid>

            <Paper>
              <TableContainer><Table size="small">
                <TableHead>
                  <TableRow>
                    <SortableHeader label="Date" sortKey="scanDate" sort={sort} onSort={onSort} />
                    <TableCell>Time</TableCell>
                    <SortableHeader label="Salesman" sortKey="agentName" sort={sort} onSort={onSort} />
                    <SortableHeader label="Shop Code" sortKey="shopCode" sort={sort} onSort={onSort} />
                    <TableCell>Shop</TableCell>
                    <TableCell>Area</TableCell>
                    <TableCell>City</TableCell>
                    <TableCell>GPS Status</TableCell>
                    <TableCell>Visit</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paged.length === 0 && (
                    <TableRow><TableCell colSpan={9} align="center">No shop visits match this filter.</TableCell></TableRow>
                  )}
                  {paged.map((r) => (
                    <TableRow key={r.visitId} hover>
                      <TableCell>{r.scanDate}</TableCell>
                      <TableCell>{r.scanTime}</TableCell>
                      <TableCell>
                        <Button size="small" onClick={() => navigate(`/sales/qr-visits/${r.agentId}`)} sx={{ textTransform: 'none', p: 0, minWidth: 0 }}>
                          {r.agentName}
                        </Button>
                      </TableCell>
                      <TableCell>{r.shopCode || r.scannedCode}</TableCell>
                      <TableCell>{r.shopName || '—'}</TableCell>
                      <TableCell>{r.areaName || '—'}</TableCell>
                      <TableCell>{r.city || '—'}</TableCell>
                      <TableCell>{GEOFENCE_LABEL[r.geofenceStatus] || r.geofenceStatus}</TableCell>
                      <TableCell>
                        <Chip size="small" color={STATUS_COLOR[r.visitStatus] || 'default'} label={STATUS_LABEL[r.visitStatus] || r.visitStatus} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table></TableContainer>
              <TablePagination
                component="div"
                count={sorted.length}
                page={page}
                onPageChange={handleChangePage}
                rowsPerPage={rowsPerPage}
                onRowsPerPageChange={handleChangeRowsPerPage}
                rowsPerPageOptions={[10, 25, 50]}
              />
            </Paper>
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}
