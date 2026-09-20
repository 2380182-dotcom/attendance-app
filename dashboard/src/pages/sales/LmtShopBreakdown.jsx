import React, { useMemo, useState } from 'react';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  IconButton, Collapse, Button, Stack, Chip, TablePagination,
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import DownloadIcon from '@mui/icons-material/Download';
import { aggregateByShop } from '../../utils/salesAggregation';
import { sortRows, useSort } from '../../utils/sorting';
import { usePagination } from '../../utils/pagination';
import { toCsv, downloadCsv } from '../../utils/csvExport';
import SortableHeader from '../../components/SortableHeader';

const pkr = (n) => `PKR ${(n ?? 0).toLocaleString()}`;

function ShopRow({ shop }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <TableRow hover>
        <TableCell padding="checkbox">
          <IconButton size="small" onClick={() => setOpen((v) => !v)}>
            {open ? <KeyboardArrowDownIcon /> : <KeyboardArrowRightIcon />}
          </IconButton>
        </TableCell>
        <TableCell>{shop.shopCode}</TableCell>
        <TableCell>{shop.shopName}</TableCell>
        <TableCell align="right">{shop.visits}</TableCell>
        <TableCell align="right">{shop.unitsSold}</TableCell>
        <TableCell align="right">
          {shop.unitsReturned > 0 ? <Chip size="small" color="warning" label={shop.unitsReturned} /> : 0}
        </TableCell>
        <TableCell align="right">{shop.discountAmount > 0 ? pkr(Math.round(shop.discountAmount)) : '—'}</TableCell>
        <TableCell align="right">{pkr(shop.revenue)}</TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={8} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ py: 1 }}>
              <TableContainer><Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Product</TableCell>
                    <TableCell align="right">Sold</TableCell>
                    <TableCell align="right">Returned</TableCell>
                    <TableCell align="right">Discount</TableCell>
                    <TableCell align="right">Revenue</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {shop.products.map((p) => (
                    <TableRow key={p.productId ?? p.productName}>
                      <TableCell>{p.productName}</TableCell>
                      <TableCell align="right">{p.sold || '—'}</TableCell>
                      <TableCell align="right">{p.returned || '—'}</TableCell>
                      <TableCell align="right">
                        {p.discountAmount > 0 ? `${p.discountPercent}% (${pkr(Math.round(p.discountAmount))})` : '—'}
                      </TableCell>
                      <TableCell align="right">{p.sold ? pkr(p.revenue) : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table></TableContainer>
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

/** Shop-wise sales, returns and discounts for a set of LMT shop-visit records. */
export default function LmtShopBreakdown({ records, filenameHint }) {
  const [sort, onSort] = useSort('revenue', 'desc');
  const shops = useMemo(() => aggregateByShop(records), [records]);
  const sorted = useMemo(() => sortRows(shops, sort.key, sort.direction), [shops, sort]);
  const { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage } = usePagination(sorted);

  const handleExport = () => {
    const rows = sorted.flatMap((s) =>
      s.products.map((p) => ({
        shopCode: s.shopCode, shopName: s.shopName, productName: p.productName,
        sold: p.sold, returned: p.returned, discountPercent: p.discountPercent || '',
        discountAmount: Math.round(p.discountAmount), revenue: p.revenue,
      }))
    );
    downloadCsv(`lmt-shop-wise-${filenameHint}.csv`, toCsv(rows, [
      { key: 'shopCode', label: 'Shop Code' },
      { key: 'shopName', label: 'Shop' },
      { key: 'productName', label: 'Product' },
      { key: 'sold', label: 'Sold' },
      { key: 'returned', label: 'Returned' },
      { key: 'discountPercent', label: 'Discount %' },
      { key: 'discountAmount', label: 'Discount (PKR)' },
      { key: 'revenue', label: 'Revenue' },
    ]));
  };

  return (
    <Paper sx={{ mt: 3 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ p: 2, pb: 0 }} flexWrap="wrap" useFlexGap spacing={1}>
        <Typography variant="h6">Shop-wise Sales, Returns &amp; Discounts</Typography>
        <Button startIcon={<DownloadIcon />} variant="outlined" size="small" onClick={handleExport} disabled={sorted.length === 0}>
          Export CSV
        </Button>
      </Stack>
      <TableContainer><Table size="small">
        <TableHead>
          <TableRow>
            <TableCell padding="checkbox" />
            <SortableHeader label="Shop Code" sortKey="shopCode" sort={sort} onSort={onSort} />
            <SortableHeader label="Shop" sortKey="shopName" sort={sort} onSort={onSort} />
            <SortableHeader label="Visits" sortKey="visits" sort={sort} onSort={onSort} align="right" />
            <SortableHeader label="Units Sold" sortKey="unitsSold" sort={sort} onSort={onSort} align="right" />
            <SortableHeader label="Returned" sortKey="unitsReturned" sort={sort} onSort={onSort} align="right" />
            <SortableHeader label="Discount Given" sortKey="discountAmount" sort={sort} onSort={onSort} align="right" />
            <SortableHeader label="Revenue" sortKey="revenue" sort={sort} onSort={onSort} align="right" />
          </TableRow>
        </TableHead>
        <TableBody>
          {paged.length === 0 && (
            <TableRow><TableCell colSpan={8} align="center">No shop visits in this period.</TableCell></TableRow>
          )}
          {paged.map((shop) => <ShopRow key={shop.shopId} shop={shop} />)}
        </TableBody>
      </Table></TableContainer>
      <TablePagination
        component="div"
        count={sorted.length}
        page={page}
        onPageChange={handleChangePage}
        rowsPerPage={rowsPerPage}
        onRowsPerPageChange={handleChangeRowsPerPage}
        rowsPerPageOptions={[10, 25, 50]}
      />
    </Paper>
  );
}
