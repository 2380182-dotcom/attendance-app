import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import MapView, { Marker, Circle } from 'react-native-maps';
import RNPickerSelect from 'react-native-picker-select';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

export default function AdminGeoFenceScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const pickerStyles = createPickerStyles(colors);
  const mapRef = useRef(null);
  const [marts, setMarts] = useState([]);
  const [selectedMartId, setSelectedMartId] = useState(null);
  const [selectedMart, setSelectedMart] = useState(null);
  const [radius, setRadius] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [region, setRegion] = useState({
    latitude: 31.5204,
    longitude: 74.3587,
    latitudeDelta: 0.01,
    longitudeDelta: 0.01
  });

  const fetchMarts = useCallback(async () => {
    try {
      const data = await apiService.admin.getMarts();
      setMarts(data);
      if (data.length > 0) {
        setSelectedMartId(data[0].id);
        setSelectedMart(data[0]);
        setRadius(data[0].radius.toString());
        const initialRegion = {
          latitude: parseFloat(data[0].latitude),
          longitude: parseFloat(data[0].longitude),
          latitudeDelta: 0.01,
          longitudeDelta: 0.01
        };
        setRegion(initialRegion);
        if (mapRef.current) {
          mapRef.current.animateToRegion(initialRegion, 1000);
        }
      }
    } catch (e) {
      Alert.alert('Error', 'Unable to fetch marts.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMarts();
  }, [fetchMarts]);

  const handleMartChange = (value) => {
    if (!value) return;
    setSelectedMartId(value);
    const mart = marts.find(m => String(m.id) === String(value));
    if (mart) {
      setSelectedMart(mart);
      setRadius(mart.radius.toString());
      const newRegion = {
        latitude: parseFloat(mart.latitude),
        longitude: parseFloat(mart.longitude),
        latitudeDelta: 0.01,
        longitudeDelta: 0.01
      };
      setRegion(newRegion);
      if (mapRef.current) {
        mapRef.current.animateToRegion(newRegion, 1000);
      }
    }
  };

  const handleSaveRadius = async () => {
    if (!selectedMart) return;
    const rVal = parseFloat(radius);
    if (isNaN(rVal) || rVal <= 0) {
      Alert.alert('Validation Error', 'Please enter a valid radius in meters.');
      return;
    }

    setSaving(true);
    try {
      const updated = await apiService.admin.updateMart(selectedMart.id, {
        ...selectedMart,
        radius: rVal
      });
      setMarts(prev => prev.map(m => m.id === selectedMart.id ? updated : m));
      setSelectedMart(updated);
      Alert.alert('Success', 'Geofence radius updated.');
    } catch (e) {
      Alert.alert('Error', 'Failed to update geofence radius.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Loading message="Loading geofence configurations..." fullScreen />;
  }

  const pickerItems = marts.map(m => ({ label: m.name, value: m.id }));

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.selectorCard}>
        <Text style={styles.label}>Select Mart to Tune</Text>
        <View style={styles.pickerWrapper}>
          {pickerItems.length > 0 && (
            <RNPickerSelect
              onValueChange={handleMartChange}
              value={selectedMartId}
              items={pickerItems}
              placeholder={{}}
              style={pickerStyles}
            />
          )}
        </View>

        {selectedMart && (
          <View style={styles.radiusRow}>
            <View style={styles.inputWrapper}>
              <Text style={styles.radiusLabel}>Radius (meters)</Text>
              <TextInput
                style={styles.radiusInput}
                value={radius}
                onChangeText={setRadius}
                keyboardType="numeric"
              />
            </View>
            <AppButton
              title={saving ? 'Saving...' : 'Save'}
              onPress={handleSaveRadius}
              disabled={saving}
              variant="secondary"
              icon="save"
              fullWidth={false}
              style={{ height: 40 }}
            />
          </View>
        )}
      </View>

      {selectedMart && (
        <View style={styles.mapWrapper}>
          <MapView ref={mapRef} style={styles.map} region={region}>
            <Marker
              coordinate={{ latitude: parseFloat(selectedMart.latitude), longitude: parseFloat(selectedMart.longitude) }}
              title={selectedMart.name}
              description={selectedMart.address}
            />
            <Circle
              center={{ latitude: parseFloat(selectedMart.latitude), longitude: parseFloat(selectedMart.longitude) }}
              radius={parseFloat(radius) || 100}
              fillColor={`${colors.secondary}26`}
              strokeColor={colors.secondary}
              strokeWidth={2}
            />
          </MapView>
        </View>
      )}
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  selectorCard: {
    backgroundColor: colors.surface,
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    elevation: 3,
    shadowColor: colors.shadow,
    shadowOpacity: 0.1,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 2 },
  },
  label: {
    fontSize: 11,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  pickerWrapper: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    backgroundColor: colors.inputBackground,
    marginBottom: 12,
  },
  radiusRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  inputWrapper: {
    flex: 1,
    marginRight: 16,
  },
  radiusLabel: {
    fontSize: 11,
    color: colors.textSecondary,
    marginBottom: 4,
  },
  radiusInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    height: 40,
    backgroundColor: colors.inputBackground,
    fontSize: 14,
    color: colors.textPrimary,
  },
  mapWrapper: {
    flex: 1,
  },
  map: {
    ...StyleSheet.absoluteFillObject,
  },
});

const createPickerStyles = (colors) => ({
  inputIOS: {
    fontSize: 14,
    paddingVertical: 10,
    paddingHorizontal: 12,
    color: colors.textPrimary,
  },
  inputAndroid: {
    fontSize: 14,
    paddingHorizontal: 12,
    paddingVertical: 6,
    color: colors.textPrimary,
  },
});
