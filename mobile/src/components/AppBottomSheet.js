import React, { forwardRef, useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import BottomSheet, { BottomSheetBackdrop, BottomSheetView } from '@gorhom/bottom-sheet';
import { useTheme, radius as radiusTokens, spacing, typography } from '../theme';

/**
 * Themed wrapper around @gorhom/bottom-sheet. Use with a ref:
 *
 *   const sheetRef = useRef(null);
 *   <AppBottomSheet ref={sheetRef} title="Filter Options">...</AppBottomSheet>
 *   sheetRef.current?.expand();  /  sheetRef.current?.close();
 */
const AppBottomSheet = forwardRef(function AppBottomSheet(
  { children, title, snapPoints = ['50%'], onClose },
  ref
) {
  const { colors } = useTheme();
  const points = useMemo(() => snapPoints, [snapPoints]);

  const renderBackdrop = (props) => (
    <BottomSheetBackdrop {...props} appearsOnIndex={0} disappearsOnIndex={-1} opacity={0.5} />
  );

  return (
    <BottomSheet
      ref={ref}
      index={-1}
      snapPoints={points}
      enablePanDownToClose
      onClose={onClose}
      backdropComponent={renderBackdrop}
      backgroundStyle={{ backgroundColor: colors.surface, borderRadius: radiusTokens.card }}
      handleIndicatorStyle={{ backgroundColor: colors.border, width: 40 }}
    >
      <BottomSheetView style={styles.content}>
        {title ? (
          <Text style={[typography.h3, { color: colors.textPrimary, marginBottom: spacing.md }]}>
            {title}
          </Text>
        ) : null}
        {children}
      </BottomSheetView>
    </BottomSheet>
  );
});

const styles = StyleSheet.create({
  content: {
    flex: 1,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.lg,
  },
});

export default AppBottomSheet;
