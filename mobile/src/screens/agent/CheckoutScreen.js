import React, { useState, useEffect, useContext } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  Alert,
  SafeAreaView
} from 'react-native';
import * as Location from 'expo-location';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';

export default function CheckoutScreen({ navigation }) {
  const { user } = useContext(AuthContext);
  const [location, setLocation] = useState(null);
  const [locationLoading, setLocationLoading] = useState(true);
  const [currentCheckIn, setCurrentCheckIn] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(true);
  const [faceModalVisible, setFaceModalVisible] = useState(false);
  const [faceConfig, setFaceConfig] = useState(null);
  const [checkingOut, setCheckingOut] = useState(false);

  const getGPSLocation = async () => {
    setLocationLoading(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert(
          'Location Permission Required',
          'GPS permission is needed to record checkout location.',
          [{ text: 'OK', onPress: () => navigation.goBack() }]
        );
        return;
      }

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
      <View style={styles.card}>
        <View style={styles.header}>
          <MaterialIcons name="store" size={28} color="#FF9800" />
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
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your GPS Checkout Location</Text>
        <TouchableOpacity style={styles.refreshLoc} onPress={getGPSLocation} disabled={locationLoading}>
          <MaterialIcons name="refresh" size={18} color="#2196F3" />
          <Text style={styles.refreshLocText}>Refresh GPS</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.locationCard}>
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
              <MaterialIcons name="gps-fixed" size={16} color="#4CAF50" />
              <Text style={styles.gpsText}>Active</Text>
            </View>
          </View>
        ) : (
          <Text style={styles.errorText}>No GPS coordinates found.</Text>
        )}
      </View>

      <View style={styles.bottomBar}>
        <TouchableOpacity
          style={[styles.checkoutButton, !location && styles.disabledButton]}
          onPress={handleCheckOut}
          disabled={!location || checkingOut}
        >
          <Text style={styles.checkoutButtonText}>CONFIRM CHECK-OUT</Text>
        </TouchableOpacity>
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    margin: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 6,
    elevation: 3,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  martName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginLeft: 10,
  },
  divider: {
    height: 1,
    backgroundColor: '#EEEEEE',
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
    color: '#9E9E9E',
    letterSpacing: 0.5,
  },
  infoValue: {
    fontSize: 14,
    color: '#424242',
    fontWeight: '500',
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  badgeIn: {
    backgroundColor: '#E8F5E9',
  },
  badgeLate: {
    backgroundColor: '#FFF3E0',
  },
  badgeText: {
    fontSize: 11,
    fontWeight: 'bold',
  },
  badgeTextIn: {
    color: '#2E7D32',
  },
  badgeTextLate: {
    color: '#EF6C00',
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
    color: '#424242',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  refreshLoc: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  refreshLocText: {
    fontSize: 13,
    color: '#2196F3',
    fontWeight: '600',
    marginLeft: 4,
  },
  locationCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginHorizontal: 16,
    borderWidth: 1,
    borderColor: '#EEEEEE',
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
    color: '#9E9E9E',
    fontWeight: 'bold',
  },
  coordVal: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 4,
  },
  gpsBadge: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#E8F5E9',
    paddingVertical: 6,
    paddingHorizontal: 8,
    borderRadius: 8,
  },
  gpsText: {
    marginLeft: 4,
    fontSize: 12,
    fontWeight: 'bold',
    color: '#2E7D32',
  },
  errorText: {
    color: '#D32F2F',
    textAlign: 'center',
    fontSize: 14,
  },
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#fff',
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  checkoutButton: {
    backgroundColor: '#FF9800',
    height: 48,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  disabledButton: {
    backgroundColor: '#BDBDBD',
  },
  checkoutButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
});
