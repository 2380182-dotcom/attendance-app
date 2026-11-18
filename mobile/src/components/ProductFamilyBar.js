import React from 'react';
import { ScrollView, TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { useTheme } from '../theme';

/**
 * The family filter buttons at the top of a product list: one row the
 * salesman swipes left/right, "All" first. Each button shows its product
 * count, e.g. "Bread (8)", with the Urdu name beneath when there is one.
 * The selected button is filled; the others are outlined. Hidden when there
 * is only "All" (no families loaded).
 */
export default function ProductFamilyBar({ buttons, selected, onSelect, style }) {
  const { colors } = useTheme();
  if (!buttons || buttons.length <= 1) return null;
  return (
    <View style={[styles.wrap, style]}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}
        keyboardShouldPersistTaps="handled" accessibilityRole="tablist">
        {buttons.map((b) => {
          const on = b.key === selected;
          return (
            <TouchableOpacity
              key={String(b.key)}
              onPress={() => onSelect(b.key)}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              accessibilityLabel={`${b.en} ${b.ur || ''} ${b.count}`.replace(/\s+/g, ' ').trim()}
              testID={`family-${b.en}`}
              style={[
                styles.button,
                on
                  ? { backgroundColor: colors.secondary, borderColor: colors.secondary }
                  : { backgroundColor: colors.surface, borderColor: colors.border },
              ]}
            >
              <Text style={[styles.label, { color: on ? colors.white : colors.textPrimary }]} numberOfLines={1}>
                {b.en} ({b.count})
              </Text>
              {!!b.ur && (
                <Text style={[styles.urdu, { color: on ? colors.white : colors.textSecondary }]} numberOfLines={1}>
                  {b.ur}
                </Text>
              )}
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
}

/** A family heading inside the "All" list (and the short confirm / review / cart lists). */
export function FamilyHeading({ en, ur, style }) {
  const { colors } = useTheme();
  return (
    <View style={[styles.heading, { borderBottomColor: colors.secondary }, style]}>
      <Text style={[styles.headingText, { color: colors.secondary }]}>{en}</Text>
      {!!ur && <Text style={[styles.headingUrdu, { color: colors.secondary }]}>{ur}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingVertical: 6 },
  row: { paddingHorizontal: 12, gap: 8, alignItems: 'center' },
  button: {
    minHeight: 48,
    minWidth: 72,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 24,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { fontSize: 16, fontWeight: '700' },
  urdu: { fontSize: 14, fontWeight: '600', writingDirection: 'rtl', marginTop: 1 },
  heading: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    borderBottomWidth: 2,
    paddingTop: 14,
    paddingBottom: 4,
    marginBottom: 6,
  },
  headingText: { fontSize: 15, fontWeight: '800', letterSpacing: 0.5, textTransform: 'uppercase' },
  headingUrdu: { fontSize: 15, fontWeight: '700', writingDirection: 'rtl' },
});
