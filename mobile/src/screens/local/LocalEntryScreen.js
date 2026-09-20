import React, { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Alert, FlatList, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import * as Location from 'expo-location';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import LocationService from '../../services/LocationService';
import Loading from '../../components/Loading';
import AppButton from '../../components/AppButton';
import ProductThumbnail from '../../components/ProductThumbnail';
import QuantityStepper from '../../components/QuantityStepper';
import SearchBar from '../../components/SearchBar';
import { useTheme } from '../../theme';
import { STRINGS } from './strings';
import { computeTotals, formatRs, getFinalUnitPrice } from './pricing';

/**
 * SALESMAN_LOCAL entry screen for ONE shop, ONE kind of entry (sales OR
 * returns, chosen on the home screen). Every product is a row — photo on the
 * left, name, the price the shop will actually pay, then the − / number / +
 * stepper — so there is no "Add" step: the number IS the entry. Totals at the
 * bottom update on every tap.
 *
 * Saving asks first, in plain words ("Sold 20 breads — Total Rs 2,000"), then
 * sends the same /sales/shop-visit request the LMT flow uses. Prices and
 * totals shown here are a preview only; the server recomputes the real
 * amounts and enforces the geofence again at save time.
 */
export default function LocalEntryScreen({ route, navigation }) {
  const { shop } = route.params;
  const mode = route.params.mode === 'RETURN' ? 'RETURN' : 'SALE';
  const isReturn = mode === 'RETURN';
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const modeColor = isReturn ? colors.warningDark : colors.successDark;
  const modeLabel = isReturn ? STRINGS.enterReturn : STRINGS.enterSales;

  const [products, setProducts] = useState([]);
  const [shopPrices, setShopPrices] = useState([]);
  const [shopDiscounts, setShopDiscounts] = useState([]);
  const [quantities, setQuantities] = useState({});
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    navigation.setOptions({ title: modeLabel.en });
  }, [navigation, modeLabel.en]);

  const load = useCallback(async () => {
    try {
      // Prices/discounts are a preview aid: if either fails to load, the
      // screen still works and the server still charges the right amount.
      const [productList, prices, discounts] = await Promise.all([
        apiService.sales.getProducts(),
        apiService.lmt.getShopProductPrices(shop.id).catch(() => []),
        apiService.lmt.getShopProductDiscounts(shop.id).catch(() => []),
      ]);
      setProducts(productList || []);
      setShopPrices(prices || []);
      setShopDiscounts(discounts || []);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to fetch the bread list.');
    } finally {
      setLoading(false);
    }
  }, [shop.id]);

  useEffect(() => {
    load();
  }, [load]);

  const totals = useMemo(
    () => computeTotals(products, quantities, mode, shop, shopPrices, shopDiscounts),
    [products, quantities, mode, shop, shopPrices, shopDiscounts]
  );

  const visibleProducts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return q ? products.filter((p) => p.name.toLowerCase().includes(q)) : products;
  }, [products, searchQuery]);

  const setQuantity = (productId, next) => {
    setQuantities((prev) => ({ ...prev, [productId]: next }));
  };

  const submit = async () => {
    setSaving(true);
    try {
      // Fresh GPS right at save time — the server measures the distance
      // from this point to the shop and refuses if it is too far.
      const status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          Alert.alert(STRINGS.saveFailed.en, `${STRINGS.locationNeeded.en}\n${STRINGS.locationNeeded.ur}`);
          return;
        }
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });

      await apiService.lmt.submitShopVisit({
        agentId: user.id,
        shopCode: shop.shopCode,
        latitude: current.coords.latitude,
        longitude: current.coords.longitude,
        items: totals.lines.map((line) => ({
          productId: line.product.id,
          quantity: line.quantity,
          transactionType: mode,
        })),
      });
      Alert.alert(`${STRINGS.savedTitle.en} · ${STRINGS.savedTitle.ur}`, `${STRINGS.savedBody.en}\n${STRINGS.savedBody.ur}`, [
        { text: 'OK', onPress: () => navigation.navigate('LocalHome') },
      ]);
    } catch (e) {
      console.error(e);
      // The server's message is plain English ("Too far from ...", "Duplicate entry ...").
      setConfirming(false);
      Alert.alert(`${STRINGS.saveFailed.en} · ${STRINGS.saveFailed.ur}`, e.message || 'Error occurred while saving.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Loading message={`${STRINGS.loadingProducts.en}\n${STRINGS.loadingProducts.ur}`} fullScreen />;
  }
  if (saving) {
    return <Loading message={`${STRINGS.saving.en}\n${STRINGS.saving.ur}`} fullScreen />;
  }

  const kindWord = isReturn ? STRINGS.returned : STRINGS.sold;
  const amountLabel = isReturn ? STRINGS.returnValue : STRINGS.totalRs;

  if (confirming) {
    return (
      <SafeAreaView style={styles.container}>
        <ScrollView contentContainerStyle={styles.confirmContent}>
          <Text style={styles.confirmShop}>{shop.shopName}</Text>
          <Text style={styles.confirmPrompt}>{STRINGS.checkThenSave.en}</Text>
          <Text style={styles.confirmPromptUrdu}>{STRINGS.checkThenSave.ur}</Text>

          <View style={[styles.summaryBox, { borderColor: modeColor }]}>
            <Text style={[styles.summaryLine, { color: modeColor }]}>
              {kindWord.en} {totals.totalBreads} {STRINGS.breads.en}
            </Text>
            <Text style={[styles.summaryLineUrdu, { color: modeColor }]}>
              {kindWord.ur} {totals.totalBreads} {STRINGS.breads.ur}
            </Text>
            <Text style={styles.summaryAmount}>
              {amountLabel.en} {formatRs(totals.totalRs)}
            </Text>
            <Text style={styles.summaryAmountUrdu}>
              {amountLabel.ur} {formatRs(totals.totalRs)}
            </Text>
          </View>

          {totals.lines.map((line) => (
            <View key={line.product.id} style={styles.confirmLine}>
              <Text style={styles.confirmLineName} numberOfLines={2}>{line.product.name}</Text>
              <Text style={styles.confirmLineQty}>× {line.quantity}</Text>
              <Text style={styles.confirmLineTotal}>{formatRs(line.lineTotal)}</Text>
            </View>
          ))}
        </ScrollView>

        <View style={styles.confirmButtons}>
          <AppButton
            title={`${STRINGS.yesSave.en} · ${STRINGS.yesSave.ur}`}
            variant="success"
            size="lg"
            icon="check-circle"
            onPress={submit}
            style={{ marginBottom: 10 }}
          />
          <AppButton
            title={`${STRINGS.goBack.en} · ${STRINGS.goBack.ur}`}
            variant="outline"
            size="lg"
            onPress={() => setConfirming(false)}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={[styles.shopBanner, { backgroundColor: modeColor }]}>
        <Text style={styles.shopBannerName} numberOfLines={1}>{shop.shopName}</Text>
        <Text style={styles.shopBannerMode}>{modeLabel.en} · {modeLabel.ur}</Text>
      </View>

      <SearchBar
        value={searchQuery}
        onChangeText={setSearchQuery}
        placeholder={`${STRINGS.searchBread.en} ${STRINGS.searchBread.ur}`}
        style={styles.search}
      />

      <FlatList
        data={visibleProducts}
        keyExtractor={(item) => item.id.toString()}
        extraData={quantities}
        contentContainerStyle={styles.listContent}
        keyboardShouldPersistTaps="handled"
        renderItem={({ item }) => {
          const quantity = quantities[item.id] || 0;
          const unitPrice = getFinalUnitPrice(item, mode, shop, shopPrices, shopDiscounts);
          return (
            <View style={[styles.row, quantity > 0 && { borderColor: modeColor, borderWidth: 2 }]}>
              <ProductThumbnail uri={item.thumbnailUrl} size={72} />
              <View style={styles.rowInfo}>
                <Text style={styles.productName} numberOfLines={2}>{item.name}</Text>
                <Text style={[styles.productPrice, { color: modeColor }]}>Rs {formatRs(unitPrice)}</Text>
              </View>
              <QuantityStepper
                value={quantity}
                onChange={(next) => setQuantity(item.id, next)}
                color={modeColor}
                label={item.name}
              />
            </View>
          );
        }}
      />

      <View style={styles.totalBar}>
        {totals.totalBreads === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: 6 }}>
            <Text style={styles.hint}>{STRINGS.nothingYet.en}</Text>
            <Text style={styles.hintUrdu}>{STRINGS.nothingYet.ur}</Text>
          </View>
        ) : (
          <View style={styles.totalsRow}>
            <View style={styles.totalCell}>
              <Text style={styles.totalLabel}>{STRINGS.totalBreads.en} · {STRINGS.totalBreads.ur}</Text>
              <Text style={styles.totalValue}>{totals.totalBreads}</Text>
            </View>
            <View style={styles.totalCell}>
              <Text style={styles.totalLabel}>{amountLabel.en} · {amountLabel.ur}</Text>
              <Text style={[styles.totalValue, { color: modeColor }]}>{formatRs(totals.totalRs)}</Text>
            </View>
          </View>
        )}
        <AppButton
          title={`${STRINGS.save.en} · ${STRINGS.save.ur}`}
          variant={isReturn ? 'warning' : 'success'}
          size="lg"
          icon="save"
          disabled={totals.totalBreads === 0}
          onPress={() => setConfirming(true)}
          style={{ marginTop: 8 }}
        />
      </View>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    shopBanner: { paddingHorizontal: 16, paddingVertical: 10 },
    shopBannerName: { fontSize: 20, fontWeight: 'bold', color: '#FFFFFF' },
    shopBannerMode: { fontSize: 15, color: '#FFFFFF', marginTop: 2 },
    search: { margin: 10 },
    listContent: { paddingHorizontal: 10, paddingBottom: 12 },
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderRadius: 14,
      borderWidth: 1,
      borderColor: colors.border,
      padding: 10,
      marginBottom: 10,
    },
    rowInfo: { flex: 1, marginHorizontal: 10 },
    productName: { fontSize: 18, fontWeight: 'bold', color: colors.textPrimary },
    productPrice: { fontSize: 20, fontWeight: 'bold', marginTop: 4 },
    totalBar: {
      backgroundColor: colors.surface,
      padding: 12,
      borderTopWidth: 1,
      borderTopColor: colors.divider,
      elevation: 8,
    },
    totalsRow: { flexDirection: 'row', justifyContent: 'space-between' },
    totalCell: { flex: 1 },
    totalLabel: { fontSize: 13, color: colors.textSecondary },
    totalValue: { fontSize: 30, fontWeight: 'bold', color: colors.textPrimary },
    hint: { fontSize: 17, color: colors.textSecondary },
    hintUrdu: { fontSize: 15, color: colors.textMuted },
    confirmContent: { padding: 20 },
    confirmShop: { fontSize: 24, fontWeight: 'bold', color: colors.textPrimary },
    confirmPrompt: { fontSize: 18, color: colors.textSecondary, marginTop: 6 },
    confirmPromptUrdu: { fontSize: 16, color: colors.textMuted, marginBottom: 16 },
    summaryBox: {
      borderWidth: 3,
      borderRadius: 16,
      backgroundColor: colors.surface,
      padding: 18,
      marginBottom: 16,
    },
    summaryLine: { fontSize: 30, fontWeight: 'bold' },
    summaryLineUrdu: { fontSize: 24, marginBottom: 12 },
    summaryAmount: { fontSize: 30, fontWeight: 'bold', color: colors.textPrimary },
    summaryAmountUrdu: { fontSize: 24, color: colors.textPrimary },
    confirmLine: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: 10,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    confirmLineName: { flex: 1, fontSize: 17, color: colors.textPrimary },
    confirmLineQty: { fontSize: 18, fontWeight: 'bold', color: colors.textPrimary, marginHorizontal: 10 },
    confirmLineTotal: { fontSize: 17, color: colors.textSecondary, minWidth: 70, textAlign: 'right' },
    confirmButtons: { padding: 16, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.divider },
  });
