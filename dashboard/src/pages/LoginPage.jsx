import React, { useContext, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Paper, TextField, Button, Typography, Alert } from '@mui/material';
import { AuthContext } from '../context/AuthContext';

const ROLE_HOME = { HR: '/hr', SALES: '/sales' };

export default function LoginPage() {
  const { login } = useContext(AuthContext);
  const navigate = useNavigate();
  const [companyCode, setCompanyCode] = useState('');
  const [agentId, setAgentId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const result = await login(companyCode, agentId, password);
    setSubmitting(false);
    if (!result.success) {
      setError(result.error);
      return;
    }
    navigate(ROLE_HOME[result.user.role] || '/login', { replace: true });
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'grey.100',
      }}
    >
      <Paper sx={{ p: 4, width: 360 }} elevation={3}>
        <Typography variant="h5" fontWeight="bold" gutterBottom>
          Dawn Bread — Staff Dashboard
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          HR and Sales sign-in
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Company Code"
            value={companyCode}
            onChange={(e) => setCompanyCode(e.target.value)}
            required
            autoFocus
          />
          <TextField
            label="Agent ID"
            value={agentId}
            onChange={(e) => setAgentId(e.target.value)}
            required
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Button type="submit" variant="contained" size="large" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign In'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
