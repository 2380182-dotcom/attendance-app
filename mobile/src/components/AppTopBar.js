import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Image, Platform, StatusBar } from 'react-native';
import { SafeAreaView } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { useTheme, spacing, typography } from '../theme';

/**
 * Consistent top bar used across dashboards/screens — brand-red background, optional
 * back button, title/subtitle, optional small logo mark, and a row of icon actions.
 *
 * actions: [{ icon: 'file-download', onPress, accessibilityLabel }]
 */
export default function AppTopBar({
  title,
  subtitle,
  onBack,
  actions = [],
  showLogo = false,
  variant = 'primary', // 'primary' | 'surface'
}) {
  const { colors } = useTheme();
  const isPrimary = variant === 'primary';
  const bg = isPrimary ? colors.primary : colors.surface;
  const fg = isPrimary ? colors.textOnPrimary : colors.textPrimary;
  const fgMuted = isPrimary ? 'rgba(255,255,255,0.85)' : colors.textSecondary;

  return (
    <View style={[styles.wrapper, { backgroundColor: bg }]}>
      <SafeAreaView>
        <View style={styles.row}>
          <View style={styles.leftGroup}>
            {onBack && (
              <TouchableOpacity
                onPress={onBack}
                style={styles.iconBtn}
                accessibilityRole="button"
                accessibilityLabel="Go back"
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <MaterialIcons name="arrow-back" size={24} color={fg} />
              </TouchableOpacity>
            )}
            {showLogo && (
              <Image
                source={require('../../assets/dawn-bread-logo.png')}
                style={styles.logo}
                resizeMode="contain"
              />
            )}
            <View style={styles.titleBlock}>
              <Text style={[typography.h3, { color: fg }]} numberOfLines={1}>
                {title}
              </Text>
              {subtitle ? (
                <Text style={[typography.caption, { color: fgMuted, marginTop: 2 }]} numberOfLines={2}>
                  {subtitle}
                </Text>
              ) : null}
            </View>
          </View>

          {actions.length > 0 && (
            <View style={styles.actionsRow}>
              {actions.map((a, idx) => (
                <TouchableOpacity
                  key={idx}
                  onPress={a.onPress}
                  style={styles.iconBtn}
                  accessibilityRole="button"
                  accessibilityLabel={a.accessibilityLabel || a.icon}
                  hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                >
                  <MaterialIcons name={a.icon} size={22} color={fg} />
                </TouchableOpacity>
              ))}
            </View>
          )}
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 4,
    minHeight: 56,
  },
  leftGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    flexShrink: 1,
    marginRight: spacing.sm,
  },
  titleBlock: {
    flex: 1,
    flexShrink: 1,
  },
  logo: {
    width: 32,
    height: 32,
    marginRight: spacing.sm,
    borderRadius: 6,
  },
  actionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 0,
  },
  iconBtn: {
    padding: 8,
    marginLeft: 4,
  },
});
