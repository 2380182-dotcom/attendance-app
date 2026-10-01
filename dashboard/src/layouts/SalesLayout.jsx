import React from 'react';
import DashboardLayout from './DashboardLayout';

// Task 4 correction: "QR Scanned Shops" and the Local/LMT Sales Voucher
// sections all live here now — ADMIN and SALES both, salesmen and HR
// blocked. The outer /sales ProtectedRoute (App.jsx) already restricts who
// reaches this layout at all to ADMIN/SALES, and the backend endpoints
// enforce the same roles independently, so every item below is shown to
// whoever gets this far — no per-item role filtering needed.
const SALES_NAV = [
  { path: '/sales', label: 'Overview' },
  { path: '/sales/history', label: 'Sales History' },
  { path: '/sales/reconciliation', label: 'LMT Reconciliation' },
  { path: '/sales/pricing', label: 'Product Pricing' },
  { path: '/sales/qr-visits', label: 'QR Scanned Shops' },
  { path: '/sales/vouchers/local/shops', label: 'Local Sales Vouchers' },
  { path: '/sales/vouchers/lmt/shops', label: 'LMT Sales Vouchers' },
];

export default function SalesLayout() {
  return <DashboardLayout title="Sales Dashboard" navItems={SALES_NAV} />;
}
