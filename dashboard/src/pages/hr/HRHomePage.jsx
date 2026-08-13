import React from 'react';
import { Typography, Paper } from '@mui/material';

export default function HRHomePage() {
  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        HR Overview
      </Typography>
      <Typography color="text.secondary">
        Stage 1 placeholder — attendance board, history, agent management, and exports land in later stages.
      </Typography>
    </Paper>
  );
}
