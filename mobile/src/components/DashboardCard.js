import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import AppCard from './AppCard';
import { useTheme, spacing, typography } from '../theme';

/**
 * Section wrapper for dashboard blocks (e.g. "Top Selling Products", "Attendance
 * Sheet") — consistent header (icon + title + optional right-side action) over
 * whatever content the screen provides as children.
 */
export default function DashboardCard({ icon, title, action, children, style }) {
  const { colors } = useTheme();

  return (
    <AppCard style={[styles.card, style]}>
      <View style={styles.header}>
        <View style={styles.titleRow}>
          {icon ? (
            <MaterialIcons name={icon} size={18} color={colors.textSecondary} style={styles.icon} />
          ) : null}
          <Text style={[typography.subtitle, { color: colors.textPrimary }]}>{title}</Text>
        </View>
        {action}
      </View>
      {children}
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.md,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 1,
  },
  icon: {
    marginRight: 6,
  },
});
