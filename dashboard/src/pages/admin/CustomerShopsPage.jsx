import React, { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box, Paper, Typography, Table, TableContainer, TableHead, TableRow, TableCell, TableBody,
  Chip, CircularProgress, Alert, Stack, TextField, InputAdornment, Button,
  Dialog, DialogTitle, DialogContent, DialogActions, TablePagination, IconButton,
  Select, MenuItem, InputLabel, FormControl, Grid, FormControlLabel, Switch, Divider,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import { customerShopApi, areaApi } from '../../services/lmtApi';
import { sortRows, useSort } from '../../utils/sorting';
import { usePagination } from '../../utils/pagination';
import SortableHeader from '../../components/SortableHeader';

const emptyForm = {
  shopCode: '', shopName: '', branch: '', address: '', phone: '', mobile: '', email: '',
  strn: '', ntn: '', areaId: '', latitude: '', longitude: '', radius: '', geoFencingEnabled: true,
};

function toNullableNumber(value) {
  if (value === '' || value === null || value === undefined) return null;
  const n = Number(value);
  return Number.isNaN(n) ? null : n;
}

export default function CustomerShopsPage() {
  const theme = useTheme();
  const fullScreenDialog = useMediaQuery(theme.breakpoints.down('sm'));
  const queryClient = useQueryClient();
  const shops = useQuery({ queryKey: ['lmt-customer-shops'], queryFn: customerShopApi.getAll });
  const areas = useQuery({ queryKey: ['lmt-areas'], queryFn: areaApi.getAll });
  const activeAreas = useMemo(() => (areas.data || []).filter((a) => a.isActive), [areas.data]);

  const [search, setSearch] = useState('');
  const [sort, onSort] = useSort('shopCode', 'asc');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [actionError, setActionError] = useState('');
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState('');

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['lmt-customer-shops'] });

  // The admin registers a shop by physically standing at it — their current
  // GPS position IS the shop's location, so this fills lat/long directly
  // rather than requiring them to look up or type coordinates by hand.
  const handleUseCurrentLocation = () => {
    setLocationError('');
    if (!navigator.geolocation) {
      setLocationError('Geolocation is not supported by this browser.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setForm((prev) => ({
          ...prev,
          latitude: position.coords.latitude.toFixed(6),
          longitude: position.coords.longitude.toFixed(6),
        }));
        setLocating(false);
      },
      (error) => {
        setLocating(false);
        if (error.code === error.PERMISSION_DENIED) {
          setLocationError('Location permission denied — allow location access for this site, or enter latitude/longitude manually.');
        } else if (error.code === error.POSITION_UNAVAILABLE) {
          setLocationError('Current location is unavailable right now — try again, or enter latitude/longitude manually.');
        } else if (error.code === error.TIMEOUT) {
          setLocationError('Timed out getting the current location — try again, or enter latitude/longitude manually.');
        } else {
          setLocationError('Could not get the current location — enter latitude/longitude manually.');
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  };

  const createMutation = useMutation({
    mutationFn: customerShopApi.create,
    onSuccess: () => { invalidate(); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to create shop.'),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, dto }) => customerShopApi.update(id, dto),
    onSuccess: () => { invalidate(); closeDialog(); },
    onError: (e) => setFormError(e.response?.data?.message || 'Failed to update shop.'),
  });
  const deactivateMutation = useMutation({
    mutationFn: customerShopApi.deactivate,
    onSuccess: invalidate,
    onError: (e) => setActionError(e.response?.data?.message || 'Failed to deactivate shop.'),
  });
  const reactivateMutation = useMutation({
    mutationFn: customerShopApi.reactivate,
    onSuccess: invalidate,
    onError: (e) => setActionError(e.response?.data?.message || 'Failed to reactivate shop.'),
  });

  const filtered = useMemo(() => {
    const rows = shops.data || [];
    if (!search.trim()) return rows;
    const q = search.trim().toLowerCase();
    return rows.filter(
      (s) => s.shopCode.toLowerCase().includes(q) || s.shopName.toLowerCase().includes(q) || (s.area?.name || '').toLowerCase().includes(q)
    );
  }, [shops.data, search]);

  const sorted = useMemo(() => sortRows(filtered, sort.key, sort.direction), [filtered, sort]);
  const { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage, resetPage } = usePagination(sorted);

  const openCreateDialog = () => {
    setEditingId(null);
    setForm(emptyForm);
    setFormError('');
    setLocationError('');
    setDialogOpen(true);
  };
  const openEditDialog = (shop) => {
    setEditingId(shop.id);
    setForm({
      shopCode: shop.shopCode, shopName: shop.shopName, branch: shop.branch || '', address: shop.address || '',
      phone: shop.phone || '', mobile: shop.mobile || '', email: shop.email || '', strn: shop.strn || '', ntn: shop.ntn || '',
      areaId: shop.area?.id || '', latitude: shop.latitude ?? '', longitude: shop.longitude ?? '', radius: shop.radius ?? '',
      geoFencingEnabled: shop.geoFencingEnabled !== false,
    });
    setFormError('');
    setLocationError('');
    setDialogOpen(true);
  };
  const closeDialog = () => setDialogOpen(false);

  const handleSave = () => {
    if (!form.shopCode.trim()) { setFormError('Shop Code is required.'); return; }
    if (!form.shopName.trim()) { setFormError('Shop Name is required.'); return; }
    if (!form.areaId) { setFormError('Area is required.'); return; }

    const dto = {
      shopCode: form.shopCode.trim(),
      shopName: form.shopName.trim(),
      branch: form.branch.trim() || null,
      address: form.address.trim() || null,
      phone: form.phone.trim() || null,
      mobile: form.mobile.trim() || null,
      email: form.email.trim() || null,
      strn: form.strn.trim() || null,
      ntn: form.ntn.trim() || null,
      areaId: form.areaId,
      latitude: toNullableNumber(form.latitude),
      longitude: toNullableNumber(form.longitude),
      radius: toNullableNumber(form.radius),
      geoFencingEnabled: form.geoFencingEnabled,
    };
    if (editingId) {
      updateMutation.mutate({ id: editingId, dto });
    } else {
      createMutation.mutate(dto);
    }
  };

  const saving = createMutation.isPending || updateMutation.isPending;

  if (shops.isLoading || areas.isLoading) return <CircularProgress />;
  if (shops.isError || areas.isError) return <Alert severity="error">Failed to load customer shops. Try refreshing.</Alert>;

  return (
    <Box>
      <Typography variant="h5" gutterBottom>Customer Shops</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        The master data salesmen's shop-code entry auto-fills from — registered here once, reused on every visit.
      </Typography>

      {actionError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError('')}>{actionError}</Alert>}

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center" flexWrap="wrap" useFlexGap>
        <TextField
          size="small"
          placeholder="Search shop code, name, or area…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); resetPage(); }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
          sx={{ minWidth: 280 }}
        />
        <Button
          startIcon={<AddIcon />}
          variant="contained"
          onClick={openCreateDialog}
          disabled={activeAreas.length === 0}
        >
          Add Shop
        </Button>
        {activeAreas.length === 0 && (
          <Typography variant="caption" color="text.secondary">Add an Area first before registering a shop.</Typography>
        )}
      </Stack>

      <Paper>
        <TableContainer><Table>
          <TableHead>
            <TableRow>
              <SortableHeader label="Shop Code" sortKey="shopCode" sort={sort} onSort={onSort} />
              <SortableHeader label="Shop Name" sortKey="shopName" sort={sort} onSort={onSort} />
              <TableCell>Area</TableCell>
              <TableCell>Geofence</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paged.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center">No customer shops match this filter.</TableCell></TableRow>
            )}
            {paged.map((shop) => (
              <TableRow key={shop.id} hover>
                <TableCell>{shop.shopCode}</TableCell>
                <TableCell>{shop.shopName}</TableCell>
                <TableCell>{shop.area?.name || '—'}</TableCell>
                <TableCell>
                  {shop.geoFencingEnabled && shop.latitude != null && shop.longitude != null && shop.radius != null
                    ? `${shop.radius}m`
                    : 'Off'}
                </TableCell>
                <TableCell>
                  <Chip size="small" label={shop.isActive ? 'Active' : 'Inactive'} color={shop.isActive ? 'success' : 'default'} />
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => openEditDialog(shop)}><EditIcon fontSize="small" /></IconButton>
                  {shop.isActive ? (
                    <Button size="small" color="error" onClick={() => deactivateMutation.mutate(shop.id)}>Deactivate</Button>
                  ) : (
                    <Button size="small" onClick={() => reactivateMutation.mutate(shop.id)}>Reactivate</Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
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

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="md" fullWidth fullScreen={fullScreenDialog}>
        <DialogTitle>{editingId ? 'Edit Customer Shop' : 'Add Customer Shop'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{formError}</Alert>}

          <Typography variant="subtitle2" sx={{ mb: 1 }}>Basic Info</Typography>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                label="Shop Code" required fullWidth autoFocus
                value={form.shopCode} onChange={(e) => setForm({ ...form, shopCode: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                label="Shop Name" required fullWidth
                value={form.shopName} onChange={(e) => setForm({ ...form, shopName: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField label="Branch" fullWidth value={form.branch} onChange={(e) => setForm({ ...form, branch: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth required>
                <InputLabel>Area</InputLabel>
                <Select label="Area" value={form.areaId} onChange={(e) => setForm({ ...form, areaId: e.target.value })}>
                  {activeAreas.map((a) => <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <TextField label="Address" fullWidth value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
            </Grid>
          </Grid>

          <Divider sx={{ mb: 2 }} />
          <Typography variant="subtitle2" sx={{ mb: 1 }}>Contact & Tax</Typography>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} sm={4}>
              <TextField label="Phone" fullWidth value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField label="Mobile" fullWidth value={form.mobile} onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField label="Email" fullWidth value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField label="STRN" fullWidth value={form.strn} onChange={(e) => setForm({ ...form, strn: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField label="NTN" fullWidth value={form.ntn} onChange={(e) => setForm({ ...form, ntn: e.target.value })} />
            </Grid>
          </Grid>

          <Divider sx={{ mb: 2 }} />
          <Typography variant="subtitle2" sx={{ mb: 1 }}>Geofence</Typography>

          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
            <Button
              variant="outlined"
              size="small"
              startIcon={locating ? <CircularProgress size={16} /> : <MyLocationIcon />}
              onClick={handleUseCurrentLocation}
              disabled={locating}
            >
              {locating ? 'Getting location…' : 'Use Current Location'}
            </Button>
            <Typography variant="caption" color="text.secondary">
              Stand at the shop, then tap this to fill latitude/longitude automatically.
            </Typography>
          </Stack>
          {locationError && (
            <Alert severity="warning" sx={{ mb: 2 }} onClose={() => setLocationError('')}>{locationError}</Alert>
          )}

          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} sm={4}>
              <TextField
                label="Latitude" type="number" fullWidth
                value={form.latitude} onChange={(e) => setForm({ ...form, latitude: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField
                label="Longitude" type="number" fullWidth
                value={form.longitude} onChange={(e) => setForm({ ...form, longitude: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField
                label="Radius (meters)" type="number" fullWidth
                value={form.radius} onChange={(e) => setForm({ ...form, radius: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.geoFencingEnabled}
                    onChange={(e) => setForm({ ...form, geoFencingEnabled: e.target.checked })}
                  />
                }
                label="Geofencing enabled for this shop"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
