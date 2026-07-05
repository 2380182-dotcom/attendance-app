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
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import StatusCard from '../../components/StatusCard';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';
import ProductThumbnail from '../../components/ProductThumbnail';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import EmptyState from '../../components/EmptyState';
import AnimatedListItem from '../../components/AnimatedListItem';
import { useTheme, typography } from '../../theme';

export default function DashboardScreen({ navigation, route }) {
  const { colors, spacing } = useTheme();
  const styles = createStyles(colors, spacing);
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

    return Object.keys(groups).sort((a, b) => b.localeCompare(a)).map((date, groupIdx) => (
      <AnimatedListItem key={date} index={groupIdx}>
        <AppCard style={styles.dateGroupCard}>
          <View style={styles.dateGroupHeader}>
            <MaterialIcons name="event" size={16} color={colors.primary} />
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
                    <ProductThumbnail uri={item.productImageUrl} size={24} style={styles.productThumbnail} />
                    <Text style={styles.productCellText} numberOfLines={1}>{item.productName}</Text>
                  </View>
                  <Text style={[styles.tableBodyCell, { flex: 0.6, textAlign: 'center', fontWeight: 'bold' }]}>
                    {item.quantity}
                  </Text>
                  <Text style={[styles.tableBodyCell, { flex: 1, textAlign: 'center', color: colors.textSecondary, fontSize: 11 }]}>
                    {timeStr}
                  </Text>
                  <Text style={[styles.tableBodyCell, { flex: 1.2, textAlign: 'right', fontWeight: 'bold', color: colors.primary }]}>
                    PKR {item.totalPrice}
                  </Text>
                </View>
              );
            })}
          </View>
        </AppCard>
      </AnimatedListItem>
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
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
      >
        <View style={styles.banner}>
          <Text style={styles.greetingText}>{getGreeting()},</Text>
          <Text style={styles.agentNameText}>{user?.name || 'Agent'}</Text>
          <Text style={styles.agentIdText}>Agent ID: {user?.agentId || 'N/A'} | Dept: {user?.department || 'N/A'}</Text>
        </View>

        <AppCard style={styles.timeCard}>
          <Text style={styles.dateText}>
            {currentTime.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
          </Text>
          <Text style={styles.timeText}>
            {currentTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </Text>
        </AppCard>

        {isCheckedIn && user?.faceVerifyAnytime && !currentCheckIn?.midDayVerificationTime && (
          <AppCard style={styles.alertBanner}>
            <View style={styles.alertBannerLeft}>
              <MaterialIcons name="warning" size={24} color={colors.warning} />
              <View style={{ marginLeft: 12, flex: 1 }}>
                <Text style={styles.alertBannerTitle}>Verification Required</Text>
                <Text style={styles.alertBannerDesc}>Please submit your face verification check for today.</Text>
              </View>
            </View>
            <AppButton
              title="VERIFY"
              onPress={() => setMidDayModalVisible(true)}
              variant="danger"
              size="sm"
              fullWidth={false}
            />
          </AppCard>
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
            <AppButton
              title="Proceed to Check-In"
              onPress={() => navigation.navigate('Checkin')}
              variant="success"
              icon="login"
              style={{ marginBottom: 8 }}
            />
          </>
        )}

        <Text style={styles.sectionTitle}>Dawn Bread Sales</Text>
        <AppButton
          title="Enter Daily Sales"
          onPress={() => navigation.navigate('SalesEntry')}
          variant="accent"
          icon="shopping-cart"
          style={{ marginBottom: 12 }}
        />

        <Text style={styles.sectionTitle}>Agent Sales History</Text>
        {salesHistory.length === 0 ? (
          <AppCard>
            <EmptyState
              icon="assessment"
              title="No sales recorded yet"
              message="Submit your daily sales to see them listed here."
            />
          </AppCard>
        ) : (
          renderSalesHistoryTable()
        )}

        <Text style={styles.sectionTitle}>Resources & Logs</Text>
        <View style={styles.grid}>
          <AppCard onPress={() => navigation.navigate('History')} style={styles.gridItem} padding={16}>
            <View style={[styles.gridIconBg, { backgroundColor: colors.secondaryLight }]}>
              <MaterialIcons name="history" size={26} color={colors.secondary} />
            </View>
            <Text style={styles.gridTitle}>My History</Text>
            <Text style={styles.gridDesc}>Past logs</Text>
          </AppCard>

          <AppCard onPress={() => navigation.navigate('Profile')} style={styles.gridItem} padding={16}>
            <View style={[styles.gridIconBg, { backgroundColor: colors.successLight }]}>
              <MaterialIcons name="person" size={26} color={colors.success} />
            </View>
            <Text style={styles.gridTitle}>My Profile</Text>
            <Text style={styles.gridDesc}>Manage account</Text>
          </AppCard>
        </View>

        <AppButton
          title="Log Out of System"
          onPress={handleLogout}
          variant="ghost"
          icon="exit-to-app"
          style={{ backgroundColor: colors.errorLight, marginTop: 32 }}
          textStyle={{ color: colors.error }}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const createStyles = (colors, spacing) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    padding: spacing.lg,
    paddingBottom: spacing.xl,
  },
  banner: {
    backgroundColor: colors.primary,
    borderRadius: 20,
    padding: spacing.lg,
    marginBottom: spacing.lg,
    shadowColor: colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  greetingText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 14,
    fontWeight: '500',
  },
  agentNameText: {
    color: colors.textOnPrimary,
    fontSize: 24,
    fontWeight: 'bold',
    marginTop: 2,
  },
  agentIdText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 12,
    marginTop: 6,
    letterSpacing: 0.5,
  },
  timeCard: {
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  dateText: {
    fontSize: 14,
    color: colors.textSecondary,
    fontWeight: '500',
  },
  timeText: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.primary,
    marginTop: 4,
  },
  sectionTitle: {
    ...typography.label,
    color: colors.textSecondary,
    marginTop: spacing.lg,
    marginBottom: spacing.sm + 2,
    textTransform: 'uppercase',
  },
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  gridItem: {
    width: '48%',
    alignItems: 'center',
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
    color: colors.textPrimary,
  },
  gridDesc: {
    fontSize: 11,
    color: colors.textSecondary,
    marginTop: 2,
  },
  dateGroupCard: {
    marginBottom: spacing.md,
    padding: spacing.md,
  },
  dateGroupHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
    paddingBottom: 8,
    marginBottom: 8,
  },
  dateGroupHeaderText: {
    fontWeight: 'bold',
    fontSize: 13,
    color: colors.primary,
    marginLeft: 6,
  },
  salesTable: {
    width: '100%',
  },
  tableHeaderRow: {
    flexDirection: 'row',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  columnHeader: {
    fontSize: 11,
    fontWeight: 'bold',
    color: colors.textSecondary,
    textTransform: 'uppercase',
  },
  tableBodyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  productCell: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  productThumbnail: {
    marginRight: 6,
  },
  productCellText: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textPrimary,
    flex: 1,
  },
  tableBodyCell: {
    fontSize: 12,
    color: colors.textPrimary,
  },
  alertBanner: {
    backgroundColor: colors.errorLight,
    marginBottom: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.md,
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
    color: colors.error,
  },
  alertBannerDesc: {
    fontSize: 11,
    color: colors.error,
    marginTop: 2,
    lineHeight: 14,
  },
});
