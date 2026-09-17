import React, { useEffect, useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, TextField, InputAdornment, IconButton, Snackbar,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import SaveIcon from '@mui/icons-material/Save';
import { productApi } from '../../services/productApi';
import { sortRows, useSort } from '../../utils/sorting';
import SortableHeader from '../../components/SortableHeader';

/**
 * Role-based pricing (P3): ADMIN + SALES edit each product's Agent Price
 * and Salesman Price here — these are the only two values that ever feed
 * a sale's revenue (SalesService reads Product.agentPrice/salesmanPrice
 * fresh at submission time; nothing here is trusted back from a client at
 * sale time, same as everywhere else in this app).
 */
export default function ProductPricingPage() {
  const queryClient = useQueryClient();
  const pricing = useQuery({ queryKey: ['product-pricing'], queryFn: productApi.getPricing });

  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('name', 'asc');
  const [edits, setEdits] = useState({});
  const [savingId, setSavingId] = useState(null);
  const [snackbar, setSnackbar] = useState('');

  // Seed local editable state from the fetched list — re-seeds only for
  // rows not already being edited, so an in-progress edit survives a
  // background refetch.
  useEffect(() => {
    if (!pricing.data) return;
    setEdits((prev) => {
      const next = { ...prev };
      pricing.data.forEach((p) => {
        if (!(p.id in next)) {
          next[p.id] = { agentPrice: String(p.agentPrice), salesmanPrice: String(p.salesmanPrice) };
        }
      });
      return next;
    });
  }, [pricing.data]);

  const mutation = useMutation({
    mutationFn: ({ id, dto }) => productApi.updatePricing(id, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['product-pricing'] });
      setSnackbar('Pricing updated.');
    },
    onError: (err) => {
      setSnackbar(err.response?.data?.message || 'Failed to update pricing.');
    },
    onSettled: () => setSavingId(null),
  });

  const filtered = useMemo(() => {
    const rows = pricing.data || [];
    const q = search.trim().toLowerCase();
    const matched = q ? rows.filter((p) => p.name.toLowerCase().includes(q)) : rows;
    return sortRows(matched, sort.key, sort.direction);
  }, [pricing.data, search, sort]);

  const handleFieldChange = (id, field, value) => {
    setEdits((prev) => ({ ...prev, [id]: { ...prev[id], [field]: value } }));
  };

  const isDirty = (p) => {
    const edit = edits[p.id];
    if (!edit) return false;
    return Number(edit.agentPrice) !== p.agentPrice || Number(edit.salesmanPrice) !== p.salesmanPrice;
  };

  const handleSave = (p) => {
    const edit = edits[p.id];
    const agentPrice = Number(edit.agentPrice);
    const salesmanPrice = Number(edit.salesmanPrice);
    if (Number.isNaN(agentPrice) || agentPrice < 0 || Number.isNaN(salesmanPrice) || salesmanPrice < 0) {
      setSnackbar('Prices must be non-negative numbers.');
      return;
    }
    setSavingId(p.id);
    mutation.mutate({ id: p.id, dto: { agentPrice, salesmanPrice } });
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight="bold" gutterBottom>
        Product Pricing
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Agent Price is charged on regular Agent sales; Salesman Price is charged on LMT shop-visit sales — set
        independently per product.
      </Typography>

      <TextField
        placeholder="Search products…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        size="small"
        sx={{ mb: 2, width: { xs: '100%', sm: 320 } }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
      />

      {pricing.isLoading && <CircularProgress />}
      {pricing.isError && <Alert severity="error">Failed to load product pricing.</Alert>}

      {pricing.data && (
        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <SortableHeader label="Product" sortKey="name" sort={sort} onSort={onSort} />
                  <SortableHeader label="Category" sortKey="category" sort={sort} onSort={onSort} />
                  <TableCell align="right">Agent Price (PKR)</TableCell>
                  <TableCell align="right">Salesman Price (PKR)</TableCell>
                  <TableCell align="center">Save</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((p) => {
                  const edit = edits[p.id] || { agentPrice: '', salesmanPrice: '' };
                  const dirty = isDirty(p);
                  return (
                    <TableRow key={p.id} hover>
                      <TableCell>{p.name}</TableCell>
                      <TableCell>{p.category || '—'}</TableCell>
                      <TableCell align="right">
                        <TextField
                          type="number"
                          size="small"
                          value={edit.agentPrice}
                          onChange={(e) => handleFieldChange(p.id, 'agentPrice', e.target.value)}
                          sx={{ width: 110 }}
                          inputProps={{ min: 0, step: '0.01' }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          type="number"
                          size="small"
                          value={edit.salesmanPrice}
                          onChange={(e) => handleFieldChange(p.id, 'salesmanPrice', e.target.value)}
                          sx={{ width: 110 }}
                          inputProps={{ min: 0, step: '0.01' }}
                        />
                      </TableCell>
                      <TableCell align="center">
                        <IconButton
                          size="small"
                          color="primary"
                          disabled={!dirty || savingId === p.id}
                          onClick={() => handleSave(p)}
                        >
                          {savingId === p.id ? <CircularProgress size={18} /> : <SaveIcon fontSize="small" />}
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      <Snackbar
        open={!!snackbar}
        autoHideDuration={3000}
        onClose={() => setSnackbar('')}
        message={snackbar}
      />
    </Box>
  );
}
