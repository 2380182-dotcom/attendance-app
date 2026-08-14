import React from 'react';
import DashboardLayout from './DashboardLayout';

const SALES_NAV = [
  { path: '/sales', label: 'Overview' },
  { path: '/sales/history', label: 'Sales History' },
];

export default function SalesLayout() {
  return <DashboardLayout title="Sales Dashboard" navItems={SALES_NAV} />;
}
