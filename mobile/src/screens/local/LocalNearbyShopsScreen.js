import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import * as Location from 'expo-location';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import LocationService from '../../services/LocationService';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import AppButton from '../../components/AppButton';
import ErrorState from '../../components/ErrorState';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';
import { STRINGS } from './strings';

/**
 * SALESMAN_LOCAL: shops the salesman is physically at, nearest first. Same
 * server endpoint as the LMT list (radius + admin buffer, computed
 * server-side), but a deliberately plainer screen — big rows, no detail
 * card, and NO manual shop-code fallback: a local salesman's proof of being
 * at a shop is the geofence, and the server enforces it again at save time.
 *
 * mode is 'SALE' or 'RETURN' (from the home screen). Tapping a shop goes
 * straight to the entry screen for that mode.
 */
export default function LocalNearbyShopsScreen({ route, navigation }) {
  const mode = route.params?.mode === 'RETURN' ? 'RETURN' : 'SALE';
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const modeLabel = mode === 'RETURN' ? STRINGS.enterReturn : STRINGS.enterSales;
  const modeColor = mode === 'RETURN' ? colors.warningDark : colors.successDark;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [shops, setShops] = useState([]);

  useEffect(() => {
    navigation.setOptions({ title: modeLabel.en });
  }, [navigation, modeLabel.en]);

  const fetchNearbyShops = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          setError(`${STRINGS.locationNeeded.en}\n${STRINGS.locationNeeded.ur}`);
          return;
        }
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const nearby = await apiService.lmt.getNearbyShops(current.coords.latitude, current.coords.longitude);
      setShops(nearby || []);
    } catch (e) {
      console.error(e);
      setError(e.message || 'Unable to find nearby shops.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNearbyShops();
  }, [fetchNearbyShops]);

  if (loading) {
    return <Loading message={`${STRINGS.findingShops.en}\n${STRINGS.findingShops.ur}`} fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={[styles.banner, { backgroundColor: modeColor }]}>
        <Text style={styles.bannerEnglish}>{modeLabel.en} — {STRINGS.pickShop.en}</Text>
        <Text style={styles.bannerUrdu}>{STRINGS.pickShop.ur}</Text>
      </View>

      {error ? (
        <ErrorState message={error} onRetry={fetchNearbyShops} />
      ) : (
        <FlatList
          data={shops}
          keyExtractor={(item) => item.id.toString()}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <TouchableOpacity
              activeOpacity={0.8}
              style={styles.shopRow}
              onPress={() => navigation.navigate('LocalEntry', { shop: item, mode })}
              accessibilityRole="button"
              accessibilityLabel={item.shopName}
            >
              <MaterialIcons name="storefront" size={40} color={modeColor} />
              <View style={styles.shopInfo}>
                <Text style={styles.shopName} numberOfLines={2}>{item.shopName}</Text>
                {!!(item.branch || item.area?.name) && (
                  <Text style={styles.shopMeta} numberOfLines={1}>{item.branch || item.area?.name}</Text>
                )}
              </View>
              {item.distanceMeters != null && (
                <Text style={styles.shopDistance}>{Math.round(item.distanceMeters)} m</Text>
              )}
            </TouchableOpacity>
          )}
          ListEmptyComponent={
            <EmptyState
              icon="storefront"
              title={`${STRINGS.noShops.en} · ${STRINGS.noShops.ur}`}
              message={`${STRINGS.noShopsHelp.en}\n${STRINGS.noShopsHelp.ur}`}
            />
          }
        />
      )}

      <View style={styles.footer}>
        <AppButton
          title={`${STRINGS.refresh.en} · ${STRINGS.refresh.ur}`}
          variant="outline"
          size="lg"
          icon="refresh"
          onPress={fetchNearbyShops}
        />
      </View>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    banner: { paddingHorizontal: 16, paddingVertical: 14 },
    bannerEnglish: { fontSize: 18, fontWeight: 'bold', color: '#FFFFFF' },
    bannerUrdu: { fontSize: 16, color: '#FFFFFF', marginTop: 2 },
    listContent: { padding: 12, paddingBottom: 24 },
    shopRow: {
      flexDirection: 'row',
      alignItems: 'center',
      minHeight: 84,
      backgroundColor: colors.surface,
      borderRadius: 14,
      borderWidth: 1,
      borderColor: colors.border,
      paddingHorizontal: 14,
      paddingVertical: 12,
      marginBottom: 12,
    },
    shopInfo: { flex: 1, marginHorizontal: 12 },
    shopName: { fontSize: 22, fontWeight: 'bold', color: colors.textPrimary },
    shopMeta: { fontSize: 15, color: colors.textSecondary, marginTop: 2 },
    shopDistance: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary },
    footer: { padding: 12 },
  });
