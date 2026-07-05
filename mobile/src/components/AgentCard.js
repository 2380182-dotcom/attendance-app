import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import AppCard from './AppCard';
import StatusChip from './StatusChip';
import { useTheme, spacing, typography } from '../theme';

/**
 * Agent summary row/card — avatar initial, name, agent ID, role/department tags,
 * optional status chip. Used anywhere an agent is listed: admin user management,
 * agent search results in report screens, etc.
 */
export default function AgentCard({ agent, onPress, status, trailing }) {
  const { colors } = useTheme();
  const initial = (agent?.name || '?').trim().charAt(0).toUpperCase();

  return (
    <AppCard onPress={onPress} padding={spacing.md} style={styles.card} accessibilityLabel={`${agent?.name}, ${agent?.agentId}`}>
      <View style={styles.row}>
        <View style={[styles.avatar, { backgroundColor: colors.primaryLight }]}>
          <Text style={[typography.title, { color: colors.primary }]}>{initial}</Text>
        </View>

        <View style={styles.infoBlock}>
          <Text style={[typography.title, { color: colors.textPrimary }]} numberOfLines={1}>
            {agent?.name}
          </Text>
          <Text style={[typography.caption, { color: colors.textSecondary, marginTop: 2 }]} numberOfLines={1}>
            {agent?.agentId} {agent?.department ? `· ${agent.department}` : ''}
          </Text>
          <View style={styles.tagRow}>
            {agent?.role ? (
              <View style={[styles.tag, { backgroundColor: colors.secondaryLight }]}>
                <Text style={[typography.caption, { color: colors.secondary }]}>{agent.role}</Text>
              </View>
            ) : null}
          </View>
        </View>

        {status ? (
          <StatusChip status={status} size="sm" />
        ) : trailing ? (
          trailing
        ) : (
          <MaterialIcons name="chevron-right" size={22} color={colors.textMuted} />
        )}
      </View>
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.sm,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: spacing.md,
  },
  infoBlock: {
    flex: 1,
    flexShrink: 1,
    marginRight: spacing.sm,
  },
  tagRow: {
    flexDirection: 'row',
    marginTop: 6,
  },
  tag: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
    marginRight: 6,
  },
});
