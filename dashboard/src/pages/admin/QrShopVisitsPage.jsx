import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, Grid, MenuItem, Select, InputLabel, FormControl,
  TextField, InputAdornment, Chip, TablePagination, FormControlLabel, Switch, Tabs, Tab, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import { shopVisitApi } from '../../services/lmtApi';
import { agentApi } from '../../services/attendanceApi';

const ALL = 'ALL';
const ROLE_LMT = 'SALESMAN_LMT';
const ROLE_LOCAL = 'SALESMAN_LOCAL';
const STATUS_COLOR = { SUCCESS: 'success', INVALID_CODE: 'error', SHOP_INACTIVE: 'error', OUTSIDE_GEOFENCE: 'warning' };
const STATUS_LABEL = { SUCCESS: 'Verified', INVALID_CODE: 'Invalid Code', SHOP_INACTIVE: 'Shop Inactive', OUTSIDE_GEOFENCE: 'Outside Geofence' };

function StatCard({ label, value, color }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold" color={color}>{value ?? '—'}</Typography>
    </Paper>
  );
}

/**
 * Task 3 — "QR Scanned Shops": its own Admin-sidebar item now (was a report
 * inside Sales), admin-only, server-side paginated and filtered throughout
 * (date/date-range, salesman, shop search, LMT/Local, failed-only).
 *
 * LMT vs Local mean genuinely different things for "Not Visited": LMT has
 * real shop assignment (CustomerShop.assignedAgent, Task 3) — Not Visited
 * there is "this salesman's assigned outlets that weren't scanned." Local
 * has no assignment concept at all — Not Visited there is "every active
 * shop nobody scanned," explicitly unassigned, never attributed to one
 * salesman. The summary counts (Total Shops / Visited / Not Visited) and
 * the Voucher-Without-Scan tab both use the same single "Date" as Not
 * Visited (a day-level concept), while the Visited tab uses its own
 * From/To range — two different date concepts, not conflated into one.
 */
