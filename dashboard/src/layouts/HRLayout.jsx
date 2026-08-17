import React from 'react';
import DashboardLayout from './DashboardLayout';

const HR_NAV = [
  { path: '/hr', label: "Today's Board" },
  { path: '/hr/status', label: 'Agent Status' },
  { path: '/hr/history', label: 'Attendance History' },
  { path: '/hr/analytics', label: 'Analytics' },
  { path: '/hr/agents', label: 'Agent Directory' },
];

export default function HRLayout() {
  return <DashboardLayout title="HR Dashboard" navItems={HR_NAV} />;
}
