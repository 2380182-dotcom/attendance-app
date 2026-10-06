import React, { useContext, useState } from 'react';
import { Alert, SafeAreaView, ScrollView, StyleSheet, Text, TextInput } from 'react-native';
import * as Location from 'expo-location';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import LocationService from '../../services/LocationService';
import AppButton from '../../components/AppButton';
import Loading from '../../components/Loading';
import { useTheme } from '../../theme';
import { STRINGS as LOCAL_STRINGS } from '../local/strings';
import { STRINGS as LMT_STRINGS } from '../lmt/strings';

/** Once per screen mount — same idempotency pattern as a shop visit's requestId. */
function generateRequestId() {
  return `feedback-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

const REASONS = [
  { key: 'SHOP_CLOSED', labelKey: 'feedbackReasonShopClosed' },
  { key: 'STOCK_FULL', labelKey: 'feedbackReasonStockFull' },
  { key: 'OWNER_NOT_AVAILABLE', labelKey: 'feedbackReasonOwnerNotAvailable' },
  { key: 'OTHER', labelKey: 'feedbackReasonOther' },
];

/**
 * "Salesman updates" feature: the salesman reached a shop but couldn't
 * make a sale, so he records why instead of a voucher. Shared between
 * Local and LMT (both navigators register this same component under
 * their own route name) since the flow is identical for both roles — only
 * the company check-in gate differs, and that's enforced server-side, not
 * here. No QR scan; GPS IS captured and sent (the server checks it against
 * the same buffered geofence rule a real visit uses).
 */
export default function ShopFeedbackScreen({ route, navigation }) {
  const { shop } = route.params;
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const STRINGS = user?.role === 'SALESMAN_LOCAL' ? LOCAL_STRINGS : LMT_STRINGS;
  const requestIdRef = React.useRef(generateRequestId());

  const [reason, setReason] = useState(null);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    if (!reason) {
      Alert.alert(STRINGS.addFeedback.en, 'Please choose a reason.');
      return;
    }
    if (reason === 'OTHER' && !note.trim()) {
      Alert.alert(STRINGS.addFeedback.en, `${STRINGS.feedbackNoteRequired.en}\n${STRINGS.feedbackNoteRequired.ur}`);
      return;
    }

    setSaving(true);
    try {
      const status = await LocationService.getPermissionStatus();
      if (!status.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          Alert.alert('Location Required', 'Location permission is required to save feedback.');
          setSaving(false);
          return;
        }
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });

      await apiService.lmt.submitFeedback({
        agentId: user.id,
        shopCode: shop.shopCode,
        reason,
        note: note.trim() || null,
        latitude: current.coords.latitude,
        longitude: current.coords.longitude,
        requestId: requestIdRef.current,
      });

      Alert.alert(`${STRINGS.feedbackSavedTitle.en} · ${STRINGS.feedbackSavedTitle.ur}`, shop.shopName, [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (e) {
      Alert.alert('Could not save feedback', e.message || 'Error occurred while saving.');
    } finally {
      setSaving(false);
    }
  };

  if (saving) {
    return <Loading message="Saving..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.shopName}>{shop.shopName}</Text>
        <Text style={styles.shopCode}>{shop.shopCode}</Text>

        <Text style={styles.sectionLabel}>
          {STRINGS.addFeedback.en} <Text style={{ writingDirection: 'rtl' }}>{STRINGS.addFeedback.ur}</Text>
        </Text>

        {REASONS.map((r) => {
          const selected = reason === r.key;
          const label = STRINGS[r.labelKey];
          return (
            <AppButton
              key={r.key}
              titleEn={label.en}
              titleUr={label.ur}
              variant={selected ? 'primary' : 'outline'}
              size="lg"
              onPress={() => setReason(r.key)}
              style={{ marginBottom: 10 }}
            />
          );
        })}

        <Text style={styles.noteLabel}>
          {STRINGS.writeYourFeedback.en} <Text style={{ writingDirection: 'rtl' }}>{STRINGS.writeYourFeedback.ur}</Text>
          {reason === 'OTHER' ? ' *' : ''}
        </Text>
        <TextInput
          style={styles.noteInput}
          value={note}
          onChangeText={setNote}
          placeholder={STRINGS.writeYourFeedback.en}
          placeholderTextColor={colors.textMuted}
          multiline
          numberOfLines={4}
        />

        <AppButton
          titleEn={STRINGS.saveFeedback.en}
          titleUr={STRINGS.saveFeedback.ur}
          variant="success"
          size="lg"
          icon="check-circle"
          onPress={handleSave}
          style={{ marginTop: 16 }}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    content: { padding: 20 },
    shopName: { fontSize: 22, fontWeight: 'bold', color: colors.textPrimary },
    shopCode: { fontSize: 14, color: colors.textSecondary, marginBottom: 20 },
    sectionLabel: { fontSize: 16, fontWeight: '600', color: colors.textPrimary, marginBottom: 12 },
    noteLabel: { fontSize: 14, color: colors.textSecondary, marginTop: 8, marginBottom: 6 },
    noteInput: {
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: 10,
      padding: 12,
      minHeight: 90,
      textAlignVertical: 'top',
      color: colors.textPrimary,
      backgroundColor: colors.inputBackground,
      fontSize: 15,
    },
  });
