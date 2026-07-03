import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  Modal,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  Dimensions,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import config from '../config';
import {
  fetchReferenceEmbedding,
  verifyWithLiveness,
  submitVerificationResult,
  extractEmbeddingFromImage,
} from '../services/OnDeviceFaceVerification';
import { base64ToFloatArray, floatArrayToBase64 } from '../services/FaceEmbeddingService';

const { width } = Dimensions.get('window');

/**
 * On-device face verification modal using ML Kit + expo-camera.
 * Never transmits face images — only verification result + confidence score.
 */
export default function FaceVerificationModal({
  visible,
  onClose,
  onSuccess,
  onFailure,
  agentId,
  agentName = 'Agent',
  checkpointType = 'CHECKIN',
  isRegistration = !agentId,
}) {
  const [permission, requestPermission] = useCameraPermissions();
  const [status, setStatus] = useState('ready'); // ready, capturing, verifying, success, failed
  const [attempts, setAttempts] = useState(0);
  const [message, setMessage] = useState('');
  const [referenceEmbedding, setReferenceEmbedding] = useState(null);
  const cameraRef = useRef(null);
  const maxAttempts = config.FACE_MAX_ATTEMPTS;

  useEffect(() => {
    if (visible) {
      setStatus('ready');
      setAttempts(0);
      setMessage('');
      loadReference();
    }
  }, [visible, agentId, isRegistration]);

  const loadReference = async () => {
    if (isRegistration) {
      console.log("[FaceVerificationModal] Registration mode - skipping reference load.");
      return;
    }
    if (!agentId) return;
    try {
      const embedding = await fetchReferenceEmbedding(agentId);
      const floats = base64ToFloatArray(embedding);
      if (floats.length !== 192) {
        throw new Error('Face profile update required. Please re-enroll your face under Settings.');
      }
      setReferenceEmbedding(embedding);
    } catch (e) {
      const isReenroll = e.message.includes('re-enroll') || e.message.includes('not enrolled');
      setMessage(isReenroll ? 'Face not registered — please contact admin to re-enroll' : e.message);
      setStatus('failed');
    }
  };

  const ensurePermission = async () => {
    if (permission?.granted) return true;
    const result = await requestPermission();
    return result.granted;
  };

  const handleCapture = useCallback(async () => {
    if (!cameraRef.current) return;
    if (!isRegistration && !referenceEmbedding) return;

    const hasPermission = await ensurePermission();
    if (!hasPermission) {
      Alert.alert('Camera Required', 'Camera permission is needed for face verification.');
      return;
    }

    setStatus('capturing');
    setMessage(isRegistration ? 'Capturing face...' : 'Hold still...');

    try {
      if (isRegistration) {
        const photo = await cameraRef.current.takePictureAsync({
          quality: 0.6,
          skipProcessing: true,
        });
        
        setStatus('verifying');
        setMessage('Processing face profile...');
        
        const { embedding } = await extractEmbeddingFromImage(photo.uri);
        const base64 = floatArrayToBase64(embedding);
        
        setStatus('success');
        setMessage('Face registered successfully!');
        
        setTimeout(() => onSuccess(base64), 1200);
      } else {
        const photo1 = await cameraRef.current.takePictureAsync({
          quality: 0.6,
          skipProcessing: true,
        });
        await new Promise((r) => setTimeout(r, 400));
        const photo2 = await cameraRef.current.takePictureAsync({
          quality: 0.6,
          skipProcessing: true,
        });

        setStatus('verifying');
        setMessage('Verifying on device...');

        const result = await verifyWithLiveness(
          photo1.uri,
          photo2.uri,
          referenceEmbedding,
          config.FACE_CONFIDENCE_THRESHOLD
        );

        if (result.verified) {
          await submitVerificationResult(agentId, true, result.confidence, checkpointType);
          setStatus('success');
          setMessage(`Verified (${(result.confidence * 100).toFixed(0)}% match)`);
          setTimeout(() => onSuccess({ confidence: result.confidence }), 1200);
        } else {
          const newAttempts = attempts + 1;
          setAttempts(newAttempts);

          if (newAttempts >= maxAttempts) {
            await submitVerificationResult(agentId, false, result.confidence || 0, checkpointType);
            setStatus('failed');
            setMessage(result.reason || 'Verification failed after maximum attempts.');
            onFailure?.({ confidence: result.confidence, attempts: newAttempts });
          } else {
            setStatus('ready');
            setMessage(
              `${result.reason || 'Verification failed'}. Attempt ${newAttempts}/${maxAttempts}.`
            );
          }
        }
      }
    } catch (e) {
      const newAttempts = attempts + 1;
      setAttempts(newAttempts);
      if (isRegistration || newAttempts >= maxAttempts) {
        if (!isRegistration) {
          await submitVerificationResult(agentId, false, 0, checkpointType).catch(() => {});
        }
        setStatus('failed');
        setMessage(e.message || (isRegistration ? 'Capture failed.' : 'Verification failed.'));
        if (!isRegistration) {
          onFailure?.({ confidence: 0, attempts: newAttempts });
        }
      } else {
        setStatus('ready');
        setMessage(`${e.message}. Attempt ${newAttempts}/${maxAttempts}.`);
      }
    }
  }, [agentId, referenceEmbedding, attempts, checkpointType, onSuccess, onFailure, permission, isRegistration]);

  const renderContent = () => {
    if (!permission?.granted && status === 'ready') {
      return (
        <View style={styles.permissionBox}>
          <MaterialIcons name="camera-alt" size={48} color="#757575" />
          <Text style={styles.permissionText}>Camera access is required for face verification.</Text>
          <TouchableOpacity style={styles.scanBtn} onPress={requestPermission}>
            <Text style={styles.scanBtnText}>Grant Camera Access</Text>
          </TouchableOpacity>
        </View>
      );
    }

    if (status === 'success') {
      return (
        <View style={styles.resultBox}>
          <MaterialIcons name="check-circle" size={80} color="#4CAF50" />
          <Text style={[styles.indicatorText, { color: '#4CAF50' }]}>{message}</Text>
        </View>
      );
    }

    if (status === 'failed') {
      const isCompatibilityError = message && message.includes('re-enroll');
      return (
        <View style={styles.resultBox}>
          <MaterialIcons name="error" size={80} color="#D32F2F" />
          <Text style={[styles.indicatorText, { color: '#D32F2F' }]}>{message}</Text>
          {!isCompatibilityError && <Text style={styles.failNote}>Admin has been notified.</Text>}
        </View>
      );
    }

    return (
      <View style={styles.cameraFrame}>
        {permission?.granted && (
          <CameraView ref={cameraRef} style={styles.camera} facing="front">
            <View style={styles.faceOverlay}>
              <View style={styles.circleBorder} />
            </View>
          </CameraView>
        )}
        {(status === 'capturing' || status === 'verifying') && (
          <View style={styles.processingOverlay}>
            <ActivityIndicator size="large" color="#2196F3" />
            <Text style={styles.processingText}>{message}</Text>
          </View>
        )}
      </View>
    );
  };

  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.container}>
          <Text style={styles.title}>Face Verification</Text>
          <Text style={styles.subtitle}>
            {agentName} · {checkpointType.replace('_', '-')}
          </Text>
          <Text style={styles.privacyNote}>Processed on-device — no photo sent to server</Text>

          {renderContent()}

          <View style={styles.statusSection}>
            {message && status !== 'success' && status !== 'failed' && (
              <Text style={styles.statusText}>{message}</Text>
            )}
            {status === 'ready' && permission?.granted && (referenceEmbedding || isRegistration) && (
              <TouchableOpacity style={styles.scanBtn} onPress={handleCapture}>
                <MaterialIcons name={isRegistration ? "add-a-photo" : "face"} size={20} color="#fff" style={{ marginRight: 6 }} />
                <Text style={styles.scanBtnText}>
                  {isRegistration ? "Register Face" : `Verify Face (${attempts}/${maxAttempts} attempts)`}
                </Text>
              </TouchableOpacity>
            )}
            {(status === 'ready' || status === 'failed') && (
              <TouchableOpacity style={styles.cancelBtn} onPress={onClose}>
                <Text style={styles.cancelBtnText}>{status === 'failed' ? 'Close' : 'Cancel'}</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  container: {
    backgroundColor: '#fff',
    borderRadius: 20,
    padding: 24,
    width: width * 0.9,
    maxWidth: 400,
    alignItems: 'center',
  },
  title: { fontSize: 18, fontWeight: 'bold', color: '#212121' },
  subtitle: { fontSize: 13, color: '#757575', marginTop: 4 },
  privacyNote: { fontSize: 11, color: '#9E9E9E', marginTop: 4, marginBottom: 12 },
  cameraFrame: {
    width: 260,
    height: 260,
    borderRadius: 130,
    overflow: 'hidden',
    backgroundColor: '#ECEFF1',
    position: 'relative',
  },
  camera: { flex: 1 },
  faceOverlay: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  circleBorder: {
    width: 220,
    height: 220,
    borderRadius: 110,
    borderWidth: 3,
    borderColor: 'rgba(33,150,243,0.8)',
    borderStyle: 'dashed',
  },
  processingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(255,255,255,0.85)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  processingText: { marginTop: 12, color: '#616161', fontSize: 13 },
  permissionBox: { alignItems: 'center', padding: 24 },
  permissionText: { textAlign: 'center', color: '#616161', marginVertical: 12 },
  resultBox: { alignItems: 'center', padding: 24 },
  indicatorText: { fontSize: 14, marginTop: 12, textAlign: 'center', fontWeight: '600' },
  failNote: { fontSize: 12, color: '#757575', marginTop: 8 },
  statusSection: { width: '100%', alignItems: 'center', marginTop: 16 },
  scanBtn: {
    backgroundColor: '#1976D2',
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 24,
    borderRadius: 8,
  },
  scanBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 14 },
  statusText: { fontSize: 13, color: '#757575', textAlign: 'center', marginBottom: 12 },
  cancelBtn: { marginTop: 12, paddingVertical: 8, paddingHorizontal: 20 },
  cancelBtnText: { color: '#757575', fontWeight: 'bold', fontSize: 13 },
});
