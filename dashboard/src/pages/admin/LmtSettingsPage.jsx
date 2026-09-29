import React, { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, TextField, Button, CircularProgress, Alert, Stack,
  Select, MenuItem, InputLabel, FormControl, Divider,
} from '@mui/material';
import { lmtSettingsApi } from '../../services/lmtApi';

const MODE_OPTIONS = [
  { value: 'PER_SHOP', label: "Follow each shop's own setting" },
  { value: 'FORCE_ON', label: 'Force ON for all shops' },
  { value: 'FORCE_OFF', label: 'Force OFF for all shops' },
];

function ModeSelect({ label, value, onChange, helperText }) {
  return (
    <FormControl fullWidth size="small">
      <InputLabel>{label}</InputLabel>
      <Select label={label} value={value} onChange={(e) => onChange(e.target.value)}>
        {MODE_OPTIONS.map((opt) => <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>)}
      </Select>
      {helperText && (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>{helperText}</Typography>
      )}
    </FormControl>
  );
}

export default function LmtSettingsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useQuery({ queryKey: ['lmt-settings'], queryFn: lmtSettingsApi.get });

  const [buffer, setBuffer] = useState('');
  const [geofenceMode, setGeofenceMode] = useState('PER_SHOP');
  const [qrMode, setQrMode] = useState('PER_SHOP');
  const [formError, setFormError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (data?.geofenceBufferMeters != null) {
      setBuffer(String(data.geofenceBufferMeters));
    }
    if (data?.geofenceMode) setGeofenceMode(data.geofenceMode);
    if (data?.qrMode) setQrMode(data.qrMode);
  }, [data]);

  const updateMutation = useMutation({
    mutationFn: (dto) => lmtSettingsApi.update(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lmt-settings'] });
      setFormError('');
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to save.'),
  });

  const handleSave = () => {
    const value = Number(buffer);
    if (buffer.trim() === '' || Number.isNaN(value) || value < 0) {
      setFormError('Buffer must be a non-negative number.');
      return;
    }
    updateMutation.mutate({ geofenceBufferMeters: value, geofenceMode, qrMode });
  };

  if (isLoading) return <CircularProgress />;
  if (isError) return <Alert severity="error">Failed to load LMT settings. Try refreshing.</Alert>;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>LMT Settings</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Applies to every shop visit going forward — a salesman is allowed up to a shop's own radius plus this buffer
        before the geofence check blocks the submission. The actual distance is always recorded regardless.
      </Typography>

      <Paper sx={{ p: 3, maxWidth: 560 }}>
        {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
        {saved && <Alert severity="success" sx={{ mb: 2 }}>Saved.</Alert>}
        <Stack spacing={2}>
          <TextField
            label="Geofence Buffer (meters)"
            type="number"
            value={buffer}
            onChange={(e) => setBuffer(e.target.value)}
            helperText="Default is 50m. Applies tenant-wide, to every shop."
          />

          <Divider />
          <Typography variant="subtitle2">Geofence Requirement — Master Override</Typography>
          <ModeSelect
            label="Geofence Mode"
            value={geofenceMode}
            onChange={setGeofenceMode}
            helperText="Overrides every shop's own Geofencing switch without changing what each shop has saved — switch back to the first option any time to restore each shop's own setting exactly."
          />

          <Divider />
          <Typography variant="subtitle2">QR Scan Requirement — Master Override</Typography>
          <ModeSelect
            label="QR Mode"
            value={qrMode}
            onChange={setQrMode}
            helperText="Overrides every shop's own QR Required switch the same way — reversible, per-shop settings are never overwritten."
          />

          <Box>
            <Button variant="contained" onClick={handleSave} disabled={updateMutation.isPending}>
              {updateMutation.isPending ? 'Saving…' : 'Save'}
            </Button>
          </Box>
        </Stack>
      </Paper>
    </Box>
  );
}
