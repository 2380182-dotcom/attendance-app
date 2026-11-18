import React, { useEffect, useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, TextField, InputAdornment, IconButton, Snackbar, Switch, Tooltip,
  ToggleButtonGroup, ToggleButton, Dialog, DialogTitle, DialogContent, DialogActions, Button,
  FormControl, InputLabel, Select, MenuItem, Checkbox, FormControlLabel, Chip,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import SaveIcon from '@mui/icons-material/Save';
import EditIcon from '@mui/icons-material/Edit';
import CategoryIcon from '@mui/icons-material/Category';
import { productApi } from '../../services/productApi';
import ProductFamiliesDialog from './ProductFamiliesDialog';

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
 *
 * ORDER: always the official price list's SR No. order, exactly as the
 * server sends it (never by name, category or price). An administrator sets
 * each product's family (the app's filter button), SR No. and whether it is
 * on the list, and manages the families.
 */
const priceText = (v) => (v === null || v === undefined ? '' : String(v));
export default function ProductPricingPage() {
  const queryClient = useQueryClient();
  // Sales sees the On/Hidden status but only an administrator can switch it
  // (the server enforces it too). Read from the stored login, not
  // AuthContext: importing AuthContext into this lazy page made the
  // production build put Vite's module runtime inside the shared "api"
  // chunk, creating a chunk import cycle that crashed every page on load.
  const user = useMemo(() => {
    try { return JSON.parse(localStorage.getItem('user') || 'null'); } catch { return null; }
  }, []);
  const canSwitch = user?.role === 'ADMIN';
  const pricing = useQuery({ queryKey: ['product-pricing'], queryFn: productApi.getPricing });
  const familiesQuery = useQuery({ queryKey: ['product-families'], queryFn: productApi.getFamilies });
  const families = useMemo(() => familiesQuery.data || [], [familiesQuery.data]);
  const familyName = useMemo(() => Object.fromEntries(families.map((f) => [f.id, f.name])), [families]);

  const [search, setSearch] = useState('');
  const [familyFilter, setFamilyFilter] = useState('ALL'); // ALL | NONE | family id
  const [placing, setPlacing] = useState(null);            // { product, familyId, sortOrder, onPriceList }
  const [familiesOpen, setFamiliesOpen] = useState(false);
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

  const placementMutation = useMutation({
    mutationFn: ({ id, placement }) => productApi.setPlacement(id, placement),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['product-pricing'] });
      setPlacing(null);
      setSnackbar(`${p.name}: list place saved.`);
    },
    onError: (err) => setSnackbar(err.response?.data?.message || 'Failed to save the list place.'),
  });

  const hiddenCount = (pricing.data || []).filter((p) => !p.active).length;
  const productCounts = useMemo(() => {
    const counts = {};
    (pricing.data || []).forEach((p) => { if (p.familyId != null) counts[p.familyId] = (counts[p.familyId] || 0) + 1; });
    return counts;
  }, [pricing.data]);

  // The server's order (the price list's SR No.) is kept as is: filters only hide rows.
  const filtered = useMemo(() => {
    const rows = pricing.data || [];
    const q = search.trim().toLowerCase();
    return rows
      .filter((p) => show === 'ALL' || (show === 'ON' ? p.active : !p.active))
      .filter((p) => familyFilter === 'ALL' || (familyFilter === 'NONE' ? p.familyId == null : p.familyId === familyFilter))
      .filter((p) => !q || p.name.toLowerCase().includes(q));
  }, [pricing.data, search, show, familyFilter]);

  const openPlacement = (p) => setPlacing({
    product: p, familyId: p.familyId ?? '', sortOrder: p.sortOrder == null ? '' : String(p.sortOrder), onPriceList: !!p.onPriceList,
  });
  const savePlacement = () => {
    const order = placing.sortOrder.trim();
    if (order !== '' && (!Number.isInteger(Number(order)) || Number(order) < 1)) {
      setSnackbar('The SR No. must be a whole number of 1 or more, or empty.');
      return;
    }
    placementMutation.mutate({ id: placing.product.id, placement: {
      familyId: placing.familyId === '' ? null : placing.familyId,
      sortOrder: order === '' ? null : Number(order),
      onPriceList: placing.onPriceList,
    } });
  };

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
        Products are listed in the order of Dawn Bread's official price list (SR No.), the same order as in the app.
      </Typography>

      {hiddenCount > 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <strong>{hiddenCount} product{hiddenCount === 1 ? ' is' : 's are'} hidden.</strong> Salesmen and agents can't see
          {hiddenCount === 1 ? ' it' : ' them'} yet: enter both prices, save, then an administrator switches {hiddenCount === 1 ? 'it' : 'them'} on.
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
      <ToggleButtonGroup size="small" exclusive value={show} onChange={(e, v) => v && setShow(v)} sx={{ mb: 2, mr: 2 }}>
        <ToggleButton value="ALL">All ({(pricing.data || []).length})</ToggleButton>
        <ToggleButton value="ON">On ({(pricing.data || []).length - hiddenCount})</ToggleButton>
        <ToggleButton value="HIDDEN">Hidden ({hiddenCount})</ToggleButton>
      </ToggleButtonGroup>
      <FormControl size="small" sx={{ mb: 2, mr: 2, minWidth: 180 }}>
        <InputLabel id="pricing-family">Family</InputLabel>
        <Select labelId="pricing-family" label="Family" value={familyFilter} onChange={(e) => setFamilyFilter(e.target.value)}>
          <MenuItem value="ALL">All families</MenuItem>
          {families.map((f) => <MenuItem key={f.id} value={f.id}>{f.name} ({productCounts[f.id] || 0})</MenuItem>)}
          <MenuItem value="NONE">No family</MenuItem>
        </Select>
      </FormControl>
      {canSwitch && (
        <Button variant="outlined" startIcon={<CategoryIcon />} onClick={() => setFamiliesOpen(true)} sx={{ mb: 2 }}>
          Families
        </Button>
      )}

      {pricing.isLoading && <CircularProgress />}
      {pricing.isError && <Alert severity="error">Failed to load product pricing.</Alert>}

      {pricing.data && (
        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell align="right" sx={{ width: 64 }}>SR No.</TableCell>
                  <TableCell>Product</TableCell>
                  <TableCell>Family</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Unit</TableCell>
                  <TableCell align="right">Agent Price (PKR)</TableCell>
                  <TableCell align="right">Salesman Price (PKR)</TableCell>
                  <TableCell align="center">Save</TableCell>
                  <TableCell>On / Hidden</TableCell>
                  {canSwitch && <TableCell align="center">List place</TableCell>}
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((p) => {
                  const edit = edits[p.id] || { agentPrice: '', salesmanPrice: '' };
                  const dirty = isDirty(p);
                  const pricesSet = p.agentPrice != null && p.salesmanPrice != null;
                  return (
                    <TableRow key={p.id} hover>
                      <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums', color: 'text.secondary' }}>
                        {p.onPriceList && p.sortOrder != null ? p.sortOrder : '—'}
                      </TableCell>
                      <TableCell>
                        {p.name}
                        {!p.onPriceList && <Chip size="small" variant="outlined" label="Not on the list" sx={{ ml: 1 }} />}
                      </TableCell>
                      <TableCell>{p.familyId != null ? familyName[p.familyId] || '—' : '—'}</TableCell>
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
                        <Tooltip title={!canSwitch ? 'Only an administrator can switch products on or off'
                          : pricesSet || p.active ? '' : 'Enter and save both prices first'}>
                          <span>
                            <Switch
                              size="small"
                              checked={!!p.active}
                              disabled={!canSwitch || (!pricesSet && !p.active) || activeMutation.isPending}
                              onChange={(e) => activeMutation.mutate({ id: p.id, active: e.target.checked })}
                              slotProps={{ input: { 'aria-label': `${p.name}: ${p.active ? 'on' : 'hidden'}` } }}
                            />
                          </span>
                        </Tooltip>
                        <Typography component="span" variant="caption" color={p.active ? 'success.main' : 'text.secondary'}>
                          {p.active ? 'On' : 'Hidden'}
                        </Typography>
                      </TableCell>
                      {canSwitch && (
                        <TableCell align="center">
                          <Tooltip title="Family, SR No. and price list">
                            <IconButton size="small" onClick={() => openPlacement(p)} aria-label={`List place of ${p.name}`}>
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      )}
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

      <Dialog open={!!placing} onClose={() => setPlacing(null)} maxWidth="xs" fullWidth>
        <DialogTitle>List place: {placing?.product.name}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Every product list in the app, the dashboard and the reports follows the SR No. Products with no SR No.
            come after all the others.
          </Typography>
          <FormControl fullWidth size="small" sx={{ mb: 2, mt: 1 }}>
            <InputLabel id="placing-family">Family</InputLabel>
            <Select labelId="placing-family" label="Family" value={placing?.familyId ?? ''}
              onChange={(e) => setPlacing((pl) => ({ ...pl, familyId: e.target.value }))}>
              <MenuItem value="">No family</MenuItem>
              {families.map((f) => <MenuItem key={f.id} value={f.id}>{f.name}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField label="SR No." type="number" size="small" fullWidth value={placing?.sortOrder ?? ''}
            onChange={(e) => setPlacing((pl) => ({ ...pl, sortOrder: e.target.value }))}
            helperText="Its number on the official price list" slotProps={{ htmlInput: { min: 1 } }} sx={{ mb: 1 }} />
          <FormControlLabel
            control={<Checkbox checked={!!placing?.onPriceList}
              onChange={(e) => setPlacing((pl) => ({ ...pl, onPriceList: e.target.checked }))} />}
            label="On the official price list" />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPlacing(null)}>Cancel</Button>
          <Button variant="contained" onClick={savePlacement} disabled={placementMutation.isPending}>Save</Button>
        </DialogActions>
      </Dialog>

      <ProductFamiliesDialog
        open={familiesOpen}
        onClose={() => setFamiliesOpen(false)}
        families={families}
        productCounts={productCounts}
        onChanged={() => queryClient.invalidateQueries({ queryKey: ['product-families'] })}
      />

      <Snackbar
        open={!!snackbar}
        autoHideDuration={3000}
        onClose={() => setSnackbar('')}
        message={snackbar}
      />
    </Box>
  );
}
