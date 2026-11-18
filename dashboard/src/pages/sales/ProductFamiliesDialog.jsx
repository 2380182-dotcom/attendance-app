import React, { useEffect, useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Table, TableHead, TableRow, TableCell, TableBody,
  TextField, IconButton, Tooltip, Typography, Alert, CircularProgress,
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { productApi } from '../../services/productApi';

const blank = { name: '', nameUr: '', sortOrder: '' };

/**
 * Admin only: the product families, i.e. the app's filter buttons. Name, Urdu
 * name (shown under the name on the button) and button order. A family can
 * be deleted only when no product is in it. Changes reach the apps within
 * five minutes, with no new app needed.
 */
export default function ProductFamiliesDialog({ open, onClose, families, productCounts, onChanged }) {
  const [rows, setRows] = useState({});
  const [adding, setAdding] = useState(blank);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setRows(Object.fromEntries((families || []).map((f) => [f.id, {
      name: f.name, nameUr: f.nameUr || '', sortOrder: String(f.sortOrder),
    }])));
    setAdding({ ...blank, sortOrder: String(Math.max(0, ...(families || []).map((f) => f.sortOrder)) + 1) });
    setError('');
  }, [open, families]);

  const run = async (key, fn) => {
    setBusy(key);
    setError('');
    try {
      await fn();
      await onChanged();
    } catch (e) {
      setError(e.response?.data?.message || 'Could not save. Try again.');
    } finally {
      setBusy(null);
    }
  };

  const body = (r) => ({ name: r.name, nameUr: r.nameUr || null, sortOrder: Number(r.sortOrder) });
  const valid = (r) => r.name.trim() !== '' && Number.isInteger(Number(r.sortOrder)) && Number(r.sortOrder) >= 1;
  const changed = (f) => {
    const r = rows[f.id];
    return r && (r.name !== f.name || r.nameUr !== (f.nameUr || '') || r.sortOrder !== String(f.sortOrder));
  };
  const setField = (id, field, value) => setRows((prev) => ({ ...prev, [id]: { ...prev[id], [field]: value } }));

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Product families</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          These are the filter buttons in the app, in button order. The Urdu name shows under the English name on
          the button. A family can be deleted only when it has no products.
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 110 }}>Button order</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Urdu name</TableCell>
              <TableCell align="right">Products</TableCell>
              <TableCell align="center" sx={{ width: 96 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {(families || []).map((f) => {
              const r = rows[f.id] || blank;
              const count = productCounts[f.id] || 0;
              return (
                <TableRow key={f.id}>
                  <TableCell>
                    <TextField size="small" type="number" value={r.sortOrder} sx={{ width: 90 }}
                      onChange={(e) => setField(f.id, 'sortOrder', e.target.value)}
                      slotProps={{ htmlInput: { min: 1, 'aria-label': `${f.name} button order` } }} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.name} fullWidth
                      onChange={(e) => setField(f.id, 'name', e.target.value)}
                      slotProps={{ htmlInput: { maxLength: 40, 'aria-label': `${f.name} name` } }} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.nameUr} fullWidth placeholder="—"
                      onChange={(e) => setField(f.id, 'nameUr', e.target.value)}
                      slotProps={{ htmlInput: { maxLength: 60, dir: 'rtl', lang: 'ur', 'aria-label': `${f.name} Urdu name` } }} />
                  </TableCell>
                  <TableCell align="right">{count}</TableCell>
                  <TableCell align="center">
                    <Tooltip title="Save">
                      <span>
                        <IconButton size="small" color="primary" disabled={!changed(f) || !valid(r) || busy !== null}
                          onClick={() => run(f.id, () => productApi.saveFamily(f.id, body(r)))}
                          aria-label={`Save ${f.name}`}>
                          {busy === f.id ? <CircularProgress size={18} /> : <SaveIcon fontSize="small" />}
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title={count > 0 ? 'Move its products to another family first' : 'Delete'}>
                      <span>
                        <IconButton size="small" color="error" disabled={count > 0 || busy !== null}
                          onClick={() => run(`del-${f.id}`, () => productApi.deleteFamily(f.id))}
                          aria-label={`Delete ${f.name}`}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              );
            })}
            <TableRow>
              <TableCell>
                <TextField size="small" type="number" value={adding.sortOrder} sx={{ width: 90 }}
                  onChange={(e) => setAdding((a) => ({ ...a, sortOrder: e.target.value }))}
                  slotProps={{ htmlInput: { min: 1, 'aria-label': 'New family button order' } }} />
              </TableCell>
              <TableCell>
                <TextField size="small" value={adding.name} fullWidth placeholder="New family"
                  onChange={(e) => setAdding((a) => ({ ...a, name: e.target.value }))}
                  slotProps={{ htmlInput: { maxLength: 40, 'aria-label': 'New family name' } }} />
              </TableCell>
              <TableCell>
                <TextField size="small" value={adding.nameUr} fullWidth placeholder="—"
                  onChange={(e) => setAdding((a) => ({ ...a, nameUr: e.target.value }))}
                  slotProps={{ htmlInput: { maxLength: 60, dir: 'rtl', lang: 'ur', 'aria-label': 'New family Urdu name' } }} />
              </TableCell>
              <TableCell />
              <TableCell align="center">
                <Button size="small" startIcon={<AddIcon />} disabled={!valid(adding) || busy !== null}
                  onClick={() => run('add', () => productApi.saveFamily(null, body(adding)))}>
                  Add
                </Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
