import React, { useState } from 'react';
import { StyleSheet, View, Text, TextInput, SafeAreaView, ScrollView } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import ErrorState from '../../components/ErrorState';
import { useTheme } from '../../theme';

/**
 * Sub-stage 2 (SALESMAN_LMT): shop-code entry -> server-side lookup ->
 * read-only display of what admin registered for this shop. Sub-stage 5
 * added the "Record Sale" hand-off into RecordVisitScreen below — this
 * screen itself still never writes anything; it only ever calls
 * GET /lmt/customer-shops/code/{code}.
 */
export default function ShopLookupScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [shopCode, setShopCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [shop, setShop] = useState(null);

  const handleLookup = async () => {
    const code = shopCode.trim();
    if (!code) {
      setError('Enter a shop code.');
      return;
    }
    setLoading(true);
    setError(null);
    setShop(null);
    try {
      const data = await apiService.lmt.getShopByCode(code);
      setShop(data);
    } catch (e) {
      // apiService.lmt.getShopByCode surfaces the backend's own message
      // directly (e.g. "No customer shop registered with code: XYZ"), so
      // there's nothing to translate here.
      setError(e.message || 'Could not find that shop.');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setShop(null);
    setError(null);
    setShopCode('');
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        <AppCard style={styles.card}>
          <Text style={styles.title}>Shop Lookup</Text>
          <Text style={styles.subtitle}>
            Enter the shop code to pull up what admin registered — you never type the shop's
            name, address, or other details by hand.
          </Text>

          <Text style={styles.label}>Shop Code</Text>
          <TextInput
            style={styles.textInput}
            placeholder="e.g. SHOP-001"
            placeholderTextColor={colors.textMuted}
            autoCapitalize="characters"
            autoCorrect={false}
            value={shopCode}
            onChangeText={setShopCode}
            editable={!loading}
            onSubmitEditing={handleLookup}
            returnKeyType="search"
          />

          <AppButton
            title="Look Up Shop"
            onPress={handleLookup}
            loading={loading}
            disabled={loading}
            icon="search"
          />

          {error && (
            <ErrorState
              title="Shop not found"
              message={error}
              retryLabel="Try Again"
              onRetry={handleLookup}
              style={styles.errorState}
            />
          )}
        </AppCard>

        {shop && (
          <AppCard style={styles.card}>
            <View style={styles.shopHeaderRow}>
              <MaterialIcons name="storefront" size={28} color={colors.primary} />
              <Text style={styles.shopName}>{shop.shopName}</Text>
            </View>
            <View style={styles.divider} />

            <InfoRow icon="tag" label="Shop Code" value={shop.shopCode} colors={colors} />
            <InfoRow icon="map" label="Area" value={shop.area?.name} colors={colors} />
            <InfoRow icon="business" label="Branch" value={shop.branch} colors={colors} />
            <InfoRow icon="place" label="Address" value={shop.address} colors={colors} />
            <InfoRow icon="phone" label="Phone" value={shop.phone} colors={colors} />
            <InfoRow icon="smartphone" label="Mobile" value={shop.mobile} colors={colors} />

            {/* Area hierarchy — same AreaDTO the web admin screens already
                use (tse/srTse/asm), just not previously surfaced here. */}
            <InfoRow icon="engineering" label="TSE" value={shop.area?.tse?.name} colors={colors} />
            <InfoRow icon="engineering" label="SR TSE" value={shop.area?.srTse?.name} colors={colors} />
            <InfoRow icon="supervisor-account" label="ASM" value={shop.area?.asm?.name} colors={colors} />

            {shop.isActive === false && (
              <Text style={styles.inactiveWarning}>
                This shop is marked inactive by admin — a visit here will be rejected.
              </Text>
            )}

            {shop.isActive !== false && (
              <AppButton
                title="Record Sale"
                variant="success"
                icon="point-of-sale"
                onPress={() => navigation.navigate('RecordVisit', { shop })}
                style={styles.recordButton}
              />
            )}

            <AppButton
              title="Look Up Another Shop"
              variant="outline"
              onPress={handleReset}
              style={styles.resetButton}
            />
          </AppCard>
        )}
      </ScrollView>
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
    label: { fontSize: 13, color: colors.textSecondary, marginBottom: 6 },
    textInput: {
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: 8,
      paddingHorizontal: 12,
      paddingVertical: 10,
      fontSize: 16,
      color: colors.textPrimary,
      backgroundColor: colors.inputBackground,
      marginBottom: 16,
    },
    errorState: { marginTop: 16 },
    shopHeaderRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
    shopName: { fontSize: 18, fontWeight: 'bold', marginLeft: 10, flex: 1, color: colors.textPrimary },
    divider: { height: 1, backgroundColor: colors.divider, marginBottom: 16 },
    inactiveWarning: { fontSize: 13, color: colors.error, marginBottom: 16 },
    recordButton: { marginBottom: 8 },
    resetButton: { marginTop: 4 },
  });
