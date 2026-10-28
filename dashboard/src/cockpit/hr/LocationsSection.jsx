import React from 'react';
import { Box, Table, TableBody, TableCell, TableHead, TableRow, Tooltip as MuiTooltip } from '@mui/material';
import ChartCard, { Legend } from '../components/ChartCard';
import { Grid } from '../components/Sections';
import { INK, MEANING } from '../tokens';
import { int, shortDate } from '../format';
import { hhmm } from './hrFormat';

const metres = (v) => (v == null ? '—' : `${int(v)} m`);

function GeofenceTag({ on }) {
  return (
    <Box component="span" sx={{
      ml: 1, px: 0.75, py: 0.1, borderRadius: 999, fontSize: 10.5, fontWeight: 800, letterSpacing: '0.04em',
      textTransform: 'uppercase', whiteSpace: 'nowrap',
      bgcolor: on ? '#E7F6EC' : '#FFF4E5', color: on ? '#126B33' : '#8A4B00',
    }}>
      {on ? 'Geofence on' : 'Geofence off'}
    </Box>
  );
}

/** Per mart: check-ins (bar), late and far-from-mart counts beside it. */
function MartList({ marts }) {
  const max = Math.max(1, ...marts.map((m) => m.checkIns));
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', maxHeight: 420, overflowY: 'auto' }}>
      {marts.map((m) => (
        <MuiTooltip key={m.martId} placement="top"
          title={`${int(m.checkIns)} check-ins by ${int(m.agents)} agents · ${int(m.lateCheckIns)} late · ${int(m.farCheckIns)} beyond the ${metres(m.radius)} radius · farthest ${metres(m.maxDistance)}`}>
          <Box tabIndex={0} sx={{
            display: 'grid', gridTemplateColumns: 'minmax(0, 1.3fr) minmax(0, 1fr) auto', gap: 1.25,
            alignItems: 'center', py: 0.8, px: 1, borderRadius: '10px', '&:hover': { bgcolor: '#F4F6FA' },
          }}>
            <Box sx={{ minWidth: 0, display: 'flex', alignItems: 'center' }}>
              <Box sx={{ fontSize: 13, fontWeight: 600, color: INK.primary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {m.name}
              </Box>
              {!m.geofencing && <GeofenceTag on={false} />}
            </Box>
            <Box sx={{ height: 10, bgcolor: '#EEF1F6', borderRadius: 999 }}>
              <Box sx={{ height: '100%', width: `${(m.checkIns / max) * 100}%`, bgcolor: MEANING.sales, borderRadius: 999, transition: 'width 600ms ease' }} />
            </Box>
            <Box className="cockpit-num" sx={{ fontSize: 12.5, textAlign: 'right', color: INK.secondary, whiteSpace: 'nowrap' }}>
              <strong style={{ color: INK.primary }}>{int(m.checkIns)}</strong>
              {' · '}<span style={{ color: m.lateCheckIns ? '#8A4B00' : INK.secondary }}>{int(m.lateCheckIns)} late</span>
              {' · '}<span style={{ color: m.farCheckIns ? MEANING.alert : INK.secondary, fontWeight: m.farCheckIns ? 700 : 400 }}>{int(m.farCheckIns)} far</span>
            </Box>
          </Box>
        </MuiTooltip>
      ))}
    </Box>
  );
}

function Outliers({ rows }) {
  return (
    <Box sx={{ overflowX: 'auto', maxHeight: 420, overflowY: 'auto' }}>
      <Table size="small" stickyHeader sx={{ minWidth: 480, '& td, & th': { borderColor: INK.grid, fontSize: 12.5, whiteSpace: 'nowrap' } }}>
        <TableHead>
          <TableRow>
            <TableCell>When</TableCell>
            <TableCell>Agent</TableCell>
            <TableCell>Mart</TableCell>
            <TableCell align="right">Distance</TableCell>
            <TableCell align="right">Radius</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={`${r.date}-${r.time}-${r.agent}`} hover>
              <TableCell className="cockpit-num">{shortDate(r.date)} · {hhmm(r.time)}</TableCell>
              <TableCell sx={{ fontWeight: 700, color: INK.primary }}>{r.agent}</TableCell>
              {/* Far check-ins only happen with geofencing off; flag the odd case where it is on now. */}
              <TableCell>{r.mart}{r.geofencing && <GeofenceTag on />}</TableCell>
              <TableCell align="right" className="cockpit-num" sx={{ fontWeight: 700, color: MEANING.alert }}>{metres(r.distance)}</TableCell>
              <TableCell align="right" className="cockpit-num" sx={{ color: INK.secondary }}>{metres(r.radius)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}

export default function LocationsSection({ query }) {
  const d = query.data;
  const loading = query.isLoading || (!d && query.isFetching);
  const error = query.isError && !d;
  const retry = () => query.refetch();

  return (
    <Grid cols={2} from="lg" className="cockpit-grid-print-2">
      <ChartCard title="Check-ins by mart" hint="Most check-ins first · late and beyond-the-radius counts beside each mart"
        loading={loading} error={error} onRetry={retry} height={300}
        empty={!d || d.marts.length === 0} emptyTitle="No check-ins in this period"
        legend={<Legend items={[{ label: 'Check-ins', color: MEANING.sales }]} />}>
        {d && <MartList marts={d.marts} />}
      </ChartCard>
      <ChartCard title="Farthest check-ins from the mart"
        hint={`Beyond the mart's radius · the ${d?.outliers.length || 20} farthest · only possible where geofencing is off (or was off at the time)`}
        loading={loading} error={error} onRetry={retry} height={300}
        empty={!d || d.outliers.length === 0} emptyTitle="Every check-in was inside the radius"
        emptyMessage="No check-in in this period was farther from the mart than its radius.">
        {d && <Outliers rows={d.outliers} />}
      </ChartCard>
    </Grid>
  );
}
