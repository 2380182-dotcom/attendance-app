import React, { useContext, useState } from 'react';
import { SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import QrScanner from '../../components/QrScanner';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

/**
 * QR shop-visit flow (Q3), LMT: a third way to pick a shop, alongside the
 * existing Nearby Shops list and manual Shop Lookup — neither of those is
 * touched by this screen. A successful scan hands off to the unchanged
 * RecordVisitScreen exactly as tapping a nearby shop already does.
 */
export default function ScanShopQrScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const [result, setResult] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [attempt, setAttempt] = useState(0);

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
        instructions="Point the camera at the shop's QR code"
        scanningLabel="Verifying shop..."
        onResult={handleResult}
      />
    );
  }

  const success = result?.visitStatus === 'SUCCESS';
  const shop = result?.shop;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <AppCard style={[styles.card, success && styles.cardSuccess]}>
          <View style={styles.iconRow}>
            <MaterialIcons
              name={success ? 'check-circle' : 'error'}
              size={48}
              color={success ? colors.successDark : colors.error}
            />
          </View>
          <Text style={styles.title}>{success ? 'Shop Verified' : 'Could Not Verify Shop'}</Text>
          <Text style={styles.message}>{errorMessage || result?.message}</Text>

          {success && shop && (
            <View style={styles.shopInfo}>
              <Text style={styles.shopName}>{shop.shopName}</Text>
              <Text style={styles.shopCode}>Shop Code: {shop.shopCode}</Text>
              {result.distanceMeters != null && (
                <Text style={styles.distance}>{Math.round(result.distanceMeters)}m from shop</Text>
              )}
            </View>
          )}

          {success ? (
            <AppButton
              title="Continue"
              variant="success"
              icon="point-of-sale"
              onPress={() => navigation.replace('RecordVisit', { shop })}
              style={{ marginTop: 16 }}
            />
          ) : (
            <AppButton title="Scan Again" variant="primary" icon="qr-code-scanner" onPress={scanAgain} style={{ marginTop: 16 }} />
          )}
          <AppButton title="Cancel" variant="outline" onPress={() => navigation.goBack()} style={{ marginTop: 8 }} />
        </AppCard>
      </ScrollView>
    </SafeAreaView>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    content: { padding: 16, flexGrow: 1, justifyContent: 'center' },
    card: { alignItems: 'center', padding: 24, borderWidth: 2, borderColor: colors.error },
    cardSuccess: { borderColor: colors.successDark },
    iconRow: { marginBottom: 8 },
    title: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary, marginBottom: 8, textAlign: 'center' },
    message: { fontSize: 14, color: colors.textSecondary, textAlign: 'center' },
    shopInfo: { marginTop: 16, alignItems: 'center' },
    shopName: { fontSize: 18, fontWeight: 'bold', color: colors.textPrimary },
    shopCode: { fontSize: 13, color: colors.textSecondary, marginTop: 2 },
    distance: { fontSize: 13, color: colors.textSecondary, marginTop: 4 },
  });
