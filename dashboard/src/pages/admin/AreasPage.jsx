import React, { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack, TextField, InputAdornment, Button,
  Dialog, DialogTitle, DialogContent, DialogActions, TablePagination, IconButton,
  Select, MenuItem, InputLabel, FormControl,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import { areaApi, hierarchyPersonApi } from '../../services/lmtApi';
import { sortRows, useSort } from '../../utils/sorting';
import { usePagination } from '../../utils/pagination';
import SortableHeader from '../../components/SortableHeader';

const NONE = '';
const emptyForm = { name: '', tseId: NONE, srTseId: NONE, asmId: NONE };

function PersonPicker({ label, value, onChange, people }) {
  return (
    <FormControl fullWidth size="small">
      <InputLabel>{label}</InputLabel>
      <Select label={label} value={value} onChange={(e) => onChange(e.target.value)}>
        <MenuItem value={NONE}><em>None</em></MenuItem>
        {people.map((p) => (
          <MenuItem key={p.id} value={p.id}>{p.name}{p.roleLabel ? ` (${p.roleLabel})` : ''}</MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}

export default function AreasPage() {
  const theme = useTheme();
  const fullScreenDialog = useMediaQuery(theme.breakpoints.down('sm'));
  const queryClient = useQueryClient();
  const areas = useQuery({ queryKey: ['lmt-areas'], queryFn: areaApi.getAll });
  const people = useQuery({ queryKey: ['lmt-hierarchy-persons'], queryFn: hierarchyPersonApi.getAll });
  const activePeople = useMemo(() => (people.data || []).filter((p) => p.isActive), [people.data]);

  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('name', 'asc');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [actionError, setActionError] = useState('');

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['lmt-areas'] });

  const createMutation = useMutation({
    mutationFn: areaApi.create,
    onSuccess: () => { invalidate(); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to create.'),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, dto }) => areaApi.update(id, dto),
    onSuccess: () => { invalidate(); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to update.'),
  });
  const deactivateMutation = useMutation({
    mutationFn: areaApi.deactivate,
    onSuccess: invalidate,
    onError: (e) => setActionError(e.response?.data?.message || 'Failed to deactivate.'),
  });
  const reactivateMutation = useMutation({
    mutationFn: areaApi.reactivate,
    onSuccess: invalidate,
    onError: (e) => setActionError(e.response?.data?.message || 'Failed to reactivate.'),
  });

  const filtered = useMemo(() => {
    const rows = areas.data || [];
    if (!search.trim()) return rows;
    const q = search.trim().toLowerCase();
    return rows.filter((a) => a.name.toLowerCase().includes(q));
  }, [areas.data, search]);

  const sorted = useMemo(() => sortRows(filtered, sort.key, sort.direction), [filtered, sort]);
  const { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage, resetPage } = usePagination(sorted);

  const openCreateDialog = () => {
    setEditingId(null);
    setForm(emptyForm);
    setFormError('');
    setDialogOpen(true);
  };
  const openEditDialog = (area) => {
    setEditingId(area.id);
    setForm({
      name: area.name,
      tseId: area.tse?.id || NONE,
      srTseId: area.srTse?.id || NONE,
      asmId: area.asm?.id || NONE,
    });
    setFormError('');
    setDialogOpen(true);
  };
  const closeDialog = () => setDialogOpen(false);

  const handleSave = () => {
    if (!form.name.trim()) {
      setFormError('Name is required.');
      return;
    }
    const dto = {
      name: form.name.trim(),
      tseId: form.tseId || null,
      srTseId: form.srTseId || null,
      asmId: form.asmId || null,
    };
    if (editingId) {
      updateMutation.mutate({ id: editingId, dto });
    } else {
      createMutation.mutate(dto);
    }
  };

  const saving = createMutation.isPending || updateMutation.isPending;

  if (areas.isLoading || people.isLoading) return <CircularProgress />;
  if (areas.isError || people.isError) return <Alert severity="error">Failed to load areas. Try refreshing.</Alert>;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>Areas</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Each area's TSE / SR TSE / ASM apply to every shop in it — reassigning here updates every shop at once.
      </Typography>

      {actionError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError('')}>{actionError}</Alert>}

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center" flexWrap="wrap" useFlexGap>
        <TextField
          size="small"
          placeholder="Search area name…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); resetPage(); }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
          sx={{ minWidth: 260 }}
        />
        <Button startIcon={<AddIcon />} variant="contained" onClick={openCreateDialog}>Add Area</Button>
      </Stack>

      <Paper>
        <TableContainer><Table>
          <TableHead>
            <TableRow>
              <SortableHeader label="Area" sortKey="name" sort={sort} onSort={onSort} />
              <TableCell>TSE</TableCell>
              <TableCell>SR TSE</TableCell>
              <TableCell>ASM</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paged.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center">No areas match this filter.</TableCell></TableRow>
            )}
            {paged.map((area) => (
              <TableRow key={area.id} hover>
                <TableCell>{area.name}</TableCell>
                <TableCell>{area.tse?.name || '—'}</TableCell>
                <TableCell>{area.srTse?.name || '—'}</TableCell>
                <TableCell>{area.asm?.name || '—'}</TableCell>
                <TableCell>
                  <Chip size="small" label={area.isActive ? 'Active' : 'Inactive'} color={area.isActive ? 'success' : 'default'} />
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => openEditDialog(area)}><EditIcon fontSize="small" /></IconButton>
                  {area.isActive ? (
                    <Button size="small" color="error" onClick={() => deactivateMutation.mutate(area.id)}>Deactivate</Button>
                  ) : (
                    <Button size="small" onClick={() => reactivateMutation.mutate(area.id)}>Reactivate</Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table></TableContainer>
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

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="sm" fullWidth fullScreen={fullScreenDialog}>
        <DialogTitle>{editingId ? 'Edit Area' : 'Add Area'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="Area Name"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              autoFocus
            />
            <PersonPicker label="TSE" value={form.tseId} onChange={(v) => setForm({ ...form, tseId: v })} people={activePeople} />
            <PersonPicker label="SR TSE" value={form.srTseId} onChange={(v) => setForm({ ...form, srTseId: v })} people={activePeople} />
            <PersonPicker label="ASM" value={form.asmId} onChange={(v) => setForm({ ...form, asmId: v })} people={activePeople} />
            {activePeople.length === 0 && (
              <Alert severity="info">No active hierarchy people yet — add one under Hierarchy People first.</Alert>
            )}
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
