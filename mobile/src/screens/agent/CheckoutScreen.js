import React, { useState, useEffect, useContext } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  Alert,
  SafeAreaView,
  Linking
} from 'react-native';
import * as Location from 'expo-location';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import LocationService from '../../services/LocationService';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

export default function CheckoutScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const [location, setLocation] = useState(null);
  const [locationLoading, setLocationLoading] = useState(true);
  const [locationPermissionGranted, setLocationPermissionGranted] = useState(true);
  const [currentCheckIn, setCurrentCheckIn] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(true);
  const [faceModalVisible, setFaceModalVisible] = useState(false);
  const [faceConfig, setFaceConfig] = useState(null);
  const [checkingOut, setCheckingOut] = useState(false);

  const blockForLocationPermission = () => {
    setLocationPermissionGranted(false);
    setLocation(null);
    Alert.alert(
      'Location Permission Required',
      'Location access is required to check out. Please enable location permissions in your phone settings.',
      [
        { text: 'Open Settings', onPress: () => Linking.openSettings() },
        { text: 'Cancel', onPress: () => navigation.goBack(), style: 'cancel' },
      ]
    );
  };

  const getGPSLocation = async () => {
    setLocationLoading(true);
    try {
      const status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          blockForLocationPermission();
          return;
        }
      }
      setLocationPermissionGranted(true);

      const currentLoc = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setLocation(currentLoc.coords);
    } catch (e) {
      console.error(e);
      Alert.alert('GPS Error', 'Unable to retrieve current coordinates.');
    } finally {
      setLocationLoading(false);
    }
  };

  const fetchActiveCheckIn = async () => {
    if (!user?.id) return;
    setDetailsLoading(true);
    try {
      const details = await apiService.attendance.getCurrentCheckIn(user.id);
      setCurrentCheckIn(details);
    } catch (e) {
      console.error(e);
      Alert.alert(
        'Check-In Data Not Found',
        'Could not fetch your active check-in details. You may not be checked in.',
        [{ text: 'OK', onPress: () => navigation.goBack() }]
      );
    } finally {
      setDetailsLoading(false);
    }
  };

  useEffect(() => {
    getGPSLocation();
    fetchActiveCheckIn();
    loadFaceConfig();
  }, []);

  const loadFaceConfig = async () => {
    if (!user?.id) return;
    try {
      const status = await apiService.face.getStatus(user.id);
      setFaceConfig(status);
    } catch (e) {
      console.warn('Could not load face config', e);
    }
  };

  const isFaceRequired = () => {
    if (faceConfig) {
      return faceConfig.faceVerificationEnabled !== false && faceConfig.faceVerifyOnCheckOut !== false;
    }
    return user?.faceVerificationEnabled !== false && user?.faceVerifyOnCheckOut !== false;
  };

  const handleCheckOut = async () => {
    if (!locationPermissionGranted) {
      blockForLocationPermission();
      return;
    }
    if (!location) {
      Alert.alert('Location Error', 'Unable to retrieve location coordinates. Please retry.');
      return;
    }

    if (isFaceRequired()) {
      setFaceModalVisible(true);
    } else {
      executeCheckOut(false);
    }
  };

  const executeCheckOut = async (faceVerified = false) => {
    setCheckingOut(true);
    try {
      await apiService.attendance.checkOut(
        user.id,
        location.latitude,
        location.longitude,
        faceVerified
      );

      Alert.alert(
        'Check-Out Success',
        'You have successfully checked out of duty.',
        [{ text: 'Return to Dashboard', onPress: () => navigation.goBack() }]
      );
    } catch (e) {
      console.error(e);
      Alert.alert('Check-Out Failed', e.message || 'Error occurred during check-out.');
    } finally {
      setCheckingOut(false);
    }
  };

  const formatTime = (timeStr) => {
    if (!timeStr) return '--:--';
    try {
      const dateObj = new Date(timeStr);
      return dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return timeStr;
    }
  };

  const formatDate = (timeStr) => {
    if (!timeStr) return '';
    try {
      const dateObj = new Date(timeStr);
      return dateObj.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
    } catch (e) {
      return timeStr;
    }
  };

  if (checkingOut) {
    return <Loading message="Submitting check-out..." fullScreen />;
  }

  if (detailsLoading) {
    return <Loading message="Loading active check-in details..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <AppCard style={styles.card}>
        <View style={styles.header}>
          <MaterialIcons name="store" size={28} color={colors.warning} />
          <Text style={styles.martName}>
            {currentCheckIn?.martName || 'Active Mart'}
          </Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>CHECK-IN DATE</Text>
          <Text style={styles.infoValue}>
            {formatDate(currentCheckIn?.checkInTime)}
          </Text>
        </View>

        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>CHECK-IN TIME</Text>
          <Text style={styles.infoValue}>
            {formatTime(currentCheckIn?.checkInTime)}
          </Text>
        </View>

        {currentCheckIn?.distanceFromMart !== undefined && (
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>CHECK-IN DISTANCE</Text>
            <Text style={styles.infoValue}>
              {currentCheckIn.distanceFromMart > 1000
                ? `${(currentCheckIn.distanceFromMart / 1000).toFixed(1)} km`
                : `${Math.round(currentCheckIn.distanceFromMart)} meters`}
            </Text>
          </View>
        )}

        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>CHECK-IN STATUS</Text>
          <View style={[styles.badge, currentCheckIn?.status === 'LATE' ? styles.badgeLate : styles.badgeIn]}>
            <Text style={[styles.badgeText, currentCheckIn?.status === 'LATE' ? styles.badgeTextLate : styles.badgeTextIn]}>
              {currentCheckIn?.status === 'LATE' ? 'LATE' : 'ON TIME'}
            </Text>
          </View>
        </View>
      </AppCard>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your GPS Checkout Location</Text>
        <TouchableOpacity style={styles.refreshLoc} onPress={getGPSLocation} disabled={locationLoading}>
          <MaterialIcons name="refresh" size={18} color={colors.secondary} />
          <Text style={styles.refreshLocText}>Refresh GPS</Text>
        </TouchableOpacity>
      </View>

      <AppCard style={styles.locationCard} padding={16}>
        {locationLoading ? (
          <Loading message="Locating coordinates..." />
        ) : location ? (
          <View style={styles.coordsRow}>
            <View style={styles.coordCol}>
              <Text style={styles.coordLabel}>LATITUDE</Text>
              <Text style={styles.coordVal}>{location.latitude.toFixed(6)}</Text>
            </View>
            <View style={styles.coordCol}>
              <Text style={styles.coordLabel}>LONGITUDE</Text>
              <Text style={styles.coordVal}>{location.longitude.toFixed(6)}</Text>
            </View>
            <View style={styles.gpsBadge}>
              <MaterialIcons name="gps-fixed" size={16} color={colors.success} />
              <Text style={styles.gpsText}>Active</Text>
            </View>
          </View>
        ) : (
          <Text style={styles.errorText}>No GPS coordinates found.</Text>
        )}
      </AppCard>

      <View style={styles.bottomBar}>
        <AppButton
          title={locationPermissionGranted ? 'CONFIRM CHECK-OUT' : 'LOCATION PERMISSION REQUIRED'}
          onPress={handleCheckOut}
          variant="warning"
          disabled={!location || checkingOut || !locationPermissionGranted}
        />
      </View>

      <FaceVerificationModal
        visible={faceModalVisible}
        onClose={() => setFaceModalVisible(false)}
        onSuccess={() => {
          setFaceModalVisible(false);
          executeCheckOut(true);
        }}
        onFailure={() => {
          setFaceModalVisible(false);
          Alert.alert(
            'Face Verification Failed',
            'Maximum attempts reached. Check-out blocked. Admin has been notified.'
          );
        }}
        agentId={user?.id}
        agentName={user ? user.name : 'Agent'}
        checkpointType="CHECKOUT"
      />
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  card: {
    margin: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  martName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: colors.textPrimary,
    marginLeft: 10,
  },
  divider: {
    height: 1,
    backgroundColor: colors.divider,
    marginBottom: 16,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  infoLabel: {
    fontSize: 11,
    fontWeight: 'bold',
    color: colors.textMuted,
    letterSpacing: 0.5,
  },
  infoValue: {
    fontSize: 14,
    color: colors.textSecondary,
    fontWeight: '500',
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  badgeIn: {
    backgroundColor: colors.successLight,
  },
  badgeLate: {
    backgroundColor: colors.warningLight,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: 'bold',
  },
  badgeTextIn: {
    color: colors.successDark,
  },
  badgeTextLate: {
    color: colors.warningDark,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginHorizontal: 16,
    marginTop: 8,
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: colors.textSecondary,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  refreshLoc: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  refreshLocText: {
    fontSize: 13,
    color: colors.secondary,
    fontWeight: '600',
    marginLeft: 4,
  },
  locationCard: {
    marginHorizontal: 16,
  },
  coordsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  coordCol: {
    flex: 1.5,
  },
  coordLabel: {
    fontSize: 10,
    color: colors.textMuted,
    fontWeight: 'bold',
  },
  coordVal: {
    fontSize: 15,
    fontWeight: 'bold',
    color: colors.textPrimary,
    marginTop: 4,
  },
  gpsBadge: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.successLight,
    paddingVertical: 6,
    paddingHorizontal: 8,
    borderRadius: 8,
  },
  gpsText: {
    marginLeft: 4,
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.successDark,
  },
  errorText: {
    color: colors.error,
    textAlign: 'center',
    fontSize: 14,
  },
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: colors.surface,
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
});
