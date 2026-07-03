import { createNavigationContainerRef } from '@react-navigation/native';

export const navigationRef = createNavigationContainerRef();

/**
 * Navigate to a route from anywhere in the app (e.g. within axios interceptors)
 * @param {string} name Route name
 * @param {object} [params] Optional route parameters
 */
export function navigate(name, params) {
  if (navigationRef.isReady()) {
    navigationRef.navigate(name, params);
  }
}
