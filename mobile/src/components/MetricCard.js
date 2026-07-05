import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import AppCard from './AppCard';
import { useTheme, spacing, typography } from '../theme';

/**
 * Stat tile for dashboards (e.g. "Total Agents: 6", "Checked In Today: 5").
 * color picks which theme token tints the icon chip — pass a semantic name,
 * not a raw hex, so multi-tile rows stay consistent with the rest of the app.
 *
 * trend: optional { value: '+12%', positive: true } for a small up/down indicator
 */
export default function MetricCard({ icon, label, value, color = 'primary', trend, style }) {
  const { colors } = useTheme();
  const tint = colors[color] || colors.primary;
  const tintLight = colors[`${color}Light`] || colors.primaryLight;
  // success/warning/accent fail WCAG contrast as icon color on their own *Light
  // chip background — use the darker "Dark" variant when one exists (see theme.js).
  const iconTint = colors[`${color}Dark`] || tint;

  return (
    <AppCard style={[styles.card, style]} padding={spacing.md}>
      <View style={[styles.iconChip, { backgroundColor: tintLight }]}>
        <MaterialIcons name={icon} size={20} color={iconTint} />
      </View>
      <Text style={[typography.h2, { color: colors.textPrimary, marginTop: spacing.sm }]}>
        {value}
      </Text>
      <Text style={[typography.caption, { color: colors.textSecondary, marginTop: 2 }]} numberOfLines={2}>
        {label}
      </Text>
      {trend ? (
        <View style={styles.trendRow}>
          <MaterialIcons
            name={trend.positive ? 'trending-up' : 'trending-down'}
            size={14}
            color={trend.positive ? colors.success : colors.error}
          />
          <Text
            style={[
              typography.caption,
              { color: trend.positive ? colors.success : colors.error, marginLeft: 2, fontWeight: '700' },
            ]}
          >
            {trend.value}
          </Text>
        </View>
      ) : null}
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    minWidth: 130,
  },
  iconChip: {
    width: 36,
    height: 36,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  trendRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 6,
  },
});
