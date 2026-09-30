import React from 'react';
import DashboardLayout from './DashboardLayout';

const ADMIN_NAV = [
  { path: '/admin', label: 'Hierarchy People' },
  { path: '/admin/areas', label: 'Areas' },
  { path: '/admin/customer-shops', label: 'Customer Shops' },
  { path: '/admin/lmt-settings', label: 'LMT Settings' },
  { path: '/admin/qr-visits', label: 'QR Scanned Shops' },
];

export default function AdminLayout() {
  return <DashboardLayout title="LMT Admin" navItems={ADMIN_NAV} />;
}
