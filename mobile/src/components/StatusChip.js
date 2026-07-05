import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme, radius as radiusTokens, typography } from '../theme';

/**
 * Canonical status→color mapping for the whole app. Before this component,
 * StatusBadge.js, AttendanceCard.js, and StatusCard.js each defined their own
 * (disagreeing) color for the same status strings — e.g. "late" was #F57F17
 * in one and #EF6C00 in another. This is now the single source of truth;
 * StatusBadge delegates to it for backward compatibility.
 */
function resolveStatus(status, colors) {
  const s = String(status || '').toUpperCase();

  if (['IN', 'PRESENT', 'ENTERED', 'CHECK_IN', 'ACTIVE', 'CHECKED_IN'].includes(s)) {
    return { bg: colors.successLight, fg: colors.successDark, label: 'ACTIVE' };
  }
  if (['OUT', 'EXITED', 'CHECK_OUT', 'AUTO_CHECKOUT', 'CHECKED_OUT'].includes(s)) {
    return { bg: colors.secondaryLight, fg: colors.secondary, label: 'CHECKED OUT' };
  }
  if (s === 'LATE') {
    return { bg: colors.warningLight, fg: colors.warningDark, label: 'LATE' };
  }
  if (s === 'PENDING') {
    return { bg: colors.warningLight, fg: colors.warningDark, label: 'PENDING' };
  }
  if (['ABSENT', 'FAILED', 'MISSING', 'ERROR'].includes(s)) {
    return { bg: colors.errorLight, fg: colors.error, label: s === 'ABSENT' ? 'ABSENT' : 'FAILED' };
  }
  return { bg: colors.surfaceMuted, fg: colors.textSecondary, label: s || 'UNKNOWN' };
}

export default function StatusChip({ status, label, size = 'md' }) {
  const { colors } = useTheme();
  const resolved = resolveStatus(status, colors);
  const isSmall = size === 'sm';

  return (
    <View
      style={[
        styles.chip,
        {
          backgroundColor: resolved.bg,
          borderRadius: radiusTokens.chip,
          paddingHorizontal: isSmall ? 8 : 10,
          paddingVertical: isSmall ? 3 : 4,
        },
      ]}
    >
      <Text
        style={[
          isSmall ? typography.caption : typography.label,
          { color: resolved.fg },
        ]}
      >
        {label || resolved.label}
      </Text>
    </View>
  );
}

export { resolveStatus };

const styles = StyleSheet.create({
  chip: {
    alignSelf: 'flex-start',
  },
});
