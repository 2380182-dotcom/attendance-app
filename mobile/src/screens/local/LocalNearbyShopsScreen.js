import React, { useEffect } from 'react';
import { FlatList, SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import Loading from '../../components/Loading';
import AppButton from '../../components/AppButton';
import ErrorState from '../../components/ErrorState';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';
import { STRINGS } from './strings';
import { useNearbyShops } from '../../hooks/useNearbyShops';

/** `{en, ur}` -> "English\nUrdu", the pattern every message on this screen uses. No Urdu (e.g. a raw server error) -> English only. */
function bilingual(strings) {
  return strings.ur ? `${strings.en}\n${strings.ur}` : strings.en;
}

function statusToStrings(status) {
  if (status.key === 'SLOW_GPS') return STRINGS.slowGps;
  if (status.key === 'SERVER_WAKING') return STRINGS.serverWaking(status.attempt, status.total);
  return STRINGS.findingShops;
}

function errorToStrings(error) {
  if (!error) return null;
  if (error.key === 'PERMISSION') return STRINGS.locationNeeded;
  if (error.key === 'GPS_TIMEOUT') return STRINGS.gpsTimeout;
  return { en: error.detail || 'Unable to find nearby shops.', ur: '' };
}

/**
 * SALESMAN_LOCAL: shops the salesman is physically at, nearest first. Same
 * server endpoint as the LMT list (radius + admin buffer, computed
 * server-side), but a deliberately plainer screen — big rows, no detail
 * card, and NO manual shop-code fallback: a local salesman's proof of being
 * at a shop is the geofence, and the server enforces it again at save time.
 *
 * One-voucher-per-visit (Task 2): no more SALE/RETURN mode picked here —
 * tapping a shop goes to LocalEntryScreen, which now covers sale and
 * optional return together in one flow.
 */
export default function LocalNearbyShopsScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const modeColor = colors.successDark;

  const { loading, error, shops, status, refetch } = useNearbyShops();

  useEffect(() => {
    navigation.setOptions({ title: STRINGS.recordVisit.en });
  }, [navigation]);

  if (loading) {
    return <Loading message={bilingual(statusToStrings(status))} fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={[styles.banner, { backgroundColor: modeColor }]}>
        <Text style={styles.bannerEnglish}>{STRINGS.recordVisit.en} — {STRINGS.pickShop.en}</Text>
        <Text style={styles.bannerUrdu}>{STRINGS.pickShop.ur}</Text>
      </View>

      {error ? (
        <ErrorState message={bilingual(errorToStrings(error))} onRetry={refetch} />
      ) : (
        <FlatList
          data={shops}
          keyExtractor={(item) => item.id.toString()}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <TouchableOpacity
              activeOpacity={0.8}
              style={styles.shopRow}
              onPress={() => navigation.navigate('LocalEntry', { shop: item })}
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
          title={`${STRINGS.scanQr.en} · ${STRINGS.scanQr.ur}`}
          variant="success"
          size="lg"
          icon="qr-code-scanner"
          onPress={() => navigation.navigate('LocalScanShopQr')}
          style={{ marginBottom: 8 }}
        />
        <AppButton
          title={`${STRINGS.refresh.en} · ${STRINGS.refresh.ur}`}
          variant="outline"
          size="lg"
          icon="refresh"
          onPress={refetch}
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
