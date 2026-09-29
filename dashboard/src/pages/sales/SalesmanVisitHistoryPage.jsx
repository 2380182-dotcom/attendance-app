import React, { useState } from 'react';
import dayjs from 'dayjs';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { LocalizationProvider, DatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Grid, Button, Stack,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { shopVisitApi } from '../../services/lmtApi';
import { agentApi } from '../../services/attendanceApi';

function StatCard({ label, value, color }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold" color={color}>{value}</Typography>
    </Paper>
  );
}

const STATUS_COLOR = { SUCCESS: 'success', INVALID_CODE: 'error', SHOP_INACTIVE: 'error', OUTSIDE_GEOFENCE: 'warning' };
const STATUS_LABEL = { SUCCESS: 'Verified', INVALID_CODE: 'Invalid Code', SHOP_INACTIVE: 'Shop Inactive', OUTSIDE_GEOFENCE: 'Outside Geofence' };

/**
 * QR shop-visit flow (Q4): "Ali Ahmed, Today's Visits: 8, ..." from the
 * spec's example screen — one salesman's scan history for one day, plus
 * the four summary counts, using GET /lmt/shop-visits/summary directly
 * (already returns exactly this shape). Same route-param/back-button
 * pattern as AgentProfilePage (HR's equivalent drill-down).
 */
export default function SalesmanVisitHistoryPage() {
  const { agentId } = useParams();
  const navigate = useNavigate();
  const [date, setDate] = useState(dayjs());

  const agents = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const salesman = (agents.data || []).find((a) => String(a.id) === String(agentId));

  const dateStr = date.format('YYYY-MM-DD');
  const summary = useQuery({
    queryKey: ['shop-visit-summary', agentId, dateStr],
    queryFn: () => shopVisitApi.getDaySummary(agentId, dateStr),
  });

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} sx={{ mb: 2 }}>Back</Button>
        <Typography variant="h5" gutterBottom>
          {salesman ? salesman.name : `Salesman #${agentId}`}
        </Typography>
        {salesman && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {salesman.role === 'SALESMAN_LOCAL' ? 'Local Salesman' : salesman.role === 'SALESMAN_LMT' ? 'LMT' : salesman.role}
            {salesman.agentId ? ` · ${salesman.agentId}` : ''}
          </Typography>
        )}

        <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center">
          <DatePicker
            label="Date"
            value={date}
            onChange={(v) => { if (v) setDate(v); }}
            maxDate={dayjs()}
            slotProps={{ textField: { size: 'small' } }}
          />
        </Stack>

        {summary.isLoading || agents.isLoading ? (
          <CircularProgress />
        ) : summary.isError ? (
          <Alert severity="error">Failed to load this salesman's visit history. Try refreshing.</Alert>
        ) : (
          <>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Total Visits" value={summary.data.totalVisits} />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Successful Visits" value={summary.data.successfulVisits} color="success.main" />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard
                  label="Failed Visits"
                  value={summary.data.failedVisits}
                  color={summary.data.failedVisits > 0 ? 'error.main' : undefined}
                />
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <StatCard label="Unique Shops Visited" value={summary.data.uniqueShopsVisited} />
              </Grid>
            </Grid>

            <Paper>
              <TableContainer><Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Time</TableCell>
                    <TableCell>Shop Code</TableCell>
                    <TableCell>Shop</TableCell>
                    <TableCell>Area</TableCell>
                    <TableCell align="right">Distance</TableCell>
                    <TableCell>Result</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(summary.data.visits || []).length === 0 && (
                    <TableRow><TableCell colSpan={6} align="center">No visits on this day.</TableCell></TableRow>
                  )}
                  {(summary.data.visits || []).map((v) => (
                    <TableRow key={v.visitId} hover>
                      <TableCell>{v.scanTime}</TableCell>
                      <TableCell>{v.shopCode || v.scannedCode}</TableCell>
                      <TableCell>{v.shopName || '—'}</TableCell>
                      <TableCell>{v.areaName || '—'}</TableCell>
                      <TableCell align="right">{v.distanceMeters != null ? `${Math.round(v.distanceMeters)}m` : '—'}</TableCell>
                      <TableCell>
                        <Chip size="small" color={STATUS_COLOR[v.visitStatus] || 'default'} label={STATUS_LABEL[v.visitStatus] || v.visitStatus} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table></TableContainer>
            </Paper>
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
}
