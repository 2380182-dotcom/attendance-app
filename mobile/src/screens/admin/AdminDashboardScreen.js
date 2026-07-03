import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import { AuthContext } from '../../context/AuthContext';

export default function AdminDashboardScreen({ navigation }) {
  const { logout } = useContext(AuthContext);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchStats = useCallback(async () => {
    try {
      const data = await apiService.admin.getStats();
      setStats(data);
    } catch (e) {
      console.error(e);
      Alert.alert('Stats Error', 'Unable to fetch administrative stats.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchStats();
  };

  const handleLogout = () => {
    Alert.alert('Confirm Logout', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Log Out', style: 'destructive', onPress: logout }
    ]);
  };

  if (loading && !refreshing) {
    return <Loading message="Loading Admin panel..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Admin Console</Text>
        <TouchableOpacity onPress={handleLogout} style={styles.logoutBtn}>
          <MaterialIcons name="exit-to-app" size={24} color="#fff" />
        </TouchableOpacity>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} color="#1976D2" />
        }
      >
        <Text style={styles.sectionTitle}>System Statistics</Text>
        <View style={styles.grid}>
          <View style={styles.statBox}>
            <MaterialIcons name="people" size={28} color="#1976D2" />
            <Text style={styles.statVal}>{stats?.totalAgents || 0}</Text>
            <Text style={styles.statLabel}>Total Agents</Text>
          </View>
          <View style={styles.statBox}>
            <MaterialIcons name="person-pin" size={28} color="#4CAF50" />
            <Text style={styles.statVal}>{stats?.activeToday || 0}</Text>
            <Text style={styles.statLabel}>Active Today</Text>
          </View>
          <View style={styles.statBox}>
            <MaterialIcons name="login" size={28} color="#FF9800" />
            <Text style={styles.statVal}>{stats?.checkInsToday || 0}</Text>
            <Text style={styles.statLabel}>Check-ins Today</Text>
          </View>
        </View>

        <View style={styles.grid}>
          <View style={styles.statBox}>
            <MaterialIcons name="notifications-active" size={28} color="#E53935" />
            <Text style={styles.statVal}>{stats?.lateArrivalsToday || 0}</Text>
            <Text style={styles.statLabel}>Late Arrivals</Text>
          </View>
          <View style={styles.statBox}>
            <MaterialIcons name="track-changes" size={28} color="#00ACC1" />
            <Text style={styles.statVal}>{stats?.activeGeoFences || 0}</Text>
            <Text style={styles.statLabel}>Active Fences</Text>
          </View>
          <View style={styles.statBox}>
            <MaterialIcons name="store" size={28} color="#5E35B1" />
            <Text style={styles.statVal}>{stats?.totalMarts || 0}</Text>
            <Text style={styles.statLabel}>Total Marts</Text>
          </View>
        </View>

        <Text style={styles.sectionTitle}>Console Operations</Text>
        
        <TouchableOpacity
          style={styles.toolRow}
          onPress={() => navigation.navigate('AdminMart')}
        >
          <View style={styles.toolIconWrapper}>
            <MaterialIcons name="store" size={22} color="#1976D2" />
          </View>
          <View style={styles.toolInfo}>
            <Text style={styles.toolTitle}>Mart Locations Management</Text>
            <Text style={styles.toolDesc}>Create, edit, or delete Mart stores</Text>
          </View>
          <MaterialIcons name="chevron-right" size={24} color="#B0BEC5" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.toolRow}
          onPress={() => navigation.navigate('AdminGeoFence')}
        >
          <View style={[styles.toolIconWrapper, { backgroundColor: '#E0F7FA' }]}>
            <MaterialIcons name="track-changes" size={22} color="#00ACC1" />
          </View>
          <View style={styles.toolInfo}>
            <Text style={styles.toolTitle}>Geo-Fence Boundaries Tuning</Text>
            <Text style={styles.toolDesc}>Adjust radius check-in circles on the map</Text>
          </View>
          <MaterialIcons name="chevron-right" size={24} color="#B0BEC5" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.toolRow}
          onPress={() => navigation.navigate('AdminUsers')}
        >
          <View style={[styles.toolIconWrapper, { backgroundColor: '#EDE7F6' }]}>
            <MaterialIcons name="people" size={22} color="#5E35B1" />
          </View>
          <View style={styles.toolInfo}>
            <Text style={styles.toolTitle}>User Roles & Authorization</Text>
            <Text style={styles.toolDesc}>Assign roles (Admin, HR, Sales, Agent)</Text>
          </View>
          <MaterialIcons name="chevron-right" size={24} color="#B0BEC5" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.toolRow}
          onPress={() => navigation.navigate('ReportGenerator')}
        >
          <View style={[styles.toolIconWrapper, { backgroundColor: '#E8F5E9' }]}>
            <MaterialIcons name="file-download" size={22} color="#2E7D32" />
          </View>
          <View style={styles.toolInfo}>
            <Text style={styles.toolTitle}>Excel Report Exporter</Text>
            <Text style={styles.toolDesc}>Download multi-sheet attendance records</Text>
          </View>
          <MaterialIcons name="chevron-right" size={24} color="#B0BEC5" />
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    backgroundColor: '#1976D2',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  logoutBtn: {
    padding: 4,
  },
  scrollContent: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#424242',
    marginTop: 12,
    marginBottom: 12,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  statBox: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    width: '31%',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#EEEEEE',
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
    elevation: 2,
  },
  statVal: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginVertical: 4,
  },
  statLabel: {
    fontSize: 10,
    color: '#757575',
    textAlign: 'center',
  },
  toolRow: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    shadowColor: '#000',
    shadowOpacity: 0.03,
    shadowRadius: 2,
    elevation: 1,
  },
  toolIconWrapper: {
    width: 42,
    height: 42,
    borderRadius: 8,
    backgroundColor: '#E3F2FD',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  toolInfo: {
    flex: 1,
  },
  toolTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
  },
  toolDesc: {
    fontSize: 11,
    color: '#757575',
    marginTop: 2,
  },
});
