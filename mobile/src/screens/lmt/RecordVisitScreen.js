import React, { useCallback, useContext, useEffect, useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  SafeAreaView,
} from 'react-native';
import * as Location from 'expo-location';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import { AuthContext } from '../../context/AuthContext';
import LocationService from '../../services/LocationService';
import Loading from '../../components/Loading';
import ProductThumbnail from '../../components/ProductThumbnail';
import SearchBar from '../../components/SearchBar';
import AppButton from '../../components/AppButton';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

/**
 * Sub-stage 5 (SALESMAN_LMT): the one write-risk screen in Phase A.
 * Product list + cart mirrors SalesEntryScreen's existing pattern almost
 * exactly — deliberately not shared code, since that screen belongs to the
 * untouched legacy AGENT flow and this must never share a code path with
 * it (zero regression risk to the 25 agents already using it daily).
 *
 * LMT flow refinement: each product now takes a Sold qty AND a Returned
 * qty for this shop — Dawn Bread needs shop-wise return visibility, so
 * Returned is captured here per-shop rather than as a day-level lump
 * figure (see LmtStockService.reconcile, which now computes Returned from
 * these same per-shop RETURN rows). A cart entry maps to up to two
 * /sales/shop-visit line items: SALE when soldQty>0, RETURN when
 * returnQty>0. Unsold remains a separate day-level entry (Enter Unsold on
 * LmtHome), not a per-shop choice.
 *
 * Geofence: a client-side pre-check (shop radius + admin buffer) here is
 * purely a fast, friendly UX short-circuit — the server's own hard-gate in
 * /sales/shop-visit is the actual authority and is never bypassed by this.
 */
function calculateDistanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c * 1000;
}

