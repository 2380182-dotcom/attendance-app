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
import api, { apiService } from '../../services/api';
import CustomMapView from '../../components/MapView';
import Loading from '../../components/Loading';
import ProductThumbnail from '../../components/ProductThumbnail';
import AppTopBar from '../../components/AppTopBar';
import AppCard from '../../components/AppCard';
import MetricCard from '../../components/MetricCard';
import StatusChip from '../../components/StatusChip';
import { AuthContext } from '../../context/AuthContext';
import { storage } from '../../utils/storage';
import config from '../../config';
import { connectSalesWebSocket } from '../../services/WebSocketService';
import { useTheme } from '../../theme';

export default function SalesDashboardScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { logout } = useContext(AuthContext);
  const [activeAgents, setActiveAgents] = useState([]);
  const [marts, setMarts] = useState([]);
  const [dashboardData, setDashboardData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const [martsRes, openAttRes, dashRes] = await Promise.all([
        apiService.marts.getAll(),
        api.get('/attendance/open'),
        apiService.sales.getRealtimeDashboard()
      ]);

      setMarts(martsRes);
      setDashboardData(dashRes);

      const agentsOnMap = (openAttRes.data?.data || []).map(att => ({
        id: att.agentId,
        name: att.agentName,
        latitude: att.checkInLatitude,
        longitude: att.checkInLongitude,
        status: att.status,
        martName: att.martName
      }));
      setActiveAgents(agentsOnMap);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to fetch Sales dashboard data.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();

    let disconnectStomp = () => {};

    const connectWS = async () => {
      try {
        const savedUrl = await storage.getServerUrl();
        const base = savedUrl || config.API_URL;
        disconnectStomp = connectSalesWebSocket(base, () => {
          console.log('[STOMP] Sales update received, refreshing dashboard...');
          fetchData();
        });
      } catch (err) {
        console.error('Failed to configure STOMP WebSocket:', err);
      }
    };

    connectWS();

    const pollInterval = setInterval(fetchData, 15000);

    return () => {
      clearInterval(pollInterval);
      disconnectStomp();
    };
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
    return <Loading message="Compiling sales department feed..." fullScreen />;
  }

  // Find max value in last 7 days sales trend for graph scaling
  const maxTrendValue = (dashboardData?.salesTrend || []).reduce((max, day) => {
    const rev = parseFloat(day?.revenue);
    return isNaN(rev) ? max : Math.max(max, rev);
  }, 1000) || 10000;

  return (
    <SafeAreaView style={styles.container}>
      <AppTopBar
        title="Dawn Bread Sales"
        subtitle="Sales Department Console"
        actions={[
          { icon: 'assessment', onPress: () => navigation.navigate('SalesReport'), accessibilityLabel: 'Sales report' },
          { icon: 'file-download', onPress: () => navigation.navigate('SalesAgentReport'), accessibilityLabel: 'Agent report' },
          { icon: 'exit-to-app', onPress: handleLogout, accessibilityLabel: 'Log out' },
        ]}
      />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
      >
        {/* Revenue / Units Summary */}
        <View style={styles.summaryGrid}>
          <MetricCard
            icon="monetization-on"
            color="success"
            value={`PKR ${dashboardData?.todayTotalRevenue?.toLocaleString() || 0}`}
            label="Revenue Today"
            style={styles.summaryCard}
          />
          <MetricCard
            icon="local-shipping"
            color="secondary"
            value={dashboardData?.todayTotalUnits || 0}
            label="Units Dispatched"
            style={styles.summaryCard}
          />
          <MetricCard
            icon="people"
            color="primary"
            value={dashboardData?.activeAgentsCount || 0}
            label="Checked In Today"
            style={styles.summaryCard}
          />
        </View>

        {/* Real-time Alerts */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="notifications-active" size={16} color={colors.error} />
          <Text style={styles.sectionTitle}>Real-time Alerts</Text>
        </View>
        <View style={styles.alertsCard}>
          {dashboardData?.recentAlerts && dashboardData.recentAlerts.length > 0 ? (
            dashboardData.recentAlerts.map((alert, idx) => (
              <Text key={idx} style={styles.alertText}>{alert}</Text>
            ))
          ) : (
            <Text style={styles.emptyAlertsText}>No transactions registered today.</Text>
          )}
        </View>

        {/* Map View */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="map" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Live Agent Map</Text>
        </View>
        <View style={styles.mapContainer}>
          <CustomMapView activeAgents={activeAgents} marts={marts} />
        </View>

        {/* Sales by Agent Table */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="bar-chart" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Sales by Agent (Today)</Text>
        </View>
        <AppCard padding={10}>
          <View style={styles.tableHeader}>
            <Text style={[styles.tableCol, { flex: 2 }]}>Agent</Text>
            <Text style={[styles.tableCol, { flex: 0.8, textAlign: 'center' }]}>Units</Text>
            <Text style={[styles.tableCol, { flex: 1.2, textAlign: 'right' }]}>Revenue</Text>
            <Text style={[styles.tableCol, { flex: 1.2, textAlign: 'center' }]}>Status</Text>
          </View>
          {dashboardData?.salesByAgent && dashboardData.salesByAgent.length > 0 ? (
            dashboardData.salesByAgent.map((agent, idx) => (
              <View key={idx} style={styles.tableRow}>
                <Text style={[styles.tableCellName, { flex: 2 }]}>{agent.agentName}</Text>
                <Text style={[styles.tableCell, { flex: 0.8, textAlign: 'center', fontWeight: '600' }]}>{agent.unitsSold}</Text>
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'right', fontWeight: 'bold', color: colors.successDark }]}>
                  PKR {(agent.revenue || 0).toLocaleString()}
                </Text>
                <View style={{ flex: 1.2, alignItems: 'center' }}>
                  <StatusChip status={agent.status} size="sm" />
                </View>
              </View>
            ))
          ) : (
            <Text style={styles.emptyTableText}>No active agent records.</Text>
          )}
        </AppCard>

        {/* Top Selling Products Table */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="bakery-dining" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Top Selling Products (Today)</Text>
        </View>
        <AppCard padding={10}>
          <View style={styles.tableHeader}>
            <Text style={[styles.tableCol, { flex: 2.2 }]}>Product</Text>
            <Text style={[styles.tableCol, { flex: 0.8, textAlign: 'center' }]}>Units</Text>
            <Text style={[styles.tableCol, { flex: 1.2, textAlign: 'right' }]}>Revenue</Text>
            <Text style={[styles.tableCol, { flex: 1, textAlign: 'center' }]}>Trend</Text>
          </View>
          {dashboardData?.topSellingProducts && dashboardData.topSellingProducts.length > 0 ? (
            dashboardData.topSellingProducts.map((p, idx) => (
              <View key={idx} style={styles.tableRow}>
                <View style={[styles.productCell, { flex: 2.2 }]}>
                  <ProductThumbnail uri={p.productImageUrl} size={36} style={styles.productThumb} />
                  <Text style={styles.productCellText} numberOfLines={1}>{p.productName}</Text>
                </View>
                <Text style={[styles.tableCell, { flex: 0.8, textAlign: 'center', fontWeight: '600' }]}>{p.unitsSold}</Text>
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'right', fontWeight: 'bold' }]}>
                  PKR {(p.revenue || 0).toLocaleString()}
                </Text>
                <Text style={[styles.trendText, { flex: 1, textAlign: 'center', color: (p.trend || '').includes('+') ? colors.successDark : colors.error }]}>
                  {p.trend || 'N/A'}
                </Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyTableText}>No product sales catalog data.</Text>
          )}
        </AppCard>

        {/* 7 Days Sales Trend Graph */}
        <View style={styles.sectionTitleRow}>
          <MaterialIcons name="trending-up" size={16} color={colors.textSecondary} />
          <Text style={styles.sectionTitle}>Sales Trend (Last 7 Days)</Text>
        </View>
        <AppCard>
          <View style={styles.chartContainer}>
            {dashboardData?.salesTrend && dashboardData.salesTrend.length > 0 ? (
              dashboardData.salesTrend.map((day, idx) => {
                const rev = parseFloat(day?.revenue) || 0;
                const heightPercent = maxTrendValue > 0 ? (rev * 100.0 / maxTrendValue) : 0;
                return (
                  <View key={idx} style={styles.barWrapper}>
                    <View style={styles.barOuter}>
                      <View style={[styles.barInner, { height: `${Math.max(5, heightPercent)}%` }]} />
                    </View>
                    <Text style={styles.barLabel}>{day?.date || 'N/A'}</Text>
                    <Text style={styles.barValue}>PKR {(rev / 1000).toFixed(1)}k</Text>
                  </View>
                );
              })
            ) : (
              <Text style={styles.emptyTableText}>No trend history available.</Text>
            )}
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
    padding: 14,
    paddingBottom: 40
  },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 16,
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
  summaryGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8
  },
  summaryCard: {
    width: '31%',
    minWidth: 0,
  },
  alertsCard: {
    // Deliberately a dark "log/terminal" panel for real-time alerts, distinct
    // from the app's themed surfaces — stays dark in both light and dark mode.
    backgroundColor: '#37474F',
    borderRadius: 10,
    padding: 12,
    elevation: 2
  },
  alertText: {
    color: '#ECEFF1',
    fontSize: 12,
    lineHeight: 18,
    marginVertical: 2
  },
  emptyAlertsText: {
    color: '#B0BEC5',
    fontSize: 12,
    textAlign: 'center',
    paddingVertical: 8
  },
  mapContainer: {
    height: 200,
    width: '100%',
    borderRadius: 10,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.border
  },
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
  tableCellName: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textPrimary
  },
  tableCell: {
    fontSize: 12,
    color: colors.textPrimary
  },
  emptyTableText: {
    color: colors.textSecondary,
    fontSize: 12,
    textAlign: 'center',
    paddingVertical: 16
  },
  productCell: {
    flexDirection: 'row',
    alignItems: 'center'
  },
  productThumb: {
    width: 22,
    height: 22,
    borderRadius: 4,
    backgroundColor: colors.surfaceMuted,
    marginRight: 6
  },
  productCellText: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textPrimary,
    flex: 1
  },
  trendText: {
    fontSize: 11,
    fontWeight: 'bold'
  },
  chartContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    height: 140,
    paddingTop: 10
  },
  barWrapper: {
    alignItems: 'center',
    width: '13%'
  },
  barOuter: {
    height: 90,
    width: 14,
    backgroundColor: colors.surfaceMuted,
    borderRadius: 7,
    justifyContent: 'flex-end'
  },
  barInner: {
    backgroundColor: colors.warning,
    borderRadius: 7,
    width: '100%'
  },
  barLabel: {
    fontSize: 9,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginTop: 6
  },
  barValue: {
    fontSize: 8,
    color: colors.textPrimary,
    fontWeight: '600',
    marginTop: 1
  }
});
