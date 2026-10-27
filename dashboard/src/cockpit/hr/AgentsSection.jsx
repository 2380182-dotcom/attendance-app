import React, { useMemo, useState } from 'react';
import {
  Box, Drawer, IconButton, InputAdornment, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, TextField,
} from '@mui/material';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import { useQuery } from '@tanstack/react-query';
import Card from '../components/Card';
import { EmptyState, ErrorState } from '../components/States';
import SortableHeader from '../../components/SortableHeader';
import { sortRows, useSort } from '../../utils/sorting';
import { hrCockpitApi } from '../api';
import { INK, MEANING } from '../tokens';
import { int, pct, shortDate } from '../format';
import { clock, hhmm } from './hrFormat';
import { StatePill } from './hrUi';

const COLUMNS = [
  { key: 'attendancePct', label: 'Attendance', fmt: (v) => (v == null ? '—' : pct(v)), strong: true },
  { key: 'daysExpected', label: 'Expected', fmt: int },
  { key: 'daysPresent', label: 'Present', fmt: int },
  { key: 'daysLate', label: 'Late', fmt: int, tone: MEANING.late },
  { key: 'daysAbsent', label: 'Absent', fmt: int, tone: MEANING.alert },
  { key: 'missedEndDuty', label: 'Missed End Duty', fmt: int, tone: MEANING.alert },
  { key: 'avgHours', label: 'Avg hours', fmt: (v) => (v == null ? '—' : `${v.toFixed(1)} h`) },
  { key: 'avgCheckIn', label: 'Avg check-in', fmt: clock },
];

const WEEKDAY = new Intl.DateTimeFormat('en-GB', { weekday: 'short' });
const weekday = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return WEEKDAY.format(new Date(y, m - 1, d));
};

