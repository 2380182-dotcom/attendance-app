import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  RefreshControl,
  Alert,
  SafeAreaView,
  ScrollView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import { AuthContext } from '../../context/AuthContext';
import AppTopBar from '../../components/AppTopBar';
import AppCard from '../../components/AppCard';
import { useTheme } from '../../theme';

export default function HRDashboardScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
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
      <AppTopBar
        title="HR Dashboard"
        subtitle="Dawn Bread Attendance & Performance"
        actions={[
          { icon: 'assessment', onPress: () => navigation.navigate('ReportGenerator'), accessibilityLabel: 'Excel reports' },
          { icon: 'notifications-active', onPress: () => navigation.navigate('HRReport'), accessibilityLabel: 'HR report' },
          { icon: 'file-download', onPress: () => navigation.navigate('HRAgentAttendanceReport'), accessibilityLabel: 'Agent attendance report' },
          { icon: 'exit-to-app', onPress: handleLogout, accessibilityLabel: 'Log out' },
        ]}
      />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
      >
        {/* Today's Attendance Summary Card */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="assignment" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Today's Attendance</Text>
        </View>
        <AppCard style={styles.card}>
          <View style={styles.headerStatsRow}>
            <Text style={styles.totalAgentsText}>Total Agents: {d?.totalAgents || 0}</Text>
            <Text style={styles.presentText}>Checked In: {d?.checkedInCount || 0} ({Math.round(d?.checkedInPercent || 0)}%)</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.statsRow}>
            <View style={styles.statCol}>
              <Text style={[styles.statNum, { color: colors.warningDark }]}>{d?.lateCount || 0}</Text>
              <Text style={styles.statLabel}>Late Arrivals</Text>
            </View>
            <View style={styles.statCol}>
              <Text style={[styles.statNum, { color: colors.error }]}>{d?.absentCount || 0}</Text>
              <Text style={styles.statLabel}>Absent Today</Text>
            </View>
          </View>
        </AppCard>

        {/* Verification Compliance Card */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="verified-user" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Verification Compliance (3x Daily)</Text>
        </View>
        <AppCard style={styles.card}>
          <View style={styles.complianceItem}>
            <View style={styles.complianceLabelRow}>
              <View style={styles.complianceLabelWithIcon}>
                <MaterialIcons name="check-circle" size={14} color={colors.successDark} />
                <Text style={styles.complianceTitle}>All 3 Verifications</Text>
              </View>
              <Text style={styles.complianceCount}>
                {d?.complianceAll3Count || 0} agents ({Math.round(d?.complianceAll3Percent || 0)}%)
              </Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressBar, { width: `${d?.complianceAll3Percent || 0}%`, backgroundColor: colors.successDark }]} />
            </View>
          </View>

          <View style={styles.complianceItem}>
            <View style={styles.complianceLabelRow}>
              <View style={styles.complianceLabelWithIcon}>
                <MaterialIcons name="warning" size={14} color={colors.warningDark} />
                <Text style={styles.complianceTitle}>Missing 1 Verification</Text>
              </View>
              <Text style={styles.complianceCount}>
                {d?.complianceMissing1Count || 0} agents ({Math.round(d?.complianceMissing1Percent || 0)}%)
              </Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressBar, { width: `${d?.complianceMissing1Percent || 0}%`, backgroundColor: colors.warningDark }]} />
            </View>
          </View>

          <View style={styles.complianceItem}>
            <View style={styles.complianceLabelRow}>
              <View style={styles.complianceLabelWithIcon}>
                <MaterialIcons name="cancel" size={14} color={colors.error} />
                <Text style={styles.complianceTitle}>Missing 2+ Verifications</Text>
              </View>
              <Text style={styles.complianceCount}>
                {d?.complianceMissing2PlusCount || 0} agents ({Math.round(d?.complianceMissing2PlusPercent || 0)}%)
              </Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressBar, { width: `${d?.complianceMissing2PlusPercent || 0}%`, backgroundColor: colors.error }]} />
            </View>
          </View>
        </AppCard>

        {/* Combined Attendance + Sales Sheet */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="table-chart" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Attendance + Sales Sheet (Today)</Text>
        </View>
        <AppCard style={styles.tableCard} padding={12}>
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
                <Text style={[styles.tableCell, { flex: 0.8, textAlign: 'center', fontWeight: 'bold', color: colors.secondary }]}>
                  {row.units}
                </Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyText}>No roster sheets recorded for today.</Text>
          )}
        </AppCard>

        {/* Top Performing Agents Leaderboard */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="emoji-events" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Top Performing Agents (Today)</Text>
        </View>
        <AppCard style={styles.leaderboardCard} padding={12}>
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
    padding: 14,
    paddingBottom: 40
  },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 18,
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginLeft: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.5
  },
  card: {},
  headerStatsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10
  },
  totalAgentsText: {
    fontWeight: 'bold',
    fontSize: 14,
    color: colors.textPrimary
  },
  presentText: {
    fontWeight: 'bold',
    fontSize: 14,
    color: colors.successDark
  },
  divider: {
    height: 1,
    backgroundColor: colors.divider,
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
    color: colors.textSecondary,
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
  complianceLabelWithIcon: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  complianceTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textSecondary,
    marginLeft: 5,
  },
  complianceCount: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.textPrimary
  },
  progressTrack: {
    height: 8,
    backgroundColor: colors.surfaceMuted,
    borderRadius: 4,
    width: '100%',
    overflow: 'hidden'
  },
  progressBar: {
    height: '100%',
    borderRadius: 4
  },
  tableCard: {},
  tableHeader: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
    paddingBottom: 6,
    marginBottom: 4
  },
  tableCol: {
    fontSize: 11,
    fontWeight: 'bold',
    color: colors.textSecondary,
    textTransform: 'uppercase'
  },
  tableRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider
  },
  tableNameCell: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textPrimary
  },
  tableCell: {
    fontSize: 12,
    color: colors.textPrimary
  },
  emptyText: {
    color: colors.textSecondary,
    fontSize: 12,
    textAlign: 'center',
    paddingVertical: 16
  },
  leaderboardCard: {},
  leaderboardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider
  },
  leaderRank: {
    fontSize: 14,
    fontWeight: 'bold',
    color: colors.textSecondary,
    width: 24
  },
  leaderDetails: {
    flex: 1
  },
  leaderName: {
    fontSize: 13,
    fontWeight: 'bold',
    color: colors.textPrimary
  },
  leaderSub: {
    fontSize: 10,
    color: colors.textSecondary,
    marginTop: 2
  },
  leaderStats: {
    alignItems: 'flex-end'
  },
  leaderUnits: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.secondary
  },
  leaderRevenue: {
    fontSize: 11,
    color: colors.successDark,
    fontWeight: '600',
    marginTop: 2
  }
});
