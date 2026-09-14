import React, { useCallback, useContext, useEffect, useState } from 'react';
import { SafeAreaView, ScrollView, RefreshControl, Text, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import StatusCard from '../../components/StatusCard';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

/**
 * Sub-stage 3 (SALESMAN_LMT): on-duty status, mirroring AgentDashboardScreen's
 * StatusCard pattern exactly (same two calls: is-checked-in + /current) —
 * but deliberately without the mid-shift verification banner, sales
 * history, or "Dawn Bread Sales" button that screen also carries, none of
 * which apply to this role's flow. This becomes the SALESMAN_LMT landing
 * screen, replacing the placeholder "Go to Check-In" link that lived on
 * ShopLookupScreen.
 */
export default function LmtHomeScreen({ navigation }) {
  const { colors } = useTheme();
  const { user } = useContext(AuthContext);
  const [isCheckedIn, setIsCheckedIn] = useState(false);
  const [currentCheckIn, setCurrentCheckIn] = useState(null);
  const [todayStock, setTodayStock] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

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
    } catch (e) {
      console.error('Error fetching attendance status', e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user]);

  // Soft-gate only — a banner prompting the LMT to enter today's stock,
  // never a hard block on navigating elsewhere (a network hiccup here
  // shouldn't strand them). Missing stock just means the reconciliation
  // this feeds at night will have nothing to compare Sold against.
  const fetchTodayStock = useCallback(async () => {
    if (!user?.id) return;
    try {
      const stock = await apiService.lmt.getTodayStock(user.id);
      setTodayStock(stock);
    } catch (e) {
      console.warn('Could not load today\'s stock status', e);
    }
  }, [user]);

  useEffect(() => {
    fetchStatus();
    fetchTodayStock();
  }, [fetchStatus, fetchTodayStock]);

  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      fetchStatus();
      fetchTodayStock();
    });
    return unsubscribe;
  }, [navigation, fetchStatus, fetchTodayStock]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchStatus();
    fetchTodayStock();
  };

  if (loading) {
    return <Loading message="Loading status..." fullScreen />;
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView
        contentContainerStyle={{ padding: 16 }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
      >
        <StatusCard isCheckedIn={isCheckedIn} currentCheckIn={currentCheckIn} />

        {isCheckedIn && !todayStock && (
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              backgroundColor: colors.surfaceMuted,
              borderRadius: 10,
              borderWidth: 1,
              borderColor: colors.border,
              padding: 12,
              marginTop: 16,
            }}
          >
            <MaterialIcons name="inventory" size={22} color={colors.primary} />
            <View style={{ flex: 1, marginLeft: 10 }}>
              <Text style={{ fontSize: 13, fontWeight: '600', color: colors.textPrimary }}>
                Enter your stock to start selling
              </Text>
              <Text style={{ fontSize: 12, color: colors.textSecondary, marginTop: 2 }}>
                Record today's opening stock per product before visiting shops.
              </Text>
            </View>
          </View>
        )}

        {isCheckedIn && !todayStock && (
          <AppButton
            title="Enter Today's Stock"
            onPress={() => navigation.navigate('MorningStockEntry')}
            variant="primary"
            icon="inventory"
            style={{ marginTop: 10, marginBottom: 8 }}
          />
        )}

        {!isCheckedIn ? (
          <AppButton
            title="Proceed to Check-In"
            onPress={() => navigation.navigate('Checkin')}
            variant="success"
            icon="login"
            style={{ marginTop: 16, marginBottom: 8 }}
          />
        ) : (
          <>
            <AppButton
              title="End Duty"
              onPress={() => navigation.navigate('Checkout')}
              variant="danger"
              icon="logout"
              style={{ marginTop: 16, marginBottom: 8 }}
            />
            <AppButton
              title="Look Up a Shop"
              onPress={() => navigation.navigate('ShopLookup')}
              variant="primary"
              icon="storefront"
              style={{ marginBottom: 8 }}
            />
          </>
        )}

        {!isCheckedIn && (
          <Text style={{ fontSize: 13, color: colors.textSecondary, textAlign: 'center', marginTop: 4 }}>
            Check in first thing in the morning before visiting shops.
          </Text>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