export default function RecordVisitScreen({ route, navigation }) {
  const { shop } = route.params;
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);

  const [products, setProducts] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [cart, setCart] = useState([]);
  const [saleQuantities, setSaleQuantities] = useState({});
  const [returnQuantities, setReturnQuantities] = useState({});
  const [reviewMode, setReviewMode] = useState(false);

  const fetchProducts = useCallback(async () => {
    try {
      const data = await apiService.sales.getProducts();
      setProducts(data);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to fetch products list.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  const handleSaleQtyChange = (productId, val) => {
    setSaleQuantities((prev) => ({ ...prev, [productId]: val }));
  };

  const handleReturnQtyChange = (productId, val) => {
    setReturnQuantities((prev) => ({ ...prev, [productId]: val }));
  };

  /** Blank is a valid "not applicable" for either field — only defaults to 0 here, at add-to-cart time. */
  const parseQty = (val) => {
    if (!val || !val.trim()) return 0;
    const n = parseInt(val, 10);
    return isNaN(n) ? NaN : n;
  };

  const handleAddToCart = (product) => {
    const saleQty = parseQty(saleQuantities[product.id]);
    const returnQty = parseQty(returnQuantities[product.id]);

    if (isNaN(saleQty) || isNaN(returnQty) || saleQty < 0 || returnQty < 0) {
      Alert.alert('Invalid Quantity', 'Sold and Returned quantities must be 0 or more.');
      return;
    }
    if (saleQty === 0 && returnQty === 0) {
      Alert.alert('Nothing to Add', 'Enter a Sold and/or Returned quantity before adding this product.');
      return;
    }
    if (saleQty > 500 || returnQty > 500) {
      Alert.alert('Limit Exceeded', 'Maximum allowed quantity per product is 500.');
      return;
    }
    if (cart.some((item) => item.product.id === product.id)) {
      Alert.alert('Product in Cart', `'${product.name}' is already in the cart. Remove it first to change the quantities.`);
      return;
    }

    // Display-only preview using the LMT's own role price — the server
    // independently recomputes the real amount from Product.salesmanPrice
    // (plus any shop discount, Feature 2) at submission time.
    setCart((prev) => [...prev, { product, saleQty, returnQty, totalPrice: product.salesmanPrice * saleQty }]);
  };

  const handleRemoveFromCart = (productId) => {
    setCart((prev) => prev.filter((item) => item.product.id !== productId));
  };

  const calculateCartTotal = () => cart.reduce((sum, item) => sum + item.totalPrice, 0);
  const calculateTotalSoldUnits = () => cart.reduce((sum, item) => sum + item.saleQty, 0);
  const calculateTotalReturnedUnits = () => cart.reduce((sum, item) => sum + item.returnQty, 0);

  // The review screen below is the confirmation surface now — cross-check
  // Sold/Returned per product before submitting — so there's no separate
  // "are you sure" Alert on top of it; "Confirm & Submit" there calls
  // submitVisit directly.
  const handleReview = () => {
    if (cart.length === 0) {
      Alert.alert('Empty Cart', 'Please add at least one product before submitting.');
      return;
    }
    setReviewMode(true);
  };

  const submitVisit = async () => {
    setSubmitting(true);
    try {
      // Fresh GPS right before submit — never reused from the earlier shop
      // lookup step, same "re-check right before the write" pattern
      // CheckinScreen uses for its own geofence validation.
      let status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          Alert.alert('Location Required', 'Location permission is required to record a shop visit.');
          setSubmitting(false);
          return;
        }
      }
      const currentLoc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const { latitude, longitude } = currentLoc.coords;

      // Client-side pre-check only — fast, friendly failure before even
      // hitting the network. The server enforces its own copy of this
      // independently and is the real authority.
      if (shop.geoFencingEnabled && shop.latitude != null && shop.longitude != null && shop.radius != null) {
        try {
          const settings = await apiService.lmt.getSettings();
          const buffer = settings?.geofenceBufferMeters ?? 50;
          const distance = calculateDistanceMeters(latitude, longitude, shop.latitude, shop.longitude);
          const allowed = shop.radius + buffer;
          if (distance > allowed) {
            Alert.alert(
              'Too Far From Shop',
              `You are not currently at this shop. Please move to the shop and try again. ` +
                `(${Math.round(distance)}m away, allowed up to ${Math.round(allowed)}m.)`
            );
            setSubmitting(false);
            return;
          }
        } catch (e) {
          // Settings fetch failing shouldn't block a legitimate visit — the
          // server's own hard-gate still runs regardless of this pre-check.
          console.warn('Could not load LMT settings for client-side geofence pre-check', e);
        }
      }

      // Each cart entry becomes up to two line items — SALE and/or RETURN —
      // since a shop can both buy and hand back the same product on one
      // visit. Server already accepts both types on this endpoint.
      const items = [];
      cart.forEach((item) => {
        if (item.saleQty > 0) {
          items.push({ productId: item.product.id, quantity: item.saleQty, transactionType: 'SALE' });
        }
        if (item.returnQty > 0) {
          items.push({ productId: item.product.id, quantity: item.returnQty, transactionType: 'RETURN' });
        }
      });

      const shopVisitRequest = {
        agentId: user.id,
        shopCode: shop.shopCode,
        latitude,
        longitude,
        items,
      };

      await apiService.lmt.submitShopVisit(shopVisitRequest);
      Alert.alert('Success', 'Visit recorded successfully!', [
        { text: 'OK', onPress: () => navigation.navigate('LmtHome') },
      ]);
    } catch (e) {
      console.error(e);
      Alert.alert('Submission Failed', e.message || 'Error occurred while recording this sale.');
    } finally {
      setSubmitting(false);
    }
  };

  const filteredProducts = products.filter((p) => p.name.toLowerCase().includes(searchQuery.toLowerCase()));

  if (loading) {
    return <Loading message="Loading Dawn Bread products..." fullScreen />;
  }
  if (submitting) {
    return <Loading message="Recording visit..." fullScreen />;
  }

  if (reviewMode) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.shopBanner}>
          <MaterialIcons name="fact-check" size={20} color={colors.primary} />
          <Text style={styles.shopBannerText} numberOfLines={1}>
            Review — {shop.shopName} ({shop.shopCode})
          </Text>
        </View>

        <View style={styles.reviewListContainer}>
          <View style={styles.reviewHeaderRow}>
            <Text style={[styles.reviewHeaderLabel, styles.reviewProductHeaderLabel]}>Product</Text>
            <Text style={styles.reviewHeaderLabel}>Sold</Text>
            <Text style={styles.reviewHeaderLabel}>Returned</Text>
          </View>
          <FlatList
            data={cart}
            keyExtractor={(item) => item.product.id.toString()}
            renderItem={({ item }) => (
              <View style={styles.reviewRow}>
                <Text style={[styles.reviewProductName, { flex: 1 }]} numberOfLines={2}>{item.product.name}</Text>
                <Text style={styles.reviewQty}>{item.saleQty || '—'}</Text>
                <Text style={styles.reviewQty}>{item.returnQty || '—'}</Text>
              </View>
            )}
          />
        </View>

        <View style={styles.cartSection}>
          <View style={styles.summaryContainer}>
            <View style={styles.summaryRow}>
              <Text style={styles.summaryText}>Total Sold:</Text>
              <Text style={styles.summaryValue}>{calculateTotalSoldUnits()} units</Text>
            </View>
            <View style={styles.summaryRow}>
              <Text style={styles.summaryText}>Total Returned:</Text>
              <Text style={styles.summaryValue}>{calculateTotalReturnedUnits()} units</Text>
            </View>
            <View style={styles.summaryRow}>
              <Text style={styles.summaryText}>Total Amount:</Text>
              <Text style={styles.summaryTotal}>PKR {calculateCartTotal()}</Text>
            </View>
          </View>

          <View style={styles.buttonGroup}>
            <AppButton title="Back to Edit" onPress={() => setReviewMode(false)} variant="ghost" style={{ flex: 1, marginRight: 8 }} />
            <AppButton
              title="Confirm & Submit"
              onPress={submitVisit}
              variant="success"
              style={{ flex: 1.5, marginLeft: 8 }}
            />
          </View>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.shopBanner}>
        <MaterialIcons name="storefront" size={20} color={colors.primary} />
        <Text style={styles.shopBannerText} numberOfLines={1}>
          {shop.shopName} ({shop.shopCode})
        </Text>
      </View>

      <SearchBar
        value={searchQuery}
        onChangeText={setSearchQuery}
        placeholder="Search products (e.g. Bread, Muffin)..."
        style={styles.searchSection}
      />

      <Text style={styles.sectionHeader}>Available Products</Text>
      <View style={styles.listContainer}>
        <FlatList
          data={filteredProducts}
          keyExtractor={(item) => item.id.toString()}
          renderItem={({ item }) => (
            <View style={styles.productRow}>
              <ProductThumbnail uri={item.thumbnailUrl} size={40} style={styles.productImage} />
              <View style={styles.productInfo}>
                <Text style={styles.productName}>{item.name}</Text>
                <Text style={styles.productPrice}>PKR {item.salesmanPrice}</Text>
              </View>
              <View style={styles.actionRow}>
                <View style={styles.qtyField}>
                  <Text style={styles.qtyFieldLabel}>Sold</Text>
                  <TextInput
                    style={styles.qtyInput}
                    keyboardType="number-pad"
                    placeholder="0"
                    placeholderTextColor={colors.textMuted}
                    value={saleQuantities[item.id] || ''}
                    onChangeText={(val) => handleSaleQtyChange(item.id, val)}
                    maxLength={3}
                  />
                </View>
                <View style={styles.qtyField}>
                  <Text style={styles.qtyFieldLabel}>Returned</Text>
                  <TextInput
                    style={styles.qtyInput}
                    keyboardType="number-pad"
                    placeholder="0"
                    placeholderTextColor={colors.textMuted}
                    value={returnQuantities[item.id] || ''}
                    onChangeText={(val) => handleReturnQtyChange(item.id, val)}
                    maxLength={3}
                  />
                </View>
                <TouchableOpacity style={styles.addButton} onPress={() => handleAddToCart(item)}>
                  <Text style={styles.addButtonText}>+ Add</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
          ListEmptyComponent={
            <EmptyState icon="search-off" title="No products found" message="No products match your search query." />
          }
        />
      </View>

      <View style={styles.cartSection}>
        <View style={styles.cartHeader}>
          <Text style={styles.cartTitle}>Current Cart ({cart.length} unique items)</Text>
          <TouchableOpacity onPress={() => setCart([])} disabled={cart.length === 0}>
            <Text style={[styles.clearCartText, cart.length === 0 && { color: colors.textMuted }]}>Clear Cart</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.cartListContainer}>
          <FlatList
            data={cart}
            keyExtractor={(item) => item.product.id.toString()}
            renderItem={({ item }) => (
              <View style={styles.cartItem}>
                <View style={styles.cartItemDetails}>
                  <Text style={styles.cartItemName}>{item.product.name}</Text>
                  <Text style={styles.cartItemSub}>
                    {item.saleQty > 0 ? `Sold: ${item.saleQty}` : ''}
                    {item.saleQty > 0 && item.returnQty > 0 ? '  •  ' : ''}
                    {item.returnQty > 0 ? `Returned: ${item.returnQty}` : ''}
                  </Text>
                </View>
                <Text style={styles.cartItemTotal}>PKR {item.totalPrice}</Text>
                <TouchableOpacity onPress={() => handleRemoveFromCart(item.product.id)} style={styles.removeBtn}>
                  <MaterialIcons name="cancel" size={20} color={colors.error} />
                </TouchableOpacity>
              </View>
            )}
            ListEmptyComponent={
              <View style={styles.emptyCart}>
                <MaterialIcons name="shopping-cart" size={32} color={colors.textMuted} />
                <Text style={styles.emptyCartText}>Your cart is empty. Add products above.</Text>
              </View>
            }
          />
        </View>

        <View style={styles.summaryContainer}>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryText}>Total Sold:</Text>
            <Text style={styles.summaryValue}>{calculateTotalSoldUnits()} units</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryText}>Total Returned:</Text>
            <Text style={styles.summaryValue}>{calculateTotalReturnedUnits()} units</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryText}>Total Amount:</Text>
            <Text style={styles.summaryTotal}>PKR {calculateCartTotal()}</Text>
          </View>
        </View>

        <View style={styles.buttonGroup}>
          <AppButton title="Cancel" onPress={() => navigation.goBack()} variant="ghost" style={{ flex: 1, marginRight: 8 }} />
          <AppButton
            title="Review & Submit"
            onPress={handleReview}
            variant="success"
            disabled={cart.length === 0}
            style={{ flex: 1.5, marginLeft: 8 }}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    shopBanner: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 16,
      paddingVertical: 10,
      backgroundColor: colors.surfaceMuted,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    shopBannerText: { marginLeft: 8, fontSize: 14, fontWeight: 'bold', color: colors.textPrimary, flex: 1 },
    searchSection: { margin: 12 },
    sectionHeader: {
      fontSize: 12,
      fontWeight: 'bold',
      color: colors.textSecondary,
      marginLeft: 14,
      marginBottom: 6,
      textTransform: 'uppercase',
      letterSpacing: 0.5,
    },
    listContainer: {
      flex: 1.2,
      backgroundColor: colors.surface,
      marginHorizontal: 12,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
      overflow: 'hidden',
    },
    reviewListContainer: {
      flex: 1,
      backgroundColor: colors.surface,
      margin: 12,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
      overflow: 'hidden',
    },
    reviewHeaderRow: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 12,
      paddingVertical: 8,
      backgroundColor: colors.surfaceMuted,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    reviewHeaderLabel: {
      fontSize: 11,
      fontWeight: 'bold',
      color: colors.textSecondary,
      textTransform: 'uppercase',
      width: 70,
      textAlign: 'center',
    },
    reviewProductHeaderLabel: { flex: 1, width: undefined, textAlign: 'left' },
    reviewRow: {
      flexDirection: 'row',
      alignItems: 'center',
      padding: 12,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    reviewProductName: { fontSize: 13, color: colors.textPrimary },
    reviewQty: { width: 70, textAlign: 'center', fontWeight: '600', fontSize: 14, color: colors.textPrimary },
    productRow: {
      flexDirection: 'row',
      padding: 12,
      alignItems: 'center',
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    productImage: { width: 40, height: 40, borderRadius: 6, backgroundColor: colors.surfaceMuted },
    productInfo: { flex: 1, marginLeft: 10, marginRight: 6 },
    productName: { fontWeight: 'bold', fontSize: 13, color: colors.textPrimary },
    productPrice: { fontSize: 11, color: colors.secondary, fontWeight: '600', marginTop: 2 },
    actionRow: { flexDirection: 'row', alignItems: 'flex-end' },
    qtyField: { alignItems: 'center', marginRight: 6 },
    qtyFieldLabel: { fontSize: 9, color: colors.textSecondary, marginBottom: 2, textTransform: 'uppercase' },
    qtyInput: {
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: 6,
      width: 36,
      height: 34,
      textAlign: 'center',
      fontSize: 13,
      color: colors.textPrimary,
      backgroundColor: colors.inputBackground,
    },
    addButton: { backgroundColor: colors.secondary, paddingHorizontal: 10, paddingVertical: 8, borderRadius: 6 },
    addButtonText: { color: colors.textOnPrimary, fontWeight: 'bold', fontSize: 13 },
    cartSection: {
      flex: 1,
      backgroundColor: colors.surface,
      borderTopLeftRadius: 20,
      borderTopRightRadius: 20,
      padding: 16,
      elevation: 8,
      shadowColor: colors.shadow,
      shadowOpacity: 0.15,
      shadowRadius: 8,
      shadowOffset: { width: 0, height: -3 },
      borderWidth: 1,
      borderColor: colors.divider,
    },
    cartHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
    cartTitle: { fontWeight: 'bold', fontSize: 15, color: colors.textPrimary },
    clearCartText: { color: colors.error, fontWeight: '600', fontSize: 13 },
    cartListContainer: {
      flex: 1,
      borderWidth: 1,
      borderColor: colors.divider,
      borderRadius: 8,
      paddingHorizontal: 8,
      backgroundColor: colors.inputBackground,
    },
    cartItem: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: 10,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    cartItemDetails: { flex: 1 },
    cartItemName: { fontWeight: '600', fontSize: 13, color: colors.textPrimary },
    cartItemSub: { fontSize: 11, color: colors.textSecondary, marginTop: 2 },
    cartItemTotal: { fontWeight: 'bold', fontSize: 13, color: colors.textPrimary, marginRight: 8 },
    removeBtn: { padding: 4 },
    emptyCart: { alignItems: 'center', justifyContent: 'center', paddingVertical: 30 },
    emptyCartText: { color: colors.textMuted, fontSize: 12, marginTop: 6 },
    summaryContainer: { paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.divider, marginTop: 10 },
    summaryRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
    summaryText: { fontSize: 13, color: colors.textSecondary },
    summaryValue: { fontWeight: '600', fontSize: 13, color: colors.textPrimary },
    summaryTotal: { fontWeight: 'bold', fontSize: 16, color: colors.secondary },
    buttonGroup: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 10 },
  });
