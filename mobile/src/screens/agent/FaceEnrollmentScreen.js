import React, { useState, useRef, useContext } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  SafeAreaView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import {
  averageEmbeddings,
  floatArrayToBase64,
} from '../../services/FaceEmbeddingService';
import { extractEmbeddingFromImage } from '../../services/OnDeviceFaceVerification';

const ENROLLMENT_SHOTS = 3;

export default function FaceEnrollmentScreen({ navigation }) {
  const { user } = useContext(AuthContext);
  const [permission, requestPermission] = useCameraPermissions();
  const [step, setStep] = useState(0);
  const [embeddings, setEmbeddings] = useState([]);
  const [capturing, setCapturing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const cameraRef = useRef(null);

  const handleCapture = async () => {
    if (!cameraRef.current || capturing) return;

    if (!permission?.granted) {
      const result = await requestPermission();
      if (!result.granted) {
        Alert.alert('Camera Required', 'Camera access is needed to enroll your face.');
        return;
      }
    }

    setCapturing(true);
    try {
      const photo = await cameraRef.current.takePictureAsync({
        quality: 0.7,
        skipProcessing: true,
      });
      const { embedding } = await extractEmbeddingFromImage(photo.uri);
      const updated = [...embeddings, embedding];
      setEmbeddings(updated);
      setStep(updated.length);

      if (updated.length >= ENROLLMENT_SHOTS) {
        await submitEnrollment(updated);
      }
    } catch (e) {
      Alert.alert('Capture Failed', e.message || 'Could not process face. Try again.');
    } finally {
      setCapturing(false);
    }
  };

  const submitEnrollment = async (collected) => {
    setSubmitting(true);
    try {
      const averaged = averageEmbeddings(collected);
      const base64 = floatArrayToBase64(averaged);
      await apiService.face.saveEmbedding(user.id, base64);
      Alert.alert(
        'Enrollment Complete',
        'Your face profile has been saved. You can now use face verification at check-in.',
        [{ text: 'Continue', onPress: () => navigation.replace('Dashboard') }]
      );
    } catch (e) {
      Alert.alert('Enrollment Failed', e.message || 'Could not save face profile.');
      setEmbeddings([]);
      setStep(0);
    } finally {
      setSubmitting(false);
    }
  };

  const instructions = [
    'Look straight at the camera',
    'Turn your head slightly left',
    'Turn your head slightly right',
  ];

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <MaterialIcons name="face" size={32} color="#1976D2" />
        <Text style={styles.title}>Face Enrollment</Text>
        <Text style={styles.subtitle}>
          Capture {ENROLLMENT_SHOTS} selfies to create your on-device face profile.
          Photos are processed locally and never uploaded.
        </Text>
      </View>

      <View style={styles.progressRow}>
        {[0, 1, 2].map((i) => (
          <View
            key={i}
            style={[styles.progressDot, i < step && styles.progressDotDone, i === step && styles.progressDotActive]}
          />
        ))}
      </View>

      <Text style={styles.instruction}>
        {step < ENROLLMENT_SHOTS ? instructions[step] : 'Processing...'}
      </Text>

      <View style={styles.cameraWrap}>
        {permission?.granted ? (
          <CameraView ref={cameraRef} style={styles.camera} facing="front">
            <View style={styles.overlay}>
              <View style={styles.guideCircle} />
            </View>
          </CameraView>
        ) : (
          <View style={styles.permissionFallback}>
            <Text style={styles.permissionText}>Camera permission required</Text>
            <TouchableOpacity style={styles.captureBtn} onPress={requestPermission}>
              <Text style={styles.captureBtnText}>Enable Camera</Text>
            </TouchableOpacity>
          </View>
        )}
        {(capturing || submitting) && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color="#fff" />
          </View>
        )}
      </View>

      <Text style={styles.stepText}>
        Selfie {Math.min(step + 1, ENROLLMENT_SHOTS)} of {ENROLLMENT_SHOTS}
      </Text>

      {step < ENROLLMENT_SHOTS && permission?.granted && !submitting && (
        <TouchableOpacity
          style={[styles.captureBtn, capturing && styles.captureBtnDisabled]}
          onPress={handleCapture}
          disabled={capturing}
        >
          <MaterialIcons name="camera-alt" size={22} color="#fff" />
          <Text style={styles.captureBtnText}>Capture Selfie</Text>
        </TouchableOpacity>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 20 },
  header: { alignItems: 'center', marginBottom: 16 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#212121', marginTop: 8 },
  subtitle: { fontSize: 13, color: '#757575', textAlign: 'center', marginTop: 8, lineHeight: 20 },
  progressRow: { flexDirection: 'row', justifyContent: 'center', gap: 12, marginBottom: 12 },
  progressDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#CFD8DC',
  },
  progressDotDone: { backgroundColor: '#4CAF50' },
  progressDotActive: { backgroundColor: '#2196F3', transform: [{ scale: 1.3 }] },
  instruction: { textAlign: 'center', fontSize: 15, fontWeight: '600', color: '#424242', marginBottom: 12 },
  cameraWrap: {
    height: 320,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: '#000',
    marginBottom: 12,
  },
  camera: { flex: 1 },
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  guideCircle: {
    width: 220,
    height: 220,
    borderRadius: 110,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.7)',
    borderStyle: 'dashed',
  },
  permissionFallback: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  permissionText: { color: '#fff', marginBottom: 16 },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepText: { textAlign: 'center', color: '#757575', marginBottom: 16 },
  captureBtn: {
    backgroundColor: '#1976D2',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 14,
    borderRadius: 10,
    gap: 8,
  },
  captureBtnDisabled: { opacity: 0.6 },
  captureBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 15 },
});
