import AsyncStorage from '@react-native-async-storage/async-storage';

const TOKEN_KEY = '@auth_token';
const USER_KEY = '@auth_user';

export const storage = {
  /**
   * Save JWT token to AsyncStorage
   */
  async setToken(token) {
    try {
      await AsyncStorage.setItem(TOKEN_KEY, token);
    } catch (e) {
      console.error('Error saving token', e);
    }
  },

  /**
   * Retrieve JWT token from AsyncStorage
   */
  async getToken() {
    try {
      return await AsyncStorage.getItem(TOKEN_KEY);
    } catch (e) {
      console.error('Error retrieving token', e);
      return null;
    }
  },

  /**
   * Remove JWT token from AsyncStorage
   */
  async removeToken() {
    try {
      await AsyncStorage.removeItem(TOKEN_KEY);
    } catch (e) {
      console.error('Error removing token', e);
    }
  },

  /**
   * Save user details to AsyncStorage
   */
  async setUser(user) {
    try {
      await AsyncStorage.setItem(USER_KEY, JSON.stringify(user));
    } catch (e) {
      console.error('Error saving user data', e);
    }
  },

  /**
   * Retrieve user details from AsyncStorage
   */
  async getUser() {
    try {
      const userStr = await AsyncStorage.getItem(USER_KEY);
      return userStr ? JSON.parse(userStr) : null;
    } catch (e) {
      console.error('Error retrieving user data', e);
      return null;
    }
  },

  /**
   * Remove user details from AsyncStorage
   */
  async removeUser() {
    try {
      await AsyncStorage.removeItem(USER_KEY);
    } catch (e) {
      console.error('Error removing user data', e);
    }
  },

  /**
   * Clear all auth storage
   */
  async clearAll() {
    try {
      await AsyncStorage.multiRemove([TOKEN_KEY, USER_KEY]);
    } catch (e) {
      console.error('Error clearing storage', e);
    }
  },

  /**
   * Save custom server URL
   */
  async setServerUrl(url) {
    try {
      if (url) {
        await AsyncStorage.setItem('@server_url', url);
      } else {
        await AsyncStorage.removeItem('@server_url');
      }
    } catch (e) {
      console.error('Error saving server URL', e);
    }
  },

  /**
   * Get custom server URL
   */
  async getServerUrl() {
    try {
      return await AsyncStorage.getItem('@server_url');
    } catch (e) {
      console.error('Error retrieving server URL', e);
      return null;
    }
  }
};
