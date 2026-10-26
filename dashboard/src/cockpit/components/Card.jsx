import React from 'react';
import { Box } from '@mui/material';
import { CARD, INK } from '../tokens';

/** White 16px card with a soft shadow and an optional coloured left border (by meaning). */
export default function Card({ accent, children, sx, onClick, ...rest }) {
  const clickable = Boolean(onClick);
  return (
    <Box
      className={`cockpit-card${clickable ? ' cockpit-clickable' : ''}`}
      onClick={onClick}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(e); } } : undefined}
      sx={{
        bgcolor: INK.surface,
        borderRadius: `${CARD.radius}px`,
        boxShadow: CARD.shadow,
        borderLeft: accent ? `4px solid ${accent}` : `1px solid ${INK.border}`,
        p: { xs: 2, sm: 2.5 },
        minWidth: 0,
        transition: 'transform 160ms ease, box-shadow 160ms ease',
        cursor: clickable ? 'pointer' : 'default',
        ...(clickable && {
          '&:hover': { transform: 'translateY(-2px)', boxShadow: '0 2px 4px rgba(15,23,42,0.06), 0 12px 28px rgba(15,23,42,0.10)' },
        }),
        ...sx,
      }}
      {...rest}
    >
      {children}
    </Box>
  );
}

export function Eyebrow({ children, sx }) {
  return (
    <Box component="span" sx={{
      display: 'block', fontSize: 11, fontWeight: 700, letterSpacing: '0.09em',
      textTransform: 'uppercase', color: INK.secondary, ...sx,
    }}>
      {children}
    </Box>
  );
}
