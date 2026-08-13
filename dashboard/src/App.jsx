import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import HRLayout from './layouts/HRLayout';
import SalesLayout from './layouts/SalesLayout';
import HRHomePage from './pages/hr/HRHomePage';
import SalesHomePage from './pages/sales/SalesHomePage';

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
          <Route index element={<HRHomePage />} />
        </Route>

        <Route
          path="/sales"
          element={
            <ProtectedRoute allowedRoles={['SALES', 'ADMIN']}>
              <SalesLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<SalesHomePage />} />
        </Route>

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AuthProvider>
  );
}
