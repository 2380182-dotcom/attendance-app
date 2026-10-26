import React, { useState } from 'react';
import { Box, Skeleton } from '@mui/material';
import ReportIcon from '@mui/icons-material/Report';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import Card from './Card';
import { INK, MEANING } from '../tokens';

/** Status tones always come with an icon + a word, never colour alone. */
const SEVERITY = {
  CRITICAL: { label: 'Action needed', color: MEANING.alert, bg: '#FBEAEA', Icon: ReportIcon },
  WARNING: { label: 'Check', color: '#9A5B00', bg: '#FDF3E1', Icon: WarningAmberRoundedIcon },
  INFO: { label: 'Info', color: INK.secondary, bg: '#EEF1F6', Icon: InfoOutlinedIcon },
};

function AlertRow({ alert, onView }) {
  const [open, setOpen] = useState(false);
  const s = SEVERITY[alert.severity] || SEVERITY.INFO;
  const shown = open ? alert.items : alert.items.slice(0, 3);
  const more = alert.items.length - shown.length;
  return (
    <Box sx={{
      display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'minmax(0,1fr) auto' }, gap: 1.5,
      alignItems: 'center', py: 1.75, px: { xs: 0, sm: 0.5 },
      '&:not(:last-of-type)': { borderBottom: `1px solid ${INK.grid}` },
    }}>
      <Box sx={{ display: 'flex', gap: 1.5, minWidth: 0 }}>
        <Box sx={{
          flexShrink: 0, width: 36, height: 36, borderRadius: '10px', bgcolor: s.bg, color: s.color,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <s.Icon fontSize="small" />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <Box sx={{ fontSize: 10.5, fontWeight: 800, letterSpacing: '0.08em', textTransform: 'uppercase', color: s.color }}>
              {s.label}
            </Box>
            <Box sx={{ fontWeight: 700, color: INK.primary, fontSize: 14.5 }}>{alert.title}</Box>
          </Box>
          <Box sx={{ fontSize: 12.5, color: INK.secondary, mt: 0.25 }}>{alert.detail}</Box>
          {alert.items.length > 0 && (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 1 }}>
              {shown.map((it, i) => (
                <Box key={i} sx={{
                  fontSize: 12, bgcolor: '#F4F6FA', border: `1px solid ${INK.grid}`, borderRadius: '8px',
                  px: 1, py: 0.35, color: INK.primary, maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }}>
                  {it.label} <Box component="span" className="cockpit-num" sx={{ color: INK.secondary }}>· {it.value}</Box>
                </Box>
              ))}
              {(more > 0 || open) && alert.items.length > 3 && (
                <Box component="button" type="button" onClick={() => setOpen((o) => !o)} className="cockpit-no-print" sx={{
                  border: 0, bgcolor: 'transparent', color: MEANING.sales, fontWeight: 700, fontSize: 12, cursor: 'pointer', font: 'inherit',
                }}>
                  {open ? 'Show less' : `+${more} more`}
                </Box>
              )}
            </Box>
          )}
        </Box>
      </Box>
      <Box
        component="button" type="button" onClick={() => onView(alert.section)} className="cockpit-no-print"
        sx={{
          justifySelf: { xs: 'start', md: 'end' }, ml: { xs: 6.5, md: 0 },
          display: 'inline-flex', alignItems: 'center', gap: 0.5, border: `1px solid ${INK.border}`, bgcolor: '#fff',
          borderRadius: '10px', px: 1.5, py: 0.75, fontWeight: 700, fontSize: 13, color: '#0E2A63', cursor: 'pointer', font: 'inherit',
          '&:hover': { bgcolor: '#F4F6FA' }, '&:focus-visible': { outline: `2px solid ${MEANING.sales}`, outlineOffset: 2 },
        }}
      >
        View <ArrowForwardRoundedIcon sx={{ fontSize: 16 }} />
      </Box>
    </Box>
  );
}

export default function AlertsPanel({ data, loading, onView }) {
  if (loading) {
    return (
      <Card>
        {[0, 1, 2].map((i) => (
          <Box key={i} sx={{ display: 'flex', gap: 1.5, py: 1.5 }}>
            <Skeleton animation="wave" variant="rounded" width={36} height={36} />
            <Box sx={{ flex: 1 }}>
              <Skeleton animation="wave" width="40%" />
              <Skeleton animation="wave" width="65%" />
            </Box>
          </Box>
        ))}
      </Card>
    );
  }
  const alerts = data?.alerts || [];
  if (!alerts.length) {
    return (
      <Card accent={MEANING.ok} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <CheckCircleRoundedIcon sx={{ color: MEANING.ok }} />
        <Box>
          <Box sx={{ fontWeight: 700, color: INK.primary }}>All clear</Box>
          <Box sx={{ fontSize: 13, color: INK.secondary }}>Nothing needs attention for these filters right now.</Box>
        </Box>
      </Card>
    );
  }
  return (
    <Card sx={{ py: 0.5 }}>
      {alerts.map((a) => <AlertRow key={a.key} alert={a} onView={onView} />)}
    </Card>
  );
}
