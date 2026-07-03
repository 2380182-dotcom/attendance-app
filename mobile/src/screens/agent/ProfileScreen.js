import React, { useContext, useEffect, useState } from 'react';
import { StyleSheet, View, Text, TouchableOpacity, Alert, SafeAreaView } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';

export default function ProfileScreen({ navigation }) {
  const { user, logout } = useContext(AuthContext);
  const [totalCheckins, setTotalCheckins] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchProfileData = async () => {
      if (!user?.id) return;
      try {
        const logs = await apiService.attendance.getHistory(user.id);
        setTotalCheckins(logs.length);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchProfileData();
  }, [user]);

  const handleLogout = () => {
    Alert.alert('Confirm Logout', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Log Out', style: 'destructive', onPress: logout }
    ]);
  };

  if (loading) {
    return <Loading message="Loading profile..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.profileCard}>
        <View style={styles.avatarContainer}>
          <MaterialIcons name="account-circle" size={80} color="#1976D2" />
        </View>
        <Text style={styles.name}>{user?.name}</Text>
        <Text style={styles.roleTag}>{user?.role || 'AGENT'}</Text>

        <View style={styles.divider} />

        <View style={styles.infoRow}>
          <MaterialIcons name="badge" size={20} color="#757575" />
          <Text style={styles.infoText}>Agent ID: {user?.agentId}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="business" size={20} color="#757575" />
          <Text style={styles.infoText}>Department: {user?.department || 'Sales'}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="email" size={20} color="#757575" />
          <Text style={styles.infoText}>Email: {user?.email}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="phone" size={20} color="#757575" />
          <Text style={styles.infoText}>Phone: {user?.phone || 'N/A'}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="event-available" size={20} color="#757575" />
          <Text style={styles.infoText}>Total Check-ins: {totalCheckins}</Text>
        </View>

        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <MaterialIcons name="exit-to-app" size={20} color="#fff" />
          <Text style={styles.logoutButtonText}>Log Out</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
    justifyContent: 'center',
    padding: 16,
  },
  profileCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  avatarContainer: {
    marginBottom: 12,
  },
  name: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#333',
  },
  roleTag: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#1976D2',
    backgroundColor: '#E3F2FD',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    marginTop: 6,
  },
  divider: {
    width: '100%',
    height: 1,
    backgroundColor: '#EEEEEE',
    marginVertical: 20,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    marginBottom: 16,
  },
  infoText: {
    fontSize: 14,
    color: '#555',
    marginLeft: 12,
  },
  logoutButton: {
    backgroundColor: '#D32F2F',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    height: 48,
    borderRadius: 8,
    width: '100%',
    marginTop: 20,
  },
  logoutButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    marginLeft: 8,
    fontSize: 15,
  },
});
