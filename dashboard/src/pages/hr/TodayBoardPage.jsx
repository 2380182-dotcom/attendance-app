import React from 'react';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack,
} from '@mui/material';
import { useTodayBoard } from '../../hooks/useTodayBoard';
import { karachiToday, formatUtcToKarachi } from '../../utils/dateUtils';

const STATUS_COLOR = { IN: 'success', LATE: 'warning', ABSENT: 'error', OFF: 'default' };

export default function TodayBoardPage() {
  const { rows, counts, isLoading, isError } = useTodayBoard();

  if (isLoading) {
    return <CircularProgress />;
  }
  if (isError) {
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
