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
import SalesVoucher from '../../components/SalesVoucher';
import { cachedFetch } from '../../services/apiCache';
import { useTheme } from '../../theme';
import { STRINGS } from './strings';
import { computeTotals, formatRs, getFinalUnitPrice } from './pricing';

/** Once per screen mount — reused across retries of the same visit so a double-tap or a network-retry resubmit is idempotent. Re-entering this screen (a fresh visit) always gets its own id. */
function generateRequestId() {
  return `visit-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * SALESMAN_LOCAL entry screen for ONE shop, sale AND optional return
 * together in ONE voucher (Task 2 — the old separate "Enter Sales"/"Enter
 * Return" screens each produced their own SalesRecord for the same visit,
 * which is exactly the bug this redesign fixes; mirrors what
 * RecordVisitScreen already does for LMT).
 *
 * Flow: SALE (product list, steppers) -> ASK_RETURN ("Is there any
 * return?") -> RETURN (product list, steppers, only if Yes) -> CONFIRM
 * (sold items, returned items, sale/return/net totals) -> one submit.
 * A visit with zero sale AND zero return can't be saved — a return-only
 * visit (zero sale, some return) is fine, matching the spec exactly.
 *
 * Every product is a row — photo on the left, name, the price the shop will
 * actually pay, then the − / number / + stepper — so there is no "Add"
 * step: the number IS the entry. Totals at the bottom update on every tap.
 * Prices and totals shown here are a preview only; the server recomputes
 * the real amounts and enforces the geofence again at save time.
 */
export default function LocalEntryScreen({ route, navigation }) {
  const { shop } = route.params;
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const requestIdRef = React.useRef(generateRequestId());

  const [products, setProducts] = useState([]);
  const [shopPrices, setShopPrices] = useState([]);
  const [shopDiscounts, setShopDiscounts] = useState([]);
  const [saleQuantities, setSaleQuantities] = useState({});
  const [returnQuantities, setReturnQuantities] = useState({});
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [voucher, setVoucher] = useState(null);
  // 'SALE' | 'ASK_RETURN' | 'RETURN' | 'CONFIRM'
  const [step, setStep] = useState('SALE');

  useEffect(() => {
    const titles = { SALE: STRINGS.enterSales, RETURN: STRINGS.enterReturn };
    navigation.setOptions({ title: (titles[step] || STRINGS.recordVisit).en });
  }, [navigation, step]);

  const load = useCallback(async () => {
    try {
      // Prices/discounts are a preview aid: if either fails to load, the
      // screen still works and the server still charges the right amount.
      // A salesman opens this screen once per shop per visit, often dozens
      // of times a day — the product catalog and a given shop's prices/
      // discounts are cached briefly (apiCache) so repeat opens are
      // instant instead of a full round trip every time.
      const [productList, prices, discounts] = await Promise.all([
        cachedFetch('products', () => apiService.sales.getProducts(), 5 * 60 * 1000),
        cachedFetch(`shop-prices:${shop.id}`, () => apiService.lmt.getShopProductPrices(shop.id), 5 * 60 * 1000).catch(() => []),
        cachedFetch(`shop-discounts:${shop.id}`, () => apiService.lmt.getShopProductDiscounts(shop.id), 5 * 60 * 1000).catch(() => []),
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

  const saleTotals = useMemo(
    () => computeTotals(products, saleQuantities, 'SALE', shop, shopPrices, shopDiscounts),
    [products, saleQuantities, shop, shopPrices, shopDiscounts]
  );
  const returnTotals = useMemo(
    () => computeTotals(products, returnQuantities, 'RETURN', shop, shopPrices, shopDiscounts),
    [products, returnQuantities, shop, shopPrices, shopDiscounts]
  );
  const netRs = saleTotals.totalRs - returnTotals.totalRs;
  const hasNothing = saleTotals.totalBreads === 0 && returnTotals.totalBreads === 0;

  const visibleProducts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return q ? products.filter((p) => p.name.toLowerCase().includes(q)) : products;
  }, [products, searchQuery]);

  const setSaleQuantity = (productId, next) => {
    setSaleQuantities((prev) => ({ ...prev, [productId]: next }));
  };
  const setReturnQuantity = (productId, next) => {
    setReturnQuantities((prev) => ({ ...prev, [productId]: next }));
  };

  const goToConfirm = () => {
    if (hasNothing) {
      Alert.alert(
        `${STRINGS.nothingEnteredTitle.en} · ${STRINGS.nothingEnteredTitle.ur}`,
        `${STRINGS.nothingEnteredBody.en}\n${STRINGS.nothingEnteredBody.ur}`
      );
      return;
    }
    setStep('CONFIRM');
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
          setSaving(false);
          return;
        }
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });

      const items = [
        ...saleTotals.lines.map((line) => ({ productId: line.product.id, quantity: line.quantity, transactionType: 'SALE' })),
        ...returnTotals.lines.map((line) => ({ productId: line.product.id, quantity: line.quantity, transactionType: 'RETURN' })),
      ];

      const saved = await apiService.lmt.submitShopVisit({
        agentId: user.id,
        shopCode: shop.shopCode,
        latitude: current.coords.latitude,
        longitude: current.coords.longitude,
        items,
        requestId: requestIdRef.current,
      });
      // The voucher (SalesVoucher) is the confirmation now, shown next —
      // no separate "Saved!" alert, so the salesman sees exactly what was
      // recorded rather than just being told it worked.
      setVoucher(saved);
    } catch (e) {
      console.error(e);
      // The server's message is plain English ("Too far from ...", "Duplicate entry ...").
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
  if (voucher) {
    return (
      <SafeAreaView style={styles.container}>
        <SalesVoucher
          record={voucher}
          salesmanName={user?.name}
          employeeId={user?.agentId}
          onDone={() => navigation.navigate('LocalHome')}
        />
      </SafeAreaView>
    );
  }

  if (step === 'ASK_RETURN') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.askContent}>
          <Text style={styles.askQuestion}>{STRINGS.askReturn.en}</Text>
          <Text style={styles.askQuestionUrdu}>{STRINGS.askReturn.ur}</Text>

          {saleTotals.totalBreads > 0 && (
            <View style={styles.askSummary}>
              <Text style={styles.askSummaryText}>
                {STRINGS.sold.en} {saleTotals.totalBreads} {STRINGS.breads.en} — {STRINGS.totalRs.en} {formatRs(saleTotals.totalRs)}
              </Text>
            </View>
          )}

          <AppButton
            title={`${STRINGS.yes.en} · ${STRINGS.yes.ur}`}
            variant="warning"
            size="lg"
            icon="assignment-return"
            onPress={() => setStep('RETURN')}
            style={{ marginTop: 24, marginBottom: 12 }}
          />
          <AppButton
            title={`${STRINGS.no.en} · ${STRINGS.no.ur}`}
            variant="success"
            size="lg"
            icon="check-circle"
            onPress={goToConfirm}
          />
          <AppButton
            title={`${STRINGS.goBack.en} · ${STRINGS.goBack.ur}`}
            variant="outline"
            size="lg"
            onPress={() => setStep('SALE')}
            style={{ marginTop: 24 }}
          />
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'CONFIRM') {
    return (
      <SafeAreaView style={styles.container}>
        <ScrollView contentContainerStyle={styles.confirmContent}>
          <Text style={styles.confirmShop}>{shop.shopName}</Text>
          <Text style={styles.confirmPrompt}>{STRINGS.checkThenSave.en}</Text>
          <Text style={styles.confirmPromptUrdu}>{STRINGS.checkThenSave.ur}</Text>

          <View style={[styles.summaryBox, { borderColor: netRs < 0 ? colors.error : colors.successDark }]}>
            <View style={styles.totalsRow}>
              <Text style={styles.totalsLabel}>{STRINGS.saleTotal.en}</Text>
              <Text style={styles.totalsValue}>{formatRs(saleTotals.totalRs)}</Text>
            </View>
            <View style={styles.totalsRow}>
              <Text style={styles.totalsLabel}>{STRINGS.returnTotal.en}</Text>
              <Text style={styles.totalsValue}>{formatRs(returnTotals.totalRs)}</Text>
            </View>
            <View style={[styles.totalsRow, styles.netRow]}>
              <Text style={styles.netLabel}>{STRINGS.netTotal.en}</Text>
              <Text style={[styles.netValue, netRs < 0 && { color: colors.error }]}>{formatRs(netRs)}</Text>
            </View>
            {netRs < 0 && (
              <Text style={styles.netNegativeHint}>
                {STRINGS.netNegativeHint.en} · {STRINGS.netNegativeHint.ur}
              </Text>
            )}
          </View>

          {saleTotals.lines.length > 0 && (
            <>
              <Text style={styles.confirmSectionHeader}>{STRINGS.soldItems.en} · {STRINGS.soldItems.ur}</Text>
              {saleTotals.lines.map((line) => (
                <View key={`sale-${line.product.id}`} style={styles.confirmLine}>
                  <Text style={styles.confirmLineName} numberOfLines={2}>{line.product.name}</Text>
                  <Text style={styles.confirmLineQty}>× {line.quantity}</Text>
                  <Text style={styles.confirmLineTotal}>{formatRs(line.lineTotal)}</Text>
                </View>
              ))}
            </>
          )}

          {returnTotals.lines.length > 0 && (
            <>
              <Text style={styles.confirmSectionHeader}>{STRINGS.returnedItems.en} · {STRINGS.returnedItems.ur}</Text>
              {returnTotals.lines.map((line) => (
                <View key={`return-${line.product.id}`} style={styles.confirmLine}>
                  <Text style={styles.confirmLineName} numberOfLines={2}>{line.product.name}</Text>
                  <Text style={styles.confirmLineQty}>× {line.quantity}</Text>
                  <Text style={styles.confirmLineTotal}>{formatRs(line.lineTotal)}</Text>
                </View>
              ))}
            </>
          )}
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
            onPress={() => setStep('SALE')}
          />
        </View>
      </SafeAreaView>
    );
  }

  // step === 'SALE' or 'RETURN' — same product-list UI, bound to a different quantity map.
  const isReturn = step === 'RETURN';
  const modeColor = isReturn ? colors.warningDark : colors.successDark;
  const modeLabel = isReturn ? STRINGS.enterReturn : STRINGS.enterSales;
  const quantities = isReturn ? returnQuantities : saleQuantities;
  const setQuantity = isReturn ? setReturnQuantity : setSaleQuantity;
  const totals = isReturn ? returnTotals : saleTotals;
  const amountLabel = isReturn ? STRINGS.returnValue : STRINGS.totalRs;

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
          const unitPrice = getFinalUnitPrice(item, isReturn ? 'RETURN' : 'SALE', shop, shopPrices, shopDiscounts);
          return (
            <View style={[styles.row, quantity > 0 && { borderColor: modeColor, borderWidth: 2 }]}>
              <View style={styles.rowTop}>
                <ProductThumbnail productId={item.id} uri={item.thumbnailUrl} size={60} />
                <View style={styles.rowInfo}>
                  <Text style={styles.productName} numberOfLines={2} ellipsizeMode="tail">{item.name}</Text>
                  <Text style={[styles.productPrice, { color: modeColor }]}>Rs {formatRs(unitPrice)}</Text>
                  {quantity > 0 && (
                    <Text style={[styles.lineTotal, { color: modeColor }]} numberOfLines={1}>
                      {quantity} × {formatRs(unitPrice)} = {formatRs(unitPrice * quantity)}
                    </Text>
                  )}
                </View>
              </View>
              <View style={styles.rowStepperWrap}>
                <QuantityStepper
                  value={quantity}
                  onChange={(next) => setQuantity(item.id, next)}
                  color={modeColor}
                  label={item.name}
                />
              </View>
            </View>
          );
        }}
      />

      <View style={styles.totalBar}>
        {totals.totalBreads === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: 6 }}>
            <Text style={styles.hint}>{isReturn ? STRINGS.nothingYet.en : STRINGS.noSaleHint.en}</Text>
            <Text style={styles.hintUrdu}>{isReturn ? STRINGS.nothingYet.ur : STRINGS.noSaleHint.ur}</Text>
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
          title={`${STRINGS.next.en} · ${STRINGS.next.ur}`}
          variant={isReturn ? 'warning' : 'success'}
          size="lg"
          icon="arrow-forward"
          onPress={() => (isReturn ? goToConfirm() : setStep('ASK_RETURN'))}
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
    // Stacked, not a single row: photo+name/price on top (full card width),
    // the stepper on its own line below. A single row here starves the name
    // column — the stepper alone is ~210px wide, which on an average phone
    // left under 40px for the product name and made text wrap letter by
    // letter. Stacking guarantees the name/price/live-total always get the
    // full card width to breathe in.
    row: {
      backgroundColor: colors.surface,
      borderRadius: 14,
      borderWidth: 1,
      borderColor: colors.border,
      padding: 12,
      marginBottom: 10,
    },
    rowTop: { flexDirection: 'row', alignItems: 'center' },
    // minWidth: 0 is required for a flex:1 row-child in RN/Yoga to actually
    // shrink and wrap its Text instead of overflowing or collapsing to a
    // near-zero width (the root cause of the letter-by-letter wrapping).
    rowInfo: { flex: 1, minWidth: 0, marginLeft: 12 },
    productName: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary, flexShrink: 1 },
    productPrice: { fontSize: 17, fontWeight: '600', marginTop: 4 },
    lineTotal: { fontSize: 16, fontWeight: 'bold', marginTop: 4 },
    rowStepperWrap: { flexDirection: 'row', justifyContent: 'flex-end', marginTop: 10 },
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
    askContent: { flex: 1, padding: 24, justifyContent: 'center' },
    askQuestion: { fontSize: 28, fontWeight: 'bold', color: colors.textPrimary, textAlign: 'center' },
    askQuestionUrdu: { fontSize: 22, color: colors.textSecondary, textAlign: 'center', marginTop: 6 },
    askSummary: { marginTop: 20, alignItems: 'center' },
    askSummaryText: { fontSize: 16, color: colors.textSecondary, textAlign: 'center' },
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
    totalsLabel: { fontSize: 16, color: colors.textSecondary },
    totalsValue: { fontSize: 16, fontWeight: '600', color: colors.textPrimary },
    netRow: { marginTop: 8, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.divider },
    netLabel: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary },
    netValue: { fontSize: 24, fontWeight: 'bold', color: colors.textPrimary },
    netNegativeHint: { fontSize: 13, color: colors.error, textAlign: 'right', marginTop: 6 },
    confirmSectionHeader: {
      fontSize: 14,
      fontWeight: 'bold',
      color: colors.textSecondary,
      textTransform: 'uppercase',
      marginTop: 12,
      marginBottom: 4,
    },
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
