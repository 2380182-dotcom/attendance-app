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
 * Phase C5: night reconciliation — Returned + Unsold, per product.
 * Reached by PROMPT from LmtHome's "End Duty" button when today's stock
 * is still OPEN; the LMT can always skip it and check out anyway (this
 * screen is never a hard gate on Checkout — Missing is recorded, not
 * blocked, per the build plan). Sold and Missing are computed server-side
 * in POST /lmt/stock/reconcile and are never entered here.
 */
export default function NightReconciliationScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [returnedValues, setReturnedValues] = useState({});
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
      'Confirm Reconciliation',
      'Submit tonight\'s Returned and Unsold quantities? Sold and Missing will be computed automatically.',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Confirm & Submit', onPress: submitReconciliation },
      ]
    );
  };

  const submitReconciliation = async () => {
    setSubmitting(true);
    try {
      const reconcileItems = items.map((item) => ({
        productId: item.productId,
        returnedQty: parseInt(returnedValues[item.productId], 10) || 0,
        unsoldQty: parseInt(unsoldValues[item.productId], 10) || 0,
      }));
      await apiService.lmt.submitReconciliation({
        agentId: user.id,
        items: reconcileItems,
      });
      Alert.alert('Success', 'Stock reconciled successfully.', [
        { text: 'OK', onPress: () => navigation.navigate('LmtHome') },
      ]);
    } catch (e) {
      console.error(e);
      Alert.alert('Submission Failed', e.message || 'Error occurred while reconciling stock.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <Loading message="Loading today's stock..." fullScreen />;
  }
  if (submitting) {
    return <Loading message="Reconciling stock..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.banner}>
        <MaterialIcons name="assignment-turned-in" size={20} color={colors.primary} />
        <Text style={styles.bannerText}>Enter Returned and Unsold quantities, per product</Text>
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
                <Text style={[styles.headerLabel, styles.qtyHeaderLabel]}>Returned</Text>
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
                value={returnedValues[item.productId] || ''}
                onChangeText={(val) => setReturnedValues((prev) => ({ ...prev, [item.productId]: val }))}
                maxLength={5}
              />
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
            <EmptyState icon="inventory" title="No stock entered today" message="Nothing to reconcile — today's opening stock was never entered." />
          }
        />
      </View>

      <View style={styles.footer}>
        <AppButton
          title="Submit Reconciliation"
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
