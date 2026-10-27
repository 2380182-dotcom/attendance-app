import React, { useMemo, useState } from 'react';
import {
  Box, Chip, Drawer, IconButton, InputAdornment, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, TextField,
} from '@mui/material';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import { useQuery } from '@tanstack/react-query';
import Card from '../components/Card';
import { EmptyState, ErrorState } from '../components/States';
import SortableHeader from '../../components/SortableHeader';
import { sortRows, useSort } from '../../utils/sorting';
import { salesCockpitApi } from '../api';
import { INK, MEANING, SEGMENT, SEGMENT_LABEL } from '../tokens';
import { int, rs, rsCompact, shortDate } from '../format';

const segOfRole = (role) => (role === 'SALESMAN_LOCAL' ? 'LOCAL' : role === 'SALESMAN_LMT' ? 'LMT' : 'OTHER');

const COLUMNS = [
  { key: 'visits', label: 'Visits', fmt: int },
  { key: 'shopsCovered', label: 'Shops', fmt: int },
  { key: 'vouchers', label: 'Vouchers', fmt: int },
  { key: 'saleValue', label: 'Sales', fmt: rsCompact },
  { key: 'returnValue', label: 'Returns', fmt: rsCompact },
  { key: 'netValue', label: 'Net', fmt: rsCompact, strong: true },
  { key: 'netPerVisit', label: 'Net / visit', fmt: rsCompact },
  { key: 'flaggedVouchers', label: 'Flags', fmt: int, flag: true },
  { key: 'avgFirstVisit', label: 'Avg first', fmt: (v) => v || '—' },
  { key: 'avgLastVisit', label: 'Avg last', fmt: (v) => v || '—' },
];

