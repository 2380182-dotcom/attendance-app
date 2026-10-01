import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, TablePagination, Button, Chip,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { salesVoucherApi } from '../../services/lmtApi';
import { formatSaleTimeToKarachi } from '../../utils/dateUtils';

const SECTION_LABEL = { local: 'Local', lmt: 'LMT' };

/**
 * Task 4 — a single shop's paginated voucher list. The backend query behind
 * this deliberately never JOIN FETCHes SalesRecord.items (verified: zero
 * HHH90003004 in the test-run logs) — per-voucher sale/return totals come
 * from a database GROUP BY aggregate instead. Dates/times shown here are
 * already Pakistan-native on the entity (SalesService's timezone fix), so
 * no further conversion happens on display, only formatting.
 */
export default function SalesVoucherListPage() {
  const { section, shopId } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);

  const { data, isLoading, error } = useQuery({
    queryKey: ['salesVoucherList', section, shopId, page, pageSize],
    queryFn: () => salesVoucherApi.getVouchersForShop(section, shopId, { page, size: pageSize }),
  });

  const label = SECTION_LABEL[section] || section;

  return (
    <Box>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(`/sales/vouchers/${section}/shops`)} sx={{ mb: 1 }}>
        Back to {label} shops
      </Button>
      <Typography variant="h5" fontWeight="bold" sx={{ mb: 2 }}>
        {label} Vouchers — Shop #{shopId}
      </Typography>

      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load vouchers: {error.message}</Alert>}

      {data && (
        <Paper>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Time (PK)</TableCell>
                  <TableCell>Salesman</TableCell>
                  <TableCell align="right">Sale Amount</TableCell>
                  <TableCell align="right">Return Amount</TableCell>
                  <TableCell align="right">Net Amount</TableCell>
                  <TableCell align="right">Units</TableCell>
                  <TableCell align="right">Distance (m)</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.length === 0 && (
                  <TableRow><TableCell colSpan={9} align="center">No vouchers for this shop.</TableCell></TableRow>
                )}
                {data.content.map((v) => (
                  <TableRow
                    key={v.voucherId}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/sales/vouchers/${v.voucherId}`)}
                  >
                    <TableCell>{v.saleDate}</TableCell>
                    <TableCell>{formatSaleTimeToKarachi(v.saleDate, v.saleTime)}</TableCell>
                    <TableCell>{v.agentName}</TableCell>
                    <TableCell align="right">{Number(v.saleAmount).toFixed(2)}</TableCell>
                    <TableCell align="right">{Number(v.returnAmount).toFixed(2)}</TableCell>
                    <TableCell align="right">{Number(v.netAmount).toFixed(2)}</TableCell>
                    <TableCell align="right">{v.totalUnits}</TableCell>
                    <TableCell align="right">{v.distanceFromShopMeters != null ? Math.round(v.distanceFromShopMeters) : '-'}</TableCell>
                    <TableCell><Chip size="small" label={v.status} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={data.page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={data.size}
            onRowsPerPageChange={(e) => { setPageSize(parseInt(e.target.value, 10)); setPage(0); }}
            rowsPerPageOptions={[10, 25, 50, 100]}
          />
        </Paper>
      )}
    </Box>
  );
}
