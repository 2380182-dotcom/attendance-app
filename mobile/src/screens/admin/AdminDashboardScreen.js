import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  RefreshControl,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import { AuthContext } from '../../context/AuthContext';
import AppTopBar from '../../components/AppTopBar';
import AppCard from '../../components/AppCard';
import MetricCard from '../../components/MetricCard';
import { useTheme } from '../../theme';

export default function AdminDashboardScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
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
      <AppTopBar
        title="Admin Console"
        actions={[{ icon: 'exit-to-app', onPress: handleLogout, accessibilityLabel: 'Log out' }]}
      />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
      >
        <Text style={styles.sectionTitle}>System Statistics</Text>
        <View style={styles.grid}>
          <MetricCard
            icon="people"
            color="secondary"
            value={stats?.totalAgents || 0}
            label="Total Agents"
            style={styles.statBox}
          />
          <MetricCard
            icon="person-pin"
            color="success"
            value={stats?.activeToday || 0}
            label="Active Today"
            style={styles.statBox}
          />
          <MetricCard
            icon="login"
            color="warning"
            value={stats?.checkInsToday || 0}
            label="Check-ins Today"
            style={styles.statBox}
          />
        </View>

        <View style={styles.grid}>
          <MetricCard
            icon="notifications-active"
            color="error"
            value={stats?.lateArrivalsToday || 0}
            label="Late Arrivals"
            style={styles.statBox}
          />
          <MetricCard
            icon="track-changes"
            color="chart2"
            value={stats?.activeGeoFences || 0}
            label="Active Fences"
            style={styles.statBox}
          />
          <MetricCard
            icon="store"
            color="chart1"
            value={stats?.totalMarts || 0}
            label="Total Marts"
            style={styles.statBox}
          />
        </View>

        <Text style={styles.sectionTitle}>Console Operations</Text>

        <AppCard style={styles.toolRow} onPress={() => navigation.navigate('AdminMart')} padding={14}>
          <View style={styles.toolRowInner}>
            <View style={[styles.toolIconWrapper, { backgroundColor: colors.secondaryLight }]}>
              <MaterialIcons name="store" size={22} color={colors.secondary} />
            </View>
            <View style={styles.toolInfo}>
              <Text style={styles.toolTitle}>Mart Locations Management</Text>
              <Text style={styles.toolDesc}>Create, edit, or delete Mart stores</Text>
            </View>
            <MaterialIcons name="chevron-right" size={24} color={colors.textMuted} />
          </View>
        </AppCard>

        <AppCard style={styles.toolRow} onPress={() => navigation.navigate('AdminGeoFence')} padding={14}>
          <View style={styles.toolRowInner}>
            <View style={[styles.toolIconWrapper, { backgroundColor: colors.chart2Light }]}>
              <MaterialIcons name="track-changes" size={22} color={colors.chart2Dark} />
            </View>
            <View style={styles.toolInfo}>
              <Text style={styles.toolTitle}>Geo-Fence Boundaries Tuning</Text>
              <Text style={styles.toolDesc}>Adjust radius check-in circles on the map</Text>
            </View>
            <MaterialIcons name="chevron-right" size={24} color={colors.textMuted} />
          </View>
        </AppCard>

        <AppCard style={styles.toolRow} onPress={() => navigation.navigate('AdminUsers')} padding={14}>
          <View style={styles.toolRowInner}>
            <View style={[styles.toolIconWrapper, { backgroundColor: colors.chart1Light }]}>
              <MaterialIcons name="people" size={22} color={colors.chart1} />
            </View>
            <View style={styles.toolInfo}>
              <Text style={styles.toolTitle}>User Roles & Authorization</Text>
              <Text style={styles.toolDesc}>Assign roles (Admin, HR, Sales, Agent)</Text>
            </View>
            <MaterialIcons name="chevron-right" size={24} color={colors.textMuted} />
          </View>
        </AppCard>

        <AppCard style={styles.toolRow} onPress={() => navigation.navigate('ReportGenerator')} padding={14}>
          <View style={styles.toolRowInner}>
            <View style={[styles.toolIconWrapper, { backgroundColor: colors.successLight }]}>
              <MaterialIcons name="file-download" size={22} color={colors.successDark} />
            </View>
            <View style={styles.toolInfo}>
              <Text style={styles.toolTitle}>Excel Report Exporter</Text>
              <Text style={styles.toolDesc}>Download multi-sheet attendance records</Text>
            </View>
            <MaterialIcons name="chevron-right" size={24} color={colors.textMuted} />
          </View>
        </AppCard>
      </ScrollView>
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: colors.textSecondary,
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
    width: '31%',
    minWidth: 0,
  },
  toolRow: {
    marginBottom: 12,
  },
  toolRowInner: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  toolIconWrapper: {
    width: 42,
    height: 42,
    borderRadius: 8,
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
    color: colors.textPrimary,
  },
  toolDesc: {
    fontSize: 11,
    color: colors.textSecondary,
    marginTop: 2,
  },
});
