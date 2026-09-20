import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useQuery, useQueries } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, MenuItem, Select, InputLabel, FormControl,
  ToggleButtonGroup, ToggleButton, TextField, InputAdornment, IconButton,
  Collapse, Button, Tabs, Tab, Chip,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import DownloadIcon from '@mui/icons-material/Download';
import { salesApi } from '../../services/salesApi';
import { agentApi } from '../../services/attendanceApi';
import { formatSaleTimeToKarachi } from '../../utils/dateUtils';
import { sortRows, useSort } from '../../utils/sorting';
import { aggregateProductMix, flattenSalesToLineItems } from '../../utils/salesAggregation';
import { toCsv, downloadCsv } from '../../utils/csvExport';
import SortableHeader from '../../components/SortableHeader';
import LmtShopBreakdown from './LmtShopBreakdown';

const ALL_AGENTS = 'ALL';
const AGENT_ROLE = 'AGENT';
const LMT_ROLE = 'SALESMAN_LMT';

function LineItemsTable({ items, showType }) {
  if (!items || items.length === 0) {
    return <Typography color="text.secondary" sx={{ p: 2 }}>No product-level detail for this sale.</Typography>;
  }
  return (
    <TableContainer><Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Product</TableCell>
          {showType && <TableCell>Type</TableCell>}
          <TableCell align="right">Quantity</TableCell>
          <TableCell align="right">Unit Price</TableCell>
          <TableCell align="right">Discount</TableCell>
          <TableCell align="right">Line Total</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item, idx) => (
          <TableRow key={item.productId ?? idx}>
            <TableCell>{item.productName}</TableCell>
            {showType && (
              <TableCell>
                <Chip size="small" label={item.transactionType || 'SALE'} color={item.transactionType === 'RETURN' ? 'warning' : 'default'} />
              </TableCell>
            )}
            <TableCell align="right">{item.quantity}</TableCell>
            <TableCell align="right">PKR {(item.unitPrice ?? 0).toLocaleString()}</TableCell>
            <TableCell align="right">{item.discountPercent ? `${item.discountPercent}%` : '—'}</TableCell>
            <TableCell align="right">PKR {(item.totalPrice ?? 0).toLocaleString()}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table></TableContainer>
  );
}

function ExpandableRow({ collapsedCells, items, colSpan, showType }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <TableRow hover>
        <TableCell padding="checkbox">
          <IconButton size="small" onClick={() => setOpen((v) => !v)}>
            {open ? <KeyboardArrowDownIcon /> : <KeyboardArrowRightIcon />}
          </IconButton>
        </TableCell>
        {collapsedCells}
      </TableRow>
      <TableRow>
        <TableCell colSpan={colSpan} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ py: 1 }}>
              <LineItemsTable items={items} showType={showType} />
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

