import React, { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack, TextField, InputAdornment, Button,
  Dialog, DialogTitle, DialogContent, DialogActions, TablePagination, IconButton,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import { hierarchyPersonApi } from '../../services/lmtApi';
import { sortRows, useSort } from '../../utils/sorting';
import { usePagination } from '../../utils/pagination';
import SortableHeader from '../../components/SortableHeader';

const emptyForm = { name: '', roleLabel: '', contact: '' };

export default function HierarchyPeoplePage() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useQuery({ queryKey: ['lmt-hierarchy-persons'], queryFn: hierarchyPersonApi.getAll });

  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('name', 'asc');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [actionError, setActionError] = useState('');

  const createMutation = useMutation({
    mutationFn: hierarchyPersonApi.create,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['lmt-hierarchy-persons'] }); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to create.'),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, dto }) => hierarchyPersonApi.update(id, dto),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['lmt-hierarchy-persons'] }); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to update.'),
  });
  const deactivateMutation = useMutation({
    mutationFn: hierarchyPersonApi.deactivate,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lmt-hierarchy-persons'] }),
    onError: (e) => setActionError(e.response?.data?.message || 'Failed to deactivate.'),
  });
  const reactivateMutation = useMutation({
    mutationFn: hierarchyPersonApi.reactivate,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lmt-hierarchy-persons'] }),
    onError: (e) => setActionError(e.response?.data?.message || 'Failed to reactivate.'),
  });

  const filtered = useMemo(() => {
    const rows = data || [];
    if (!search.trim()) return rows;
    const q = search.trim().toLowerCase();
    return rows.filter(
      (r) => r.name.toLowerCase().includes(q) || (r.roleLabel || '').toLowerCase().includes(q) || (r.contact || '').toLowerCase().includes(q)
    );
  }, [data, search]);

  const sorted = useMemo(() => sortRows(filtered, sort.key, sort.direction), [filtered, sort]);
  const { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage, resetPage } = usePagination(sorted);

  const openCreateDialog = () => {
    setEditingId(null);
    setForm(emptyForm);
    setFormError('');
    setDialogOpen(true);
  };
  const openEditDialog = (person) => {
    setEditingId(person.id);
    setForm({ name: person.name, roleLabel: person.roleLabel || '', contact: person.contact || '' });
    setFormError('');
    setDialogOpen(true);
  };
  const closeDialog = () => setDialogOpen(false);

  const handleSave = () => {
    if (!form.name.trim()) {
      setFormError('Name is required.');
      return;
    }
    const dto = { name: form.name.trim(), roleLabel: form.roleLabel.trim() || null, contact: form.contact.trim() || null };
    if (editingId) {
      updateMutation.mutate({ id: editingId, dto });
    } else {
      createMutation.mutate(dto);
    }
  };

  const saving = createMutation.isPending || updateMutation.isPending;

  if (isLoading) return <CircularProgress />;
  if (isError) return <Alert severity="error">Failed to load hierarchy people. Try refreshing.</Alert>;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>Hierarchy People</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        TSE, SR TSE, and ASM reference records — assign them to Areas next.
      </Typography>

      {actionError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError('')}>{actionError}</Alert>}

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center">
        <TextField
          size="small"
          placeholder="Search name, role, or contact…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); resetPage(); }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
          sx={{ minWidth: 260 }}
        />
        <Button startIcon={<AddIcon />} variant="contained" onClick={openCreateDialog}>Add Person</Button>
      </Stack>

      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <SortableHeader label="Name" sortKey="name" sort={sort} onSort={onSort} />
              <SortableHeader label="Role Label" sortKey="roleLabel" sort={sort} onSort={onSort} />
              <SortableHeader label="Contact" sortKey="contact" sort={sort} onSort={onSort} />
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paged.length === 0 && (
              <TableRow><TableCell colSpan={5} align="center">No hierarchy people match this filter.</TableCell></TableRow>
            )}
            {paged.map((person) => (
              <TableRow key={person.id} hover>
                <TableCell>{person.name}</TableCell>
                <TableCell>{person.roleLabel || '—'}</TableCell>
                <TableCell>{person.contact || '—'}</TableCell>
                <TableCell>
                  <Chip size="small" label={person.isActive ? 'Active' : 'Inactive'} color={person.isActive ? 'success' : 'default'} />
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => openEditDialog(person)}><EditIcon fontSize="small" /></IconButton>
                  {person.isActive ? (
                    <Button size="small" color="error" onClick={() => deactivateMutation.mutate(person.id)}>Deactivate</Button>
                  ) : (
                    <Button size="small" onClick={() => reactivateMutation.mutate(person.id)}>Reactivate</Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={sorted.length}
          page={page}
          onPageChange={handleChangePage}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleChangeRowsPerPage}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Paper>

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{editingId ? 'Edit Person' : 'Add Person'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="Name"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              autoFocus
            />
            <TextField
              label="Role Label"
              placeholder="e.g. TSE, SR_TSE, ASM"
              helperText="A hint for which Area slot this person fits — not enforced, just for the picker below."
              value={form.roleLabel}
              onChange={(e) => setForm({ ...form, roleLabel: e.target.value })}
            />
            <TextField
              label="Contact"
              placeholder="Phone or email (optional)"
              value={form.contact}
              onChange={(e) => setForm({ ...form, contact: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
