import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { storage } from '../utils/storage';
import NotificationService from './NotificationService';
import { apiService } from './api';

const BACKGROUND_LOCATION_TASK = 'BACKGROUND_LOCATION';

TaskManager.defineTask(BACKGROUND_LOCATION_TASK, async ({ data, error }) => {
  if (error) {
    console.error('Background location task error:', error);
    return;
  }
  if (data) {
    const { locations } = data;
    if (locations && locations.length > 0) {
      const location = locations[0];
      const { latitude, longitude } = location.coords;
      console.log(`[Background Location Update] Lat: ${latitude}, Lon: ${longitude}`);

      try {
        const user = await storage.getUser();
        if (user && user.id) {
          const geoResponse = await apiService.geoFence.check(user.id, latitude, longitude);
          if (geoResponse) {
            const { status, message } = geoResponse;
            if (status === 'ENTERED' || status === 'EXITED') {
              await NotificationService.sendLocalNotification(
                status === 'ENTERED' ? 'Mart Check-In' : 'Mart Check-Out',
                message
              );
            }
          }
        }
      } catch (err) {
        console.error('Failed to report background location to server:', err.message);
      }
    }
  }
});

export const LocationService = {
  async requestPermissions() {
    const { status: foregroundStatus } = await Location.requestForegroundPermissionsAsync();
    if (foregroundStatus !== 'granted') {
      return { success: false, error: 'Foreground location permission is required' };
    }

    const { status: backgroundStatus } = await Location.requestBackgroundPermissionsAsync();
    if (backgroundStatus !== 'granted') {
      return { success: false, error: 'Background location permission is required' };
    }

    return { success: true };
  },

  async startBackgroundTracking() {
    try {
      const isRegistered = await TaskManager.isTaskRegisteredAsync(BACKGROUND_LOCATION_TASK);
      if (isRegistered) {
        console.log('Background location task already registered.');
      }

      await Location.startLocationUpdatesAsync(BACKGROUND_LOCATION_TASK, {
        accuracy: Location.Accuracy.High,
        timeInterval: 10000, 
        distanceInterval: 10, 
        foregroundService: {
          notificationTitle: 'Attendance System',
          notificationBody: 'Geo-fencing check-in service is running in background',
          notificationColor: '#2196F3',
        },
      });
      console.log('Background location tracking started.');
      return true;
    } catch (e) {
      console.error('Error starting location updates:', e);
      return false;
    }
  },

  async stopBackgroundTracking() {
    try {
      const hasStarted = await Location.hasStartedLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
      if (hasStarted) {
        await Location.stopLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
        console.log('Background location tracking stopped.');
      }
      return true;
    } catch (e) {
      console.error('Error stopping location updates:', e);
      return false;
    }
  },

  /**
   * Reads current permission status WITHOUT prompting the OS dialog.
   * Use this to detect if an agent has revoked location access after
   * previously granting it (e.g. from phone Settings), so check-in/out
   * can be blocked with a clear message instead of silently failing.
   */
  async getPermissionStatus() {
    try {
      const foreground = await Location.getForegroundPermissionsAsync();
      const background = await Location.getBackgroundPermissionsAsync();
      return {
        foregroundGranted: foreground.status === 'granted',
        backgroundGranted: background.status === 'granted',
        granted: foreground.status === 'granted' && background.status === 'granted',
      };
    } catch (e) {
      console.error('Error checking location permission status:', e);
      return { foregroundGranted: false, backgroundGranted: false, granted: false };
    }
  }
};

export default LocationService;
