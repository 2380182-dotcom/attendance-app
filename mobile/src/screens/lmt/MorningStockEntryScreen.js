import React, { useCallback, useContext, useEffect, useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  FlatList,
  Alert,
  SafeAreaView,
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import { AuthContext } from '../../context/AuthContext';
import Loading from '../../components/Loading';
import ProductThumbnail from '../../components/ProductThumbnail';
import SearchBar from '../../components/SearchBar';
import AppButton from '../../components/AppButton';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

/**
 * Phase C4: morning full-stock entry, per product. Always reachable from
 * LmtHome once checked in — not time-locked, and not hidden once entered
 * either: if today's stock already exists, this shows it read-only
 * instead of a blank form (the backend rejects a second submission
 * outright — see LmtStockService.enterMorningStock — so re-showing a
 * fillable form would just confuse the LMT into re-entering data that can
 * never actually save).
 *
 * Only products the LMT actually enters a quantity for are submitted —
 * leaving a product at 0 means "not carrying this today", not "reconcile
 * this against a 0 baseline".
 */
export default function MorningStockEntryScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);

  const [products, setProducts] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [quantities, setQuantities] = useState({});
  const [existingStock, setExistingStock] = useState(null);

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

  const checkExistingThenLoad = useCallback(async () => {
    if (!user?.id) {
      fetchProducts();
      return;
    }
    try {
      const stock = await apiService.lmt.getTodayStock(user.id);
      if (stock) {
        setExistingStock(stock);
        setLoading(false);
        return;
      }
    } catch (e) {
      console.warn('Could not check for already-entered stock', e);
    }
    fetchProducts();
  }, [user, fetchProducts]);

  useEffect(() => {
    checkExistingThenLoad();
  }, [checkExistingThenLoad]);

  const handleQtyChange = (productId, val) => {
    setQuantities((prev) => ({ ...prev, [productId]: val }));
  };

  const enteredItems = () =>
    Object.entries(quantities)
      .map(([productId, val]) => ({ productId: Number(productId), openingStock: parseInt(val, 10) }))
      .filter((item) => !isNaN(item.openingStock) && item.openingStock > 0);

  const handleSubmit = () => {
    const items = enteredItems();
    if (items.length === 0) {
      Alert.alert('No Stock Entered', 'Enter today\'s opening stock for at least one product.');
      return;
    }
    Alert.alert(
      'Confirm Today\'s Stock',
      `Submit opening stock for ${items.length} product${items.length === 1 ? '' : 's'}? This can\'t be re-entered once submitted.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Confirm & Submit', onPress: submitStock },
      ]
    );
  };

  const submitStock = async () => {
    setSubmitting(true);
    try {
      await apiService.lmt.submitMorningStock({
        agentId: user.id,
        items: enteredItems(),
      });
      Alert.alert('Success', 'Today\'s stock has been recorded.', [
        { text: 'OK', onPress: () => navigation.navigate('LmtHome') },
      ]);
    } catch (e) {
      console.error(e);
      Alert.alert('Submission Failed', e.message || 'Error occurred while recording stock.');
    } finally {
      setSubmitting(false);
    }
  };

  const filteredProducts = products.filter((p) => p.name.toLowerCase().includes(searchQuery.toLowerCase()));
  const enteredCount = enteredItems().length;

  if (loading) {
    return <Loading message="Loading Dawn Bread products..." fullScreen />;
  }
  if (submitting) {
    return <Loading message="Recording today's stock..." fullScreen />;
  }

  if (existingStock) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.banner}>
          <MaterialIcons name="check-circle" size={20} color={colors.success} />
          <Text style={styles.bannerText}>Today's stock has already been entered</Text>
        </View>

        <View style={styles.listContainer}>
          <FlatList
            data={existingStock.items || []}
            keyExtractor={(item) => item.productId.toString()}
            renderItem={({ item }) => (
              <View style={styles.readOnlyRow}>
                <Text style={[styles.productName, { flex: 1 }]} numberOfLines={2}>{item.productName}</Text>
                <Text style={styles.readOnlyQty}>{item.openingStock}</Text>
              </View>
            )}
            ListEmptyComponent={
              <EmptyState icon="inventory" title="No products entered" message="Today's stock was submitted with no product lines." />
            }
          />
        </View>

        <View style={styles.footer}>
          <AppButton title="Back to Home" onPress={() => navigation.navigate('LmtHome')} variant="ghost" />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.banner}>
        <MaterialIcons name="inventory" size={20} color={colors.primary} />
        <Text style={styles.bannerText}>Enter your full stock for today, per product</Text>
      </View>

      <SearchBar
        value={searchQuery}
        onChangeText={setSearchQuery}
        placeholder="Search products (e.g. Bread, Muffin)..."
        style={styles.searchSection}
      />

      <View style={styles.listContainer}>
        <FlatList
          data={filteredProducts}
          keyExtractor={(item) => item.id.toString()}
          renderItem={({ item }) => (
            <View style={styles.productRow}>
              <ProductThumbnail uri={item.thumbnailUrl} size={40} style={styles.productImage} />
              <View style={styles.productInfo}>
                <Text style={styles.productName}>{item.name}</Text>
              </View>
              <TextInput
                style={styles.qtyInput}
                keyboardType="number-pad"
                placeholder="0"
                placeholderTextColor={colors.textMuted}
                value={quantities[item.id] || ''}
                onChangeText={(val) => handleQtyChange(item.id, val)}
                maxLength={5}
              />
            </View>
          )}
          ListEmptyComponent={
            <EmptyState icon="search-off" title="No products found" message="No products match your search query." />
          }
        />
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>{enteredCount} product{enteredCount === 1 ? '' : 's'} entered</Text>
        <AppButton
          title="Submit Today's Stock"
          onPress={handleSubmit}
          variant="success"
          disabled={enteredCount === 0}
        />
      </View>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    banner: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 16,
      paddingVertical: 10,
      backgroundColor: colors.surfaceMuted,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    bannerText: { marginLeft: 8, fontSize: 13, fontWeight: '600', color: colors.textPrimary, flex: 1 },
    searchSection: { margin: 12 },
    listContainer: {
      flex: 1,
      backgroundColor: colors.surface,
      marginHorizontal: 12,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
      overflow: 'hidden',
    },
    productRow: {
      flexDirection: 'row',
      padding: 12,
      alignItems: 'center',
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    productImage: { width: 44, height: 44, borderRadius: 6, backgroundColor: colors.surfaceMuted },
    productInfo: { flex: 1, marginLeft: 12 },
    productName: { fontWeight: 'bold', fontSize: 14, color: colors.textPrimary },
    qtyInput: {
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: 6,
      width: 60,
      height: 36,
      textAlign: 'center',
      fontSize: 13,
      color: colors.textPrimary,
      backgroundColor: colors.inputBackground,
    },
    footer: {
      padding: 16,
      backgroundColor: colors.surface,
      borderTopWidth: 1,
      borderTopColor: colors.divider,
    },
    footerText: { textAlign: 'center', fontSize: 12, color: colors.textSecondary, marginBottom: 10 },
    readOnlyRow: {
      flexDirection: 'row',
      alignItems: 'center',
      padding: 12,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    readOnlyQty: { width: 60, textAlign: 'center', fontWeight: '600', fontSize: 14, color: colors.textPrimary },
  });
