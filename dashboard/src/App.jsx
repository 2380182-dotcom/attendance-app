import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import HRLayout from './layouts/HRLayout';
import SalesLayout from './layouts/SalesLayout';
import TodayBoardPage from './pages/hr/TodayBoardPage';
import AttendanceHistoryPage from './pages/hr/AttendanceHistoryPage';
import HRAnalyticsPage from './pages/hr/HRAnalyticsPage';
import AgentDirectoryPage from './pages/hr/AgentDirectoryPage';
import AgentProfilePage from './pages/hr/AgentProfilePage';
import SalesOverviewPage from './pages/sales/SalesOverviewPage';
import SalesHistoryPage from './pages/sales/SalesHistoryPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/hr"
          element={
            <ProtectedRoute allowedRoles={['HR', 'ADMIN']}>
              <HRLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<TodayBoardPage />} />
          <Route path="history" element={<AttendanceHistoryPage />} />
          <Route path="analytics" element={<HRAnalyticsPage />} />
          <Route path="agents" element={<AgentDirectoryPage />} />
          <Route path="agents/:agentId" element={<AgentProfilePage />} />
        </Route>

        <Route
          path="/sales"
          element={
            <ProtectedRoute allowedRoles={['SALES', 'ADMIN']}>
              <SalesLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<SalesOverviewPage />} />
          <Route path="history" element={<SalesHistoryPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AuthProvider>
  );
}
