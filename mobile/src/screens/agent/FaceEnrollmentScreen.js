import React, { useState, useRef, useContext } from 'react';
import {
  StyleSheet,
  View,
  Text,
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
  checkLiveness,
} from '../../services/FaceEmbeddingService';
import { extractEmbeddingFromImage } from '../../services/OnDeviceFaceVerification';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

const ENROLLMENT_SHOTS = 3;

export default function FaceEnrollmentScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const [permission, requestPermission] = useCameraPermissions();
  const [step, setStep] = useState(0);
  const [embeddings, setEmbeddings] = useState([]);
  const [faces, setFaces] = useState([]);
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
      const { embedding, face } = await extractEmbeddingFromImage(photo.uri);
      
      // Perform liveness check
      if (step === 0) {
        const liveness = checkLiveness(face);
        if (!liveness.passed) {
          Alert.alert('Liveness Check Failed', liveness.reason);
          return;
        }
        setFaces([face]);
      } else {
        const firstFace = faces[0];
        const liveness = checkLiveness(face, firstFace);
        if (!liveness.passed) {
          Alert.alert('Liveness Check Failed', liveness.reason);
          return;
        }
        setFaces([...faces, face]);
      }

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
      setFaces([]);
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
        <MaterialIcons name="face" size={32} color={colors.secondary} />
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
            <AppButton
              title="Enable Camera"
              onPress={requestPermission}
              variant="secondary"
              fullWidth={false}
            />
          </View>
        )}
        {(capturing || submitting) && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color={colors.white} />
          </View>
        )}
      </View>

      <Text style={styles.stepText}>
        Selfie {Math.min(step + 1, ENROLLMENT_SHOTS)} of {ENROLLMENT_SHOTS}
      </Text>

      {step < ENROLLMENT_SHOTS && permission?.granted && !submitting && (
        <AppButton
          title="Capture Selfie"
          onPress={handleCapture}
          variant="secondary"
          icon="camera-alt"
          disabled={capturing}
        />
      )}
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background, padding: 20 },
  header: { alignItems: 'center', marginBottom: 16 },
  title: { fontSize: 22, fontWeight: 'bold', color: colors.textPrimary, marginTop: 8 },
  subtitle: { fontSize: 13, color: colors.textSecondary, textAlign: 'center', marginTop: 8, lineHeight: 20 },
  progressRow: { flexDirection: 'row', justifyContent: 'center', gap: 12, marginBottom: 12 },
  progressDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: colors.border,
  },
  progressDotDone: { backgroundColor: colors.success },
  progressDotActive: { backgroundColor: colors.secondary, transform: [{ scale: 1.3 }] },
  instruction: { textAlign: 'center', fontSize: 15, fontWeight: '600', color: colors.textSecondary, marginBottom: 12 },
  cameraWrap: {
    height: 320,
    borderRadius: 16,
    overflow: 'hidden',
    // Always black: this is the live camera viewfinder's backdrop, not a themed
    // app surface, so it stays the same in light and dark mode.
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
    // Overlay drawn on top of the live camera feed, not app chrome — stays white regardless of theme.
    borderColor: 'rgba(255,255,255,0.7)',
    borderStyle: 'dashed',
  },
  permissionFallback: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  permissionText: { color: colors.white, marginBottom: 16 },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    // Dimmer over the camera feed, not app chrome — stays black regardless of theme.
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepText: { textAlign: 'center', color: colors.textSecondary, marginBottom: 16 },
});
