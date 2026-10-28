import React, { useEffect, useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, TextField, InputAdornment, IconButton, Snackbar, Switch, Tooltip,
  ToggleButtonGroup, ToggleButton, Dialog, DialogTitle, DialogContent, DialogActions, Button,
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
 *
 * New products arrive hidden with prices "Not set" (null; 0 is a real
 * price). Enter both prices, then switch the product on: only then do
 * salesmen and agents see it. Saving a 0 price asks first.
 */
const priceText = (v) => (v === null || v === undefined ? '' : String(v));
export default function ProductPricingPage() {
  const queryClient = useQueryClient();
  const pricing = useQuery({ queryKey: ['product-pricing'], queryFn: productApi.getPricing });

  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('name', 'asc');
  const [edits, setEdits] = useState({});
  const [savingId, setSavingId] = useState(null);
  const [snackbar, setSnackbar] = useState('');
  const [show, setShow] = useState('ALL');             // ALL | ON | HIDDEN
  const [confirmZero, setConfirmZero] = useState(null); // { id, dto, name } awaiting "save 0?"

  // Seed local editable state from the fetched list — re-seeds only for
  // rows not already being edited, so an in-progress edit survives a
  // background refetch.
  useEffect(() => {
    if (!pricing.data) return;
    setEdits((prev) => {
      const next = { ...prev };
      pricing.data.forEach((p) => {
        if (!(p.id in next)) {
          next[p.id] = { agentPrice: priceText(p.agentPrice), salesmanPrice: priceText(p.salesmanPrice) };
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

  const activeMutation = useMutation({
    mutationFn: ({ id, active }) => productApi.setActive(id, active),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['product-pricing'] });
      setSnackbar(p.active ? `${p.name} is on: salesmen and agents can now see it.` : `${p.name} is hidden.`);
    },
    onError: (err) => setSnackbar(err.response?.data?.message || 'Failed to change the product.'),
  });

  const hiddenCount = (pricing.data || []).filter((p) => !p.active).length;

  const filtered = useMemo(() => {
    const rows = pricing.data || [];
    const q = search.trim().toLowerCase();
    const shown = rows.filter((p) => show === 'ALL' || (show === 'ON' ? p.active : !p.active));
    const matched = q ? shown.filter((p) => p.name.toLowerCase().includes(q)) : shown;
    return sortRows(matched, sort.key, sort.direction);
  }, [pricing.data, search, sort, show]);

  const handleFieldChange = (id, field, value) => {
    setEdits((prev) => ({ ...prev, [id]: { ...prev[id], [field]: value } }));
  };

  const isDirty = (p) => {
    const edit = edits[p.id];
    if (!edit) return false;
    return edit.agentPrice !== priceText(p.agentPrice) || edit.salesmanPrice !== priceText(p.salesmanPrice);
  };

  const save = (id, dto) => {
    setSavingId(id);
    mutation.mutate({ id, dto });
  };

  const handleSave = (p) => {
    const edit = edits[p.id];
    if (edit.agentPrice.trim() === '' || edit.salesmanPrice.trim() === '') {
      setSnackbar('Enter both the agent price and the salesman price.');
      return;
    }
    const agentPrice = Number(edit.agentPrice);
    const salesmanPrice = Number(edit.salesmanPrice);
    if (Number.isNaN(agentPrice) || agentPrice < 0 || Number.isNaN(salesmanPrice) || salesmanPrice < 0) {
      setSnackbar('Prices must be non-negative numbers.');
      return;
    }
    // 0 is allowed (as before) but almost always a typo: ask first.
    if (agentPrice === 0 || salesmanPrice === 0) {
      setConfirmZero({ id: p.id, name: p.name, dto: { agentPrice, salesmanPrice } });
      return;
    }
    save(p.id, { agentPrice, salesmanPrice });
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight="bold" gutterBottom>
        Product Pricing
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Agent Price is charged on regular Agent sales; Salesman Price is charged on LMT shop-visit sales — set
        independently per product. A product is offered to salesmen and agents only when it is switched on.
      </Typography>

      {hiddenCount > 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <strong>{hiddenCount} product{hiddenCount === 1 ? ' is' : 's are'} hidden.</strong> Salesmen and agents can't see
          {hiddenCount === 1 ? ' it' : ' them'} yet: enter both prices, save, then switch {hiddenCount === 1 ? 'it' : 'them'} on.
        </Alert>
      )}

      <TextField
        placeholder="Search products…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        size="small"
        sx={{ mb: 2, mr: 2, width: { xs: '100%', sm: 320 } }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
      />
      <ToggleButtonGroup size="small" exclusive value={show} onChange={(e, v) => v && setShow(v)} sx={{ mb: 2 }}>
        <ToggleButton value="ALL">All ({(pricing.data || []).length})</ToggleButton>
        <ToggleButton value="ON">On ({(pricing.data || []).length - hiddenCount})</ToggleButton>
        <ToggleButton value="HIDDEN">Hidden ({hiddenCount})</ToggleButton>
      </ToggleButtonGroup>

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
                  <SortableHeader label="Unit" sortKey="unit" sort={sort} onSort={onSort} />
                  <TableCell align="right">Agent Price (PKR)</TableCell>
                  <TableCell align="right">Salesman Price (PKR)</TableCell>
                  <TableCell align="center">Save</TableCell>
                  <SortableHeader label="On / Hidden" sortKey="active" sort={sort} onSort={onSort} />
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((p) => {
                  const edit = edits[p.id] || { agentPrice: '', salesmanPrice: '' };
                  const dirty = isDirty(p);
                  const pricesSet = p.agentPrice != null && p.salesmanPrice != null;
                  return (
                    <TableRow key={p.id} hover>
                      <TableCell>{p.name}</TableCell>
                      <TableCell>{p.category || '—'}</TableCell>
                      <TableCell>{p.unit || '—'}</TableCell>
                      <TableCell align="right">
                        <TextField
                          type="number"
                          size="small"
                          value={edit.agentPrice}
                          placeholder="Not set"
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
                          placeholder="Not set"
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
                      <TableCell>
                        <Tooltip title={pricesSet || p.active ? '' : 'Enter and save both prices first'}>
                          <span>
                            <Switch
                              size="small"
                              checked={!!p.active}
                              disabled={(!pricesSet && !p.active) || activeMutation.isPending}
                              onChange={(e) => activeMutation.mutate({ id: p.id, active: e.target.checked })}
                              slotProps={{ input: { 'aria-label': `${p.name}: ${p.active ? 'on' : 'hidden'}` } }}
                            />
                          </span>
                        </Tooltip>
                        <Typography component="span" variant="caption" color={p.active ? 'success.main' : 'text.secondary'}>
                          {p.active ? 'On' : 'Hidden'}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      <Dialog open={!!confirmZero} onClose={() => setConfirmZero(null)}>
        <DialogTitle>Save a price of 0?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            {confirmZero?.name}: {confirmZero?.dto.agentPrice === 0 && 'agent price'}
            {confirmZero?.dto.agentPrice === 0 && confirmZero?.dto.salesmanPrice === 0 && ' and '}
            {confirmZero?.dto.salesmanPrice === 0 && 'salesman price'} will be Rs 0 — it will be sold for free.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmZero(null)}>Cancel</Button>
          <Button color="warning" variant="contained"
            onClick={() => { save(confirmZero.id, confirmZero.dto); setConfirmZero(null); }}>
            Save Rs 0
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={!!snackbar}
        autoHideDuration={3000}
        onClose={() => setSnackbar('')}
        message={snackbar}
      />
    </Box>
  );
}
