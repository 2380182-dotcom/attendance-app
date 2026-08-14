import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  TextField, TablePagination, CircularProgress, Alert, Chip,
} from '@mui/material';
import { agentApi } from '../../services/attendanceApi';

export default function AgentDirectoryPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  const activeAgents = useQuery({ queryKey: ['agents', 'active'], queryFn: () => agentApi.getActive() });

  const filtered = useMemo(() => {
    if (!activeAgents.data) return [];
    const q = search.trim().toLowerCase();
    if (!q) return activeAgents.data;
    return activeAgents.data.filter(
      (a) => a.name?.toLowerCase().includes(q) || a.agentId?.toLowerCase().includes(q) || a.department?.toLowerCase().includes(q)
    );
  }, [activeAgents.data, search]);

  const paged = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  if (activeAgents.isLoading) return <CircularProgress />;
  if (activeAgents.isError) return <Alert severity="error">Failed to load the agent directory. Try refreshing.</Alert>;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>Agent Directory</Typography>

      <TextField
        label="Search by name, agent ID, or department"
        size="small"
        value={search}
        onChange={(e) => { setSearch(e.target.value); setPage(0); }}
        sx={{ mb: 2, width: 360 }}
      />

      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Agent ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Department</TableCell>
              <TableCell>Face Enrolled</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paged.length === 0 && (
              <TableRow><TableCell colSpan={4} align="center">No agents match "{search}".</TableCell></TableRow>
            )}
            {paged.map((agent) => (
              <TableRow key={agent.id} hover onClick={() => navigate(`/hr/agents/${agent.id}`)} sx={{ cursor: 'pointer' }}>
                <TableCell>{agent.agentId}</TableCell>
                <TableCell>{agent.name}</TableCell>
                <TableCell>{agent.department}</TableCell>
                <TableCell>
                  <Chip size="small" label={agent.faceRegistered ? 'Yes' : 'No'} color={agent.faceRegistered ? 'success' : 'default'} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={filtered.length}
          page={page}
          onPageChange={(e, newPage) => setPage(newPage)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50, 100]}
        />
      </Paper>
    </Box>
  );
}
