import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, Grid, MenuItem, Select, InputLabel, FormControl,
  TextField, InputAdornment, IconButton, Collapse, Button, Chip, TablePagination,
  FormControlLabel, Switch,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import DownloadIcon from '@mui/icons-material/Download';
import { lmtStockApi } from '../../services/lmtApi';
import { agentApi } from '../../services/attendanceApi';
import { sortRows, useSort } from '../../utils/sorting';
import { usePagination } from '../../utils/pagination';
import { toCsv, downloadCsv } from '../../utils/csvExport';
import SortableHeader from '../../components/SortableHeader';

const ALL_LMTS = 'ALL';
const SHORTAGE_BG = 'rgba(211, 47, 47, 0.08)';

function StatCard({ label, value, color }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold" color={color}>{value}</Typography>
    </Paper>
  );
}

function ReturnsByShopTable({ returnsByShop }) {
  if (!returnsByShop || returnsByShop.length === 0) {
    return <Typography color="text.secondary" sx={{ p: 2 }}>No per-shop returns recorded for this product.</Typography>;
  }
  return (
    <TableContainer><Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Shop Code</TableCell>
          <TableCell>Shop</TableCell>
          <TableCell align="right">Returned</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {returnsByShop.map((shop) => (
          <TableRow key={shop.shopId}>
            <TableCell>{shop.shopCode}</TableCell>
            <TableCell>{shop.shopName}</TableCell>
            <TableCell align="right">{shop.returnedQty}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table></TableContainer>
  );
}

function ReconciliationRow({ row }) {
  const [open, setOpen] = useState(false);
  const hasShortage = row.reconciled && row.missingQty > 0;
  return (
    <>
      <TableRow hover sx={hasShortage ? { backgroundColor: SHORTAGE_BG } : undefined}>
        <TableCell padding="checkbox">
          <IconButton size="small" onClick={() => setOpen((v) => !v)}>
            {open ? <KeyboardArrowDownIcon /> : <KeyboardArrowRightIcon />}
          </IconButton>
        </TableCell>
        <TableCell>{row.stockDate}</TableCell>
        <TableCell>{row.agentName}</TableCell>
        <TableCell>{row.productName}</TableCell>
        <TableCell align="right">{row.openingStock ?? '—'}</TableCell>
        <TableCell align="right">{row.reconciled ? row.soldQty : '—'}</TableCell>
        <TableCell align="right">{row.reconciled ? row.returnedQty : '—'}</TableCell>
        <TableCell align="right">{row.reconciled ? row.unsoldQty : '—'}</TableCell>
        <TableCell align="right">
          {row.reconciled ? (
            hasShortage
              ? <Chip size="small" color="error" label={`${row.missingQty} missing`} />
              : <Typography variant="body2" color="text.secondary">0</Typography>
          ) : (
            <Chip size="small" label="Not reconciled" />
          )}
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={9} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ py: 1 }}>
              <Typography variant="subtitle2" sx={{ px: 2, pt: 1 }}>Returns by shop</Typography>
              <ReturnsByShopTable returnsByShop={row.returnsByShop} />
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

export default function LmtReconciliationPage() {
  const [selectedLmt, setSelectedLmt] = useState(ALL_LMTS);
  const [rangeStart, setRangeStart] = useState(dayjs());
  const [rangeEnd, setRangeEnd] = useState(dayjs());
  const [search, setSearch] = useState('');
  const [shortagesOnly, setShortagesOnly] = useState(false);
  const [sort, onSort] = useSort('stockDate', 'desc');

  const agents = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const lmts = useMemo(() => (agents.data || []).filter((a) => a.role === 'SALESMAN_LMT'), [agents.data]);
  const nameById = useMemo(() => Object.fromEntries(lmts.map((a) => [a.id, a.name])), [lmts]);

  const startStr = rangeStart.format('YYYY-MM-DD');
  const endStr = rangeEnd.format('YYYY-MM-DD');
  const report = useQuery({
    queryKey: ['lmt-reconciliation', startStr, endStr, selectedLmt],
    queryFn: () => lmtStockApi.getReconciliation(startStr, endStr, selectedLmt === ALL_LMTS ? null : selectedLmt),
  });

  // One flat row per (salesman, day, product) so the existing sort/pagination
  // patterns work unchanged. A day's record is "reconciled" once the sales
  // department's reconcile step has run — until then sold/missing are null
  // and must not read as a zero shortage.
  const rows = useMemo(() => {
    const out = [];
    (report.data || []).forEach((day) => {
      const reconciled = day.status === 'RECONCILED' || day.reconciledAt != null;
      (day.items || []).forEach((item) => {
        out.push({
          key: `${day.id}-${item.productId}`,
          stockDate: day.stockDate,
          agentName: nameById[day.agentId] || `Salesman #${day.agentId}`,
          productName: item.productName,
          openingStock: item.openingStock,
          soldQty: item.soldQty,
          returnedQty: item.returnedQty,
          unsoldQty: item.unsoldQty,
          missingQty: item.missingQty,
          returnsByShop: item.returnsByShop,
          reconciled,
        });
      });
    });
    return out;
  }, [report.data, nameById]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (shortagesOnly && !(r.reconciled && r.missingQty > 0)) return false;
      if (!q) return true;
      return r.productName.toLowerCase().includes(q) || r.agentName.toLowerCase().includes(q);
    });
  }, [rows, search, shortagesOnly]);

  const sorted = useMemo(() => sortRows(filtered, sort.key, sort.direction), [filtered, sort]);
  const { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage, resetPage } = usePagination(sorted, 25);

  const totals = useMemo(() => {
    const shortageRows = rows.filter((r) => r.reconciled && r.missingQty > 0);
    return {
      missingUnits: shortageRows.reduce((sum, r) => sum + r.missingQty, 0),
      shortageLines: shortageRows.length,
      salesmenWithShortage: new Set(shortageRows.map((r) => r.agentName)).size,
      pendingDays: (report.data || []).filter((d) => !(d.status === 'RECONCILED' || d.reconciledAt != null)).length,
    };
  }, [rows, report.data]);

  const handleExport = () => {
    const csv = toCsv(
      sorted.map((r) => ({
        ...r,
        openingStock: r.openingStock ?? '',
        soldQty: r.reconciled ? r.soldQty : '',
        returnedQty: r.reconciled ? r.returnedQty : '',
        unsoldQty: r.reconciled ? r.unsoldQty : '',
        missingQty: r.reconciled ? r.missingQty : '',
        status: r.reconciled ? 'Reconciled' : 'Not reconciled',
        returnsByShopText: (r.returnsByShop || []).map((s) => `${s.shopName}: ${s.returnedQty}`).join('; '),
      })),
      [
        { key: 'stockDate', label: 'Date' },
        { key: 'agentName', label: 'Salesman' },
        { key: 'productName', label: 'Product' },
        { key: 'openingStock', label: 'Opening Stock' },
        { key: 'soldQty', label: 'Sold' },
        { key: 'returnedQty', label: 'Returned' },
        { key: 'unsoldQty', label: 'Unsold' },
        { key: 'missingQty', label: 'Missing' },
        { key: 'status', label: 'Status' },
        { key: 'returnsByShopText', label: 'Returns by Shop' },
      ]
    );
    downloadCsv(`lmt-reconciliation-${startStr}-to-${endStr}.csv`, csv);
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>LMT Stock Reconciliation</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Opening stock against what was sold, returned and left unsold — anything that doesn't add up shows as Missing.
        </Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="recon-lmt-filter-label">Salesman</InputLabel>
            <Select
              labelId="recon-lmt-filter-label"
              label="Salesman"
              value={selectedLmt}
              onChange={(e) => { setSelectedLmt(e.target.value); resetPage(); }}
            >
              <MenuItem value={ALL_LMTS}>All LMT Salesmen</MenuItem>
              {lmts.map((a) => <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}
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
          <TextField
            size="small"
            placeholder="Search salesman or product…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); resetPage(); }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
            sx={{ minWidth: 220 }}
          />
          <FormControlLabel
            control={<Switch checked={shortagesOnly} onChange={(e) => { setShortagesOnly(e.target.checked); resetPage(); }} />}
            label="Shortages only"
          />
          <Button
            startIcon={<DownloadIcon />}
            variant="outlined"
            size="small"
            onClick={handleExport}
            disabled={sorted.length === 0}
          >
            Export CSV
          </Button>
        </Stack>

        {report.isLoading || agents.isLoading ? (
          <CircularProgress />
        ) : report.isError || agents.isError ? (
          <Alert severity="error">Failed to load the reconciliation report. Try refreshing.</Alert>
        ) : (
          <>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Total Missing (units)" value={totals.missingUnits} color={totals.missingUnits > 0 ? 'error' : undefined} />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Shortage Lines" value={totals.shortageLines} />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Salesmen With Shortage" value={totals.salesmenWithShortage} />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Days Not Reconciled" value={totals.pendingDays} />
              </Grid>
            </Grid>

            <Paper>
              <TableContainer><Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox" />
                    <SortableHeader label="Date" sortKey="stockDate" sort={sort} onSort={onSort} />
                    <SortableHeader label="Salesman" sortKey="agentName" sort={sort} onSort={onSort} />
                    <SortableHeader label="Product" sortKey="productName" sort={sort} onSort={onSort} />
                    <SortableHeader label="Opening" sortKey="openingStock" sort={sort} onSort={onSort} align="right" />
                    <SortableHeader label="Sold" sortKey="soldQty" sort={sort} onSort={onSort} align="right" />
                    <SortableHeader label="Returned" sortKey="returnedQty" sort={sort} onSort={onSort} align="right" />
                    <SortableHeader label="Unsold" sortKey="unsoldQty" sort={sort} onSort={onSort} align="right" />
                    <SortableHeader label="Missing" sortKey="missingQty" sort={sort} onSort={onSort} align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paged.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={9} align="center">
                        {shortagesOnly ? 'No shortages in this period.' : 'No stock records in this period.'}
                      </TableCell>
                    </TableRow>
                  )}
                  {paged.map((row) => <ReconciliationRow key={row.key} row={row} />)}
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
