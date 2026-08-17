import React, { useMemo, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack, TextField, InputAdornment,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { useTodayBoard } from '../../hooks/useTodayBoard';
import { karachiToday, formatUtcToKarachi } from '../../utils/dateUtils';
import { sortRows, useSort } from '../../utils/sorting';
import SortableHeader from '../../components/SortableHeader';

const STATUS_COLOR = { IN: 'success', LATE: 'warning', ABSENT: 'error', OFF: 'default' };
const STATUSES = ['ALL', 'IN', 'LATE', 'ABSENT', 'OFF'];

export default function AgentStatusPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialStatus = STATUSES.includes(searchParams.get('status')) ? searchParams.get('status') : 'ALL';

  const { rows, counts, isLoading, isError } = useTodayBoard();
  const [statusFilter, setStatusFilter] = useState(initialStatus);
  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('name', 'asc');

  const filtered = useMemo(() => {
    let result = rows;
    if (statusFilter !== 'ALL') result = result.filter((r) => r.status === statusFilter);
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      result = result.filter(
        (r) => r.name.toLowerCase().includes(q) || (r.employeeId || '').toLowerCase().includes(q) || (r.department || '').toLowerCase().includes(q)
      );
    }
    return result;
  }, [rows, statusFilter, search]);

  const sorted = useMemo(() => sortRows(filtered, sort.key, sort.direction), [filtered, sort]);

  if (isLoading) return <CircularProgress />;
  if (isError) return <Alert severity="error">Failed to load today's agent status. Try refreshing.</Alert>;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Agent Status — {karachiToday().format('dddd, MMMM D')} (Pakistan time)
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 3 }} alignItems="center" flexWrap="wrap" useFlexGap>
        <ToggleButtonGroup size="small" exclusive value={statusFilter} onChange={(e, v) => v && setStatusFilter(v)}>
          <ToggleButton value="ALL">All ({rows.length})</ToggleButton>
          <ToggleButton value="IN">In ({counts.IN})</ToggleButton>
          <ToggleButton value="LATE">Late ({counts.LATE})</ToggleButton>
          <ToggleButton value="ABSENT">Absent ({counts.ABSENT})</ToggleButton>
          <ToggleButton value="OFF">Off ({counts.OFF})</ToggleButton>
        </ToggleButtonGroup>

        <TextField
          size="small"
          placeholder="Search name, ID, or department…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
          sx={{ minWidth: 260 }}
        />
      </Stack>

      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <SortableHeader label="Agent" sortKey="name" sort={sort} onSort={onSort} />
              <SortableHeader label="ID" sortKey="employeeId" sort={sort} onSort={onSort} />
              <SortableHeader label="Department" sortKey="department" sort={sort} onSort={onSort} />
              <TableCell>Status</TableCell>
              <SortableHeader label="Check-In Time" sortKey="checkInTime" sort={sort} onSort={onSort} />
              <SortableHeader label="Late By" sortKey="lateMinutes" sort={sort} onSort={onSort} align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {sorted.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center">No agents match this filter.</TableCell></TableRow>
            )}
            {sorted.map((row) => (
              <TableRow key={row.agentId} hover onClick={() => navigate(`/hr/agents/${row.agentId}`)} sx={{ cursor: 'pointer' }}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.employeeId || '—'}</TableCell>
                <TableCell>{row.department}</TableCell>
                <TableCell>
                  <Chip size="small" label={row.status} color={STATUS_COLOR[row.status]} />
                </TableCell>
                <TableCell>{formatUtcToKarachi(row.checkInTime, 'h:mm A') || '—'}</TableCell>
                <TableCell align="right">{row.lateMinutes ? `${row.lateMinutes} min` : '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
}
