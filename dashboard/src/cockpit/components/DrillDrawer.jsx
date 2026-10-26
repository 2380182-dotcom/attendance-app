import React, { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box, Drawer, IconButton, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, TablePagination, Chip,
} from '@mui/material';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { salesCockpitApi } from '../api';
import { EmptyState, ErrorState } from './States';
import { INK, MEANING, SEGMENT, SEGMENT_LABEL } from '../tokens';
import { int, rs, shortDate } from '../format';

const segmentOfRole = (role) => (role === 'SALESMAN_LOCAL' ? 'LOCAL' : role === 'SALESMAN_LMT' ? 'LMT' : 'OTHER');

/**
 * Side panel with the vouchers behind a clicked bar / slice / row — the
 * page underneath stays where it is. `drill` = { title, subtitle, extra }
 * where extra narrows the page's filters (a date, a salesman, an area,
 * a product, a segment).
 */
export default function DrillDrawer({ drill, filters, onClose }) {
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [drill]);

  const query = useQuery({
    queryKey: ['cockpit', 'sales', 'drill', filters, drill?.extra, page],
    queryFn: () => salesCockpitApi.vouchers(filters, { ...drill.extra, page, size: 15 }),
    enabled: Boolean(drill),
    placeholderData: keepPreviousData,
  });
  const data = query.data;

  return (
    <Drawer
      anchor="right"
      open={Boolean(drill)}
      onClose={onClose}
      className="cockpit-no-print"
      sx={{ zIndex: (t) => t.zIndex.modal }}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 560 }, bgcolor: '#F7F9FC' } } }}
    >
      {drill && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          <Box sx={{ px: 3, pt: 3, pb: 2, bgcolor: '#fff', borderBottom: `1px solid ${INK.border}` }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
              <Box sx={{ minWidth: 0 }}>
                <Box sx={{ fontSize: 11, fontWeight: 800, letterSpacing: '0.09em', textTransform: 'uppercase', color: INK.secondary }}>
                  Vouchers behind this
                </Box>
                <Box component="h2" sx={{ m: 0, mt: 0.5, fontSize: 20, fontWeight: 800, color: INK.primary }}>{drill.title}</Box>
                {drill.subtitle && <Box sx={{ fontSize: 13, color: INK.secondary, mt: 0.25 }}>{drill.subtitle}</Box>}
              </Box>
              <IconButton onClick={onClose} aria-label="Close panel"><CloseRoundedIcon /></IconButton>
            </Box>
            {data && (
              <Box sx={{ mt: 1.5, fontSize: 13, color: INK.secondary }}>
                <strong className="cockpit-num" style={{ color: INK.primary }}>{int(data.totalElements)}</strong> vouchers
              </Box>
            )}
          </Box>

          <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
            {query.isLoading && (
              <Box sx={{ bgcolor: '#fff', borderRadius: 3, p: 2 }}>
                {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} animation="wave" height={36} />)}
              </Box>
            )}
            {query.isError && <ErrorState onRetry={() => query.refetch()} />}
            {data && data.content.length === 0 && (
              <EmptyState title="No vouchers here" message="Nothing was recorded for this selection." />
            )}
            {data && data.content.length > 0 && (
              <Box sx={{ bgcolor: '#fff', borderRadius: 3, overflowX: 'auto', boxShadow: '0 1px 2px rgba(15,23,42,0.05)' }}>
                <Table size="small" sx={{ '& td, & th': { borderColor: INK.grid, fontSize: 12.5 } }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Shop · Salesman</TableCell>
                      <TableCell align="right">Sold</TableCell>
                      <TableCell align="right">Net</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.content.map((v) => {
                      const seg = segmentOfRole(v.role);
                      return (
                        <TableRow
                          key={v.id} hover component={RouterLink} to={`/sales/vouchers/${v.id}`}
                          sx={{ textDecoration: 'none', cursor: 'pointer' }}
                          title="Open the full voucher"
                        >
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>
                            <Box className="cockpit-num">{shortDate(v.saleDate)}</Box>
                            <Box sx={{ color: INK.muted }} className="cockpit-num">{(v.saleTime || '').slice(0, 5)}</Box>
                          </TableCell>
                          <TableCell sx={{ minWidth: 180 }}>
                            <Box sx={{ fontWeight: 700, color: INK.primary }}>{v.shopName || '—'}</Box>
                            <Box sx={{ color: INK.secondary, display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap' }}>
                              {v.agentName}
                              <Chip size="small" label={SEGMENT_LABEL[seg]} sx={{
                                height: 18, fontSize: 10.5, fontWeight: 700, bgcolor: `${SEGMENT[seg]}1A`, color: INK.primary,
                              }} />
                              {v.rbeUnits > 0 && (
                                <Chip size="small" label={`RBE ${v.rbeUnits}`} sx={{
                                  height: 18, fontSize: 10.5, fontWeight: 700, bgcolor: `${MEANING.rbe}1A`, color: INK.primary,
                                }} />
                              )}
                            </Box>
                          </TableCell>
                          <TableCell align="right" className="cockpit-num">{int(v.soldUnits)}</TableCell>
                          <TableCell align="right" className="cockpit-num" sx={{ fontWeight: 700, color: INK.primary }}>
                            {rs(v.netValue)}
                            {v.returnValue > 0 && (
                              <Box sx={{ fontWeight: 500, color: INK.muted, fontSize: 11.5 }}>returns {rs(v.returnValue)}</Box>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
                <TablePagination
                  component="div" count={data.totalElements} page={page} rowsPerPage={15}
                  rowsPerPageOptions={[15]} onPageChange={(_, p) => setPage(p)}
                />
              </Box>
            )}
          </Box>
        </Box>
      )}
    </Drawer>
  );
}
