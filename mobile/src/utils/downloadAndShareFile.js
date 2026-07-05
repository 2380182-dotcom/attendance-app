import { Alert } from 'react-native';
import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import api from '../services/api';

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  if (typeof globalThis.btoa === 'function') {
    return globalThis.btoa(binary);
  }
  // Fallback manual base64 encode for environments without a global btoa
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let result = '';
  for (let i = 0; i < binary.length; i += 3) {
    const a = binary.charCodeAt(i);
    const b = i + 1 < binary.length ? binary.charCodeAt(i + 1) : 0;
    const c = i + 2 < binary.length ? binary.charCodeAt(i + 2) : 0;
    result += chars[a >> 2];
    result += chars[((a & 3) << 4) | (b >> 4)];
    result += i + 1 < binary.length ? chars[((b & 15) << 2) | (c >> 6)] : '=';
    result += i + 2 < binary.length ? chars[c & 63] : '=';
  }
  return result;
}

/**
 * Downloads a report/export from the backend using the authenticated axios instance
 * (JWT attached via its request interceptor) and opens the native share sheet.
 *
 * Do not use Linking.openURL() for these endpoints — it hands off to the system
 * browser, which never attaches the app's Authorization header, so the backend's
 * auth filter rejects it and the user sees a raw "Missing or invalid Authorization
 * header" error page instead of their file.
 *
 * @param {string} path - relative API path + query string, e.g. from apiService.reports.*Path()
 * @param {string} filename - suggested filename, e.g. 'attendance_report.xlsx'
 */
export async function downloadAndShareFile(path, filename) {
  try {
    const response = await api.get(path, { responseType: 'arraybuffer' });
    const base64Data = arrayBufferToBase64(response.data);
    const fileUri = `${FileSystem.cacheDirectory}${filename}`;
    await FileSystem.writeAsStringAsync(fileUri, base64Data, {
      encoding: FileSystem.EncodingType.Base64,
    });

    const canShare = await Sharing.isAvailableAsync();
    if (canShare) {
      await Sharing.shareAsync(fileUri);
    } else {
      Alert.alert('Download Complete', `File saved: ${filename}`);
    }
  } catch (error) {
    console.error('Report download failed:', error);
    Alert.alert(
      'Download Failed',
      error.message || 'Could not download the report. Please try again.'
    );
  }
}

export default downloadAndShareFile;
