import React from 'react';
import DashboardLayout from './DashboardLayout';

const SALES_NAV = [{ path: '/sales', label: 'Overview' }];

export default function SalesLayout() {
  return <DashboardLayout title="Sales Dashboard" navItems={SALES_NAV} />;
}
