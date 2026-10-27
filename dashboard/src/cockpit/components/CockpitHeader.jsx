import React from 'react';
import { Box, Button, MenuItem, TextField, ToggleButton, ToggleButtonGroup, CircularProgress } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import { BRAND_RED, HEADER_GRADIENT } from '../tokens';
import { COMPANY_NAME, LOGO_SRC } from '../brand';
import { PRESETS } from '../useCockpitFilters';
import { pkTime } from '../format';

const glass = {
  '& .MuiOutlinedInput-root': {
    color: '#fff',
    bgcolor: 'rgba(255,255,255,0.08)',
    borderRadius: '10px',
    fontSize: 14,
    '& fieldset': { borderColor: 'rgba(255,255,255,0.22)' },
    '&:hover fieldset': { borderColor: 'rgba(255,255,255,0.45)' },
    '&.Mui-focused fieldset': { borderColor: '#fff' },
  },
  '& .MuiInputLabel-root': { color: 'rgba(255,255,255,0.72)', fontSize: 13 },
  '& .MuiInputLabel-root.Mui-focused': { color: '#fff' },
  '& .MuiSvgIcon-root': { color: 'rgba(255,255,255,0.8)' },
  '& input::-webkit-calendar-picker-indicator': { filter: 'invert(1)', opacity: 0.8 },
};

const toggleSx = {
  '& .MuiToggleButton-root': {
    bgcolor: 'rgba(255,255,255,0.08)',
    color: 'rgba(255,255,255,0.78)',
    borderColor: 'rgba(255,255,255,0.22)',
    textTransform: 'none',
    fontWeight: 600,
    fontSize: 13,
    px: 1.5,
    py: 0.75,
    '&.Mui-selected': { color: '#0E2A63', bgcolor: '#fff', '&:hover': { bgcolor: '#fff' } },
  },
};

/**
 * The banner: title, company, and every global filter. `personFilter`
 * describes the third dropdown (salesman for Sales, department for HR) and
 * `placeFilter` the area/mart dropdown, so the HR cockpit reuses this.
 */
