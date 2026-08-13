import React, { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

/**
 * Gates a route by login state and, optionally, role. `allowedRoles` should
 * match the `role` string the backend returns on login (e.g. "HR", "SALES").
 */
export default function ProtectedRoute({ allowedRoles, children }) {
  const { user, isLoading } = useContext(AuthContext);

  if (isLoading) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/login" replace />;
  }
  return children;
}
