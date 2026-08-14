import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, MenuItem, Select, InputLabel, FormControl,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material';
import { salesApi } from '../../services/salesApi';
import { agentApi } from '../../services/attendanceApi';
import { formatSaleTimeToKarachi } from '../../utils/dateUtils';

const ALL_AGENTS = 'ALL';

export default function SalesHistoryPage() {
  const [selectedAgent, setSelectedAgent] = useState(ALL_AGENTS);
  const [period, setPeriod] = useState('daily');
  const [anchorDate, setAnchorDate] = useState(dayjs());
  const [rangeStart, setRangeStart] = useState(dayjs().subtract(7, 'day'));
  const [rangeEnd, setRangeEnd] = useState(dayjs());

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
      return !saleDate.isBefore(rangeStart, 'day') && !saleDate.isAfter(rangeEnd, 'day');
    });
  }, [agentSales.data, rangeStart, rangeEnd]);

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>Sales History</Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap">
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="sales-agent-filter-label">Agent</InputLabel>
            <Select
              labelId="sales-agent-filter-label"
              label="Agent"
              value={selectedAgent}
              onChange={(e) => setSelectedAgent(e.target.value)}
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
                <Paper>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell>Agent</TableCell>
                        <TableCell>Employee ID</TableCell>
                        <TableCell align="right">Units</TableCell>
                        <TableCell align="right">Revenue</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(!companyReport.data.agentSummaries || companyReport.data.agentSummaries.length === 0) && (
                        <TableRow><TableCell colSpan={4} align="center">No sales in this period.</TableCell></TableRow>
                      )}
                      {companyReport.data.agentSummaries?.map((row) => (
                        <TableRow key={row.employeeId}>
                          <TableCell>{row.agentName}</TableCell>
                          <TableCell>{row.employeeId}</TableCell>
                          <TableCell align="right">{row.totalUnits}</TableCell>
                          <TableCell align="right">PKR {row.totalRevenue.toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Paper>
              </>
            )}
          </>
        ) : (
          <>
            {agentSales.isLoading && <CircularProgress />}
            {agentSales.isError && <Alert severity="error">Failed to load this agent's sales history. Try refreshing.</Alert>}
            {agentSales.data && (
              <Paper>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Location</TableCell>
                      <TableCell align="right">Amount</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredAgentSales.length === 0 && (
                      <TableRow><TableCell colSpan={4} align="center">No sales in this date range.</TableCell></TableRow>
                    )}
                    {filteredAgentSales.map((record) => (
                      <TableRow key={record.id}>
                        <TableCell>{record.saleDate}</TableCell>
                        <TableCell>{formatSaleTimeToKarachi(record.saleDate, record.saleTime) || '—'}</TableCell>
                        <TableCell>{record.location || '—'}</TableCell>
                        <TableCell align="right">PKR {(record.totalAmount ?? 0).toLocaleString()}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Paper>
            )}
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}