export default function CockpitHeader({
  title, subtitle, state, placeFilter, personFilter, segmentOptions,
  onRefresh, refreshing, updatedAt, onPrint,
}) {
  const {
    preset, setPreset, custom, setCustom, segment, setSegment, autoRefresh, setAutoRefresh,
  } = state;

  return (
    <Box sx={{ mb: 2 }}>
      <Box
        className="cockpit-banner"
        sx={{
          position: 'relative',
          overflow: 'hidden',
          borderRadius: '20px',
          background: HEADER_GRADIENT,
          color: '#fff',
          px: { xs: 2, sm: 3 },
          pt: { xs: 2.5, sm: 3 },
          pb: { xs: 2, sm: 2.5 },
          boxShadow: '0 10px 30px rgba(8, 22, 52, 0.28)',
        }}
      >
        {/* Dawn Bread red accent: a stripe along the top edge and a soft glow behind the title. */}
        <Box aria-hidden sx={{ position: 'absolute', inset: '0 0 auto 0', height: 4, bgcolor: BRAND_RED }} />
        <Box aria-hidden sx={{
          position: 'absolute', width: 420, height: 420, right: -140, top: -260, borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(227,6,19,0.32) 0%, rgba(227,6,19,0) 70%)',
        }} />

        <Box sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
          <Box sx={{ minWidth: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.75 }}>
              {LOGO_SRC ? (
                <Box component="img" src={LOGO_SRC} alt={COMPANY_NAME} sx={{ height: 22 }} />
              ) : (
                <Box sx={{
                  fontSize: 12, fontWeight: 800, letterSpacing: '0.14em', textTransform: 'uppercase',
                  display: 'flex', alignItems: 'center', gap: 0.75,
                }}>
                  <Box component="span" sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: BRAND_RED, display: 'inline-block' }} />
                  {COMPANY_NAME}
                </Box>
              )}
            </Box>
            <Box component="h1" sx={{ m: 0, fontSize: { xs: 24, sm: 30 }, fontWeight: 800, letterSpacing: '-0.02em', lineHeight: 1.15 }}>
              {title}
            </Box>
            {subtitle && (
              <Box sx={{ mt: 0.5, fontSize: 14, color: 'rgba(255,255,255,0.72)' }}>{subtitle}</Box>
            )}
          </Box>

          <Box className="cockpit-no-print" sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <TextField
              select size="small" label="Auto refresh" value={autoRefresh}
              onChange={(e) => setAutoRefresh(Number(e.target.value))}
              sx={{ ...glass, minWidth: 124 }}
            >
              <MenuItem value={0}>Off</MenuItem>
              <MenuItem value={5}>Every 5 min</MenuItem>
              <MenuItem value={15}>Every 15 min</MenuItem>
            </TextField>
            <Button
              onClick={onRefresh}
              disabled={refreshing}
              startIcon={refreshing ? <CircularProgress size={16} sx={{ color: '#fff' }} /> : <RefreshIcon />}
              sx={{
                bgcolor: BRAND_RED, color: '#fff', textTransform: 'none', fontWeight: 700, borderRadius: '10px', px: 2,
                height: 40, '&:hover': { bgcolor: '#C40510' }, '&.Mui-disabled': { color: 'rgba(255,255,255,0.8)', bgcolor: 'rgba(227,6,19,0.6)' },
              }}
            >
              Refresh now
            </Button>
            {onPrint && (
              <Button
                onClick={onPrint}
                aria-label="Print this page"
                sx={{ minWidth: 40, height: 40, color: '#fff', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.22)' }}
              >
                <PrintOutlinedIcon fontSize="small" />
              </Button>
            )}
          </Box>
        </Box>

        {/* Global filters */}
        <Box className="cockpit-no-print" sx={{
          position: 'relative', mt: 2.5, pt: 2, borderTop: '1px solid rgba(255,255,255,0.14)',
          display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center',
        }}>
          <ToggleButtonGroup
            exclusive size="small" value={preset} aria-label="Date range"
            onChange={(_, v) => v && setPreset(v)} sx={{ ...toggleSx, flexWrap: 'wrap' }}
          >
            {PRESETS.map((p) => <ToggleButton key={p.key} value={p.key}>{p.label}</ToggleButton>)}
            <ToggleButton value="custom">Custom</ToggleButton>
          </ToggleButtonGroup>

          {preset === 'custom' && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <TextField
                size="small" type="date" label="From" value={custom.startDate}
                onChange={(e) => e.target.value && setCustom((c) => ({ ...c, startDate: e.target.value }))}
                slotProps={{ inputLabel: { shrink: true } }} sx={{ ...glass, width: 160 }}
              />
              <TextField
                size="small" type="date" label="To" value={custom.endDate}
                onChange={(e) => e.target.value && setCustom((c) => ({ ...c, endDate: e.target.value }))}
                slotProps={{ inputLabel: { shrink: true } }} sx={{ ...glass, width: 160 }}
              />
            </Box>
          )}

          {segmentOptions?.length > 0 && (
            <ToggleButtonGroup
              exclusive size="small" value={segment} aria-label="Salesman type"
              onChange={(_, v) => v && setSegment(v)} sx={toggleSx}
            >
              {segmentOptions.map((o) => <ToggleButton key={o.value} value={o.value}>{o.label}</ToggleButton>)}
            </ToggleButtonGroup>
          )}

          {placeFilter && (
            <TextField
              select size="small" label={placeFilter.label} value={placeFilter.value}
              onChange={(e) => placeFilter.onChange(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }} sx={{ ...glass, minWidth: 170 }}
            >
              <MenuItem value="">All</MenuItem>
              {placeFilter.options.map((o) => <MenuItem key={o.id} value={o.id}>{o.name}</MenuItem>)}
            </TextField>
          )}
          {personFilter && (
            <TextField
              select size="small" label={personFilter.label} value={personFilter.value}
              onChange={(e) => personFilter.onChange(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }} sx={{ ...glass, minWidth: 190 }}
            >
              <MenuItem value="">All</MenuItem>
              {personFilter.options.map((o) => (
                <MenuItem key={o.id} value={o.id}>{o.name}{o.hint ? ` · ${o.hint}` : ''}</MenuItem>
              ))}
            </TextField>
          )}
        </Box>
      </Box>

      <Box sx={{ mt: 1, px: 0.5, fontSize: 12.5, color: '#5B6577', minHeight: 20, lineHeight: 1.6 }}>
        {updatedAt ? (
          <>
            <Box component="span" sx={{
              display: 'inline-block', width: 7, height: 7, borderRadius: '50%', mr: 1, verticalAlign: 'middle',
              bgcolor: refreshing ? '#E8710A' : '#16A34A',
            }} />
            Data updated at <strong className="cockpit-num" style={{ color: '#0F172A' }}>{pkTime(updatedAt)}</strong> (Pakistan time)
            {autoRefresh > 0 && <span className="cockpit-no-print"> · refreshes every {autoRefresh} min</span>}
          </>
        ) : 'Loading latest data…'}
      </Box>
    </Box>
  );
}
