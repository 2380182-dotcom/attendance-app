import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, TextField, InputAdornment, TablePagination,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { salesVoucherApi } from '../../services/lmtApi';

const SECTION_LABEL = { local: 'Local', lmt: 'LMT' };

/**
 * Task 4 — shop-list page for "Local Sales Vouchers" / "LMT Sales
 * Vouchers" (:section is 'local' or 'lmt'). Every total shown here comes
 * straight from the backend's database aggregate query (SalesRecordRepository
 * .findShopVoucherSummaries) — never summed client-side.
 */
export default function SalesVoucherShopsPage() {
  const { section } = useParams();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);

  const { data, isLoading, error } = useQuery({
    queryKey: ['salesVoucherShops', section, search, page, pageSize],
    queryFn: () => salesVoucherApi.getShops(section, { shopSearch: search || undefined, page, size: pageSize }),
  });

  const label = SECTION_LABEL[section] || section;

  return (
    <Box>
      <Typography variant="h5" fontWeight="bold" sx={{ mb: 2 }}>
        {label} Sales Vouchers
      </Typography>

      <TextField
        size="small"
        placeholder="Search shop name or code"
        value={search}
        onChange={(e) => { setSearch(e.target.value); setPage(0); }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        sx={{ mb: 2, minWidth: 280 }}
      />

      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load shops: {error.message}</Alert>}

      {data && (
        <Paper>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Shop Code</TableCell>
                  <TableCell>Shop Name</TableCell>
                  <TableCell>Branch</TableCell>
                  <TableCell align="right">Vouchers</TableCell>
                  <TableCell align="right">Sale Total</TableCell>
                  <TableCell align="right">Return Total</TableCell>
                  <TableCell align="right">Net Total</TableCell>
                  <TableCell align="right">Total Units</TableCell>
                  <TableCell>Last Visit</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.length === 0 && (
                  <TableRow><TableCell colSpan={9} align="center">No {label} vouchers found.</TableCell></TableRow>
                )}
                {data.content.map((shop) => (
                  <TableRow
                    key={shop.shopId}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/sales/vouchers/${section}/shops/${shop.shopId}`)}
                  >
                    <TableCell>{shop.shopCode}</TableCell>
                    <TableCell>{shop.shopName}</TableCell>
                    <TableCell>{shop.branch || '-'}</TableCell>
                    <TableCell align="right">{shop.voucherCount}</TableCell>
                    <TableCell align="right">{Number(shop.saleAmount).toFixed(2)}</TableCell>
                    <TableCell align="right">{Number(shop.returnAmount).toFixed(2)}</TableCell>
                    <TableCell align="right">{Number(shop.netAmount).toFixed(2)}</TableCell>
                    <TableCell align="right">{shop.totalUnits}</TableCell>
                    <TableCell>{shop.lastVisitDate}</TableCell>
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
