import React, { createContext, useState, useEffect } from 'react';
import { apiService } from '../services/api';
import { storage } from '../utils/storage';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [userToken, setUserToken] = useState(null);
  const [user, setUser] = useState(null);

  // Load storage keys on launch
  const bootstrapAsync = async () => {
    try {
      const token = await storage.getToken();
      const userData = await storage.getUser();
      
      if (token && userData) {
        setUserToken(token);
        setUser(userData);
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

  const login = async (agentId, password) => {
    setIsLoading(true);
    try {
      const data = await apiService.auth.login(agentId, password);
      const token = data.token;
      if (!token) {
        throw new Error('No authentication token received from server');
      }
      
      // Store token and user details in AsyncStorage
      await storage.setToken(token);
      await storage.setUser(data);
      
      setUserToken(token);
      setUser(data);
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
