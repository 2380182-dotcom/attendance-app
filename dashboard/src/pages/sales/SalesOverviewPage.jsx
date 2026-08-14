import React, { useEffect, useState, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Grid, List, ListItem, ListItemText,
} from '@mui/material';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { salesApi } from '../../services/salesApi';
import { connectStompTopic } from '../../services/websocketService';
import { formatUtcToKarachi } from '../../utils/dateUtils';

const MAX_FEED_ITEMS = 20;
const TYPE_COLOR = { CHECK_IN: 'success', CHECK_OUT: 'default', LATE: 'warning' };

function StatCard({ label, value }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight="bold">{value}</Typography>
    </Paper>
  );
}

export default function SalesOverviewPage() {
  const dashboard = useQuery({ queryKey: ['sales-dashboard', 'realtime'], queryFn: () => salesApi.getRealtimeDashboard() });
  const [feed, setFeed] = useState([]);
  const [wsStatus, setWsStatus] = useState('connecting');
  const feedIdCounter = useRef(0);

  useEffect(() => {
    const disconnect = connectStompTopic(
      '/topic/alerts',
      (notification) => {
        if (notification.department !== 'SALES') return; // shared topic — HR-tagged events also flow through it
        feedIdCounter.current += 1;
        setFeed((prev) => [{ ...notification, _feedId: feedIdCounter.current }, ...prev].slice(0, MAX_FEED_ITEMS));
      },
      setWsStatus
    );
    return disconnect;
  }, []);

  if (dashboard.isLoading) return <CircularProgress />;
  if (dashboard.isError) return <Alert severity="error">Failed to load today's sales dashboard. Try refreshing.</Alert>;

  const d = dashboard.data;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>Sales Overview — Today (Pakistan time)</Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}><StatCard label="Revenue" value={`PKR ${(d.todayTotalRevenue ?? 0).toLocaleString()}`} /></Grid>
        <Grid item xs={12} sm={4}><StatCard label="Units Dispatched" value={d.todayTotalUnits ?? 0} /></Grid>
        <Grid item xs={12} sm={4}><StatCard label="Agents Checked In" value={d.activeAgentsCount ?? 0} /></Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>Revenue Trend</Typography>
            {(!d.salesTrend || d.salesTrend.length === 0) ? (
              <Typography color="text.secondary">No trend data yet.</Typography>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={d.salesTrend}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis />
                  <Tooltip formatter={(value) => `PKR ${value.toLocaleString()}`} />
                  <Line type="monotone" dataKey="revenue" stroke="#1976d2" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Paper>

          <Paper>
            <Typography variant="h6" sx={{ p: 2, pb: 0 }}>Sales by Agent (Today)</Typography>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Agent</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Units</TableCell>
                  <TableCell align="right">Revenue</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(!d.salesByAgent || d.salesByAgent.length === 0) && (
                  <TableRow><TableCell colSpan={4} align="center">No agent activity yet today.</TableCell></TableRow>
                )}
                {d.salesByAgent?.map((row) => (
                  <TableRow key={row.agentName}>
                    <TableCell>{row.agentName}</TableCell>
                    <TableCell>{row.status}</TableCell>
                    <TableCell align="right">{row.unitsSold}</TableCell>
                    <TableCell align="right">PKR {row.revenue.toLocaleString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h6">Live Feed</Typography>
              <Chip
                size="small"
                label={wsStatus === 'connected' ? 'Live' : wsStatus === 'error' ? 'Disconnected' : 'Connecting…'}
                color={wsStatus === 'connected' ? 'success' : wsStatus === 'error' ? 'error' : 'default'}
              />
            </Box>
            {wsStatus === 'error' && (
              <Alert severity="warning" sx={{ mb: 1 }}>
                Live feed couldn't connect — likely a CORS issue with the WebSocket handshake. Check-in/out data everywhere else is unaffected.
              </Alert>
            )}
            {feed.length === 0 ? (
              <Typography color="text.secondary">Waiting for check-in/check-out activity…</Typography>
            ) : (
              <List dense>
                {feed.map((item) => (
                  <ListItem key={item._feedId} divider>
                    <ListItemText
                      primary={item.message}
                      secondary={formatUtcToKarachi(item.createdAt, 'h:mm:ss A')}
                    />
                    <Chip size="small" label={item.type} color={TYPE_COLOR[item.type] || 'default'} />
                  </ListItem>
                ))}
              </List>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
