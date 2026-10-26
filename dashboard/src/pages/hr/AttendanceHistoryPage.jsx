import React, { useState } from 'react';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack, Autocomplete, TextField,
} from '@mui/material';
import { attendanceApi, agentApi } from '../../services/attendanceApi';
import { absenceApi } from '../../services/holidayApi';
import { karachiStartOfDayIso, karachiEndOfDayIso, formatUtcToKarachi, karachiDateOf } from '../../utils/dateUtils';

const STATUS_COLOR = { IN: 'success', LATE: 'warning', NON_WORKING_DAY: 'default', ABSENT: 'error' };
const ALL_AGENTS = 'ALL';
/** People who can have attendance history: mart agents, plus salesmen (older check-ins). Not Admin/HR/Sales staff. */
const FIELD_ROLES = { AGENT: 'Mart agent', SALESMAN_LOCAL: 'Local salesman', SALESMAN_LMT: 'LMT salesman' };
const ALL_OPTION = { id: ALL_AGENTS, name: 'All agents' };

export default function AttendanceHistoryPage() {
  const [startDate, setStartDate] = useState(dayjs().subtract(7, 'day'));
  const [endDate, setEndDate] = useState(dayjs());
  const [selectedAgent, setSelectedAgent] = useState(ALL_AGENTS);

  // Every ACTIVE agent (not only those seen in the last 7 days), sorted by name.
  const allAgents = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const agentOptions = React.useMemo(() => [
    ALL_OPTION,
    ...(allAgents.data || [])
      .filter((a) => a.isActive !== false && FIELD_ROLES[a.role])
      .sort((a, b) => (a.name || '').localeCompare(b.name || '')),
  ], [allAgents.data]);
  const selectedOption = agentOptions.find((o) => o.id === selectedAgent) || ALL_OPTION;

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

  // Saved absences (mart agents, from the 10 PM job) for the same Pakistan dates.
  const startDay = startDate.format('YYYY-MM-DD');
  const endDay = endDate.format('YYYY-MM-DD');
  const absences = useQuery({
    queryKey: ['absences', selectedAgent, startDay, endDay],
    queryFn: () => absenceApi.getRange(startDay, endDay, selectedAgent === ALL_AGENTS ? null : selectedAgent),
  });

  // One list, newest first: real attendance rows plus ABSENT rows.
  const rows = React.useMemo(() => {
    if (!history.data) return null;
    const attendanceRows = history.data.map((r) => ({
      key: `a-${r.id}`,
      sortKey: `${karachiDateOf(r.checkInTime) || ''} ${formatUtcToKarachi(r.checkInTime, 'HH:mm') || ''}`,
      record: r,
    }));
    const absentRows = (absences.data || []).map((a) => ({
      key: `x-${a.id}`,
      sortKey: `${a.absenceDate} 00:00`,
      record: {
        agentName: a.agentName, martName: '—', status: 'ABSENT', absenceDate: a.absenceDate,
      },
    }));
    return [...attendanceRows, ...absentRows].sort((x, y) => (x.sortKey < y.sortKey ? 1 : x.sortKey > y.sortKey ? -1 : 0));
  }, [history.data, absences.data]);

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Typography variant="h5" gutterBottom>
          Attendance History <Typography component="span" variant="body2" color="text.secondary">(times shown in Pakistan time)</Typography>
        </Typography>

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
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
          <Autocomplete
            size="small"
            sx={{ minWidth: 260 }}
            options={agentOptions}
            value={selectedOption}
            loading={allAgents.isLoading}
            disableClearable
            isOptionEqualToValue={(o, v) => o.id === v.id}
            getOptionLabel={(o) => o.name || ''}
            onChange={(_, option) => setSelectedAgent(option ? option.id : ALL_AGENTS)}
            renderOption={(props, o) => {
              const { key, ...rest } = props;
              return (
                <li key={key} {...rest}>
                  <Box>
                    <Box>{o.name}</Box>
                    {o.id !== ALL_AGENTS && (
                      <Typography variant="caption" color="text.secondary">
                        {FIELD_ROLES[o.role]}{o.agentId ? ` · ${o.agentId}` : ''}
                      </Typography>
                    )}
                  </Box>
                </li>
              );
            }}
            renderInput={(params) => <TextField {...params} label="Agent" placeholder="Type a name to search" />}
          />
        </Stack>

        {history.isLoading && <CircularProgress />}
        {history.isError && <Alert severity="error">Failed to load attendance history. Try refreshing.</Alert>}
        {absences.isError && <Alert severity="warning" sx={{ mb: 2 }}>Absences could not be loaded, so only check-ins are shown.</Alert>}

        {rows && (
          <Paper>
            <TableContainer><Table>
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
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      No attendance records in this range.
                    </TableCell>
                  </TableRow>
                )}
                {rows.map(({ key, record }) => (
                  <TableRow key={key}>
                    <TableCell>{record.agentName}</TableCell>
                    <TableCell>{record.martName}</TableCell>
                    <TableCell>
                      {record.absenceDate
                        ? `${dayjs(record.absenceDate).format('MMM D, YYYY')} · no check-in`
                        : formatUtcToKarachi(record.checkInTime) || '—'}
                    </TableCell>
                    <TableCell>{formatUtcToKarachi(record.checkOutTime) || '—'}</TableCell>
                    <TableCell>
                      <Chip size="small" label={record.status} color={STATUS_COLOR[record.status] || 'default'} />
                    </TableCell>
                    <TableCell>{record.distanceFromMart != null ? `${Math.round(record.distanceFromMart)}m` : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table></TableContainer>
          </Paper>
        )}
      </Box>
    </LocalizationProvider>
  );
}
