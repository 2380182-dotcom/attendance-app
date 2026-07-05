import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Switch,
  Modal,
  TextInput,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import * as Location from 'expo-location';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

export default function AdminMartScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [marts, setMarts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingMart, setEditingMart] = useState(null);

  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [radius, setRadius] = useState('');
  const [fetchingLocation, setFetchingLocation] = useState(false);

  const handleGetCurrentLocation = async () => {
    setFetchingLocation(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert('Permission Denied', 'Location permission is required to fetch current coordinates.');
        return;
      }
      const loc = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setLatitude(loc.coords.latitude.toString());
      setLongitude(loc.coords.longitude.toString());
      Alert.alert('Location Fetched', 'Current coordinates successfully loaded.');
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Failed to fetch current location.');
    } finally {
      setFetchingLocation(false);
    }
  };

  const fetchMarts = useCallback(async () => {
    try {
      const data = await apiService.admin.getMarts();
      setMarts(data);
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Unable to retrieve marts.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMarts();
  }, [fetchMarts]);

  const handleOpenAdd = () => {
    setEditingMart(null);
    setName('');
    setAddress('');
    setLatitude('');
    setLongitude('');
    setRadius('100');
    setModalVisible(true);
  };

  const handleOpenEdit = (mart) => {
    setEditingMart(mart);
    setName(mart.name);
    setAddress(mart.address || '');
    setLatitude(mart.latitude.toString());
    setLongitude(mart.longitude.toString());
    setRadius(mart.radius.toString());
    setModalVisible(true);
  };

  const handleSave = async () => {
    if (!name.trim() || !latitude.trim() || !longitude.trim() || !radius.trim()) {
      Alert.alert('Validation Error', 'Please fill all required fields.');
      return;
    }

    const payload = {
      name: name.trim(),
      address: address.trim(),
      latitude: parseFloat(latitude),
      longitude: parseFloat(longitude),
      radius: parseFloat(radius)
    };

    setLoading(true);
    try {
      if (editingMart) {
        await apiService.admin.updateMart(editingMart.id, payload);
        Alert.alert('Success', 'Mart updated successfully.');
      } else {
        await apiService.admin.createMart(payload);
        Alert.alert('Success', 'Mart created successfully.');
      }
      setModalVisible(false);
      fetchMarts();
    } catch (e) {
      Alert.alert('Error', e.message || 'Failed to save mart.');
      setLoading(false);
    }
  };

  const handleDelete = (mart) => {
    Alert.alert(
      'Confirm Delete',
      `Delete ${mart.name}? Historical attendance and sales records will be preserved, but this mart will no longer be available for check-ins.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            setLoading(true);
            try {
              await apiService.admin.deleteMart(mart.id);
              Alert.alert('Success', 'Mart deleted. It can be reactivated later if needed.');
              fetchMarts();
            } catch (e) {
              Alert.alert('Error', 'Failed to delete mart.');
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const handleReactivate = (mart) => {
    Alert.alert(
      'Reactivate Mart',
      `Reactivate ${mart.name}? It will become available for check-ins and geofencing again.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reactivate',
          onPress: async () => {
            setLoading(true);
            try {
              await apiService.admin.reactivateMart(mart.id);
              Alert.alert('Success', 'Mart reactivated.');
              fetchMarts();
            } catch (e) {
              Alert.alert('Error', 'Failed to reactivate mart.');
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const handleToggleGeo = async (id, enabled) => {
    try {
      await apiService.admin.toggleGeoFence(id, enabled);
      setMarts(prev => prev.map(m => m.id === id ? { ...m, geoFencingEnabled: enabled } : m));
    } catch (e) {
      Alert.alert('Error', 'Failed to toggle geo-fence.');
    }
  };

  if (loading && marts.length === 0) {
    return <Loading message="Loading marts..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>All Mart Locations</Text>
        <AppButton
          title="Add Mart"
          onPress={handleOpenAdd}
          variant="secondary"
          size="sm"
          icon="add"
          fullWidth={false}
        />
      </View>

      <FlatList
        data={marts}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => {
          const isInactive = item.isActive === false;
          return (
            <AppCard style={[styles.martCard, isInactive && styles.martCardInactive]} padding={14}>
              <View style={styles.martCardRow}>
                <View style={styles.martInfo}>
                  <View style={styles.martNameRow}>
                    <Text style={styles.martName}>{item.name}</Text>
                    {isInactive && (
                      <View style={styles.inactiveBadge}>
                        <Text style={styles.inactiveBadgeText}>DELETED</Text>
                      </View>
                    )}
                  </View>
                  <Text style={styles.martAddress}>{item.address || 'No address'}</Text>
                  <Text style={styles.martCoords}>
                    Lat: {item.latitude} | Lon: {item.longitude} | Radius: {item.radius}m
                  </Text>
                </View>

                <View style={styles.cardActions}>
                  <View style={styles.switchRow}>
                    <Text style={styles.switchLabel}>Geofence</Text>
                    <Switch
                      value={item.geoFencingEnabled}
                      onValueChange={(val) => handleToggleGeo(item.id, val)}
                      trackColor={{ false: colors.border, true: colors.secondaryLight }}
                      thumbColor={item.geoFencingEnabled ? colors.secondary : colors.surface}
                      disabled={isInactive}
                    />
                  </View>

                  <View style={styles.buttonRow}>
                    <TouchableOpacity style={styles.actionBtn} onPress={() => handleOpenEdit(item)}>
                      <MaterialIcons name="edit" size={18} color={colors.secondary} />
                    </TouchableOpacity>
                    {isInactive ? (
                      <TouchableOpacity style={[styles.actionBtn, styles.reactivateBtn]} onPress={() => handleReactivate(item)}>
                        <MaterialIcons name="restore" size={18} color={colors.successDark} />
                      </TouchableOpacity>
                    ) : (
                      <TouchableOpacity style={[styles.actionBtn, styles.deleteBtn]} onPress={() => handleDelete(item)}>
                        <MaterialIcons name="delete" size={18} color={colors.error} />
                      </TouchableOpacity>
                    )}
                  </View>
                </View>
              </View>
            </AppCard>
          );
        }}
        contentContainerStyle={styles.list}
      />

      <Modal visible={modalVisible} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <AppCard style={styles.modalContent}>
            <Text style={styles.modalTitle}>{editingMart ? 'Edit Mart' : 'Add New Mart'}</Text>

            <Text style={styles.label}>Name *</Text>
            <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="e.g. Metro Mart" placeholderTextColor={colors.textMuted} />

            <Text style={styles.label}>Address</Text>
            <TextInput style={styles.input} value={address} onChangeText={setAddress} placeholder="e.g. 5th Avenue" placeholderTextColor={colors.textMuted} />

            <AppButton
              title={fetchingLocation ? 'Fetching Location...' : 'Use Current GPS Location'}
              onPress={handleGetCurrentLocation}
              disabled={fetchingLocation}
              variant="success"
              icon="my-location"
              style={{ marginTop: 12, marginBottom: 4 }}
            />

            <Text style={styles.label}>Latitude *</Text>
            <TextInput style={styles.input} value={latitude} onChangeText={setLatitude} keyboardType="numeric" placeholder="e.g. 31.5204" placeholderTextColor={colors.textMuted} />

            <Text style={styles.label}>Longitude *</Text>
            <TextInput style={styles.input} value={longitude} onChangeText={setLongitude} keyboardType="numeric" placeholder="e.g. 74.3587" placeholderTextColor={colors.textMuted} />

            <Text style={styles.label}>Radius (meters) *</Text>
            <TextInput style={styles.input} value={radius} onChangeText={setRadius} keyboardType="numeric" placeholder="e.g. 100" placeholderTextColor={colors.textMuted} />

            <View style={styles.modalButtons}>
              <AppButton
                title="Cancel"
                onPress={() => setModalVisible(false)}
                variant="ghost"
                style={{ flex: 1, marginRight: 10 }}
              />
              <AppButton
                title="Save"
                onPress={handleSave}
                variant="secondary"
                style={{ flex: 1, marginLeft: 10 }}
              />
            </View>
          </AppCard>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: {
    fontSize: 16,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  list: {
    padding: 16,
  },
  martCard: {
    marginBottom: 12,
  },
  martCardInactive: {
    opacity: 0.7,
  },
  martCardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  martInfo: {
    flex: 1,
    paddingRight: 8,
  },
  martNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  martName: {
    fontWeight: 'bold',
    fontSize: 15,
    color: colors.textPrimary,
  },
  inactiveBadge: {
    backgroundColor: colors.error,
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    marginLeft: 8,
  },
  inactiveBadgeText: {
    color: colors.textOnPrimary,
    fontSize: 9,
    fontWeight: 'bold',
    letterSpacing: 0.5,
  },
  martAddress: {
    fontSize: 12,
    color: colors.textSecondary,
    marginTop: 4,
  },
  martCoords: {
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 6,
  },
  cardActions: {
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  switchLabel: {
    fontSize: 10,
    color: colors.textSecondary,
    marginRight: 6,
  },
  buttonRow: {
    flexDirection: 'row',
    marginTop: 8,
  },
  actionBtn: {
    padding: 6,
    borderRadius: 6,
    backgroundColor: colors.secondaryLight,
    marginLeft: 8,
  },
  deleteBtn: {
    backgroundColor: colors.errorLight,
  },
  reactivateBtn: {
    backgroundColor: colors.successLight,
  },
  modalOverlay: {
    flex: 1,
    // Modal dimmer scrim, not a themed surface — stays dark regardless of theme.
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    width: '85%',
    maxWidth: 400,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginBottom: 4,
    marginTop: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    height: 40,
    backgroundColor: colors.inputBackground,
    fontSize: 14,
    color: colors.textPrimary,
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 20,
  },
});