/** Side panel: one agent, day by day. */
function AgentPanel({ agent, filters, onClose }) {
  const query = useQuery({
    queryKey: ['cockpit', 'hr', 'agent-days', filters.startDate, filters.endDate, agent?.agentId],
    queryFn: () => hrCockpitApi.agentDays(filters, agent.agentId),
    enabled: Boolean(agent),
  });
  return (
    <Drawer anchor="right" open={Boolean(agent)} onClose={onClose} className="cockpit-no-print"
      sx={{ zIndex: (t) => t.zIndex.modal }}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 640 }, bgcolor: '#F7F9FC' } } }}>
      {agent && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          <Box sx={{ px: 3, pt: 3, pb: 2, bgcolor: '#fff', borderBottom: `1px solid ${INK.border}` }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
              <Box>
                <Box sx={{ fontSize: 11, fontWeight: 800, letterSpacing: '0.09em', textTransform: 'uppercase', color: INK.secondary }}>
                  Mart agent · {agent.department}
                </Box>
                <Box component="h2" sx={{ m: 0, mt: 0.5, fontSize: 20, fontWeight: 800, color: INK.primary }}>{agent.name}</Box>
                <Box sx={{ fontSize: 13, color: INK.secondary, mt: 0.25 }}>
                  {agent.attendancePct == null ? 'Not expected in this period' : `${pct(agent.attendancePct)} attendance`}
                  {' · '}{agent.daysLate} late · {agent.daysAbsent} absent · {agent.missedEndDuty} missed End Duty
                </Box>
              </Box>
              <IconButton onClick={onClose} aria-label="Close panel"><CloseRoundedIcon /></IconButton>
            </Box>
          </Box>
          <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
            {query.isLoading && <Skeleton animation="wave" variant="rounded" height={260} sx={{ borderRadius: 3 }} />}
            {query.isError && <ErrorState onRetry={() => query.refetch()} />}
            {query.data && (
              <Box sx={{ bgcolor: '#fff', borderRadius: 3, overflowX: 'auto' }}>
                <Table size="small" sx={{ '& td, & th': { borderColor: INK.grid, fontSize: 12.5, whiteSpace: 'nowrap' } }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Day</TableCell>
                      <TableCell>State</TableCell>
                      <TableCell align="right">In</TableCell>
                      <TableCell align="right">Out</TableCell>
                      <TableCell align="right">Hours</TableCell>
                      <TableCell>Mart</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {query.data.map((d) => (
                      <TableRow key={d.date}>
                        <TableCell className="cockpit-num">
                          <Box component="span" sx={{ color: INK.muted, mr: 0.75 }}>{weekday(d.date)}</Box>{shortDate(d.date)}
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                            <StatePill state={d.state}>
                              {d.state === 'LATE' ? `Late · ${d.minutesLate} min` : d.state === 'HOLIDAY' ? d.holiday : undefined}
                            </StatePill>
                            {d.missedEndDuty && <StatePill state="ABSENT">No End Duty</StatePill>}
                            {d.farFromMart && <StatePill state="LATE">Far from mart</StatePill>}
                          </Box>
                        </TableCell>
                        <TableCell align="right" className="cockpit-num">{hhmm(d.checkIn)}</TableCell>
                        <TableCell align="right" className="cockpit-num">{d.checkIn && !d.checkOut ? 'On duty' : hhmm(d.checkOut)}</TableCell>
                        <TableCell align="right" className="cockpit-num">{d.hours == null ? '—' : `${d.hours.toFixed(1)} h`}</TableCell>
                        <TableCell sx={{ color: d.mart ? INK.primary : INK.muted, maxWidth: 130, overflow: 'hidden', textOverflow: 'ellipsis' }} title={d.mart || ''}>{d.mart || '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
          </Box>
        </Box>
      )}
    </Drawer>
  );
}

export default function AgentsSection({ query, filters }) {
  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('attendancePct', 'asc');
  const [selected, setSelected] = useState(null);
  const d = query.data;
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = (d?.rows || []).filter((r) => !q || r.name.toLowerCase().includes(q) || r.department.toLowerCase().includes(q));
    return sortRows(filtered, sort.key, sort.direction);
  }, [d, search, sort]);

  return (
    <Card sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <Box sx={{ fontSize: 12.5, color: INK.muted, maxWidth: 720 }}>
          Attendance = expected days with a check-in · avg hours = closed shifts only (missed End Duty shifts are not real hours) · lowest attendance first
        </Box>
        <TextField size="small" placeholder="Search agent or department" value={search} onChange={(e) => setSearch(e.target.value)}
          className="cockpit-no-print" sx={{ minWidth: 240 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> } }} />
      </Box>

      {(query.isLoading || (!d && query.isFetching)) && <Skeleton animation="wave" variant="rounded" height={300} sx={{ borderRadius: 3 }} />}
      {query.isError && !d && <ErrorState onRetry={() => query.refetch()} />}
      {d && d.rows.length === 0 && <EmptyState title="No agents" message="No active mart agents for these filters." />}
      {d && d.rows.length > 0 && (
        <Box sx={{ overflowX: 'auto', maxHeight: 520, overflowY: 'auto' }}>
          <Table size="small" stickyHeader sx={{ minWidth: 980, '& td, & th': { borderColor: INK.grid, fontSize: 12.5, whiteSpace: 'nowrap' } }}>
            <TableHead>
              <TableRow>
                <SortableHeader label="Agent" sortKey="name" sort={sort} onSort={onSort} />
                <SortableHeader label="Department" sortKey="department" sort={sort} onSort={onSort} />
                {COLUMNS.map((c) => <SortableHeader key={c.key} label={c.label} sortKey={c.key} sort={sort} onSort={onSort} align="right" />)}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 && (
                <TableRow><TableCell colSpan={COLUMNS.length + 2} align="center" sx={{ color: INK.secondary, py: 3 }}>No agent matches “{search}”.</TableCell></TableRow>
              )}
              {rows.map((r) => (
                <TableRow key={r.agentId} hover>
                  <TableCell>
                    <Box component="button" type="button" onClick={() => setSelected(r)} sx={{
                      border: 0, bgcolor: 'transparent', p: 0, cursor: 'pointer', font: 'inherit', fontWeight: 700,
                      color: '#0E2A63', textAlign: 'left', '&:hover': { textDecoration: 'underline' },
                      '&:focus-visible': { outline: `2px solid ${MEANING.sales}`, outlineOffset: 2 },
                    }}>
                      {r.name}
                    </Box>
                  </TableCell>
                  <TableCell sx={{ color: INK.secondary }}>{r.department}</TableCell>
                  {COLUMNS.map((c) => (
                    <TableCell key={c.key} align="right" className="cockpit-num" sx={{
                      fontWeight: c.strong ? 700 : 400,
                      color: c.tone && r[c.key] > 0 ? c.tone : INK.primary,
                    }}>
                      {c.fmt(r[c.key])}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      <AgentPanel agent={selected} filters={filters} onClose={() => setSelected(null)} />
    </Card>
  );
}
