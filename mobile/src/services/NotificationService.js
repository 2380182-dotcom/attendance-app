import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { debugLog } from '../utils/debugLog';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export const NotificationService = {
  async registerForPushNotificationsAsync() {
    let token;
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'default',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#FF231F7C',
      });
    }

    const { status: existingStatus } = await Notifications.getPermissionsAsync();
    let finalStatus = existingStatus;
    if (existingStatus !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }
    if (finalStatus !== 'granted') {
      console.warn('Failed to get push token for push notification!');
      return;
    }
    try {
      // A push token is a device-targeting credential — don't put it in a
      // production log where any app with legacy log-read access could
      // harvest it and use it to send notifications to this device.
      token = (await Notifications.getDevicePushTokenAsync()).data;
      debugLog('NotificationService', 'Device push token:', token);
    } catch (e) {
      console.error('Error getting push token, probably running on emulator without play services', e);
    }
    return token;
  },

  async sendLocalNotification(title, body) {
    try {
      await Notifications.scheduleNotificationAsync({
        content: {
          title,
          body,
          sound: true,
        },
        trigger: null,
      });
    } catch (e) {
      console.error('Failed to schedule local notification', e);
    }
  }
};

export default NotificationService;
