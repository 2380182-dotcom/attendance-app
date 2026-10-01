import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Button, Chip, Grid, Divider,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import { salesVoucherApi } from '../../services/lmtApi';
import { formatSaleTimeToKarachi } from '../../utils/dateUtils';

const ROLE_LABEL = { SALESMAN_LOCAL: 'Local', SALESMAN_LMT: 'LMT' };

/**
 * Task 4 — single voucher detail. Unlike the shop/list pages, this one DOES
 * load the full item list (a single record by id, never paginated) — safe
 * under the to-one-vs-collection fetch-join distinction the whole backend
 * relies on. The PDF button downloads the same server-generated PDF a
 * salesman/admin would print.
 */
export default function SalesVoucherDetailPage() {
  const { voucherId } = useParams();
  const navigate = useNavigate();
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState(null);

  const { data: voucher, isLoading, error } = useQuery({
    queryKey: ['salesVoucherDetail', voucherId],
    queryFn: () => salesVoucherApi.getVoucherDetail(voucherId),
  });

  const handleDownloadPdf = async () => {
    setDownloading(true);
    setDownloadError(null);
    try {
      const blob = await salesVoucherApi.getVoucherPdfBlob(voucherId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `voucher-${voucherId}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (e) {
      setDownloadError('Failed to download PDF.');
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Box>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} sx={{ mb: 1 }}>
        Back
      </Button>

      {isLoading && <CircularProgress />}
      {error && (
        <Alert severity="error">
          {error.response?.status === 404 ? 'Voucher not found.' : `Failed to load voucher: ${error.message}`}
        </Alert>
      )}
      {downloadError && <Alert severity="error" sx={{ mb: 2 }}>{downloadError}</Alert>}

      {voucher && (
        <Paper sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
            <Typography variant="h5" fontWeight="bold">
              Voucher #{voucher.voucherId}
            </Typography>
            <Button variant="contained" startIcon={<PictureAsPdfIcon />} onClick={handleDownloadPdf} disabled={downloading}>
              {downloading ? 'Preparing…' : 'Print / PDF'}
            </Button>
          </Box>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">Shop</Typography>
              <Typography>{voucher.shopName} ({voucher.shopCode})</Typography>
              {voucher.branch && <Typography variant="body2" color="text.secondary">{voucher.branch}</Typography>}
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">Salesman</Typography>
              <Typography>{voucher.agentName} <Chip size="small" label={ROLE_LABEL[voucher.agentRole] || voucher.agentRole} sx={{ ml: 1 }} /></Typography>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">Date / Time (Pakistan)</Typography>
              <Typography>{voucher.saleDate} — {formatSaleTimeToKarachi(voucher.saleDate, voucher.saleTime)}</Typography>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">Status</Typography>
              <Chip size="small" label={voucher.status} />
            </Grid>
            {voucher.distanceFromShopMeters != null && (
              <Grid item xs={12} sm={6}>
                <Typography variant="body2" color="text.secondary">Distance from shop</Typography>
                <Typography>{Math.round(voucher.distanceFromShopMeters)} m</Typography>
              </Grid>
            )}
          </Grid>

          <Divider sx={{ mb: 2 }} />

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Product</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell align="right">Qty</TableCell>
                  <TableCell align="right">Unit Price</TableCell>
                  <TableCell align="right">Discount %</TableCell>
                  <TableCell align="right">Total</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {voucher.items.map((item, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{item.productName}</TableCell>
                    <TableCell><Chip size="small" label={item.transactionType} /></TableCell>
                    <TableCell align="right">{item.quantity}</TableCell>
                    <TableCell align="right">{Number(item.unitPrice).toFixed(2)}</TableCell>
                    <TableCell align="right">{item.discountPercent != null ? item.discountPercent.toFixed(1) : '-'}</TableCell>
                    <TableCell align="right">{Number(item.totalPrice).toFixed(2)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}
    </Box>
  );
}
