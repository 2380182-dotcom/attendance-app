import React, { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack,
} from '@mui/material';
import { attendanceApi, agentApi } from '../../services/attendanceApi';
import { karachiToday, karachiTodayDayCode, formatUtcToKarachi } from '../../utils/dateUtils';

const STATUS_COLOR = { IN: 'success', LATE: 'warning', ABSENT: 'error', OFF: 'default' };

export default function TodayBoardPage() {
  // Pass Pakistan's calendar date explicitly rather than omitting the param
  // — the backend's own "today" default is LocalDate.now() on its UTC
  // container, which can be a different calendar date than Pakistan's
  // during the ~5-hour window after midnight PKT. This fixes the common
  // case; the underlying day-boundary comparison being UTC-based (not just
  // the default) is a separate backend issue, flagged below.
  const todayDate = karachiToday().format('YYYY-MM-DD');
  const dailyReport = useQuery({ queryKey: ['daily-report', todayDate], queryFn: () => attendanceApi.getDailyReport(todayDate) });
  const activeAgents = useQuery({ queryKey: ['agents', 'active'], queryFn: () => agentApi.getActive() });

  const rows = useMemo(() => {
    if (!dailyReport.data || !activeAgents.data) return [];

    const reportByAgentId = new Map(dailyReport.data.map((r) => [r.attendance.agentId, r]));
    const todayCode = karachiTodayDayCode();

    return activeAgents.data.map((agent) => {
      const record = reportByAgentId.get(agent.id);
      if (record) {
        return {
          agentId: agent.id,
          name: agent.name,
          department: agent.department,
          status: record.attendance.status === 'NON_WORKING_DAY' ? 'OFF' : record.attendance.status,
          checkInTime: record.attendance.checkInTime,
          lateMinutes: record.lateMinutes,
          faceVerified: record.faceVerified,
        };
      }
      // No record at all today — absent only if today is actually one of
      // their working days; otherwise they're legitimately off, not absent.
      const isWorkingDay = !agent.workingDays || agent.workingDays.length === 0 || agent.workingDays.includes(todayCode);
      return {
        agentId: agent.id,
        name: agent.name,
        department: agent.department,
        status: isWorkingDay ? 'ABSENT' : 'OFF',
        checkInTime: null,
        lateMinutes: null,
        faceVerified: null,
      };
    });
  }, [dailyReport.data, activeAgents.data]);

  const counts = useMemo(() => {
    const c = { IN: 0, LATE: 0, ABSENT: 0, OFF: 0 };
    rows.forEach((r) => { c[r.status] = (c[r.status] || 0) + 1; });
    return c;
  }, [rows]);

  if (dailyReport.isLoading || activeAgents.isLoading) {
    return <CircularProgress />;
  }
  if (dailyReport.isError || activeAgents.isError) {
    return <Alert severity="error">Failed to load today's attendance. Try refreshing.</Alert>;
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Today's Attendance — {karachiToday().format('dddd, MMMM D')} (Pakistan time)
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <Chip label={`In: ${counts.IN}`} color="success" />
        <Chip label={`Late: ${counts.LATE}`} color="warning" />
        <Chip label={`Absent: ${counts.ABSENT}`} color="error" />
        <Chip label={`Off today: ${counts.OFF}`} variant="outlined" />
      </Stack>

      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Agent</TableCell>
              <TableCell>Department</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Check-In Time</TableCell>
              <TableCell>Late By</TableCell>
              <TableCell>Face Verified</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.agentId}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.department}</TableCell>
                <TableCell>
                  <Chip size="small" label={row.status} color={STATUS_COLOR[row.status]} />
                </TableCell>
                <TableCell>{formatUtcToKarachi(row.checkInTime, 'h:mm A') || '—'}</TableCell>
                <TableCell>{row.lateMinutes ? `${row.lateMinutes} min` : '—'}</TableCell>
                <TableCell>{row.faceVerified === null ? '—' : row.faceVerified ? 'Yes' : 'No'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
}
