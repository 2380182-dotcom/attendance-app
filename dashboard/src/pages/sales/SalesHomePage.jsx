import React from 'react';
import { Typography, Paper } from '@mui/material';

export default function SalesHomePage() {
  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        Sales Overview
      </Typography>
      <Typography color="text.secondary">
        Stage 1 placeholder — revenue, live check-in feed, sales history, and mart management land in later stages.
      </Typography>
    </Paper>
  );
}
