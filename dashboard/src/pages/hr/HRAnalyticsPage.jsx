import React, { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Grid, Stack, TableContainer,
} from '@mui/material';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { hrApi } from '../../services/hrApi';
import { attendanceApi, agentApi } from '../../services/attendanceApi';
import { karachiStartOfDayIso, karachiEndOfDayIso } from '../../utils/dateUtils';
import { computeChronicLateComers, computeMostAbsences, computeAttendanceTrend } from '../../utils/attendanceStats';
import { useTodayBoard } from '../../hooks/useTodayBoard';

function StatCard({ label, value, sub, onClick }) {
  return (
    <Paper
      sx={onClick ? { p: 2.5, cursor: 'pointer', '&:hover': { boxShadow: 6 } } : { p: 2.5 }}
      onClick={onClick}
    >
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold">{value}</Typography>
      <Typography variant="caption" color={onClick ? 'primary' : 'text.secondary'}>
        {onClick ? `${sub} — view agents →` : sub}
      </Typography>
    </Paper>
  );
}

export default function HRAnalyticsPage() {
  const navigate = useNavigate();
  const [rangeStart] = useState(dayjs().subtract(30, 'day'));
  const [rangeEnd] = useState(dayjs());

  // Mid-Shift Compliance is the one stat here that can't come from
  // daily-report + active-roster — daily-report's shiftCompliance field is
  // about check-in timing (on-time/late/outside-shift), not the 3x/day
  // mid-shift verification schedule this stat is actually about. That data
  // only exists in this summary endpoint's attendanceSalesSheet, so it stays
  // here rather than being folded into useTodayBoard.
  const summary = useQuery({ queryKey: ['hr-dashboard'], queryFn: () => hrApi.getDashboard() });
  const activeAgents = useQuery({ queryKey: ['agents', 'active'], queryFn: () => agentApi.getActive() });
  const today = useTodayBoard();

  const startIso = karachiStartOfDayIso(rangeStart.format('YYYY-MM-DD'));
  const endIso = karachiEndOfDayIso(rangeEnd.format('YYYY-MM-DD'));
  const rangeRecords = useQuery({
    queryKey: ['attendance-range-30d', startIso, endIso],
    queryFn: () => attendanceApi.getDateRange(startIso, endIso),
  });

  const lateComers = useMemo(
    () => (rangeRecords.data ? computeChronicLateComers(rangeRecords.data) : []),
    [rangeRecords.data]
  );
  const absences = useMemo(
    () => (rangeRecords.data && activeAgents.data ? computeMostAbsences(rangeRecords.data, activeAgents.data, rangeStart, rangeEnd) : []),
    [rangeRecords.data, activeAgents.data, rangeStart, rangeEnd]
  );
  const trend = useMemo(
    () => (rangeRecords.data ? computeAttendanceTrend(rangeRecords.data) : []),
    [rangeRecords.data]
  );

  const loading = summary.isLoading || activeAgents.isLoading || rangeRecords.isLoading || today.isLoading;
  const errored = summary.isError || activeAgents.isError || rangeRecords.isError || today.isError;

  if (loading) return <CircularProgress />;
  if (errored) return <Alert severity="error">Failed to load HR analytics. Try refreshing.</Alert>;

  const s = summary.data;
  const totalToday = today.rows.length;
  const pct = (count) => (totalToday === 0 ? 0 : (count / totalToday) * 100);

  return (
    <Box>
        <Typography variant="h5" gutterBottom>HR Analytics</Typography>

        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={3}>
            <StatCard
              label="Checked In Today"
              value={`${pct(today.counts.IN).toFixed(0)}%`}
              sub={`${today.counts.IN} of ${totalToday} agents`}
              onClick={() => navigate('/hr/status?status=IN')}
            />
          </Grid>
          <Grid item xs={12} sm={3}>
            <StatCard
              label="Late Today"
              value={`${pct(today.counts.LATE).toFixed(0)}%`}
              sub={`${today.counts.LATE} agents`}
              onClick={() => navigate('/hr/status?status=LATE')}
            />
          </Grid>
          <Grid item xs={12} sm={3}>
            <StatCard
              label="Absent Today"
              value={`${pct(today.counts.ABSENT).toFixed(0)}%`}
              sub={`${today.counts.ABSENT} agents`}
              onClick={() => navigate('/hr/status?status=ABSENT')}
            />
          </Grid>
          <Grid item xs={12} sm={3}>
            <StatCard label="Full Mid-Shift Compliance" value={`${s.complianceAll3Percent?.toFixed(0) ?? 0}%`} sub={`${s.complianceAll3Count ?? 0} agents`} />
          </Grid>
        </Grid>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Typography variant="h6" gutterBottom>Attendance Trend — last 30 days</Typography>
          {trend.length === 0 ? (
            <Typography color="text.secondary">No attendance records in the last 30 days.</Typography>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={trend}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Line type="monotone" dataKey="count" name="Check-ins" stroke="#1976d2" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </Paper>

        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TableContainer component={Paper} sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ p: 2, pb: 0 }}>Chronic Late-Comers — last 30 days</Typography>
            <Table>
              <TableHead>
                <TableRow><TableCell>Agent</TableCell><TableCell align="right">Late Days</TableCell></TableRow>
              </TableHead>
              <TableBody>
                {lateComers.length === 0 && (
                  <TableRow><TableCell colSpan={2} align="center">No late check-ins in this period.</TableCell></TableRow>
                )}
                {lateComers.map((row) => (
                  <TableRow key={row.agentId} hover onClick={() => navigate(`/hr/agents/${row.agentId}`)} sx={{ cursor: 'pointer' }}>
                    <TableCell>{row.agentName}</TableCell>
                    <TableCell align="right">{row.lateCount}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          <TableContainer component={Paper} sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ p: 2, pb: 0 }}>Most Absences — last 30 days</Typography>
            <Table>
              <TableHead>
                <TableRow><TableCell>Agent</TableCell><TableCell align="right">Absences</TableCell></TableRow>
              </TableHead>
              <TableBody>
                {absences.length === 0 && (
                  <TableRow><TableCell colSpan={2} align="center">No absences in this period.</TableCell></TableRow>
                )}
                {absences.map((row) => (
                  <TableRow key={row.agentId} hover onClick={() => navigate(`/hr/agents/${row.agentId}`)} sx={{ cursor: 'pointer' }}>
                    <TableCell>{row.agentName}</TableCell>
                    <TableCell align="right">{row.absenceCount}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Stack>
    </Box>
  );
}