function ProductMixTable({ rows, title, searchActive }) {
  const [sort, onSort] = useSort('revenue', 'desc');
  const sorted = useMemo(() => sortRows(rows, sort.key, sort.direction), [rows, sort]);
  const total = useMemo(
    () => rows.reduce((acc, r) => ({ quantity: acc.quantity + r.quantity, revenue: acc.revenue + r.revenue }), { quantity: 0, revenue: 0 }),
    [rows]
  );
  return (
    <Paper sx={{ mt: 3 }}>
      <Typography variant="h6" sx={{ p: 2, pb: 0 }}>{title}</Typography>
      {searchActive && (
        <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 1 }}>
          {rows.length === 0
            ? 'No matching products.'
            : `Matching products — Total: ${total.quantity} units, PKR ${total.revenue.toLocaleString()}`}
        </Typography>
      )}
      <TableContainer><Table size="small">
        <TableHead>
          <TableRow>
            <SortableHeader label="Product" sortKey="productName" sort={sort} onSort={onSort} />
            <SortableHeader label="Quantity Sold" sortKey="quantity" sort={sort} onSort={onSort} align="right" />
            <SortableHeader label="Revenue" sortKey="revenue" sort={sort} onSort={onSort} align="right" />
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.length === 0 && (
            <TableRow><TableCell colSpan={3} align="center">No product sales in this period.</TableCell></TableRow>
          )}
          {sorted.map((row) => (
            <TableRow key={row.productId ?? row.productName}>
              <TableCell>{row.productName}</TableCell>
              <TableCell align="right">{row.quantity}</TableCell>
              <TableCell align="right">PKR {row.revenue.toLocaleString()}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table></TableContainer>
    </Paper>
  );
}

function SalesHistoryPanel({ role }) {
  const isLmt = role === LMT_ROLE;
  const sellerLabel = isLmt ? 'LMT Salesman' : 'Agent';
  const allSellersLabel = isLmt ? 'All LMT Salesmen' : 'All Agents';
  const [selectedAgent, setSelectedAgent] = useState(ALL_AGENTS);
  const [period, setPeriod] = useState('daily');
  const [anchorDate, setAnchorDate] = useState(dayjs());
  const [rangeStart, setRangeStart] = useState(dayjs().subtract(7, 'day'));
  const [rangeEnd, setRangeEnd] = useState(dayjs());
  const [search, setSearch] = useState('');
  const [productSearch, setProductSearch] = useState('');

  const [agentSort, onAgentSort] = useSort('revenue', 'desc');
  const [salesSort, onSalesSort] = useSort('saleDate', 'desc');

  // Full roster narrowed to this tab's role, so each section only ever offers
  // its own sellers (getActive() only covers the last 7 days of attendance).
  const roster = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const sellers = useMemo(() => (roster.data || []).filter((a) => a.role === role), [roster.data, role]);
  const activeAgents = { data: sellers };

  // Two genuinely different backend shapes, not a UI choice: /sales/reports/*
  // only supports a single anchor date spanning a fixed day/week/month period
  // (no arbitrary all-agents range endpoint exists), while a specific agent's
  // full history has no server-side date filter at all — filtered client-side
  // against an arbitrary range instead. Same underlying data, different query
  // shape depending on which filter is active.
  const companyReport = useQuery({
    queryKey: ['sales-report', role, period, anchorDate.format('YYYY-MM-DD')],
    queryFn: () => salesApi.getReport(period, anchorDate.format('YYYY-MM-DD'), role),
    enabled: selectedAgent === ALL_AGENTS,
  });

  const agentSales = useQuery({
    queryKey: ['agent-sales', selectedAgent],
    queryFn: () => salesApi.getAgentSales(selectedAgent),
    enabled: selectedAgent !== ALL_AGENTS,
  });

  // LMT shop-wise detail needs per-visit records (shop + line types), which
  // only /sales/agent-sales/{id} carries. In the All view that means one
  // fetch per LMT (a small roster), sharing the same cache key as the
  // single-seller query above; the period's date range is applied client-side.
  const allView = selectedAgent === ALL_AGENTS;
  const lmtSalesQueries = useQueries({
    queries: (isLmt && allView ? sellers : []).map((a) => ({
      queryKey: ['agent-sales', a.id],
      queryFn: () => salesApi.getAgentSales(a.id),
    })),
  });
  const lmtSalesLoading = lmtSalesQueries.some((q) => q.isLoading);
  const lmtSalesSignature = lmtSalesQueries.map((q) => q.dataUpdatedAt).join(',');

  const filteredAgentSales = useMemo(() => {
    if (!agentSales.data) return [];
    return agentSales.data.filter((record) => {
      const saleDate = dayjs(record.saleDate);
      const inRange = !saleDate.isBefore(rangeStart, 'day') && !saleDate.isAfter(rangeEnd, 'day');
      if (!inRange) return false;
      if (!search.trim()) return true;
      const q = search.trim().toLowerCase();
      if ((record.location || '').toLowerCase().includes(q)) return true;
      return (record.items || []).some((item) => (item.productName || '').toLowerCase().includes(q));
    });
  }, [agentSales.data, rangeStart, rangeEnd, search]);

  const shopRecords = useMemo(() => {
    if (!isLmt) return [];
    if (!allView) return filteredAgentSales;
    const end = anchorDate;
    const start = period === 'daily' ? anchorDate
      : period === 'weekly' ? anchorDate.subtract(6, 'day')
      : anchorDate.startOf('month');
    const last = period === 'monthly' ? anchorDate.endOf('month') : end;
    return lmtSalesQueries
      .flatMap((q) => q.data || [])
      .filter((r) => {
        const d = dayjs(r.saleDate);
        return !d.isBefore(start, 'day') && !d.isAfter(last, 'day');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLmt, allView, filteredAgentSales, anchorDate, period, lmtSalesSignature]);

  const sortedAgentSales = useMemo(
    () => sortRows(filteredAgentSales, salesSort.key, salesSort.direction),
    [filteredAgentSales, salesSort]
  );

  const agentProductMix = useMemo(
    () => aggregateProductMix(filteredAgentSales.flatMap((r) => r.items || [])),
    [filteredAgentSales]
  );

  const filteredAgentSummaries = useMemo(() => {
    const rows = companyReport.data?.agentSummaries || [];
    if (!search.trim()) return rows;
    const q = search.trim().toLowerCase();
    return rows.filter(
      (row) => (row.agentName || '').toLowerCase().includes(q) || (row.employeeId || '').toLowerCase().includes(q)
    );
  }, [companyReport.data, search]);

  const sortedAgentSummaries = useMemo(
    () => sortRows(filteredAgentSummaries, agentSort.key, agentSort.direction),
    [filteredAgentSummaries, agentSort]
  );

  const companyProductMix = useMemo(
    () =>
      (companyReport.data?.productPerformance || []).map((p) => ({
        productName: p.productName,
        quantity: p.quantitySold,
        revenue: p.totalRevenue,
      })),
    [companyReport.data]
  );

  const filterByProductSearch = (rows) => {
    if (!productSearch.trim()) return rows;
    const q = productSearch.trim().toLowerCase();
    return rows.filter((r) => (r.productName || '').toLowerCase().includes(q));
  };
  const filteredCompanyProductMix = useMemo(() => filterByProductSearch(companyProductMix), [companyProductMix, productSearch]);
  const filteredAgentProductMix = useMemo(() => filterByProductSearch(agentProductMix), [agentProductMix, productSearch]);

  const canExpandAgentSummaries = period === 'daily';

  const handleExportAgentSummaries = () => {
    const rows = sortedAgentSummaries.flatMap((row) => {
      const items = row.items && row.items.length > 0 ? row.items : [null];
      return items.map((item) => ({
        agentName: row.agentName,
        employeeId: row.employeeId,
        totalUnits: row.totalUnits,
        totalRevenue: row.totalRevenue,
        productName: item?.productName ?? '',
        quantity: item?.quantity ?? '',
        unitPrice: item?.unitPrice ?? '',
        discountPercent: item?.discountPercent ?? '',
        lineTotal: item?.totalPrice ?? '',
      }));
    });
    const csv = toCsv(rows, [
      { key: 'agentName', label: 'Agent' },
      { key: 'employeeId', label: 'Employee ID' },
      { key: 'totalUnits', label: 'Total Units (period)' },
      { key: 'totalRevenue', label: 'Total Revenue (period)' },
      { key: 'productName', label: 'Product' },
      { key: 'quantity', label: 'Quantity' },
      { key: 'unitPrice', label: 'Unit Price' },
      { key: 'discountPercent', label: 'Discount %' },
      { key: 'lineTotal', label: 'Line Total' },
    ]);
    downloadCsv(`sales-${period}-${anchorDate.format('YYYY-MM-DD')}.csv`, csv);
  };

  const handleExportAgentSales = () => {
    const rows = flattenSalesToLineItems(sortedAgentSales);
    const csv = toCsv(rows, [
      { key: 'agentName', label: 'Agent' },
      { key: 'employeeId', label: 'Employee ID' },
      { key: 'saleDate', label: 'Date' },
      { key: 'saleTime', label: 'Time' },
      { key: 'location', label: 'Location' },
      { key: 'productName', label: 'Product' },
      { key: 'quantity', label: 'Quantity' },
      { key: 'unitPrice', label: 'Unit Price' },
      { key: 'discountPercent', label: 'Discount %' },
      { key: 'lineTotal', label: 'Line Total' },
      { key: 'saleTotalAmount', label: 'Sale Total' },
    ]);
    const agentLabel = activeAgents.data?.find((a) => a.id === selectedAgent)?.name || selectedAgent;
    downloadCsv(`sales-${agentLabel}-${rangeStart.format('YYYY-MM-DD')}-to-${rangeEnd.format('YYYY-MM-DD')}.csv`, csv);
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="sales-agent-filter-label">{sellerLabel}</InputLabel>
            <Select
              labelId="sales-agent-filter-label"
              label={sellerLabel}
              value={selectedAgent}
              onChange={(e) => { setSelectedAgent(e.target.value); setSearch(''); }}
            >
              <MenuItem value={ALL_AGENTS}>{allSellersLabel}</MenuItem>
              {activeAgents.data?.map((agent) => (
                <MenuItem key={agent.id} value={agent.id}>{agent.name}</MenuItem>
              ))}
            </Select>
          </FormControl>

          {selectedAgent === ALL_AGENTS ? (
            <>
              <ToggleButtonGroup size="small" exclusive value={period} onChange={(e, v) => v && setPeriod(v)}>
                <ToggleButton value="daily">Day</ToggleButton>
                <ToggleButton value="weekly">Week</ToggleButton>
                <ToggleButton value="monthly">Month</ToggleButton>
              </ToggleButtonGroup>
              <DatePicker
                label="As of"
                value={anchorDate}
                onChange={(v) => v && setAnchorDate(v)}
                maxDate={dayjs()}
                slotProps={{ textField: { size: 'small' } }}
              />
            </>
          ) : (
            <>
              <DatePicker
                label="From"
                value={rangeStart}
                onChange={(v) => v && setRangeStart(v)}
                maxDate={rangeEnd}
                slotProps={{ textField: { size: 'small' } }}
              />
              <DatePicker
                label="To"
                value={rangeEnd}
                onChange={(v) => v && setRangeEnd(v)}
                minDate={rangeStart}
                maxDate={dayjs()}
                slotProps={{ textField: { size: 'small' } }}
              />
            </>
          )}

          <TextField
            size="small"
            placeholder={selectedAgent === ALL_AGENTS ? `Search ${sellerLabel.toLowerCase()}…` : 'Search product or location…'}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
            sx={{ minWidth: 220 }}
          />

          <TextField
            size="small"
            placeholder="Search product totals…"
            value={productSearch}
            onChange={(e) => setProductSearch(e.target.value)}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
            sx={{ minWidth: 220 }}
          />

          <Button
            startIcon={<DownloadIcon />}
            variant="outlined"
            size="small"
            onClick={selectedAgent === ALL_AGENTS ? handleExportAgentSummaries : handleExportAgentSales}
            disabled={selectedAgent === ALL_AGENTS ? !companyReport.data : filteredAgentSales.length === 0}
          >
            Export CSV
          </Button>
        </Stack>

        {selectedAgent === ALL_AGENTS ? (
          <>
            {companyReport.isLoading && <CircularProgress />}
            {companyReport.isError && <Alert severity="error">Failed to load the sales report. Try refreshing.</Alert>}
            {companyReport.data && (
              <>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  {companyReport.data.title} — {companyReport.data.dateRange} — Revenue PKR {(companyReport.data.totalRevenue ?? 0).toLocaleString()}, {companyReport.data.totalUnits ?? 0} units, {companyReport.data.activeAgents ?? 0} active {isLmt ? 'salesmen' : 'agents'}
                </Typography>
                {!canExpandAgentSummaries && (
                  <Alert severity="info" sx={{ mb: 2 }}>
                    Per-agent product breakdown is only available for the Day view — the backend doesn't currently return it for Week/Month (flagged separately). Totals below are still accurate.
                  </Alert>
                )}
                <Paper>
                  <TableContainer><Table>
                    <TableHead>
                      <TableRow>
                        {canExpandAgentSummaries && <TableCell padding="checkbox" />}
                        <SortableHeader label={sellerLabel} sortKey="agentName" sort={agentSort} onSort={onAgentSort} />
                        <SortableHeader label="Employee ID" sortKey="employeeId" sort={agentSort} onSort={onAgentSort} />
                        <SortableHeader label="Units" sortKey="totalUnits" sort={agentSort} onSort={onAgentSort} align="right" />
                        <SortableHeader label="Revenue" sortKey="totalRevenue" sort={agentSort} onSort={onAgentSort} align="right" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {sortedAgentSummaries.length === 0 && (
                        <TableRow><TableCell colSpan={canExpandAgentSummaries ? 5 : 4} align="center">No sales in this period.</TableCell></TableRow>
                      )}
                      {sortedAgentSummaries.map((row) =>
                        canExpandAgentSummaries ? (
                          <ExpandableRow
                            key={row.employeeId}
                            colSpan={5}
                            items={row.items}
                            showType={isLmt}
                            collapsedCells={
                              <>
                                <TableCell>{row.agentName}</TableCell>
                                <TableCell>{row.employeeId}</TableCell>
                                <TableCell align="right">{row.totalUnits}</TableCell>
                                <TableCell align="right">PKR {row.totalRevenue.toLocaleString()}</TableCell>
                              </>
                            }
                          />
                        ) : (
                          <TableRow key={row.employeeId}>
                            <TableCell>{row.agentName}</TableCell>
                            <TableCell>{row.employeeId}</TableCell>
                            <TableCell align="right">{row.totalUnits}</TableCell>
                            <TableCell align="right">PKR {row.totalRevenue.toLocaleString()}</TableCell>
                          </TableRow>
                        )
                      )}
                    </TableBody>
                  </Table></TableContainer>
                </Paper>

                <ProductMixTable rows={filteredCompanyProductMix} title={`Product-wise Totals — ${allSellersLabel}`} searchActive={!!productSearch.trim()} />
                {isLmt && (lmtSalesLoading
                  ? <CircularProgress sx={{ mt: 3 }} />
                  : <LmtShopBreakdown records={shopRecords} filenameHint={`${period}-${anchorDate.format('YYYY-MM-DD')}`} />)}
              </>
            )}
          </>
        ) : (
          <>
            {agentSales.isLoading && <CircularProgress />}
            {agentSales.isError && <Alert severity="error">Failed to load this seller's sales history. Try refreshing.</Alert>}
            {agentSales.data && (
              <>
                <Paper>
                  <TableContainer><Table>
                    <TableHead>
                      <TableRow>
                        <TableCell padding="checkbox" />
                        <SortableHeader label="Date" sortKey="saleDate" sort={salesSort} onSort={onSalesSort} />
                        <TableCell>Time</TableCell>
                        {isLmt && <TableCell>Shop</TableCell>}
                        <TableCell>Location</TableCell>
                        <SortableHeader label="Amount" sortKey="totalAmount" sort={salesSort} onSort={onSalesSort} align="right" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {sortedAgentSales.length === 0 && (
                        <TableRow><TableCell colSpan={isLmt ? 6 : 5} align="center">No sales in this date range.</TableCell></TableRow>
                      )}
                      {sortedAgentSales.map((record) => (
                        <ExpandableRow
                          key={record.id}
                          colSpan={isLmt ? 6 : 5}
                          items={record.items}
                          showType={isLmt}
                          collapsedCells={
                            <>
                              <TableCell>{record.saleDate}</TableCell>
                              <TableCell>{formatSaleTimeToKarachi(record.saleDate, record.saleTime) || '—'}</TableCell>
                              {isLmt && <TableCell>{record.customerShopName ? `${record.customerShopName} (${record.customerShopCode})` : '—'}</TableCell>}
                              <TableCell>{record.location || '—'}</TableCell>
                              <TableCell align="right">PKR {(record.totalAmount ?? 0).toLocaleString()}</TableCell>
                            </>
                          }
                        />
                      ))}
                    </TableBody>
                  </Table></TableContainer>
                </Paper>

                <ProductMixTable rows={filteredAgentProductMix} title="Product Mix — this range" searchActive={!!productSearch.trim()} />
                {isLmt && (
                  <LmtShopBreakdown
                    records={shopRecords}
                    filenameHint={`${rangeStart.format('YYYY-MM-DD')}-to-${rangeEnd.format('YYYY-MM-DD')}`}
                  />
                )}
              </>
            )}
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}

export default function SalesHistoryPage() {
  const [tab, setTab] = useState(AGENT_ROLE);
  return (
    <Box>
      <Typography variant="h5" gutterBottom>Sales History</Typography>
      <Tabs value={tab} onChange={(e, v) => setTab(v)} sx={{ mb: 2 }} variant="scrollable" scrollButtons="auto">
        <Tab value={AGENT_ROLE} label="Agents" />
        <Tab value={LMT_ROLE} label="LMTs" />
      </Tabs>
      <SalesHistoryPanel key={tab} role={tab} />
    </Box>
  );
}
