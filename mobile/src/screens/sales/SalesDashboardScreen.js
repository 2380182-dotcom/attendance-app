import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  RefreshControl,
  Alert,
  SafeAreaView,
  TouchableOpacity,
  ScrollView,
  Image
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import api, { apiService } from '../../services/api';
import CustomMapView from '../../components/MapView';
import Loading from '../../components/Loading';
import { AuthContext } from '../../context/AuthContext';
import { storage } from '../../utils/storage';
import config from '../../config';
import { connectSalesWebSocket } from '../../services/WebSocketService';

export default function SalesDashboardScreen({ navigation }) {
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
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>Dawn Bread Sales</Text>
          <Text style={styles.headerSubtitle}>Sales Department Console</Text>
        </View>
        <View style={styles.headerActions}>
          <TouchableOpacity onPress={() => navigation.navigate('SalesReport')} style={styles.iconButton}>
            <MaterialIcons name="assessment" size={24} color="#fff" />
          </TouchableOpacity>
          <TouchableOpacity onPress={() => navigation.navigate('SalesAgentReport')} style={styles.iconButton}>
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
        {/* Revenue / Units Summary */}
        <View style={styles.summaryGrid}>
          <View style={[styles.summaryCard, { borderLeftColor: '#4CAF50' }]}>
            <MaterialIcons name="monetization-on" size={20} color="#4CAF50" />
            <Text style={styles.summaryVal}>PKR {dashboardData?.todayTotalRevenue?.toLocaleString() || 0}</Text>
            <Text style={styles.summaryLabel}>Revenue Today</Text>
          </View>
          <View style={[styles.summaryCard, { borderLeftColor: '#2196F3' }]}>
            <MaterialIcons name="local-shipping" size={20} color="#2196F3" />
            <Text style={styles.summaryVal}>{dashboardData?.todayTotalUnits || 0}</Text>
            <Text style={styles.summaryLabel}>Units Dispatched</Text>
          </View>
          <View style={[styles.summaryCard, { borderLeftColor: '#E91E63' }]}>
            <MaterialIcons name="people" size={20} color="#E91E63" />
            <Text style={styles.summaryVal}>{dashboardData?.activeAgentsCount || 0}</Text>
            <Text style={styles.summaryLabel}>Active Agents</Text>
          </View>
        </View>

        {/* Real-time Alerts */}
        <Text style={styles.sectionTitle}>🔴 Real-time Alerts</Text>
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
        <Text style={styles.sectionTitle}>🗺️ Live Agent Map</Text>
        <View style={styles.mapContainer}>
          <CustomMapView activeAgents={activeAgents} marts={marts} />
        </View>

        {/* Sales by Agent Table */}
        <Text style={styles.sectionTitle}>📊 Sales by Agent (Today)</Text>
        <View style={styles.tableCard}>
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
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'right', fontWeight: 'bold', color: '#2E7D32' }]}>
                  PKR {(agent.revenue || 0).toLocaleString()}
                </Text>
                <Text style={[styles.tableCellStatus, { flex: 1.2, textAlign: 'center' }]}>
                  {agent.status}
                </Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyTableText}>No active agent records.</Text>
          )}
        </View>

        {/* Top Selling Products Table */}
        <Text style={styles.sectionTitle}>🍞 Top Selling Products (Today)</Text>
        <View style={styles.tableCard}>
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
                  <Image 
                    source={{ uri: `https://placehold.co/50x50/1976D2/FFFFFF?text=${(p.productName || 'Bread').substring(0, 2)}` }} 
                    style={styles.productThumb} 
                  />
                  <Text style={styles.productCellText} numberOfLines={1}>{p.productName}</Text>
                </View>
                <Text style={[styles.tableCell, { flex: 0.8, textAlign: 'center', fontWeight: '600' }]}>{p.unitsSold}</Text>
                <Text style={[styles.tableCell, { flex: 1.2, textAlign: 'right', fontWeight: 'bold' }]}>
                  PKR {(p.revenue || 0).toLocaleString()}
                </Text>
                <Text style={[styles.trendText, { flex: 1, textAlign: 'center', color: (p.trend || '').includes('+') ? '#4CAF50' : '#F44336' }]}>
                  {p.trend || 'N/A'}
                </Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyTableText}>No product sales catalog data.</Text>
          )}
        </View>

        {/* 7 Days Sales Trend Graph */}
        <Text style={styles.sectionTitle}>📈 Sales Trend (Last 7 Days)</Text>
        <View style={styles.chartCard}>
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
    marginTop: 16,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5
  },
  summaryGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8
  },
  summaryCard: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 12,
    width: '31%',
    borderLeftWidth: 4,
    elevation: 2,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 }
  },
  summaryVal: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 6
  },
  summaryLabel: {
    fontSize: 9,
    color: '#757575',
    marginTop: 2
  },
  alertsCard: {
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
    borderColor: '#E0E0E0'
  },
  tableCard: {
    backgroundColor: '#fff',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    padding: 10,
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
  tableCellName: {
    fontSize: 12,
    fontWeight: '700',
    color: '#212121'
  },
  tableCell: {
    fontSize: 12,
    color: '#212121'
  },
  tableCellStatus: {
    fontSize: 11,
    fontWeight: '600',
    color: '#1565C0',
    backgroundColor: '#E3F2FD',
    borderRadius: 6,
    paddingVertical: 2,
    paddingHorizontal: 4,
    overflow: 'hidden'
  },
  emptyTableText: {
    color: '#757575',
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
    backgroundColor: '#EEEEEE',
    marginRight: 6
  },
  productCellText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#212121',
    flex: 1
  },
  trendText: {
    fontSize: 11,
    fontWeight: 'bold'
  },
  chartCard: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 16,
    elevation: 2,
    borderWidth: 1,
    borderColor: '#EEEEEE'
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
    backgroundColor: '#ECEFF1',
    borderRadius: 7,
    justifyContent: 'flex-end'
  },
  barInner: {
    backgroundColor: '#FF9800',
    borderRadius: 7,
    width: '100%'
  },
  barLabel: {
    fontSize: 9,
    fontWeight: 'bold',
    color: '#757575',
    marginTop: 6
  },
  barValue: {
    fontSize: 8,
    color: '#333',
    fontWeight: '600',
    marginTop: 1
  }
});
