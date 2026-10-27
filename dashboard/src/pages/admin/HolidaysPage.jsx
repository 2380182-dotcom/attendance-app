import React, { useState } from 'react';
import dayjs from 'dayjs';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Stack, TextField, Button, Dialog, DialogTitle, DialogContent,
  DialogActions, IconButton, Tooltip, Switch, FormControlLabel,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineRounded';
import { holidayApi, companySettingsApi } from '../../services/holidayApi';

const emptyForm = { holidayDate: '', name: '' };

/**
 * Company holidays. The 10 PM job never marks a mart agent absent on these
 * dates, and adding a holiday removes any absences already saved for it.
 */
/**
 * Company switch: Absence tracking (off by default). Turning it on asks
 * first, because from the next 10 PM run HR starts getting absence alerts.
 */
function AbsenceTrackingCard() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ['company-settings'], queryFn: companySettingsApi.get });
  const [confirmOn, setConfirmOn] = useState(false);
  const change = useMutation({
    mutationFn: companySettingsApi.setAbsenceTracking,
    onSuccess: () => {
      setConfirmOn(false);
      queryClient.invalidateQueries({ queryKey: ['company-settings'] });
      queryClient.invalidateQueries({ queryKey: ['cockpit', 'hr'] });
    },
  });
  const on = settings.data?.absenceTracking === true;

  return (
    <Paper sx={{ maxWidth: 720, p: 2.5, mb: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
        <Box>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Absence tracking</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {on
              ? 'On: every night at 10 PM, active mart agents with no check-in on one of their working days are marked absent, and HR is notified.'
              : 'Off: nobody is marked absent and HR gets no absence alerts. The HR cockpit shows "Absence tracking is off" instead of absence numbers.'}
          </Typography>
        </Box>
        {settings.isLoading ? <CircularProgress size={24} /> : (
          <FormControlLabel
            sx={{ m: 0 }}
            labelPlacement="start"
            label={on ? 'On' : 'Off'}
            control={(
              <Switch
                checked={on}
                disabled={settings.isError || change.isPending}
                onChange={(e) => (e.target.checked ? setConfirmOn(true) : change.mutate(false))}
                slotProps={{ input: { 'aria-label': 'Absence tracking' } }}
              />
            )}
          />
        )}
      </Stack>
      {settings.isError && <Alert severity="error" sx={{ mt: 2 }}>Could not load this setting. Try refreshing.</Alert>}
      {change.isError && <Alert severity="error" sx={{ mt: 2 }}>Could not save. Try again.</Alert>}

      <Dialog open={confirmOn} onClose={() => setConfirmOn(false)}>
        <DialogTitle>Turn on absence tracking?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            From tonight at 10 PM, every active mart agent who has not checked in on one of their working days
            (holidays excepted) is marked absent, and HR is notified. Deactivate agents who no longer work here first.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOn(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => change.mutate(true)} disabled={change.isPending}>Turn on</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}

export default function HolidaysPage() {
  const theme = useTheme();
  const fullScreenDialog = useMediaQuery(theme.breakpoints.down('sm'));
  const queryClient = useQueryClient();
  const holidays = useQuery({ queryKey: ['holidays'], queryFn: holidayApi.getAll });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [deleting, setDeleting] = useState(null);
  const [actionError, setActionError] = useState('');

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['holidays'] });
  const closeDialog = () => { setDialogOpen(false); setEditingId(null); setForm(emptyForm); setFormError(''); };

  const save = useMutation({
    mutationFn: (dto) => (editingId ? holidayApi.update(editingId, dto) : holidayApi.create(dto)),
    onSuccess: () => { invalidate(); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Could not save the holiday.'),
  });
  const remove = useMutation({
    mutationFn: (id) => holidayApi.remove(id),
    onSuccess: () => { invalidate(); setDeleting(null); },
    onError: (e) => { setActionError(e.response?.data?.message || 'Could not delete the holiday.'); setDeleting(null); },
  });

  const openNew = () => { setForm(emptyForm); setEditingId(null); setFormError(''); setDialogOpen(true); };
  const openEdit = (h) => { setForm({ holidayDate: h.holidayDate, name: h.name }); setEditingId(h.id); setFormError(''); setDialogOpen(true); };

  const submit = () => {
    if (!form.holidayDate) { setFormError('Please choose a date.'); return; }
    if (!form.name.trim()) { setFormError('Please enter the holiday name.'); return; }
    save.mutate({ holidayDate: form.holidayDate, name: form.name.trim() });
  };

  const today = dayjs().format('YYYY-MM-DD');

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap spacing={1}>
        <Typography variant="h5">Holidays</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openNew}>Add holiday</Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3, maxWidth: 720 }}>
        Mart agents are never marked absent on these dates. Adding a holiday for a date that has already passed
        removes the absences saved for that date. Deleting a holiday does not add absences back.
      </Typography>

      <AbsenceTrackingCard />

      {actionError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError('')}>{actionError}</Alert>}
      {holidays.isLoading && <CircularProgress />}
      {holidays.isError && <Alert severity="error">Could not load holidays. Try refreshing.</Alert>}

      {holidays.data && (
        <Paper sx={{ maxWidth: 720 }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Holiday</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {holidays.data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      No holidays yet. Add Eid, Independence Day and other company holidays here.
                    </TableCell>
                  </TableRow>
                )}
                {holidays.data.map((h) => (
                  <TableRow key={h.id} hover sx={h.holidayDate < today ? { '& td': { color: 'text.secondary' } } : undefined}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{dayjs(h.holidayDate).format('ddd, D MMM YYYY')}</TableCell>
                    <TableCell>{h.name}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title="Edit"><IconButton aria-label={`Edit ${h.name}`} onClick={() => openEdit(h)}><EditIcon fontSize="small" /></IconButton></Tooltip>
                      <Tooltip title="Delete"><IconButton aria-label={`Delete ${h.name}`} onClick={() => setDeleting(h)}><DeleteOutlineIcon fontSize="small" /></IconButton></Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      <Dialog open={dialogOpen} onClose={closeDialog} fullScreen={fullScreenDialog} fullWidth maxWidth="xs">
        <DialogTitle>{editingId ? 'Edit holiday' : 'Add holiday'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Date" type="date" value={form.holidayDate}
              onChange={(e) => setForm((f) => ({ ...f, holidayDate: e.target.value }))}
              slotProps={{ inputLabel: { shrink: true } }} required
            />
            <TextField
              label="Name" value={form.name} placeholder="e.g. Eid ul Fitr"
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              slotProps={{ htmlInput: { maxLength: 100 } }} required
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(deleting)} onClose={() => setDeleting(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete holiday?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            {deleting && `${deleting.name} on ${dayjs(deleting.holidayDate).format('D MMM YYYY')} will be removed. Absences are not added back for that date.`}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleting(null)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={() => remove.mutate(deleting.id)} disabled={remove.isPending}>Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
