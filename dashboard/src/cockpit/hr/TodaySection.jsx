import React, { useMemo, useState } from 'react';
import { Box, InputAdornment, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, TextField } from '@mui/material';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import Card from '../components/Card';
import ChartCard, { Legend } from '../components/ChartCard';
import { Grid } from '../components/Sections';
import { EmptyState, ErrorState } from '../components/States';
import { INK, MEANING } from '../tokens';
import { STATE, hhmm } from './hrFormat';
import { StatePill, StackRow } from './hrUi';

const ORDER = ['ON_TIME', 'LATE', 'ABSENT', 'NOT_DUE', 'OFF'];
const KEY = { ON_TIME: 'onTime', LATE: 'late', ABSENT: 'absent', NOT_DUE: 'notDue', OFF: 'off' };

function counts(group) {
  return Object.fromEntries(ORDER.map((s) => [s, group[KEY[s]]]));
}

/** Department / mart bars. Clicking a bar narrows the list below to that group. */
function GroupChart({ title, hint, groups, pick, picked }) {
  const max = Math.max(1, ...groups.map((g) => ORDER.reduce((n, s) => n + g[KEY[s]], 0)));
  return (
    <ChartCard title={title} hint={hint} empty={groups.length === 0} emptyTitle="Nobody on the roster" height={160}
      legend={<Legend items={ORDER.map((s) => ({ label: STATE[s].label, color: STATE[s].color }))} />}>
      <Box sx={{ display: 'flex', flexDirection: 'column', maxHeight: 340, overflowY: 'auto' }}>
        {groups.map((g) => (
          <StackRow key={g.name} name={g.name} counts={counts(g)} order={ORDER} max={max}
            selected={picked === g.name} onClick={() => pick(picked === g.name ? null : g.name)} />
        ))}
      </Box>
    </ChartCard>
  );
}

export default function TodaySection({ query }) {
  const d = query.data;
  const [state, setState] = useState(null);
  const [group, setGroup] = useState(null);   // { by: 'department' | 'mart', name }
  const [search, setSearch] = useState('');

  const totals = useMemo(() => {
    const t = Object.fromEntries(ORDER.map((s) => [s, 0]));
    (d?.agents || []).forEach((a) => { t[a.state] += 1; });
    return t;
  }, [d]);

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (d?.agents || []).filter((a) => (!state || a.state === state)
      && (!group || (group.by === 'department' ? a.department : (a.mart || 'No mart yet')) === group.name)
      && (!q || a.name.toLowerCase().includes(q)));
  }, [d, state, group, search]);

  if (query.isError && !d) return <ErrorState onRetry={() => query.refetch()} />;
  if (!d) return <Skeleton animation="wave" variant="rounded" height={420} sx={{ borderRadius: 4 }} />;
  if (d.agents.length === 0) {
    return <Card><EmptyState title="Nobody on the roster" message="No active mart agents for these filters." /></Card>;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {/* State chips double as the list filter. */}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }} role="group" aria-label="Filter by state">
        {ORDER.map((s) => {
          const on = state === s;
          return (
            <Box key={s} component="button" type="button" onClick={() => setState(on ? null : s)} aria-pressed={on}
              sx={{
                display: 'inline-flex', alignItems: 'center', gap: 1, px: 1.5, py: 0.9, borderRadius: '12px', cursor: 'pointer',
                font: 'inherit', fontSize: 13.5, fontWeight: 700, color: INK.primary,
                border: `1px solid ${on ? STATE[s].color : INK.border}`, bgcolor: on ? `${STATE[s].color}1F` : '#fff',
                boxShadow: on ? 'none' : '0 1px 2px rgba(15,23,42,0.04)',
                '&:focus-visible': { outline: `2px solid ${MEANING.sales}`, outlineOffset: 2 },
              }}>
              <Box component="span" sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: STATE[s].color }} />
              {STATE[s].label}
              <Box component="span" className="cockpit-num" sx={{ fontSize: 15 }}>{totals[s]}</Box>
            </Box>
          );
        })}
      </Box>

      <Grid cols={2} from="md" className="cockpit-grid-print-2">
        <GroupChart title="By department" hint="Click a department to list its agents" groups={d.departments}
          picked={group?.by === 'department' ? group.name : null}
          pick={(name) => setGroup(name ? { by: 'department', name } : null)} />
        <GroupChart title="By mart" hint="Today's mart, or the usual one for agents not in yet" groups={d.marts}
          picked={group?.by === 'mart' ? group.name : null}
          pick={(name) => setGroup(name ? { by: 'mart', name } : null)} />
      </Grid>

      <Card sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
          <Box sx={{ fontSize: 13, color: INK.secondary }}>
            <strong style={{ color: INK.primary }}>{rows.length}</strong> of {d.agents.length} agents
            {state && <> · {STATE[state].label.toLowerCase()}</>}
            {group && <> · {group.name}</>}
            {(state || group) && (
              <Box component="button" type="button" onClick={() => { setState(null); setGroup(null); }} className="cockpit-no-print"
                sx={{ ml: 1, border: 0, bgcolor: 'transparent', color: MEANING.sales, fontWeight: 700, cursor: 'pointer', font: 'inherit', p: 0 }}>
                Show everyone
              </Box>
            )}
          </Box>
          <TextField size="small" placeholder="Search agent" value={search} onChange={(e) => setSearch(e.target.value)}
            className="cockpit-no-print" sx={{ minWidth: 220 }}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> } }} />
        </Box>
        <Box sx={{ overflowX: 'auto', maxHeight: 460, overflowY: 'auto' }}>
          <Table size="small" stickyHeader sx={{ minWidth: 760, '& td, & th': { borderColor: INK.grid, fontSize: 12.5, whiteSpace: 'nowrap' } }}>
            <TableHead>
              <TableRow>
                <TableCell>Agent</TableCell>
                <TableCell>State</TableCell>
                <TableCell>Department</TableCell>
                <TableCell>Mart</TableCell>
                <TableCell align="right">Check-in</TableCell>
                <TableCell align="right">End duty</TableCell>
                <TableCell align="right">Due by</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 && (
                <TableRow><TableCell colSpan={7} align="center" sx={{ color: INK.secondary, py: 3 }}>Nobody matches these filters.</TableCell></TableRow>
              )}
              {rows.map((a) => (
                <TableRow key={a.agentId} hover>
                  <TableCell sx={{ fontWeight: 700, color: INK.primary }}>{a.name}</TableCell>
                  <TableCell>
                    <StatePill state={a.state}>
                      {a.state === 'LATE' ? `Late · ${a.minutesLate} min` : undefined}
                    </StatePill>
                  </TableCell>
                  <TableCell>{a.department}</TableCell>
                  <TableCell sx={{ color: a.mart ? INK.primary : INK.muted }}>{a.mart || '—'}</TableCell>
                  <TableCell align="right" className="cockpit-num">{hhmm(a.checkIn)}</TableCell>
                  <TableCell align="right" className="cockpit-num" sx={{ color: a.onDuty ? MEANING.ok : INK.primary }}>
                    {a.onDuty ? 'On duty' : hhmm(a.checkOut)}
                  </TableCell>
                  <TableCell align="right" className="cockpit-num" sx={{ color: INK.secondary }}>{hhmm(a.dueBy)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Card>
    </Box>
  );
}
