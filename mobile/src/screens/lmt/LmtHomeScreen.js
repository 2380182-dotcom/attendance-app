import React, { useCallback, useContext, useEffect, useState } from 'react';
import { SafeAreaView, ScrollView, RefreshControl, Text, View, Alert } from 'react-native';
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

  // PROMPT, not require — an LMT can always end duty without entering
  // Unsold; it just stays OPEN and shows up flagged in the Sales
  // Department's report. Only prompted when there's stock to account for
  // at all (todayStock exists) and it hasn't been submitted yet (still
  // OPEN). This is a convenience shortcut into Enter Unsold, not the only
  // way to reach it — that screen is always reachable below regardless.
  const handleEndDuty = () => {
    if (todayStock && todayStock.status === 'OPEN') {
      Alert.alert(
        'Enter Unsold Before Ending Duty?',
        'Enter today\'s Unsold quantities now, or skip and do it later.',
        [
          { text: 'Skip & End Duty', onPress: () => navigation.navigate('Checkout'), style: 'cancel' },
          { text: 'Enter Unsold Now', onPress: () => navigation.navigate('EnterUnsold') },
        ]
      );
    } else {
      navigation.navigate('Checkout');
    }
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

        {/*
          Both Enter Stock and Enter Unsold are always reachable once
          checked in — not time-locked to morning/night. Each is still
          entered once (the backend rejects a second morning-stock
          submission outright); this just means the LMT is never stuck
          waiting for a particular time of day to reach either screen.
        */}
        {isCheckedIn && (
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
                {todayStock ? 'Stock entered ✓' : 'Enter your stock to start selling'}
              </Text>
              <Text style={{ fontSize: 12, color: colors.textSecondary, marginTop: 2 }}>
                {todayStock
                  ? 'Today\'s opening stock is recorded.'
                  : 'Record today\'s opening stock per product before visiting shops.'}
              </Text>
            </View>
          </View>
        )}

        {isCheckedIn && (
          <AppButton
            title="Enter Today's Stock"
            onPress={() => navigation.navigate('MorningStockEntry')}
            variant="primary"
            icon="inventory"
            style={{ marginTop: 10, marginBottom: 8 }}
          />
        )}

        {isCheckedIn && (
          <AppButton
            title="Enter Unsold"
            onPress={() => navigation.navigate('EnterUnsold')}
            variant="primary"
            icon="inventory-2"
            style={{ marginBottom: 8 }}
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
              onPress={handleEndDuty}
              variant="danger"
              icon="logout"
              style={{ marginTop: 16, marginBottom: 8 }}
            />
            <AppButton
              title="Look Up a Shop"
              onPress={() => navigation.navigate('NearbyShops')}
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
