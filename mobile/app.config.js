/**
 * Dynamic Expo config — reads environment variables at build time.
 * Replaces static app.json for production-safe configuration.
 */
export default ({ config }) => {
  const isProduction = process.env.EAS_BUILD_PROFILE === 'production';
  const googleMapsKey =
    process.env.EXPO_PUBLIC_GOOGLE_MAPS_KEY || 'YOUR_GOOGLE_MAPS_KEY_HERE';

  return {
    ...config,
    expo: {
      name: 'Attendance System',
      slug: 'attendance-mobile',
      version: '1.0.0',
      orientation: 'portrait',
      userInterfaceStyle: 'light',
      icon: './assets/icon.png',
      splash: {
        backgroundColor: '#ffffff',
      },
      assetBundlePatterns: ['**/*'],
      ios: {
        supportsTablet: true,
        infoPlist: {
          NSLocationAlwaysAndWhenInUseUsageDescription:
            'This app requires background location to auto check in and check out.',
          NSLocationWhenInUseUsageDescription:
            'This app requires location permission to verify check ins.',
          NSCameraUsageDescription:
            'Camera access is required for on-device face verification during check-in.',
          UIBackgroundModes: ['location', 'fetch'],
        },
        bundleIdentifier: 'com.raffaay.attendancemobile',
        config: {
          googleMapsApiKey: googleMapsKey,
        },
      },
      android: {
        package: 'com.raffaay.attendancemobile',
        adaptiveIcon: {
          foregroundImage: './assets/adaptive-icon.png',
          backgroundColor: '#0a1830',
        },
        config: {
          googleMaps: {
            apiKey: googleMapsKey,
          },
        },
        permissions: [
          'ACCESS_COARSE_LOCATION',
          'ACCESS_FINE_LOCATION',
          'ACCESS_BACKGROUND_LOCATION',
          'FOREGROUND_SERVICE',
          'FOREGROUND_SERVICE_LOCATION',
          'CAMERA',
        ],
      },
      web: {
        favicon: './assets/icon.png',
      },
      extra: {
        eas: {
          projectId: '1bd11e5d-3c8c-4d2c-8943-d20cdc112d49',
        },
        apiUrl: process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080/api',
      },
      plugins: [
        [
          'expo-build-properties',
          {
            android: {
              compileSdkVersion: 34,
              targetSdkVersion: 34,
              buildToolsVersion: '34.0.0',
              // Cleartext HTTP only for non-production builds (local dev)
              usesCleartextTraffic: !isProduction,
            },
          },
        ],
        [
          'expo-notifications',
          {
            icon: './assets/notification-icon.png',
            color: '#ffffff',
          },
        ],
        [
          'expo-camera',
          {
            cameraPermission: 'Allow Attendance System to use your camera for face verification.',
          },
        ],
        [
          'expo-location',
          {
            locationAlwaysAndWhenInUsePermission:
              'This app needs background location to automatically record your check-in and check-out at each mart, even when the app is closed.',
            isAndroidBackgroundLocationEnabled: true,
            isIosBackgroundLocationEnabled: true,
          },
        ],
      ],
      owner: 'raffaay1',
    },
  };
};
