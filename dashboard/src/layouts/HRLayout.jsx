import React from 'react';
import DashboardLayout from './DashboardLayout';

const HR_NAV = [
  { path: '/hr', label: "Today's Board" },
  { path: '/hr/history', label: 'Attendance History' },
];

export default function HRLayout() {
  return <DashboardLayout title="HR Dashboard" navItems={HR_NAV} />;
}
