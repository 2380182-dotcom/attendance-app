import React, { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Box, Paper, Typography, TextField, Button, CircularProgress, Alert, Stack } from '@mui/material';
import { lmtSettingsApi } from '../../services/lmtApi';

export default function LmtSettingsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useQuery({ queryKey: ['lmt-settings'], queryFn: lmtSettingsApi.get });

  const [buffer, setBuffer] = useState('');
  const [formError, setFormError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (data?.geofenceBufferMeters != null) {
      setBuffer(String(data.geofenceBufferMeters));
    }
  }, [data]);

  const updateMutation = useMutation({
    mutationFn: (value) => lmtSettingsApi.update(value),
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
    updateMutation.mutate(value);
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

      <Paper sx={{ p: 3, maxWidth: 480 }}>
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