/** Side panel: one salesman, day by day. */
function SalesmanPanel({ salesman, filters, onClose }) {
  const query = useQuery({
    queryKey: ['cockpit', 'sales', 'salesman-days', filters, salesman?.agentId],
    queryFn: () => salesCockpitApi.salesmanDays(filters, salesman.agentId),
    enabled: Boolean(salesman),
  });
  return (
    <Drawer anchor="right" open={Boolean(salesman)} onClose={onClose} className="cockpit-no-print"
      sx={{ zIndex: (t) => t.zIndex.modal }}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 520 }, bgcolor: '#F7F9FC' } } }}>
      {salesman && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          <Box sx={{ px: 3, pt: 3, pb: 2, bgcolor: '#fff', borderBottom: `1px solid ${INK.border}` }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
              <Box>
                <Box sx={{ fontSize: 11, fontWeight: 800, letterSpacing: '0.09em', textTransform: 'uppercase', color: INK.secondary }}>Salesman</Box>
                <Box component="h2" sx={{ m: 0, mt: 0.5, fontSize: 20, fontWeight: 800, color: INK.primary }}>{salesman.name}</Box>
                <Box sx={{ fontSize: 13, color: INK.secondary, mt: 0.25 }}>
                  {SEGMENT_LABEL[segOfRole(salesman.role)]} · net {rs(salesman.netValue)} · {int(salesman.visits)} visits · {int(salesman.flaggedVouchers)} flags
                </Box>
              </Box>
              <IconButton onClick={onClose} aria-label="Close panel"><CloseRoundedIcon /></IconButton>
            </Box>
          </Box>
          <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
            {query.isLoading && <Skeleton animation="wave" variant="rounded" height={260} sx={{ borderRadius: 3 }} />}
            {query.isError && <ErrorState onRetry={() => query.refetch()} />}
            {query.data && query.data.length === 0 && <EmptyState title="No activity" message="No visits or vouchers in this period." />}
            {query.data && query.data.length > 0 && (
              <Box sx={{ bgcolor: '#fff', borderRadius: 3, overflowX: 'auto' }}>
                <Table size="small" sx={{ '& td, & th': { borderColor: INK.grid, fontSize: 12.5 } }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Day</TableCell>
                      <TableCell align="right">Visits</TableCell>
                      <TableCell align="right">Vouchers</TableCell>
                      <TableCell align="right">Net</TableCell>
                      <TableCell align="right">First</TableCell>
                      <TableCell align="right">Last</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {query.data.map((d) => (
                      <TableRow key={d.date}>
                        <TableCell className="cockpit-num">{shortDate(d.date)}</TableCell>
                        <TableCell align="right" className="cockpit-num">{int(d.visits)}</TableCell>
                        <TableCell align="right" className="cockpit-num">{int(d.vouchers)}</TableCell>
                        <TableCell align="right" className="cockpit-num" sx={{ fontWeight: 700 }}>{rs(d.netValue)}</TableCell>
                        <TableCell align="right" className="cockpit-num">{d.firstVisit || '—'}</TableCell>
                        <TableCell align="right" className="cockpit-num">{d.lastVisit || '—'}</TableCell>
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

export default function ScorecardSection({ query, filters }) {
  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('netValue', 'desc');
  const [selected, setSelected] = useState(null);
  const d = query.data;
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = (d?.rows || []).filter((r) => !q || r.name.toLowerCase().includes(q));
    return sortRows(filtered, sort.key, sort.direction);
  }, [d, search, sort]);

  return (
    <Card sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <Box sx={{ fontSize: 12.5, color: INK.muted }}>
          Visits = shops visited per day (scan or feedback) · flags = vouchers without a QR scan + off-location attempts · avg first/last visit over active days
        </Box>
        <TextField size="small" placeholder="Search salesman" value={search} onChange={(e) => setSearch(e.target.value)}
          className="cockpit-no-print" sx={{ minWidth: 220 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> } }} />
      </Box>

      {(query.isLoading || (!d && query.isFetching)) && <Skeleton animation="wave" variant="rounded" height={300} sx={{ borderRadius: 3 }} />}
      {query.isError && !d && <ErrorState onRetry={() => query.refetch()} />}
      {d && d.rows.length === 0 && <EmptyState title="No salesmen" message="No active salesmen for these filters." />}
      {d && d.rows.length > 0 && (
        <Box sx={{ overflowX: 'auto', maxHeight: 520, overflowY: 'auto' }}>
          <Table size="small" stickyHeader sx={{ minWidth: 980, '& td, & th': { borderColor: INK.grid, fontSize: 12.5, whiteSpace: 'nowrap' } }}>
            <TableHead>
              <TableRow>
                <SortableHeader label="Salesman" sortKey="name" sort={sort} onSort={onSort} />
                {COLUMNS.map((c) => <SortableHeader key={c.key} label={c.label} sortKey={c.key} sort={sort} onSort={onSort} align="right" />)}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 && (
                <TableRow><TableCell colSpan={COLUMNS.length + 1} align="center" sx={{ color: INK.secondary, py: 3 }}>No salesman matches “{search}”.</TableCell></TableRow>
              )}
              {rows.map((r) => {
                const seg = segOfRole(r.role);
                return (
                  <TableRow key={r.agentId} hover>
                    <TableCell>
                      <Box component="button" type="button" onClick={() => setSelected(r)} sx={{
                        border: 0, bgcolor: 'transparent', p: 0, cursor: 'pointer', font: 'inherit', fontWeight: 700,
                        color: '#0E2A63', textAlign: 'left', '&:hover': { textDecoration: 'underline' },
                        '&:focus-visible': { outline: `2px solid ${MEANING.sales}`, outlineOffset: 2 },
                      }}>
                        {r.name}
                      </Box>
                      <Chip size="small" label={SEGMENT_LABEL[seg]} sx={{ ml: 1, height: 18, fontSize: 10.5, fontWeight: 700, bgcolor: `${SEGMENT[seg]}1A` }} />
                    </TableCell>
                    {COLUMNS.map((c) => (
                      <TableCell key={c.key} align="right" className="cockpit-num" sx={{
                        fontWeight: c.strong ? 700 : 400,
                        color: c.flag && r[c.key] > 0 ? MEANING.alert : INK.primary,
                      }}>
                        {c.fmt(r[c.key])}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Box>
      )}
      <SalesmanPanel salesman={selected} filters={filters} onClose={() => setSelected(null)} />
    </Card>
  );
}
