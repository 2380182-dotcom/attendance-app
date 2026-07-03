import React, { useState, useEffect, useContext } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  FlatList,
  Alert,
  SafeAreaView
} from 'react-native';
import * as Location from 'expo-location';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';

export default function CheckinScreen({ navigation }) {
  const { user } = useContext(AuthContext);
  const [location, setLocation] = useState(null);
  const [locationLoading, setLocationLoading] = useState(true);
  const [marts, setMarts] = useState([]);
  const [selectedMart, setSelectedMart] = useState(null);
  const [checkingIn, setCheckingIn] = useState(false);
  const [martsLoading, setMartsLoading] = useState(true);
  const [faceModalVisible, setFaceModalVisible] = useState(false);
  const [faceVerified, setFaceVerified] = useState(false);
  const [faceConfig, setFaceConfig] = useState(null);

  const calculateDistance = (lat1, lon1, lat2, lon2) => {
    const R = 6371; 
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c * 1000;
  };

  const getGPSLocation = async () => {
    setLocationLoading(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert(
          'Location Permission Required',
          'GPS permission is needed to verify your check-in distance from marts.',
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
      Alert.alert('GPS Error', 'Unable to retrieve current coordinates. Make sure location services are enabled.');
    } finally {
      setLocationLoading(false);
    }
  };

  const fetchMarts = async () => {
    setMartsLoading(true);
    try {
      const allMarts = await apiService.marts.getAll();
      setMarts(allMarts);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to retrieve marts list from backend.');
    } finally {
      setMartsLoading(false);
    }
  };

  useEffect(() => {
    getGPSLocation();
    fetchMarts();
    loadFaceConfig();
  }, []);

  const loadFaceConfig = async () => {
    if (!user?.id) return;
    try {
      const status = await apiService.face.getStatus(user.id);
      setFaceConfig(status);
      if (!status.registered && status.faceVerificationEnabled !== false) {
        Alert.alert(
          'Face Enrollment Required',
          'Please enroll your face before checking in.',
          [{ text: 'Enroll Now', onPress: () => navigation.replace('FaceEnrollment') }]
        );
      }
    } catch (e) {
      console.warn('Could not load face config', e);
    }
  };

  const isFaceRequired = () => {
    if (faceConfig) {
      return faceConfig.faceVerificationEnabled !== false && faceConfig.faceVerifyOnCheckIn !== false;
    }
    return user?.faceVerificationEnabled !== false && user?.faceVerifyOnCheckIn !== false;
  };

  const processedMarts = marts.map((mart) => {
    let distance = null;
    if (location) {
      distance = calculateDistance(
        location.latitude,
        location.longitude,
        mart.latitude,
        mart.longitude
      );
    }
    return { ...mart, distance };
  }).sort((a, b) => {
    if (a.distance === null) return 1;
    if (b.distance === null) return -1;
    return a.distance - b.distance;
  });

  const handleCheckIn = async () => {
    if (!selectedMart) {
      Alert.alert('Required Selection', 'Please select a mart to check-in.');
      return;
    }
    if (!location) {
      Alert.alert('Location Error', 'Still waiting for coordinates. Please refresh location.');
      return;
    }

    if (isFaceRequired()) {
      setFaceVerified(false);
      setFaceModalVisible(true);
    } else {
      executeCheckIn(false);
    }
  };

  const executeCheckIn = async (verified = faceVerified) => {
    setCheckingIn(true);
    try {
      const result = await apiService.attendance.checkIn(
        user.id,
        selectedMart.id,
        location.latitude,
        location.longitude,
        verified
      );
      
      const isLate = result.status === 'LATE';
      const msg = isLate 
        ? `You checked in successfully, but were marked as LATE (Distance: ${Math.round(result.distanceFromMart)}m from mart).`
        : `Check-in successful at ${selectedMart.name}.`;

      Alert.alert(
        isLate ? 'Checked In (Late)' : 'Checked In Successfully',
        msg,
        [{ text: 'Return to Dashboard', onPress: () => navigation.goBack() }]
      );
    } catch (e) {
      console.error(e);
      Alert.alert('Check-In Failed', e.message || 'Error occurred during check-in.');
    } finally {
      setCheckingIn(false);
    }
  };

  if (checkingIn) {
    return <Loading message="Submitting check-in..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your GPS Location</Text>
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

      <Text style={[styles.sectionTitle, { marginHorizontal: 16, marginTop: 12 }]}>
        Select Mart (Nearest First)
      </Text>

      {martsLoading ? (
        <Loading message="Loading marts..." />
      ) : (
        <FlatList
          data={processedMarts}
          keyExtractor={(item) => item.id.toString()}
          renderItem={({ item }) => {
            const isSelected = selectedMart?.id === item.id;
            return (
              <TouchableOpacity
                style={[styles.martItem, isSelected && styles.selectedMartItem]}
                onPress={() => setSelectedMart(item)}
              >
                <View style={styles.martRow}>
                  <View style={styles.martInfo}>
                    <Text style={[styles.martName, isSelected && styles.selectedText]}>{item.name}</Text>
                    <Text style={styles.martAddress} numberOfLines={1}>{item.address}</Text>
                  </View>
                  <View style={styles.martDistanceContainer}>
                    {item.distance !== null ? (
                      <Text style={[styles.distanceText, isSelected && styles.selectedText]}>
                        {item.distance > 1000
                          ? `${(item.distance / 1000).toFixed(1)} km`
                          : `${Math.round(item.distance)} m`}
                      </Text>
                    ) : (
                      <Text style={styles.distanceText}>--</Text>
                    )}
                    <MaterialIcons 
                      name={isSelected ? "radio-button-checked" : "radio-button-unchecked"} 
                      size={20} 
                      color={isSelected ? "#2196F3" : "#BDBDBD"} 
                      style={{ marginTop: 4 }}
                    />
                  </View>
                </View>
              </TouchableOpacity>
            );
          }}
          ListEmptyComponent={
            <Text style={styles.emptyText}>No marts available to display.</Text>
          }
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 80 }}
        />
      )}

      <View style={styles.bottomBar}>
        <TouchableOpacity
          style={[styles.checkInButton, !selectedMart && styles.disabledButton]}
          onPress={handleCheckIn}
          disabled={!selectedMart || checkingIn}
        >
          <Text style={styles.checkInButtonText}>CONFIRM CHECK-IN</Text>
        </TouchableOpacity>
      </View>

      <FaceVerificationModal
        visible={faceModalVisible}
        onClose={() => setFaceModalVisible(false)}
        onSuccess={({ confidence }) => {
          setFaceModalVisible(false);
          setFaceVerified(true);
          executeCheckIn(true);
        }}
        onFailure={() => {
          setFaceModalVisible(false);
          Alert.alert(
            'Face Verification Failed',
            'Maximum attempts reached. Check-in blocked. Admin has been notified.'
          );
        }}
        agentId={user?.id}
        agentName={user ? user.name : 'Agent'}
        checkpointType="CHECKIN"
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginHorizontal: 16,
    marginTop: 16,
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 14,
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
    marginBottom: 12,
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
  martItem: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginVertical: 6,
    borderWidth: 1.5,
    borderColor: '#EEEEEE',
  },
  selectedMartItem: {
    borderColor: '#2196F3',
    backgroundColor: '#E3F2FD',
  },
  martRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  martInfo: {
    flex: 3,
    paddingRight: 8,
  },
  martName: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#212121',
  },
  selectedText: {
    color: '#1565C0',
  },
  martAddress: {
    fontSize: 12,
    color: '#757575',
    marginTop: 4,
  },
  martDistanceContainer: {
    flex: 1,
    alignItems: 'flex-end',
  },
  distanceText: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#666',
  },
  emptyText: {
    textAlign: 'center',
    color: '#757575',
    marginTop: 40,
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
  checkInButton: {
    backgroundColor: '#2196F3',
    height: 48,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  disabledButton: {
    backgroundColor: '#BDBDBD',
  },
  checkInButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
});
