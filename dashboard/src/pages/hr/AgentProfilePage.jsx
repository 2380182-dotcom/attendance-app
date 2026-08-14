import React, { useState } from 'react';
import dayjs from 'dayjs';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Grid, Button,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { hrApi } from '../../services/hrApi';
import { attendanceApi } from '../../services/attendanceApi';
import { formatUtcToKarachi, karachiStartOfDayIso, karachiEndOfDayIso } from '../../utils/dateUtils';

const STATUS_COLOR = { IN: 'success', LATE: 'warning', NON_WORKING_DAY: 'default' };

function InfoRow({ label, value }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', py: 0.75 }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography fontWeight="500">{value ?? '—'}</Typography>
    </Box>
  );
}

export default function AgentProfilePage() {
  const { agentId } = useParams();
  const navigate = useNavigate();
  const [rangeStart] = useState(dayjs().subtract(30, 'day'));
  const [rangeEnd] = useState(dayjs());

  const agent = useQuery({ queryKey: ['agent', agentId], queryFn: () => hrApi.getAgent(agentId) });
  const faceStatus = useQuery({ queryKey: ['face-status', agentId], queryFn: () => hrApi.getFaceStatus(agentId) });
  const history = useQuery({
    queryKey: ['agent-attendance-range', agentId],
    queryFn: () =>
      attendanceApi.getAgentDateRange(
        agentId,
        karachiStartOfDayIso(rangeStart.format('YYYY-MM-DD')),
        karachiEndOfDayIso(rangeEnd.format('YYYY-MM-DD'))
      ),
  });

  if (agent.isLoading || faceStatus.isLoading) return <CircularProgress />;
  if (agent.isError) return <Alert severity="error">Failed to load this agent. They may not exist or you may not have access.</Alert>;

  const a = agent.data;
  const f = faceStatus.data;

  return (
    <Box>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} sx={{ mb: 2 }}>Back</Button>
      <Typography variant="h5" gutterBottom>{a.name}</Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2.5 }}>
            <Typography variant="h6" gutterBottom>Details</Typography>
            <InfoRow label="Agent ID" value={a.agentId} />
            <InfoRow label="Department" value={a.department} />
            <InfoRow label="Role" value={a.role} />
            <InfoRow label="Email" value={a.email} />
            <InfoRow label="Phone" value={a.phone} />
            <InfoRow label="Shift" value={a.shiftStartTime && a.shiftEndTime ? `${a.shiftStartTime} – ${a.shiftEndTime}` : '—'} />
            <InfoRow label="Working Days" value={a.workingDays?.join(', ') || 'All days'} />
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2.5 }}>
            <Typography variant="h6" gutterBottom>Face Verification</Typography>
            <Box sx={{ mb: 1 }}>
              <Chip
                label={f?.registered ? 'Enrolled' : 'Not Enrolled'}
                color={f?.registered ? 'success' : 'error'}
                size="small"
              />
            </Box>
            <InfoRow label="Verification Enabled" value={f?.faceVerificationEnabled ? 'Yes' : 'No'} />
            <InfoRow label="Verify on Check-In" value={f?.faceVerifyOnCheckIn ? 'Yes' : 'No'} />
            <InfoRow label="Verify on Check-Out" value={f?.faceVerifyOnCheckOut ? 'Yes' : 'No'} />
            <InfoRow label="Mid-Shift Frequency" value={f?.faceVerificationFrequency != null ? `${f.faceVerificationFrequency}x/day` : '—'} />
            <InfoRow label="Profile Last Updated" value={f?.templateUpdatedAt ? formatUtcToKarachi(f.templateUpdatedAt) : '—'} />
          </Paper>
        </Grid>
      </Grid>

      <Paper>
        <Typography variant="h6" sx={{ p: 2, pb: 0 }}>Attendance History — last 30 days</Typography>
        {history.isLoading && <Box sx={{ p: 2 }}><CircularProgress size={24} /></Box>}
        {history.isError && <Alert severity="error" sx={{ m: 2 }}>Failed to load attendance history.</Alert>}
        {history.data && (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Mart</TableCell>
                <TableCell>Check-In</TableCell>
                <TableCell>Check-Out</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {history.data.length === 0 && (
                <TableRow><TableCell colSpan={4} align="center">No attendance records in the last 30 days.</TableCell></TableRow>
              )}
              {history.data.map((record) => (
                <TableRow key={record.id}>
                  <TableCell>{record.martName}</TableCell>
                  <TableCell>{formatUtcToKarachi(record.checkInTime) || '—'}</TableCell>
                  <TableCell>{formatUtcToKarachi(record.checkOutTime) || '—'}</TableCell>
                  <TableCell><Chip size="small" label={record.status} color={STATUS_COLOR[record.status] || 'default'} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>
    </Box>
  );
}
