import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  RefreshControl,
  Alert,
  SafeAreaView,
  TouchableOpacity,
  ScrollView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import { AuthContext } from '../../context/AuthContext';

export default function HRDashboardScreen({ navigation }) {
  const { logout } = useContext(AuthContext);
  const [dashboardData, setDashboardData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const data = await apiService.sales.getHRDashboard();
      setDashboardData(data);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to fetch HR Dashboard metrics.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 20000); // refresh every 20s
    return () => clearInterval(interval);
  }, [fetchData]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchData();
  };

  const handleLogout = () => {
    Alert.alert('Confirm Logout', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Log Out', style: 'destructive', onPress: logout }
    ]);
  };

  if (loading && !refreshing) {
    return <Loading message="Loading HR Roster & Compliance..." fullScreen />;
  }

  const d = dashboardData;

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>HR Dashboard</Text>
          <Text style={styles.headerSubtitle}>Dawn Bread Attendance & Performance</Text>
        </View>
        <View style={styles.headerActions}>
          <TouchableOpacity onPress={() => navigation.navigate('ReportGenerator')} style={styles.iconButton}>
            <MaterialIcons name="assessment" size={24} color="#fff" />
          </TouchableOpacity>
          <TouchableOpacity onPress={() => navigation.navigate('HRReport')} style={styles.iconButton}>
            <MaterialIcons name="notifications-active" size={24} color="#fff" />
          </TouchableOpacity>
          <TouchableOpacity onPress={() => navigation.navigate('HRAgentAttendanceReport')} style={styles.iconButton}>
            <MaterialIcons name="file-download" size={24} color="#fff" />
          </TouchableOpacity>
          <TouchableOpacity onPress={handleLogout} style={styles.iconButton}>
            <MaterialIcons name="exit-to-app" size={24} color="#fff" />
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} color="#1976D2" />
        }
      >
        {/* Today's Attendance Summary Card */}
        <Text style={styles.sectionTitle}>📋 Today's Attendance</Text>
        <View style={styles.card}>
          <View style={styles.headerStatsRow}>
            <Text style={styles.totalAgentsText}>Total Agents: {d?.totalAgents || 0}</Text>
            <Text style={styles.presentText}>Checked In: {d?.checkedInCount || 0} ({Math.round(d?.checkedInPercent || 0)}%)</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.statsRow}>
            <View style={styles.statCol}>
              <Text style={[styles.statNum, { color: '#F57F17' }]}>{d?.lateCount || 0}</Text>
              <Text style={styles.statLabel}>Late Arrivals</Text>
            </View>
            <View style={styles.statCol}>
              <Text style={[styles.statNum, { color: '#D32F2F' }]}>{d?.absentCount || 0}</Text>
              <Text style={styles.statLabel}>Absent Today</Text>
            </View>
          </View>
        </View>

        {/* Verification Compliance Card */}
        <Text style={styles.sectionTitle}>🔒 Verification Compliance (3x Daily)</Text>
        <View style={styles.card}>
          <View style={styles.complianceItem}>
            <View style={styles.complianceLabelRow}>
              <Text style={styles.complianceTitle}>✅ All 3 Verifications</Text>
              <Text style={styles.complianceCount}>
                {d?.complianceAll3Count || 0} agents ({Math.round(d?.complianceAll3Percent || 0)}%)
              </Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressBar, { width: `${d?.complianceAll3Percent || 0}%`, backgroundColor: '#4CAF50' }]} />
            </View>
          </View>

          <View style={styles.complianceItem}>
            <View style={styles.complianceLabelRow}>
              <Text style={styles.complianceTitle}>⚠️ Missing 1 Verification</Text>
              <Text style={styles.complianceCount}>
                {d?.complianceMissing1Count || 0} agents ({Math.round(d?.complianceMissing1Percent || 0)}%)
              </Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressBar, { width: `${d?.complianceMissing1Percent || 0}%`, backgroundColor: '#FF9800' }]} />
            </View>
          </View>

          <View style={styles.complianceItem}>
            <View style={styles.complianceLabelRow}>
              <Text style={styles.complianceTitle}>❌ Missing 2+ Verifications</Text>
              <Text style={styles.complianceCount}>
                {d?.complianceMissing2PlusCount || 0} agents ({Math.round(d?.complianceMissing2PlusPercent || 0)}%)
              </Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressBar, { width: `${d?.complianceMissing2PlusPercent || 0}%`, backgroundColor: '#D32F2F' }]} />
            </View>
          </View>
        </View>

        {/* Combined Attendance + Sales Sheet */}
        <Text style={styles.sectionTitle}>📊 Attendance + Sales Sheet (Today)</Text>
        <View style={styles.tableCard}>
          <View style={styles.tableHeader}>
            <Text style={[styles.tableCol, { flex: 2.2 }]}>Agent</Text>
            <Text style={[styles.tableCol, { flex: 1.2, textAlign: 'center' }]}>In</Text>
            <Text style={[styles.tableCol, { flex: 1.2, textAlign: 'center' }]}>Mid</Text>
            <Text style={[styles.tableCol, { flex: 1.2, textAlign: 'center' }]}>Out</Text>
            <Text style={[styles.tableCol, { flex: 0.8, textAlign: 'center' }]}>Units</Text>
          </View>
          {d?.attendanceSalesSheet && d.attendanceSalesSheet.length > 0 ? (
            d.attendanceSalesSheet.map((row, idx) => (
              <View key={idx} style={styles.tableRow}>
                <Text style={[styles.tableNameCell, { flex: 2.2 }]} numberOfLines={1}>{row.agentName}</Text>
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'center', fontSize: 11 }]}>{row.checkInTime}</Text>
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'center', fontSize: 11 }]}>{row.midDayTime}</Text>
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'center', fontSize: 11 }]}>{row.checkOutTime}</Text>
                <Text style={[styles.tableCell, { flex: 0.8, textAlign: 'center', fontWeight: 'bold', color: '#1976D2' }]}>
                  {row.units}
                </Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyText}>No roster sheets recorded for today.</Text>
          )}
        </View>

        {/* Top Performing Agents Leaderboard */}
        <Text style={styles.sectionTitle}>🏆 Top Performing Agents (Today)</Text>
        <View style={styles.leaderboardCard}>
          {d?.topPerformers && d.topPerformers.length > 0 ? (
            d.topPerformers.map((agent, idx) => (
              <View key={idx} style={styles.leaderboardRow}>
                <Text style={styles.leaderRank}>{idx + 1}.</Text>
                <View style={styles.leaderDetails}>
                  <Text style={styles.leaderName}>{agent.agentName}</Text>
                  <Text style={styles.leaderSub}>Attendance: {Math.round(agent.attendancePercent)}% | Status: {agent.status}</Text>
                </View>
                <View style={styles.leaderStats}>
                  <Text style={styles.leaderUnits}>{agent.totalSalesUnits} Units</Text>
                  <Text style={styles.leaderRevenue}>PKR {(agent.totalSalesRevenue || 0).toLocaleString()}</Text>
                </View>
              </View>
            ))
          ) : (
            <Text style={styles.emptyText}>No leaderboards available.</Text>
          )}
        </View>
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
    paddingVertical: 14,
    elevation: 3,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 }
  },
  headerTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  headerSubtitle: {
    color: '#E3F2FD',
    fontSize: 11,
    marginTop: 1
  },
  headerActions: {
    flexDirection: 'row',
  },
  iconButton: {
    marginLeft: 18,
  },
  scrollContent: {
    padding: 14,
    paddingBottom: 40
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#424242',
    marginTop: 18,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    elevation: 2,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 }
  },
  headerStatsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10
  },
  totalAgentsText: {
    fontWeight: 'bold',
    fontSize: 14,
    color: '#333'
  },
  presentText: {
    fontWeight: 'bold',
    fontSize: 14,
    color: '#4CAF50'
  },
  divider: {
    height: 1,
    backgroundColor: '#EEEEEE',
    marginVertical: 8
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 4
  },
  statCol: {
    alignItems: 'center',
    width: '40%'
  },
  statNum: {
    fontSize: 20,
    fontWeight: 'bold'
  },
  statLabel: {
    fontSize: 11,
    color: '#757575',
    marginTop: 2
  },
  complianceItem: {
    marginVertical: 6
  },
  complianceLabelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4
  },
  complianceTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: '#424242'
  },
  complianceCount: {
    fontSize: 11,
    fontWeight: '700',
    color: '#333'
  },
  progressTrack: {
    height: 8,
    backgroundColor: '#ECEFF1',
    borderRadius: 4,
    width: '100%',
    overflow: 'hidden'
  },
  progressBar: {
    height: '100%',
    borderRadius: 4
  },
  tableCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    padding: 12,
    elevation: 2
  },
  tableHeader: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#EEEEEE',
    paddingBottom: 6,
    marginBottom: 4
  },
  tableCol: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#757575',
    textTransform: 'uppercase'
  },
  tableRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5'
  },
  tableNameCell: {
    fontSize: 12,
    fontWeight: '700',
    color: '#212121'
  },
  tableCell: {
    fontSize: 12,
    color: '#333'
  },
  emptyText: {
    color: '#757575',
    fontSize: 12,
    textAlign: 'center',
    paddingVertical: 16
  },
  leaderboardCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    paddingHorizontal: 12,
    paddingVertical: 6,
    elevation: 2
  },
  leaderboardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5'
  },
  leaderRank: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#757575',
    width: 24
  },
  leaderDetails: {
    flex: 1
  },
  leaderName: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#212121'
  },
  leaderSub: {
    fontSize: 10,
    color: '#757575',
    marginTop: 2
  },
  leaderStats: {
    alignItems: 'flex-end'
  },
  leaderUnits: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#1976D2'
  },
  leaderRevenue: {
    fontSize: 11,
    color: '#2E7D32',
    fontWeight: '600',
    marginTop: 2
  }
});
