import React from 'react';
import DashboardLayout from './DashboardLayout';

const HR_NAV = [{ path: '/hr', label: 'Overview' }];

export default function HRLayout() {
  return <DashboardLayout title="HR Dashboard" navItems={HR_NAV} />;
}
