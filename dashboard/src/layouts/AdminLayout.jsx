import React from 'react';
import DashboardLayout from './DashboardLayout';

const ADMIN_NAV = [
  { path: '/admin', label: 'Hierarchy People' },
  { path: '/admin/areas', label: 'Areas' },
  { path: '/admin/customer-shops', label: 'Customer Shops' },
  { path: '/admin/lmt-settings', label: 'LMT Settings' },
  // QR Scanned Shops moved to the Sales sidebar (Task 4 correction) —
  // ADMIN and SALES can both open it now, see SalesLayout.
];

export default function AdminLayout() {
  return <DashboardLayout title="LMT Admin" navItems={ADMIN_NAV} />;
}
