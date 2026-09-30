import React from 'react';
import DashboardLayout from './DashboardLayout';

// QR / Shop Visits moved to its own Admin-sidebar item ("QR Scanned Shops")
// and became admin-only — see AdminLayout.
const SALES_NAV = [
  { path: '/sales', label: 'Overview' },
  { path: '/sales/history', label: 'Sales History' },
  { path: '/sales/reconciliation', label: 'LMT Reconciliation' },
  { path: '/sales/pricing', label: 'Product Pricing' },
];

export default function SalesLayout() {
  return <DashboardLayout title="Sales Dashboard" navItems={SALES_NAV} />;
}
