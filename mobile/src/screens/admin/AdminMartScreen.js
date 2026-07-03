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

export default function AdminMartScreen() {
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

  const handleDelete = (id) => {
    Alert.alert('Confirm Delete', 'Are you sure you want to delete this Mart?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          setLoading(true);
          try {
            await apiService.admin.deleteMart(id);
            Alert.alert('Success', 'Mart deleted.');
            fetchMarts();
          } catch (e) {
            Alert.alert('Error', 'Failed to delete mart.');
            setLoading(false);
          }
        }
      }
    ]);
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
        <TouchableOpacity style={styles.addButton} onPress={handleOpenAdd}>
          <MaterialIcons name="add" size={20} color="#fff" />
          <Text style={styles.addButtonText}>Add Mart</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={marts}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => (
          <View style={styles.martCard}>
            <View style={styles.martInfo}>
              <Text style={styles.martName}>{item.name}</Text>
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
                  trackColor={{ false: '#767577', true: '#90CAF9' }}
                  thumbColor={item.geoFencingEnabled ? '#1976D2' : '#f4f3f4'}
                />
              </View>

              <View style={styles.buttonRow}>
                <TouchableOpacity style={styles.actionBtn} onPress={() => handleOpenEdit(item)}>
                  <MaterialIcons name="edit" size={18} color="#1976D2" />
                </TouchableOpacity>
                <TouchableOpacity style={[styles.actionBtn, styles.deleteBtn]} onPress={() => handleDelete(item.id)}>
                  <MaterialIcons name="delete" size={18} color="#D32F2F" />
                </TouchableOpacity>
              </View>
            </View>
          </View>
        )}
        contentContainerStyle={styles.list}
      />

      <Modal visible={modalVisible} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>{editingMart ? 'Edit Mart' : 'Add New Mart'}</Text>

            <Text style={styles.label}>Name *</Text>
            <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="e.g. Metro Mart" />

            <Text style={styles.label}>Address</Text>
            <TextInput style={styles.input} value={address} onChangeText={setAddress} placeholder="e.g. 5th Avenue" />

            <TouchableOpacity 
              style={[styles.locationBtn, fetchingLocation && { opacity: 0.7 }]} 
              onPress={handleGetCurrentLocation}
              disabled={fetchingLocation}
            >
              <MaterialIcons name="my-location" size={16} color="#fff" style={{ marginRight: 6 }} />
              <Text style={styles.locationBtnText}>
                {fetchingLocation ? 'Fetching Location...' : 'Use Current GPS Location'}
              </Text>
            </TouchableOpacity>

            <Text style={styles.label}>Latitude *</Text>
            <TextInput style={styles.input} value={latitude} onChangeText={setLatitude} keyboardType="numeric" placeholder="e.g. 31.5204" />

            <Text style={styles.label}>Longitude *</Text>
            <TextInput style={styles.input} value={longitude} onChangeText={setLongitude} keyboardType="numeric" placeholder="e.g. 74.3587" />

            <Text style={styles.label}>Radius (meters) *</Text>
            <TextInput style={styles.input} value={radius} onChangeText={setRadius} keyboardType="numeric" placeholder="e.g. 100" />

            <View style={styles.modalButtons}>
              <TouchableOpacity style={styles.cancelBtn} onPress={() => setModalVisible(false)}>
                <Text style={styles.cancelBtnText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.saveBtn} onPress={handleSave}>
                <Text style={styles.saveBtnText}>Save</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  title: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
  },
  addButton: {
    backgroundColor: '#1976D2',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
  },
  addButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    marginLeft: 4,
    fontSize: 12,
  },
  list: {
    padding: 16,
  },
  martCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: '#EEEEEE',
    shadowColor: '#000',
    shadowOpacity: 0.03,
    shadowRadius: 2,
    elevation: 1,
  },
  martInfo: {
    flex: 1,
    paddingRight: 8,
  },
  martName: {
    fontWeight: 'bold',
    fontSize: 15,
    color: '#333',
  },
  martAddress: {
    fontSize: 12,
    color: '#757575',
    marginTop: 4,
  },
  martCoords: {
    fontSize: 11,
    color: '#9E9E9E',
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
    color: '#757575',
    marginRight: 6,
  },
  buttonRow: {
    flexDirection: 'row',
    marginTop: 8,
  },
  actionBtn: {
    padding: 6,
    borderRadius: 6,
    backgroundColor: '#E3F2FD',
    marginLeft: 8,
  },
  deleteBtn: {
    backgroundColor: '#FFEBEE',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    width: '85%',
    maxWidth: 400,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    color: '#333',
    textAlign: 'center',
  },
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#666',
    marginBottom: 4,
    marginTop: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    paddingHorizontal: 12,
    height: 40,
    backgroundColor: '#FAFAFA',
    fontSize: 14,
    color: '#333',
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 20,
  },
  cancelBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#ECEFF1',
    alignItems: 'center',
    marginRight: 10,
  },
  cancelBtnText: {
    fontWeight: 'bold',
    color: '#607D8B',
  },
  saveBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#1976D2',
    alignItems: 'center',
    marginLeft: 10,
  },
  saveBtnText: {
    fontWeight: 'bold',
    color: '#fff',
  },
  locationBtn: {
    backgroundColor: '#2E7D32',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 8,
    marginTop: 12,
    marginBottom: 4,
    elevation: 1,
  },
  locationBtnText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 13,
  },
});
