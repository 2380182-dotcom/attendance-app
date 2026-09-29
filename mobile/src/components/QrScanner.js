import React, { useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as Location from 'expo-location';
import AppButton from './AppButton';
import LocationService from '../services/LocationService';
import { apiService } from '../services/api';
import { useTheme } from '../theme';

/**
 * QR shop-visit flow (Q3): the camera + scan + submit mechanics only —
 * shared by the LMT and Local scan screens, each of which owns its own
 * result UI (English vs bilingual, and where a successful scan navigates
 * next) so this component stays role-agnostic.
 *
 * On every scan it calls onResult(dto, null) or onResult(null, message) —
 * exactly once, guarded against the camera firing onBarcodeScanned
 * repeatedly for the same code while it's in frame. A rejected scan
 * (invalid code, inactive shop, outside geofence) is a normal dto with
 * that reason in visitStatus, NOT the error path — only a genuine
 * precondition failure (e.g. LMT not checked in, or no network) calls
 * onResult with an error message.
 *
 * Deliberately uses a FRESH GPS reading here (not LocationService's quick/
 * cached lookup used for the nearby-shops list) — this is the actual
 * verification event, mirroring the same "fresh GPS right at save time"
 * pattern RecordVisitScreen/LocalEntryScreen already use for their own
 * submit calls.
 */
export default function QrScanner({
  agentId,
  instructions,
  scanningLabel,
  permissionText = "Camera access is needed to scan a shop's QR code.",
  allowCameraLabel = 'Allow Camera',
  onResult,
}) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [permission, requestPermission] = useCameraPermissions();
  const [submitting, setSubmitting] = useState(false);
  const hasScannedRef = useRef(false);

  const handleBarcodeScanned = async ({ data }) => {
    if (hasScannedRef.current || !data) {
      return;
    }
    hasScannedRef.current = true;
    setSubmitting(true);
    try {
      const status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          onResult(null, 'Location permission is required to verify a shop visit.');
          return;
        }
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const dto = await apiService.lmt.scanShopQr(agentId, data, current.coords.latitude, current.coords.longitude);
      onResult(dto, null);
    } catch (e) {
      console.error(e);
      onResult(null, e.message || 'Could not verify this shop. Try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!permission) {
    return <View style={styles.container} />;
  }

  if (!permission.granted) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Text style={styles.permissionText}>{permissionText}</Text>
        <AppButton title={allowCameraLabel} variant="primary" icon="camera-alt" onPress={requestPermission} style={{ marginTop: 16 }} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <CameraView
        style={StyleSheet.absoluteFillObject}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
        onBarcodeScanned={submitting ? undefined : handleBarcodeScanned}
      />
      <View style={styles.overlay} pointerEvents="none">
        <View style={styles.frame} />
      </View>
      <View style={styles.instructionsBar}>
        {submitting ? (
          <>
            <ActivityIndicator color={colors.textOnPrimary} />
            <Text style={styles.instructionsText}>{scanningLabel || 'Verifying...'}</Text>
          </>
        ) : (
          <Text style={styles.instructionsText}>{instructions}</Text>
        )}
      </View>
    </View>
  );
}

const FRAME_SIZE = 240;

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: '#000000' },
    centered: { alignItems: 'center', justifyContent: 'center', padding: 24 },
    permissionText: { fontSize: 16, color: colors.textPrimary, textAlign: 'center' },
    overlay: { ...StyleSheet.absoluteFillObject, alignItems: 'center', justifyContent: 'center' },
    frame: {
      width: FRAME_SIZE,
      height: FRAME_SIZE,
      borderWidth: 3,
      borderColor: '#FFFFFF',
      borderRadius: 16,
      backgroundColor: 'transparent',
    },
    instructionsBar: {
      position: 'absolute',
      bottom: 0,
      left: 0,
      right: 0,
      backgroundColor: 'rgba(0,0,0,0.7)',
      paddingVertical: 20,
      paddingHorizontal: 16,
      alignItems: 'center',
      flexDirection: 'row',
      justifyContent: 'center',
    },
    instructionsText: { color: '#FFFFFF', fontSize: 16, fontWeight: '600', textAlign: 'center', marginLeft: 10 },
  });
