import React from 'react';
import { TouchableOpacity, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../theme';

/**
 * "Feedback · رائے" on a shop row in the nearby-shops lists (LMT and Local).
 * Every shop in those lists is already inside its geofence (radius + buffer,
 * server-filtered), so a salesman at a closed shop — who can't reach the QR
 * inside — can record feedback straight from the list. Opens the existing
 * feedback screen; its rules (GPS checked again, reasons, counts as visited)
 * are unchanged.
 */
export default function ShopFeedbackButton({ onPress, shopName }) {
  const { colors } = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`Feedback رائے ${shopName || ''}`.trim()}
      hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}
      style={[styles.button, { borderColor: colors.primary }]}
    >
      <MaterialIcons name="feedback" size={16} color={colors.primary} />
      <Text style={[styles.text, { color: colors.primary }]}>
        Feedback
        {' · '}
        <Text style={{ writingDirection: 'rtl' }}>رائے</Text>
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 10,
    paddingVertical: 6,
    marginLeft: 8,
  },
  text: { fontSize: 13, fontWeight: '600', marginLeft: 4 },
});
