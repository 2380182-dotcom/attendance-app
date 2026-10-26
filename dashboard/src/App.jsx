import React, { lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import HRLayout from './layouts/HRLayout';
import SalesLayout from './layouts/SalesLayout';
import AdminLayout from './layouts/AdminLayout';
const TodayBoardPage = lazy(() => import('./pages/hr/TodayBoardPage'));
const AgentStatusPage = lazy(() => import('./pages/hr/AgentStatusPage'));
const AttendanceHistoryPage = lazy(() => import('./pages/hr/AttendanceHistoryPage'));
const HRAnalyticsPage = lazy(() => import('./pages/hr/HRAnalyticsPage'));
const AgentDirectoryPage = lazy(() => import('./pages/hr/AgentDirectoryPage'));
const AgentProfilePage = lazy(() => import('./pages/hr/AgentProfilePage'));
const SalesOverviewPage = lazy(() => import('./pages/sales/SalesOverviewPage'));
const SalesHistoryPage = lazy(() => import('./pages/sales/SalesHistoryPage'));
const ProductPricingPage = lazy(() => import('./pages/sales/ProductPricingPage'));
const LmtReconciliationPage = lazy(() => import('./pages/sales/LmtReconciliationPage'));
const SalesVoucherShopsPage = lazy(() => import('./pages/sales/SalesVoucherShopsPage'));
const SalesVoucherListPage = lazy(() => import('./pages/sales/SalesVoucherListPage'));
const SalesVoucherDetailPage = lazy(() => import('./pages/sales/SalesVoucherDetailPage'));
const QrShopVisitsPage = lazy(() => import('./pages/sales/QrShopVisitsPage'));
const SalesmanVisitHistoryPage = lazy(() => import('./pages/sales/SalesmanVisitHistoryPage'));
const HierarchyPeoplePage = lazy(() => import('./pages/admin/HierarchyPeoplePage'));
const SalesCockpitPage = lazy(() => import('./cockpit/sales/SalesCockpitPage'));
const AreasPage = lazy(() => import('./pages/admin/AreasPage'));
const CustomerShopsPage = lazy(() => import('./pages/admin/CustomerShopsPage'));
const LmtSettingsPage = lazy(() => import('./pages/admin/LmtSettingsPage'));

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
          <Route path="status" element={<AgentStatusPage />} />
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
          {/* Cockpit is the Sales landing page; the old Overview moved to /sales/overview. */}
          <Route index element={<SalesCockpitPage />} />
          <Route path="cockpit" element={<Navigate to="/sales" replace />} />
          <Route path="overview" element={<SalesOverviewPage />} />
          <Route path="history" element={<SalesHistoryPage />} />
          <Route path="reconciliation" element={<LmtReconciliationPage />} />
          <Route path="pricing" element={<ProductPricingPage />} />
          {/* Task 4 correction: ADMIN and SALES both — the outer /sales
              ProtectedRoute above already enforces exactly that, and the
              backend (ShopVisitScanController/SalesVoucherController)
              enforces it independently too, so no extra nested gate is
              needed here. Salesmen and HR are blocked by the outer gate. */}
          <Route path="vouchers/:section/shops" element={<SalesVoucherShopsPage />} />
          <Route path="vouchers/:section/shops/:shopId" element={<SalesVoucherListPage />} />
          <Route path="vouchers/:voucherId" element={<SalesVoucherDetailPage />} />
          <Route path="qr-visits" element={<QrShopVisitsPage />} />
          <Route path="qr-visits/:agentId" element={<SalesmanVisitHistoryPage />} />
        </Route>

        <Route
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<HierarchyPeoplePage />} />
          <Route path="areas" element={<AreasPage />} />
          <Route path="customer-shops" element={<CustomerShopsPage />} />
          <Route path="lmt-settings" element={<LmtSettingsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AuthProvider>
  );
}
