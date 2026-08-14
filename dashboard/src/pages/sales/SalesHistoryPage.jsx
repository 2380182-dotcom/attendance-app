import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, MenuItem, Select, InputLabel, FormControl,
  ToggleButtonGroup, ToggleButton, TextField, InputAdornment, IconButton,
  Collapse, TableSortLabel, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import DownloadIcon from '@mui/icons-material/Download';
import { salesApi } from '../../services/salesApi';
import { agentApi } from '../../services/attendanceApi';
import { formatSaleTimeToKarachi } from '../../utils/dateUtils';
import { sortRows } from '../../utils/sorting';
import { aggregateProductMix, flattenSalesToLineItems } from '../../utils/salesAggregation';
import { toCsv, downloadCsv } from '../../utils/csvExport';

const ALL_AGENTS = 'ALL';

function SortableHeader({ label, sortKey, sort, onSort, align }) {
  return (
    <TableCell align={align}>
      <TableSortLabel
        active={sort.key === sortKey}
        direction={sort.key === sortKey ? sort.direction : 'asc'}
        onClick={() => onSort(sortKey)}
      >
        {label}
      </TableSortLabel>
    </TableCell>
  );
}

function useSort(initialKey, initialDirection = 'desc') {
  const [sort, setSort] = useState({ key: initialKey, direction: initialDirection });
  const onSort = (key) => {
    setSort((prev) =>
      prev.key === key ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' } : { key, direction: 'asc' }
    );
  };
  return [sort, onSort];
}

