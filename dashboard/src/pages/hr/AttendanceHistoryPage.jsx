import React, { useState } from 'react';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack, MenuItem, Select, InputLabel, FormControl,
} from '@mui/material';
import { attendanceApi, agentApi } from '../../services/attendanceApi';
import { karachiStartOfDayIso, karachiEndOfDayIso, formatUtcToKarachi } from '../../utils/dateUtils';

const STATUS_COLOR = { IN: 'success', LATE: 'warning', NON_WORKING_DAY: 'default' };
const ALL_AGENTS = 'ALL';

export default function AttendanceHistoryPage() {
  const [startDate, setStartDate] = useState(dayjs().subtract(7, 'day'));
  const [endDate, setEndDate] = useState(dayjs());
  const [selectedAgent, setSelectedAgent] = useState(ALL_AGENTS);

  const activeAgents = useQuery({ queryKey: ['agents', 'active'], queryFn: () => agentApi.getActive() });

  // The picker's dayjs values are in whatever timezone the viewing browser
  // happens to be in — only the calendar date (YYYY-MM-DD) the user actually
  // picked is meaningful. Converting that date to a Pakistan-midnight UTC
  // boundary (not "start of day in the browser's zone") is what keeps this
  // correct regardless of what timezone HR's machine is configured to.
  const startIso = karachiStartOfDayIso(startDate.format('YYYY-MM-DD'));
  const endIso = karachiEndOfDayIso(endDate.format('YYYY-MM-DD'));

  const history = useQuery({
    queryKey: ['attendance-history', selectedAgent, startIso, endIso],
    queryFn: () =>
      selectedAgent === ALL_AGENTS
        ? attendanceApi.getDateRange(startIso, endIso)
        : attendanceApi.getAgentDateRange(selectedAgent, startIso, endIso),
    enabled: !!startIso && !!endIso,
  });

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>
          Attendance History <Typography component="span" variant="body2" color="text.secondary">(times shown in Pakistan time)</Typography>
        </Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center">
          <DatePicker
            label="From"
            value={startDate}
            onChange={(v) => v && setStartDate(v)}
            maxDate={endDate}
            slotProps={{ textField: { size: 'small' } }}
          />
          <DatePicker
            label="To"
            value={endDate}
            onChange={(v) => v && setEndDate(v)}
            minDate={startDate}
            maxDate={dayjs()}
            slotProps={{ textField: { size: 'small' } }}
          />
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel id="agent-filter-label">Agent</InputLabel>
            <Select
              labelId="agent-filter-label"
              label="Agent"
              value={selectedAgent}
              onChange={(e) => setSelectedAgent(e.target.value)}
            >
              <MenuItem value={ALL_AGENTS}>All Agents</MenuItem>
              {activeAgents.data?.map((agent) => (
                <MenuItem key={agent.id} value={agent.id}>
                  {agent.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>

        {history.isLoading && <CircularProgress />}
        {history.isError && <Alert severity="error">Failed to load attendance history. Try refreshing.</Alert>}

        {history.data && (
          <Paper>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Agent</TableCell>
                  <TableCell>Mart</TableCell>
                  <TableCell>Check-In</TableCell>
                  <TableCell>Check-Out</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Distance From Mart</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      No attendance records in this range.
                    </TableCell>
                  </TableRow>
                )}
                {history.data.map((record) => (
                  <TableRow key={record.id}>
                    <TableCell>{record.agentName}</TableCell>
                    <TableCell>{record.martName}</TableCell>
                    <TableCell>{formatUtcToKarachi(record.checkInTime) || '—'}</TableCell>
                    <TableCell>{formatUtcToKarachi(record.checkOutTime) || '—'}</TableCell>
                    <TableCell>
                      <Chip size="small" label={record.status} color={STATUS_COLOR[record.status] || 'default'} />
                    </TableCell>
                    <TableCell>{record.distanceFromMart != null ? `${Math.round(record.distanceFromMart)}m` : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        )}
      </Box>
    </LocalizationProvider>
  );
}
