import React, { useContext, useEffect, useState } from 'react';
import { SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import QrScanner from '../../components/QrScanner';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';
import { STRINGS } from './strings';

/** `{en, ur}` -> "English\nUrdu", the pattern every message on the Local screens uses. */
function bilingual(strings) {
  return strings.ur ? `${strings.en}\n${strings.ur}` : strings.en;
}

/**
 * QR shop-visit flow (Q3), Local: a third way to pick a shop, alongside
 * the existing Nearby Shops list — that screen is untouched. A successful
 * scan hands off to LocalEntryScreen exactly as tapping a nearby shop
 * already does — one flow covering sale and optional return (Task 2), no
 * SALE/RETURN mode picked ahead of time anymore.
 */
export default function ScanShopQrScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);

  const [result, setResult] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    navigation.setOptions({ title: STRINGS.recordVisit.en });
  }, [navigation]);

  const scanAgain = () => {
    setResult(null);
    setErrorMessage(null);
    setAttempt((a) => a + 1);
  };

  const handleResult = (dto, error) => {
    if (error) {
      setErrorMessage(error);
      return;
    }
    setResult(dto);
  };

  if (!result && !errorMessage) {
    return (
      <QrScanner
        key={attempt}
        agentId={user?.id}
        instructions={bilingual(STRINGS.pointCamera)}
        scanningLabel={bilingual(STRINGS.verifyingShop)}
        permissionText={bilingual(STRINGS.cameraPermissionNeeded)}
        allowCameraLabel={`${STRINGS.allowCamera.en} · ${STRINGS.allowCamera.ur}`}
        onResult={handleResult}
      />
    );
  }

  const success = result?.visitStatus === 'SUCCESS';
  const shop = result?.shop;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={[styles.card, { borderColor: success ? colors.successDark : colors.error }]}>
          <MaterialIcons
            name={success ? 'check-circle' : 'error'}
            size={56}
            color={success ? colors.successDark : colors.error}
          />
          <Text style={styles.title}>{success ? STRINGS.shopVerified.en : STRINGS.couldNotVerifyShop.en}</Text>
          <Text style={styles.titleUrdu}>{success ? STRINGS.shopVerified.ur : STRINGS.couldNotVerifyShop.ur}</Text>
          {!!(errorMessage || result?.message) && <Text style={styles.message}>{errorMessage || result?.message}</Text>}

          {success && shop && (
            <View style={styles.shopInfo}>
              <Text style={styles.shopName}>{shop.shopName}</Text>
              {result.distanceMeters != null && (
                <Text style={styles.distance}>
                  {Math.round(result.distanceMeters)}m {STRINGS.distanceFromShop.en} · {STRINGS.distanceFromShop.ur}
                </Text>
              )}
            </View>
          )}

          {success ? (
            <AppButton
              title={`${STRINGS.continueLabel.en} · ${STRINGS.continueLabel.ur}`}
              variant="success"
              size="lg"
              icon="shopping-cart"
              onPress={() => navigation.replace('LocalEntry', { shop })}
              style={{ marginTop: 20 }}
            />
          ) : (
            <AppButton
              title={`${STRINGS.scanAgain.en} · ${STRINGS.scanAgain.ur}`}
              variant="primary"
              size="lg"
              icon="qr-code-scanner"
              onPress={scanAgain}
              style={{ marginTop: 20 }}
            />
          )}
          <AppButton
            title={`${STRINGS.cancel.en} · ${STRINGS.cancel.ur}`}
            variant="outline"
            size="lg"
            onPress={() => navigation.goBack()}
            style={{ marginTop: 10 }}
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    content: { padding: 16, flexGrow: 1, justifyContent: 'center' },
    card: {
      alignItems: 'center',
      padding: 24,
      borderWidth: 3,
      borderRadius: 20,
      backgroundColor: colors.surface,
    },
    title: { fontSize: 22, fontWeight: 'bold', color: colors.textPrimary, marginTop: 8, textAlign: 'center' },
    titleUrdu: { fontSize: 18, color: colors.textSecondary, marginBottom: 8, textAlign: 'center' },
    message: { fontSize: 15, color: colors.textSecondary, textAlign: 'center', marginTop: 4 },
    shopInfo: { marginTop: 16, alignItems: 'center' },
    shopName: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary },
    distance: { fontSize: 14, color: colors.textSecondary, marginTop: 4 },
  });