export default function QrShopVisitsPage() {
  const navigate = useNavigate();
  const [role, setRole] = useState(ROLE_LMT);
  const [tab, setTab] = useState('visited');
  const [selectedSalesman, setSelectedSalesman] = useState(ALL);
  const [rangeStart, setRangeStart] = useState(dayjs());
  const [rangeEnd, setRangeEnd] = useState(dayjs());
  const [singleDate, setSingleDate] = useState(dayjs());
  const [search, setSearch] = useState('');
  const [failedOnly, setFailedOnly] = useState(false);
  const [visitedPage, setVisitedPage] = useState(0);
  const [visitedPageSize, setVisitedPageSize] = useState(25);
  const [notVisitedPage, setNotVisitedPage] = useState(0);
  const [notVisitedPageSize, setNotVisitedPageSize] = useState(25);
  const [vwsPage, setVwsPage] = useState(0);
  const [vwsPageSize, setVwsPageSize] = useState(25);

  const agents = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const salesmen = useMemo(() => (agents.data || []).filter((a) => a.role === role), [agents.data, role]);
  const nameById = useMemo(() => Object.fromEntries((agents.data || []).map((a) => [a.id, a.name])), [agents.data]);

  const agentIdParam = selectedSalesman === ALL ? undefined : selectedSalesman;
  const rangeStartStr = rangeStart.format('YYYY-MM-DD');
  const rangeEndStr = rangeEnd.format('YYYY-MM-DD');
  const dateStr = singleDate.format('YYYY-MM-DD');

  // Switching role resets the salesman filter and every tab's page —
  // a salesman picked under LMT is meaningless once viewing Local.
  const handleRoleChange = (next) => {
    setRole(next);
    setSelectedSalesman(ALL);
    setVisitedPage(0);
    setNotVisitedPage(0);
    setVwsPage(0);
  };

  const summary = useQuery({
    queryKey: ['qr-summary', dateStr, role, agentIdParam],
    queryFn: () => shopVisitApi.getSummaryCounts(dateStr, role, agentIdParam),
  });

  const visited = useQuery({
    queryKey: ['qr-visited', rangeStartStr, rangeEndStr, agentIdParam, role, search, failedOnly, visitedPage, visitedPageSize],
    queryFn: () => shopVisitApi.getReport(rangeStartStr, rangeEndStr, {
      agentId: agentIdParam, role, shopSearch: search || undefined, failedOnly, page: visitedPage, size: visitedPageSize,
    }),
    enabled: tab === 'visited',
  });

  const notVisited = useQuery({
    queryKey: ['qr-not-visited', dateStr, role, agentIdParam, search, notVisitedPage, notVisitedPageSize],
    queryFn: () => shopVisitApi.getNotVisited(dateStr, role, {
      agentId: agentIdParam, shopSearch: search || undefined, page: notVisitedPage, size: notVisitedPageSize,
    }),
    enabled: tab === 'not-visited',
  });

  const vouchersWithoutScan = useQuery({
    queryKey: ['qr-vws', dateStr, role, agentIdParam, vwsPage, vwsPageSize],
    queryFn: () => shopVisitApi.getVouchersWithoutScan(dateStr, dateStr, { agentId: agentIdParam, role, page: vwsPage, size: vwsPageSize }),
    enabled: tab === 'voucher-without-scan',
  });

  const resetTabPages = () => { setVisitedPage(0); setNotVisitedPage(0); setVwsPage(0); };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>QR Scanned Shops</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Every QR scan a salesman attempted, pass or fail — the physical proof they were at a shop before recording
          sales or returns there.
        </Typography>

        <Tabs value={role} onChange={(e, v) => handleRoleChange(v)} sx={{ mb: 2 }}>
          <Tab value={ROLE_LMT} label="LMT" />
          <Tab value={ROLE_LOCAL} label="Local" />
        </Tabs>

        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Total Shops" value={summary.data?.totalShops} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Visited" value={summary.data?.visitedShops} color="success.main" />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard
              label="Not Visited"
              value={summary.data?.notVisitedShops}
              color={summary.data?.notVisitedShops > 0 ? 'warning.main' : undefined}
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard
              label="Voucher Without Scan"
              value={summary.data?.voucherWithoutScanCount}
              color={summary.data?.voucherWithoutScanCount > 0 ? 'error.main' : undefined}
            />
          </Grid>
        </Grid>

        <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center" flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="qr-salesman-label">Salesman</InputLabel>
            <Select
              labelId="qr-salesman-label"
              label="Salesman"
              value={selectedSalesman}
              onChange={(e) => { setSelectedSalesman(e.target.value); resetTabPages(); }}
            >
              <MenuItem value={ALL}>{role === ROLE_LMT ? 'All LMT Salesmen' : 'All Local Salesmen'}</MenuItem>
              {salesmen.map((a) => <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField
            size="small"
            placeholder="Search shop name or code…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); resetTabPages(); }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
            sx={{ minWidth: 240 }}
          />
        </Stack>

        <Tabs value={tab} onChange={(e, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab value="visited" label="Visited" />
          <Tab value="not-visited" label="Not Visited" />
          <Tab value="voucher-without-scan" label="Voucher Without Scan" />
        </Tabs>

        {tab === 'visited' && (
          <>
            <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
              <DatePicker
                label="From"
                value={rangeStart}
                onChange={(v) => { if (v) { setRangeStart(v); resetTabPages(); } }}
                maxDate={rangeEnd}
                slotProps={{ textField: { size: 'small', sx: { minWidth: 160 } } }}
              />
              <DatePicker
                label="To"
                value={rangeEnd}
                onChange={(v) => { if (v) { setRangeEnd(v); resetTabPages(); } }}
                minDate={rangeStart}
                maxDate={dayjs()}
                slotProps={{ textField: { size: 'small', sx: { minWidth: 160 } } }}
              />
              <FormControlLabel
                control={<Switch checked={failedOnly} onChange={(e) => { setFailedOnly(e.target.checked); resetTabPages(); }} />}
                label="Failed only"
              />
            </Stack>

            {visited.isLoading || agents.isLoading ? (
              <CircularProgress />
            ) : visited.isError ? (
              <Alert severity="error">Failed to load shop visits. Try refreshing.</Alert>
            ) : (
              <Paper>
                <TableContainer><Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Salesman</TableCell>
                      <TableCell>Shop Code</TableCell>
                      <TableCell>Shop</TableCell>
                      <TableCell>Area</TableCell>
                      <TableCell>GPS Status</TableCell>
                      <TableCell>Result</TableCell>
                      <TableCell align="center">Voucher</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(visited.data?.content || []).length === 0 && (
                      <TableRow><TableCell colSpan={9} align="center">No shop visits match this filter.</TableCell></TableRow>
                    )}
                    {(visited.data?.content || []).map((r) => (
                      <TableRow key={r.visitId} hover>
                        <TableCell>{r.scanDate}</TableCell>
                        <TableCell>{r.scanTime}</TableCell>
                        <TableCell>
                          <Button size="small" onClick={() => navigate(`/admin/qr-visits/${r.agentId}`)} sx={{ textTransform: 'none', p: 0, minWidth: 0 }}>
                            {nameById[r.agentId] || `Salesman #${r.agentId}`}
                          </Button>
                        </TableCell>
                        <TableCell>{r.shopCode || r.scannedCode}</TableCell>
                        <TableCell>{r.shopName || '—'}</TableCell>
                        <TableCell>{r.areaName || '—'}</TableCell>
                        <TableCell>{r.geofenceStatus === 'INSIDE' ? 'Inside' : r.geofenceStatus === 'OUTSIDE' ? 'Outside' : '—'}</TableCell>
                        <TableCell>
                          <Chip size="small" color={STATUS_COLOR[r.visitStatus] || 'default'} label={STATUS_LABEL[r.visitStatus] || r.visitStatus} />
                        </TableCell>
                        <TableCell align="center">
                          {r.voucherCreated
                            ? <CheckCircleIcon fontSize="small" color="success" titleAccess="Voucher created" />
                            : <CancelIcon fontSize="small" color="disabled" titleAccess="No voucher" />}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table></TableContainer>
                <TablePagination
                  component="div"
                  count={visited.data?.totalElements ?? 0}
                  page={visitedPage}
                  onPageChange={(e, p) => setVisitedPage(p)}
                  rowsPerPage={visitedPageSize}
                  onRowsPerPageChange={(e) => { setVisitedPageSize(parseInt(e.target.value, 10)); setVisitedPage(0); }}
                  rowsPerPageOptions={[10, 25, 50, 100]}
                />
              </Paper>
            )}
          </>
        )}

        {tab === 'not-visited' && (
          <>
            <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
              <DatePicker
                label="Date"
                value={singleDate}
                onChange={(v) => { if (v) { setSingleDate(v); resetTabPages(); } }}
                maxDate={dayjs()}
                slotProps={{ textField: { size: 'small', sx: { minWidth: 160 } } }}
              />
              {role === ROLE_LOCAL && (
                <Alert severity="info" sx={{ py: 0 }}>
                  Local shops have no fixed salesman assignment — these are every active shop nobody scanned today, not attributed to anyone.
                </Alert>
              )}
            </Stack>

            {notVisited.isLoading || agents.isLoading ? (
              <CircularProgress />
            ) : notVisited.isError ? (
              <Alert severity="error">Failed to load not-visited shops. Try refreshing.</Alert>
            ) : (
              <Paper>
                <TableContainer><Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Shop Code</TableCell>
                      <TableCell>Shop</TableCell>
                      <TableCell>Area</TableCell>
                      <TableCell>City</TableCell>
                      <TableCell>{role === ROLE_LMT ? 'Assigned Salesman' : 'Assignment'}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(notVisited.data?.content || []).length === 0 && (
                      <TableRow><TableCell colSpan={5} align="center">
                        {role === ROLE_LMT ? 'Every assigned outlet was scanned on this date.' : 'Every active shop was scanned on this date.'}
                      </TableCell></TableRow>
                    )}
                    {(notVisited.data?.content || []).map((d) => (
                      <TableRow key={d.shopId} hover>
                        <TableCell>{d.shopCode}</TableCell>
                        <TableCell>{d.shopName}</TableCell>
                        <TableCell>{d.areaName || '—'}</TableCell>
                        <TableCell>{d.city || '—'}</TableCell>
                        <TableCell>
                          {d.assignedAgentId
                            ? (d.assignedAgentName || nameById[d.assignedAgentId] || `Salesman #${d.assignedAgentId}`)
                            : <Chip size="small" variant="outlined" label="Not assigned" />}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table></TableContainer>
                <TablePagination
                  component="div"
                  count={notVisited.data?.totalElements ?? 0}
                  page={notVisitedPage}
                  onPageChange={(e, p) => setNotVisitedPage(p)}
                  rowsPerPage={notVisitedPageSize}
                  onRowsPerPageChange={(e) => { setNotVisitedPageSize(parseInt(e.target.value, 10)); setNotVisitedPage(0); }}
                  rowsPerPageOptions={[10, 25, 50, 100]}
                />
              </Paper>
            )}
          </>
        )}

        {tab === 'voucher-without-scan' && (
          <>
            <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
              <DatePicker
                label="Date"
                value={singleDate}
                onChange={(v) => { if (v) { setSingleDate(v); resetTabPages(); } }}
                maxDate={dayjs()}
                slotProps={{ textField: { size: 'small', sx: { minWidth: 160 } } }}
              />
              <Alert severity="warning" sx={{ py: 0 }}>
                A sale/return voucher was saved for this shop on this date, but the salesman never scanned its QR code.
              </Alert>
            </Stack>

            {vouchersWithoutScan.isLoading || agents.isLoading ? (
              <CircularProgress />
            ) : vouchersWithoutScan.isError ? (
              <Alert severity="error">Failed to load vouchers without a scan. Try refreshing.</Alert>
            ) : (
              <Paper>
                <TableContainer><Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Voucher #</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Salesman</TableCell>
                      <TableCell>Shop Code</TableCell>
                      <TableCell>Shop</TableCell>
                      <TableCell align="right">Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(vouchersWithoutScan.data?.content || []).length === 0 && (
                      <TableRow><TableCell colSpan={7} align="center">No vouchers without a matching scan on this date.</TableCell></TableRow>
                    )}
                    {(vouchersWithoutScan.data?.content || []).map((v) => (
                      <TableRow key={v.salesRecordId} hover>
                        <TableCell>{v.salesRecordId}</TableCell>
                        <TableCell>{v.saleDate}</TableCell>
                        <TableCell>{v.saleTime}</TableCell>
                        <TableCell>{nameById[v.agentId] || `Salesman #${v.agentId}`}</TableCell>
                        <TableCell>{v.shopCode}</TableCell>
                        <TableCell>{v.shopName}</TableCell>
                        <TableCell align="right">PKR {(v.totalAmount ?? 0).toLocaleString()}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table></TableContainer>
                <TablePagination
                  component="div"
                  count={vouchersWithoutScan.data?.totalElements ?? 0}
                  page={vwsPage}
                  onPageChange={(e, p) => setVwsPage(p)}
                  rowsPerPage={vwsPageSize}
                  onRowsPerPageChange={(e) => { setVwsPageSize(parseInt(e.target.value, 10)); setVwsPage(0); }}
                  rowsPerPageOptions={[10, 25, 50, 100]}
                />
              </Paper>
            )}
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}