function LineItemsTable({ items }) {
  if (!items || items.length === 0) {
    return <Typography color="text.secondary" sx={{ p: 2 }}>No product-level detail for this sale.</Typography>;
  }
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Product</TableCell>
          <TableCell align="right">Quantity</TableCell>
          <TableCell align="right">Unit Price</TableCell>
          <TableCell align="right">Line Total</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item, idx) => (
          <TableRow key={item.productId ?? idx}>
            <TableCell>{item.productName}</TableCell>
            <TableCell align="right">{item.quantity}</TableCell>
            <TableCell align="right">PKR {(item.unitPrice ?? 0).toLocaleString()}</TableCell>
            <TableCell align="right">PKR {(item.totalPrice ?? 0).toLocaleString()}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function ExpandableRow({ collapsedCells, items, colSpan }) {
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
              <LineItemsTable items={items} />
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

function ProductMixTable({ rows, title }) {
  const [sort, onSort] = useSort('revenue', 'desc');
  const sorted = useMemo(() => sortRows(rows, sort.key, sort.direction), [rows, sort]);
  return (
    <Paper sx={{ mt: 3 }}>
      <Typography variant="h6" sx={{ p: 2, pb: 0 }}>{title}</Typography>
      <Table size="small">
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
      </Table>
    </Paper>
  );
}

export default function SalesHistoryPage() {
  const [selectedAgent, setSelectedAgent] = useState(ALL_AGENTS);
  const [period, setPeriod] = useState('daily');
  const [anchorDate, setAnchorDate] = useState(dayjs());
  const [rangeStart, setRangeStart] = useState(dayjs().subtract(7, 'day'));
  const [rangeEnd, setRangeEnd] = useState(dayjs());
  const [search, setSearch] = useState('');

  const [agentSort, onAgentSort] = useSort('revenue', 'desc');
  const [salesSort, onSalesSort] = useSort('saleDate', 'desc');

  const activeAgents = useQuery({ queryKey: ['agents', 'active'], queryFn: () => agentApi.getActive() });

  // Two genuinely different backend shapes, not a UI choice: /sales/reports/*
  // only supports a single anchor date spanning a fixed day/week/month period
  // (no arbitrary all-agents range endpoint exists), while a specific agent's
  // full history has no server-side date filter at all — filtered client-side
  // against an arbitrary range instead. Same underlying data, different query
  // shape depending on which filter is active.
  const companyReport = useQuery({
    queryKey: ['sales-report', period, anchorDate.format('YYYY-MM-DD')],
    queryFn: () => salesApi.getReport(period, anchorDate.format('YYYY-MM-DD')),
    enabled: selectedAgent === ALL_AGENTS,
  });

  const agentSales = useQuery({
    queryKey: ['agent-sales', selectedAgent],
    queryFn: () => salesApi.getAgentSales(selectedAgent),
    enabled: selectedAgent !== ALL_AGENTS,
  });

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
      { key: 'lineTotal', label: 'Line Total' },
      { key: 'saleTotalAmount', label: 'Sale Total' },
    ]);
    const agentLabel = activeAgents.data?.find((a) => a.id === selectedAgent)?.name || selectedAgent;
    downloadCsv(`sales-${agentLabel}-${rangeStart.format('YYYY-MM-DD')}-to-${rangeEnd.format('YYYY-MM-DD')}.csv`, csv);
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>Sales History</Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="sales-agent-filter-label">Agent</InputLabel>
            <Select
              labelId="sales-agent-filter-label"
              label="Agent"
              value={selectedAgent}
              onChange={(e) => { setSelectedAgent(e.target.value); setSearch(''); }}
            >
              <MenuItem value={ALL_AGENTS}>All Agents</MenuItem>
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
            placeholder={selectedAgent === ALL_AGENTS ? 'Search agent…' : 'Search product or location…'}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
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
                  {companyReport.data.title} — {companyReport.data.dateRange} — Revenue PKR {(companyReport.data.totalRevenue ?? 0).toLocaleString()}, {companyReport.data.totalUnits ?? 0} units, {companyReport.data.activeAgents ?? 0} active agents
                </Typography>
                {!canExpandAgentSummaries && (
                  <Alert severity="info" sx={{ mb: 2 }}>
                    Per-agent product breakdown is only available for the Day view — the backend doesn't currently return it for Week/Month (flagged separately). Totals below are still accurate.
                  </Alert>
                )}
                <Paper>
                  <Table>
                    <TableHead>
                      <TableRow>
                        {canExpandAgentSummaries && <TableCell padding="checkbox" />}
                        <SortableHeader label="Agent" sortKey="agentName" sort={agentSort} onSort={onAgentSort} />
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
                  </Table>
                </Paper>

                <ProductMixTable rows={companyProductMix} title="Product-wise Totals — All Agents" />
              </>
            )}
          </>
        ) : (
          <>
            {agentSales.isLoading && <CircularProgress />}
            {agentSales.isError && <Alert severity="error">Failed to load this agent's sales history. Try refreshing.</Alert>}
            {agentSales.data && (
              <>
                <Paper>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell padding="checkbox" />
                        <SortableHeader label="Date" sortKey="saleDate" sort={salesSort} onSort={onSalesSort} />
                        <TableCell>Time</TableCell>
                        <TableCell>Location</TableCell>
                        <SortableHeader label="Amount" sortKey="totalAmount" sort={salesSort} onSort={onSalesSort} align="right" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {sortedAgentSales.length === 0 && (
                        <TableRow><TableCell colSpan={5} align="center">No sales in this date range.</TableCell></TableRow>
                      )}
                      {sortedAgentSales.map((record) => (
                        <ExpandableRow
                          key={record.id}
                          colSpan={5}
                          items={record.items}
                          collapsedCells={
                            <>
                              <TableCell>{record.saleDate}</TableCell>
                              <TableCell>{formatSaleTimeToKarachi(record.saleDate, record.saleTime) || '—'}</TableCell>
                              <TableCell>{record.location || '—'}</TableCell>
                              <TableCell align="right">PKR {(record.totalAmount ?? 0).toLocaleString()}</TableCell>
                            </>
                          }
                        />
                      ))}
                    </TableBody>
                  </Table>
                </Paper>

                <ProductMixTable rows={agentProductMix} title="Product Mix — this range" />
              </>
            )}
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}
