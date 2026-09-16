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
import AppButton from '../../components/AppButton';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

/**
 * LMT flow refinement: day-level Unsold entry, once per day — renamed and
 * repurposed from the old "Night Reconciliation" screen. Returned is no
 * longer entered here at all: it's now captured per-shop on the shop-visit
 * screen and computed server-side (see LmtStockService.reconcile), the
 * same way Sold already is. This screen only ever collects Unsold.
 *
 * Always reachable from LmtHome once checked in (not gated behind "End
 * Duty" — that's still a convenience shortcut into this same screen).
 * Sold/Returned/Missing are computed server-side in POST
 * /lmt/stock/reconcile and are never shown here — the salesman only does
 * data entry, never sees the reconciliation figures (confirmed: the
 * backend's /today response already strips these fields for a
 * SALESMAN_LMT caller too, so there's nothing to hide here even if a
 * future edit added a result screen).
 */
export default function EnterUnsoldScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [unsoldValues, setUnsoldValues] = useState({});

  const fetchTodayStock = useCallback(async () => {
    if (!user?.id) return;
    try {
      const stock = await apiService.lmt.getTodayStock(user.id);
      setItems(stock?.items || []);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to load today\'s stock.');
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    fetchTodayStock();
  }, [fetchTodayStock]);

  const handleSubmit = () => {
    Alert.alert(
      'Confirm Unsold Quantities',
      'Submit today\'s Unsold quantities?',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Confirm & Submit', onPress: submitUnsold },
      ]
    );
  };

  const submitUnsold = async () => {
    setSubmitting(true);
    try {
      const reconcileItems = items.map((item) => ({
        productId: item.productId,
        unsoldQty: parseInt(unsoldValues[item.productId], 10) || 0,
      }));
      await apiService.lmt.submitReconciliation({
        agentId: user.id,
        items: reconcileItems,
      });
      Alert.alert('Success', 'Unsold quantities recorded.', [
        { text: 'OK', onPress: () => navigation.navigate('LmtHome') },
      ]);
    } catch (e) {
      console.error(e);
      Alert.alert('Submission Failed', e.message || 'Error occurred while recording unsold quantities.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <Loading message="Loading today's stock..." fullScreen />;
  }
  if (submitting) {
    return <Loading message="Recording unsold quantities..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.banner}>
        <MaterialIcons name="inventory-2" size={20} color={colors.primary} />
        <Text style={styles.bannerText}>Enter Unsold quantity, per product</Text>
      </View>

      <View style={styles.listContainer}>
        <FlatList
          data={items}
          keyExtractor={(item) => item.productId.toString()}
          ListHeaderComponent={
            items.length > 0 ? (
              <View style={styles.headerRow}>
                <Text style={[styles.headerLabel, { flex: 1 }]}>Product</Text>
                <Text style={[styles.headerLabel, { width: 40, textAlign: 'center' }]}>Stock</Text>
                <Text style={[styles.headerLabel, styles.qtyHeaderLabel]}>Unsold</Text>
              </View>
            ) : null
          }
          renderItem={({ item }) => (
            <View style={styles.productRow}>
              <Text style={[styles.productName, { flex: 1 }]} numberOfLines={2}>{item.productName}</Text>
              <Text style={styles.openingStock}>{item.openingStock}</Text>
              <TextInput
                style={styles.qtyInput}
                keyboardType="number-pad"
                placeholder="0"
                placeholderTextColor={colors.textMuted}
                value={unsoldValues[item.productId] || ''}
                onChangeText={(val) => setUnsoldValues((prev) => ({ ...prev, [item.productId]: val }))}
                maxLength={5}
              />
            </View>
          )}
          ListEmptyComponent={
            <EmptyState icon="inventory" title="No stock entered today" message="Nothing to record — today's opening stock was never entered." />
          }
        />
      </View>

      <View style={styles.footer}>
        <AppButton
          title="Submit Unsold"
          onPress={handleSubmit}
          variant="success"
          disabled={items.length === 0}
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
    listContainer: {
      flex: 1,
      backgroundColor: colors.surface,
      margin: 12,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
      overflow: 'hidden',
    },
    headerRow: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 12,
      paddingVertical: 8,
      backgroundColor: colors.surfaceMuted,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    headerLabel: { fontSize: 11, fontWeight: 'bold', color: colors.textSecondary, textTransform: 'uppercase' },
    qtyHeaderLabel: { width: 56, textAlign: 'center', marginLeft: 8 },
    productRow: {
      flexDirection: 'row',
      padding: 12,
      alignItems: 'center',
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    productName: { fontSize: 13, color: colors.textPrimary },
    openingStock: { width: 40, textAlign: 'center', fontWeight: '600', fontSize: 13, color: colors.textPrimary },
    qtyInput: {
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: 6,
      width: 56,
      height: 36,
      textAlign: 'center',
      fontSize: 13,
      color: colors.textPrimary,
      backgroundColor: colors.inputBackground,
      marginLeft: 8,
    },
    footer: {
      padding: 16,
      backgroundColor: colors.surface,
      borderTopWidth: 1,
      borderTopColor: colors.divider,
    },
  });
