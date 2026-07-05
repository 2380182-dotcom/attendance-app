import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import AppCard from './AppCard';
import StatusChip from './StatusChip';
import { useTheme, spacing, typography } from '../theme';

export default function AttendanceCard({
  title,
  subtitle,
  checkInTime,
  checkOutTime,
  status,
  distance,
  date,
}) {
  const { colors } = useTheme();

  const formatTime = (timeStr) => {
    if (!timeStr) return '--:--';
    try {
      const dateObj = new Date(timeStr);
      return dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return timeStr;
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const dateObj = new Date(dateStr);
      return dateObj.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
    } catch (e) {
      return dateStr;
    }
  };

  return (
    <AppCard style={styles.card} padding={spacing.md}>
      <View style={styles.row}>
        <View style={styles.titleContainer}>
          <Text style={[typography.title, { color: colors.textPrimary }]} numberOfLines={1}>
            {title}
          </Text>
          {subtitle ? (
            <Text style={[typography.bodySmall, { color: colors.textSecondary, marginTop: 2 }]} numberOfLines={1}>
              {subtitle}
            </Text>
          ) : null}
          {date ? (
            <Text style={[typography.caption, { color: colors.textMuted, marginTop: 4 }]}>
              {formatDate(date)}
            </Text>
          ) : null}
        </View>
        <StatusChip status={status} size="sm" />
      </View>

      <View style={[styles.divider, { backgroundColor: colors.divider }]} />

      <View style={styles.detailsRow}>
        <View style={styles.timeCol}>
          <Text style={[typography.caption, { color: colors.textMuted }]}>CHECK-IN</Text>
          <View style={styles.timeValueRow}>
            <MaterialIcons name="login" size={14} color={colors.success} />
            <Text style={[typography.bodySmall, { color: colors.textPrimary, marginLeft: 4, fontWeight: '600' }]}>
              {formatTime(checkInTime)}
            </Text>
          </View>
        </View>

        <View style={styles.timeCol}>
          <Text style={[typography.caption, { color: colors.textMuted }]}>CHECK-OUT</Text>
          <View style={styles.timeValueRow}>
            <MaterialIcons name="logout" size={14} color={colors.error} />
            <Text style={[typography.bodySmall, { color: colors.textPrimary, marginLeft: 4, fontWeight: '600' }]}>
              {formatTime(checkOutTime)}
            </Text>
          </View>
        </View>

        {distance !== undefined && distance !== null ? (
          <View style={styles.distanceCol}>
            <Text style={[typography.caption, { color: colors.textMuted }]}>DISTANCE</Text>
            <View style={styles.timeValueRow}>
              <MaterialIcons name="location-searching" size={14} color={colors.secondary} />
              <Text style={[typography.bodySmall, { color: colors.textPrimary, marginLeft: 4, fontWeight: '600' }]}>
                {distance > 1000
                  ? `${(distance / 1000).toFixed(1)} km`
                  : `${Math.round(distance)} m`}
              </Text>
            </View>
          </View>
        ) : null}
      </View>
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginVertical: 6,
    marginHorizontal: spacing.lg,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  titleContainer: {
    flex: 1,
    paddingRight: spacing.sm,
  },
  divider: {
    height: 1,
    marginVertical: spacing.md,
  },
  detailsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  timeCol: {
    flex: 1,
  },
  distanceCol: {
    flex: 1,
    alignItems: 'flex-end',
  },
  timeValueRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
});
