import React, { useCallback, useState } from 'react';
import { StyleSheet, View, Text, FlatList, TouchableOpacity, SafeAreaView, ScrollView } from 'react-native';
import * as Location from 'expo-location';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import LocationService from '../../services/LocationService';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import ErrorState from '../../components/ErrorState';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

/**
 * LMT flow redesign, D3: replaces manual shop-code entry as LmtHome's
 * primary entry point — fetches GPS, shows only registered shops within
 * server-computed radius+buffer range (GET /lmt/customer-shops/nearby),
 * nearest first. Tapping a shop shows the same detail fields
 * ShopLookupScreen already displays, then hands off to the unchanged
 * RecordVisitScreen exactly as before.
 *
 * ShopLookupScreen (manual code entry) is kept as a fallback, linked from
 * here — untouched by this screen, still reachable for shops without
 * geofencing configured (which can never appear in this nearby list) or
 * whenever GPS/nearby lookup doesn't turn up the right shop.
 */
export default function NearbyShopsScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [shops, setShops] = useState([]);
  const [selectedShop, setSelectedShop] = useState(null);

  const fetchNearbyShops = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      let status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          setError('Location permission is required to find nearby shops.');
          setLoading(false);
          return;
        }
      }
      const currentLoc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const { latitude, longitude } = currentLoc.coords;
      const nearby = await apiService.lmt.getNearbyShops(latitude, longitude);
      setShops(nearby || []);
    } catch (e) {
      console.error(e);
      setError(e.message || 'Unable to find nearby shops.');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchNearbyShops();
  }, [fetchNearbyShops]);

  if (loading) {
    return <Loading message="Finding nearby shops..." fullScreen />;
  }

  if (selectedShop) {
    const shop = selectedShop;
    return (
      <SafeAreaView style={styles.container}>
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <AppCard style={styles.card}>
            <Text style={styles.title}>{shop.shopName}</Text>
            <Text style={styles.subtitle}>Shop Code: {shop.shopCode}</Text>

            <InfoRow icon="map" label="Area" value={shop.area?.name} colors={colors} />
            <InfoRow icon="business" label="Branch" value={shop.branch} colors={colors} />
            <InfoRow icon="place" label="Address" value={shop.address} colors={colors} />
            <InfoRow icon="phone" label="Phone" value={shop.phone} colors={colors} />
            <InfoRow icon="smartphone" label="Mobile" value={shop.mobile} colors={colors} />
            <InfoRow icon="engineering" label="TSE" value={shop.area?.tse?.name} colors={colors} />
            <InfoRow icon="engineering" label="SR TSE" value={shop.area?.srTse?.name} colors={colors} />
            <InfoRow icon="supervisor-account" label="ASM" value={shop.area?.asm?.name} colors={colors} />

            <AppButton
              title="Record Sale"
              variant="success"
              icon="point-of-sale"
              onPress={() => navigation.navigate('RecordVisit', { shop })}
              style={{ marginTop: 8, marginBottom: 8 }}
            />
            <AppButton
              title="Back to Nearby Shops"
              variant="outline"
              onPress={() => setSelectedShop(null)}
            />
          </AppCard>
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      {error ? (
        <ErrorState message={error} onRetry={fetchNearbyShops} />
      ) : (
        <FlatList
          data={shops}
          keyExtractor={(item) => item.id.toString()}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <TouchableOpacity onPress={() => setSelectedShop(item)}>
              <AppCard style={styles.shopCard}>
                <View style={styles.shopRow}>
                  <MaterialIcons name="storefront" size={22} color={colors.primary} />
                  <View style={styles.shopInfo}>
                    <Text style={styles.shopName}>{item.shopName}</Text>
                    <Text style={styles.shopMeta}>{item.branch || item.area?.name || 'No branch'}</Text>
                  </View>
                  <Text style={styles.shopDistance}>
                    {item.distanceMeters != null ? `${Math.round(item.distanceMeters)}m` : ''}
                  </Text>
                </View>
              </AppCard>
            </TouchableOpacity>
          )}
          ListEmptyComponent={
            <EmptyState
              icon="storefront"
              title="No shops nearby"
              message="No registered shops within range. Move closer, or enter a shop code manually."
            />
          }
        />
      )}

      <View style={styles.footer}>
        <AppButton
          title="Enter Shop Code Manually"
          variant="outline"
          icon="keyboard"
          onPress={() => navigation.navigate('ShopLookup')}
        />
      </View>
    </SafeAreaView>
  );
}

function InfoRow({ icon, label, value, colors }) {
  return (
    <View style={rowStyles.row}>
      <MaterialIcons name={icon} size={20} color={colors.textSecondary} style={rowStyles.icon} />
      <Text style={[rowStyles.label, { color: colors.textSecondary }]}>{label}</Text>
      <Text style={[rowStyles.value, { color: colors.textPrimary }]}>{value || '—'}</Text>
    </View>
  );
}

const rowStyles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  icon: { marginRight: 12 },
  label: { fontSize: 13, width: 90 },
  value: { fontSize: 14, flex: 1 },
});

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    scrollContent: { padding: 16 },
    card: { marginBottom: 16 },
    title: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary, marginBottom: 4 },
    subtitle: { fontSize: 13, color: colors.textSecondary, marginBottom: 16 },
    listContent: { padding: 16, flexGrow: 1 },
    shopCard: { marginBottom: 10 },
    shopRow: { flexDirection: 'row', alignItems: 'center' },
    shopInfo: { flex: 1, marginLeft: 12 },
    shopName: { fontWeight: 'bold', fontSize: 15, color: colors.textPrimary },
    shopMeta: { fontSize: 12, color: colors.textSecondary, marginTop: 2 },
    shopDistance: { fontSize: 13, fontWeight: '600', color: colors.secondary },
    footer: {
      padding: 16,
      backgroundColor: colors.surface,
      borderTopWidth: 1,
      borderTopColor: colors.divider,
    },
  });
