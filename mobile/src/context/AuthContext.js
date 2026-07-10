import React, { createContext, useState, useEffect } from 'react';
import { apiService, setSessionExpiredHandler } from '../services/api';
import { storage } from '../utils/storage';
import LocationService from '../services/LocationService';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [userToken, setUserToken] = useState(null);
  const [user, setUser] = useState(null);

  // Location tracking is mandatory for AGENT accounts (it's how attendance is verified) —
  // there is no agent-facing opt-out. Requested/started automatically as part of using the
  // app, not as an in-app toggle. Fire-and-forget: actual enforcement happens at check-in/out
  // time (LocationService.getPermissionStatus() gate), so this doesn't need to block login UX.
  const ensureLocationTracking = (userData) => {
    if (!userData || userData.role !== 'AGENT') return;
    (async () => {
      try {
        const permission = await LocationService.requestPermissions();
        if (permission.success) {
          await LocationService.startBackgroundTracking();
        } else {
          console.warn('Location permission not granted:', permission.error);
        }
      } catch (e) {
        console.error('Failed to start background location tracking', e);
      }
    })();
  };

  // Load storage keys on launch
  const bootstrapAsync = async () => {
    try {
      const token = await storage.getToken();
      const userData = await storage.getUser();

      if (token && userData) {
        setUserToken(token);
        setUser(userData);
        ensureLocationTracking(userData);
      }
    } catch (e) {
      console.error('Failed to load session from storage', e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    bootstrapAsync();
  }, []);

  // Registered with the axios response interceptor (api.js) so a 401 from any
  // authenticated request — not just an explicit logout — clears session state.
  // Storage is already cleared by the interceptor before this fires.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      LocationService.stopBackgroundTracking().catch(() => {});
      setUserToken(null);
      setUser(null);
    });
  }, []);

  const login = async (companyCode, agentId, password) => {
    setIsLoading(true);
    try {
      const data = await apiService.auth.login(companyCode, agentId, password);
      const token = data.token;
      if (!token) {
        throw new Error('No authentication token received from server');
      }
      
      // Store token and user details in AsyncStorage
      await storage.setToken(token);
      await storage.setUser(data);
      
      setUserToken(token);
      setUser(data);
      ensureLocationTracking(data);
      return { success: true };
    } catch (e) {
      setIsLoading(false);
      return { success: false, error: e.message || 'Login failed' };
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (agentId, name, email, phone, password, role, department, faceVerifyOnCheckIn = true, faceVerifyOnCheckOut = true, faceVerifyAnytime = true, faceRegistered = false, faceTemplate = '') => {
    setIsLoading(true);
    try {
      await apiService.auth.register({
        agentId,
        name,
        email,
        phone,
        password,
        role,
        department,
        faceVerifyOnCheckIn,
        faceVerifyOnCheckOut,
        faceVerifyAnytime,
        faceRegistered,
        faceTemplate
      });
      setIsLoading(false);
      return { success: true };
    } catch (e) {
      setIsLoading(false);
      return { success: false, error: e.message || 'Registration failed' };
    }
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await LocationService.stopBackgroundTracking();
      await storage.clearAll();
      setUserToken(null);
      setUser(null);
    } catch (e) {
      console.error('Failed to clear session on logout', e);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        isLoading,
        userToken,
        user,
        login,
        register,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
