import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Alert,
  SafeAreaView,
  Image
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import StatusCard from '../../components/StatusCard';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';

export default function DashboardScreen({ navigation, route }) {
  const { user, logout } = useContext(AuthContext);
  const [refreshing, setRefreshing] = useState(false);
  const [isCheckedIn, setIsCheckedIn] = useState(false);
  const [currentCheckIn, setCurrentCheckIn] = useState(null);
  const [loading, setLoading] = useState(true);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [salesHistory, setSalesHistory] = useState([]);
  const [midDayModalVisible, setMidDayModalVisible] = useState(false);

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const handleMidDayVerify = async () => {
    fetchStatus();
  };

  const fetchStatus = useCallback(async () => {
    if (!user?.id) return;
    try {
      const checkedInStatus = await apiService.attendance.isCheckedIn(user.id);
      setIsCheckedIn(checkedInStatus);

      if (checkedInStatus) {
        const details = await apiService.attendance.getCurrentCheckIn(user.id);
        setCurrentCheckIn(details);
      } else {
        setCurrentCheckIn(null);
      }

      // Fetch agent sales history
      try {
        const sales = await apiService.sales.getAgentSales(user.id);
        setSalesHistory(sales);
      } catch (err) {
        console.error('Failed to load agent sales history', err);
      }
    } catch (e) {
      console.error('Error fetching attendance status', e);
      Alert.alert('Status Error', 'Could not refresh check-in status from server.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user]);

  useEffect(() => {
    fetchStatus();
    checkEnrollment();
  }, [fetchStatus]);

  const checkEnrollment = async () => {
    if (!user?.id) return;
    try {
      const status = await apiService.face.getStatus(user.id);
      if (status.faceVerificationEnabled !== false && !status.registered) {
        navigation.navigate('FaceEnrollment');
      }
    } catch (e) {
      console.warn('Face enrollment check failed', e);
    }
  };

  const onRefresh = () => {
    setRefreshing(true);
    fetchStatus();
  };

  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      fetchStatus();
      if (route?.params?.openFaceVerification) {
        setMidDayModalVisible(true);
        navigation.setParams({ openFaceVerification: undefined });
      }
    });
    return unsubscribe;
  }, [navigation, fetchStatus, route?.params?.openFaceVerification]);

  const handleLogout = () => {
    Alert.alert(
      'Confirm Logout',
      'Are you sure you want to log out?',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Log Out', style: 'destructive', onPress: logout }
      ]
    );
  };

  const getGreeting = () => {
    const hours = new Date().getHours();
    if (hours < 12) return 'Good Morning';
    if (hours < 18) return 'Good Afternoon';
    return 'Good Evening';
  };

  const renderSalesHistoryTable = () => {
    const groups = {};
    salesHistory.forEach(record => {
      const dateStr = record.saleDate;
      if (!groups[dateStr]) {
        groups[dateStr] = [];
      }
      record.items.forEach(item => {
        groups[dateStr].push({
          ...item,
          time: record.saleTime,
          location: record.location,
          saleId: record.id
        });
      });
    });

    return Object.keys(groups).sort((a, b) => b.localeCompare(a)).map(date => (
      <View key={date} style={styles.dateGroupCard}>
        <View style={styles.dateGroupHeader}>
          <MaterialIcons name="event" size={16} color="#1976D2" />
          <Text style={styles.dateGroupHeaderText}>Date: {date}</Text>
        </View>
        <View style={styles.salesTable}>
          <View style={styles.tableHeaderRow}>
            <Text style={[styles.columnHeader, { flex: 2 }]}>Product</Text>
            <Text style={[styles.columnHeader, { flex: 0.6, textAlign: 'center' }]}>Qty</Text>
            <Text style={[styles.columnHeader, { flex: 1, textAlign: 'center' }]}>Time</Text>
            <Text style={[styles.columnHeader, { flex: 1.2, textAlign: 'right' }]}>Total</Text>
          </View>
          {groups[date].map((item, idx) => {
            const timeStr = item.time ? item.time.substring(0, 5) : '';
            return (
              <View key={idx} style={styles.tableBodyRow}>
                <View style={[styles.productCell, { flex: 2 }]}>
                  <Image source={{ uri: item.productImageUrl }} style={styles.productThumbnail} />
                  <Text style={styles.productCellText} numberOfLines={1}>{item.productName}</Text>
                </View>
                <Text style={[styles.tableBodyCell, { flex: 0.6, textAlign: 'center', fontWeight: 'bold' }]}>
                  {item.quantity}
                </Text>
                <Text style={[styles.tableBodyCell, { flex: 1, textAlign: 'center', color: '#757575', fontSize: 11 }]}>
                  {timeStr}
                </Text>
                <Text style={[styles.tableBodyCell, { flex: 1.2, textAlign: 'right', fontWeight: 'bold', color: '#1976D2' }]}>
                  PKR {item.totalPrice}
                </Text>
              </View>
            );
          })}
        </View>
      </View>
    ));
  };

  if (loading && !refreshing) {
    return <Loading message="Loading dashboard..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} color="#2196F3" />
        }
      >
        <View style={styles.banner}>
          <Text style={styles.greetingText}>{getGreeting()},</Text>
          <Text style={styles.agentNameText}>{user?.name || 'Agent'}</Text>
          <Text style={styles.agentIdText}>Agent ID: {user?.agentId || 'N/A'} | Dept: {user?.department || 'N/A'}</Text>
        </View>

        <View style={styles.timeCard}>
          <Text style={styles.dateText}>
            {currentTime.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
          </Text>
          <Text style={styles.timeText}>
            {currentTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </Text>
        </View>

        {isCheckedIn && user?.faceVerifyAnytime && !currentCheckIn?.midDayVerificationTime && (
          <View style={styles.alertBanner}>
            <View style={styles.alertBannerLeft}>
              <MaterialIcons name="warning" size={24} color="#E53935" />
              <View style={{ marginLeft: 12, flex: 1 }}>
                <Text style={styles.alertBannerTitle}>Verification Required</Text>
                <Text style={styles.alertBannerDesc}>Please submit your face verification check for today.</Text>
              </View>
            </View>
            <TouchableOpacity style={styles.alertBannerBtn} onPress={() => setMidDayModalVisible(true)}>
              <Text style={styles.alertBannerBtnText}>VERIFY</Text>
            </TouchableOpacity>
          </View>
        )}

        <StatusCard isCheckedIn={isCheckedIn} currentCheckIn={currentCheckIn} />

        <FaceVerificationModal
          visible={midDayModalVisible}
          onClose={() => setMidDayModalVisible(false)}
          onSuccess={() => {
            setMidDayModalVisible(false);
            Alert.alert('Verification Successful', 'Your mid-shift face verification is recorded.');
            handleMidDayVerify();
          }}
          onFailure={() => {
            setMidDayModalVisible(false);
            Alert.alert(
              'Verification Failed',
              'Maximum attempts reached. Admin has been notified.'
            );
          }}
          agentId={user?.id}
          agentName={user ? user.name : 'Agent'}
          checkpointType="MIDSHIFT"
        />

        {!isCheckedIn && (
          <>
            <Text style={styles.sectionTitle}>Manual Duty Operations</Text>
            <TouchableOpacity
              style={[styles.actionButton, styles.checkinButton]}
              onPress={() => navigation.navigate('Checkin')}
            >
              <MaterialIcons name="login" size={24} color="#fff" />
              <Text style={styles.actionButtonText}>Proceed to Check-In</Text>
            </TouchableOpacity>
          </>
        )}

        <Text style={styles.sectionTitle}>Dawn Bread Sales</Text>
        <TouchableOpacity
          style={[styles.actionButton, { backgroundColor: '#FF9800', shadowColor: '#FF9800', marginTop: 0, marginBottom: 12 }]}
          onPress={() => navigation.navigate('SalesEntry')}
        >
          <MaterialIcons name="shopping-cart" size={24} color="#fff" />
          <Text style={styles.actionButtonText}>Enter Daily Sales</Text>
        </TouchableOpacity>

        <Text style={styles.sectionTitle}>📊 Agent Sales History</Text>
        {salesHistory.length === 0 ? (
          <View style={styles.emptySalesCard}>
            <MaterialIcons name="assessment" size={32} color="#B0BEC5" style={{ marginBottom: 6 }} />
            <Text style={styles.emptySalesText}>No sales recorded yet.</Text>
          </View>
        ) : (
          renderSalesHistoryTable()
        )}

        <Text style={styles.sectionTitle}>Resources & Logs</Text>
        <View style={styles.grid}>
          <TouchableOpacity
            style={styles.gridItem}
            onPress={() => navigation.navigate('History')}
          >
            <View style={[styles.gridIconBg, { backgroundColor: '#E3F2FD' }]}>
              <MaterialIcons name="history" size={26} color="#2196F3" />
            </View>
            <Text style={styles.gridTitle}>My History</Text>
            <Text style={styles.gridDesc}>Past logs</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.gridItem}
            onPress={() => navigation.navigate('Profile')}
          >
            <View style={[styles.gridIconBg, { backgroundColor: '#E8F5E9' }]}>
              <MaterialIcons name="person" size={26} color="#4CAF50" />
            </View>
            <Text style={styles.gridTitle}>My Profile</Text>
            <Text style={styles.gridDesc}>Manage account</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <MaterialIcons name="exit-to-app" size={20} color="#D32F2F" />
          <Text style={styles.logoutButtonText}>Log Out of System</Text>
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
  scrollContent: {
    padding: 16,
    paddingBottom: 32,
  },
  banner: {
    backgroundColor: '#1976D2',
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    shadowColor: '#1976D2',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  greetingText: {
    color: '#E3F2FD',
    fontSize: 14,
    fontWeight: '500',
  },
  agentNameText: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    marginTop: 2,
  },
  agentIdText: {
    color: '#E3F2FD',
    fontSize: 12,
    marginTop: 6,
    letterSpacing: 0.5,
  },
  timeCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#EEEEEE',
  },
  dateText: {
    fontSize: 14,
    color: '#757575',
    fontWeight: '500',
  },
  timeText: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1976D2',
    marginTop: 4,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#424242',
    marginTop: 20,
    marginBottom: 10,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  actionButton: {
    height: 54,
    borderRadius: 12,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
    elevation: 3,
  },
  checkinButton: {
    backgroundColor: '#4CAF50',
    shadowColor: '#4CAF50',
  },
  checkoutButton: {
    backgroundColor: '#FF9800',
    shadowColor: '#FF9800',
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
    marginLeft: 8,
  },
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  gridItem: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    width: '48%',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#EEEEEE',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  gridIconBg: {
    width: 46,
    height: 46,
    borderRadius: 23,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  gridTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#212121',
  },
  gridDesc: {
    fontSize: 11,
    color: '#757575',
    marginTop: 2,
  },
  logoutButton: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 32,
    padding: 12,
    borderWidth: 1.5,
    borderColor: '#FFCDD2',
    borderRadius: 10,
    backgroundColor: '#FFEBEE',
  },
  logoutButtonText: {
    color: '#D32F2F',
    fontWeight: 'bold',
    marginLeft: 8,
    fontSize: 14,
  },
  dateGroupCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    marginBottom: 16,
    padding: 12,
    shadowColor: '#000',
    shadowOpacity: 0.03,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
    elevation: 2,
  },
  dateGroupHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: '#EEEEEE',
    paddingBottom: 8,
    marginBottom: 8,
  },
  dateGroupHeaderText: {
    fontWeight: 'bold',
    fontSize: 13,
    color: '#1976D2',
    marginLeft: 6,
  },
  salesTable: {
    width: '100%',
  },
  tableHeaderRow: {
    flexDirection: 'row',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: '#EEEEEE',
  },
  columnHeader: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#757575',
    textTransform: 'uppercase',
  },
  tableBodyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  productCell: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  productThumbnail: {
    width: 24,
    height: 24,
    borderRadius: 4,
    backgroundColor: '#EEEEEE',
    marginRight: 6,
  },
  productCellText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#333',
    flex: 1,
  },
  tableBodyCell: {
    fontSize: 12,
    color: '#333',
  },
  emptySalesCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#EEEEEE',
    marginBottom: 16,
  },
  emptySalesText: {
    color: '#757575',
    fontSize: 13,
  },
  alertBanner: {
    backgroundColor: '#FFEBEE',
    borderWidth: 1,
    borderColor: '#FFCDD2',
    borderRadius: 12,
    padding: 14,
    marginHorizontal: 16,
    marginBottom: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  alertBannerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    paddingRight: 8,
  },
  alertBannerTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#C62828',
  },
  alertBannerDesc: {
    fontSize: 11,
    color: '#D32F2F',
    marginTop: 2,
    lineHeight: 14,
  },
  alertBannerBtn: {
    backgroundColor: '#D32F2F',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 6,
  },
  alertBannerBtnText: {
    color: '#fff',
    fontSize: 11,
    fontWeight: 'bold',
  },
});
