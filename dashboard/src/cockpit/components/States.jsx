import React from 'react';
import { Box, Skeleton } from '@mui/material';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineRounded';
import { INK, MEANING } from '../tokens';

/** Grey shimmering placeholder shaped like the content it replaces. */
export function ChartSkeleton({ height = 260 }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }} aria-busy="true" aria-label="Loading">
      <Skeleton animation="wave" variant="rounded" height={height} sx={{ borderRadius: 3 }} />
      <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
        <Skeleton animation="wave" width={90} />
        <Skeleton animation="wave" width={90} />
      </Box>
    </Box>
  );
}

export function EmptyState({ title = 'Nothing to show yet', message, height = 220, icon }) {
  const Icon = icon || InsightsOutlinedIcon;
  return (
    <Box sx={{
      height, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      textAlign: 'center', gap: 0.75, color: INK.secondary, bgcolor: '#F7F9FC', borderRadius: 3, px: 3,
    }}>
      <Icon sx={{ fontSize: 30, color: INK.muted }} />
      <Box sx={{ fontWeight: 700, color: INK.primary, fontSize: 15 }}>{title}</Box>
      {message && <Box sx={{ fontSize: 13, maxWidth: 360 }}>{message}</Box>}
    </Box>
  );
}

export function ErrorState({ onRetry, height = 220 }) {
  return (
    <EmptyState
      height={height}
      icon={ErrorOutlineIcon}
      title="Could not load this section"
      message={
        <span>
          Check your connection.{' '}
          {onRetry && (
            <Box component="button" type="button" onClick={onRetry} sx={{
              border: 0, background: 'none', color: MEANING.sales, fontWeight: 700, cursor: 'pointer', p: 0, font: 'inherit',
            }}>
              Try again
            </Box>
          )}
        </span>
      }
    />
  );
}
