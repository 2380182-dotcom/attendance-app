import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  useReducedMotion,
} from 'react-native-reanimated';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import AppButton from './AppButton';
import { useTheme, spacing, typography } from '../theme';
import { duration } from '../animation';

/**
 * Standard "no data" placeholder — use for empty lists/searches, not errors
 * (see ErrorState for failures). Fades in so it doesn't just pop when a list
 * resolves to zero items.
 */
export default function EmptyState({
  icon = 'inbox',
  title = 'Nothing here yet',
  message,
  actionLabel,
  onAction,
  style,
}) {
  const { colors } = useTheme();
  const reduceMotionEnabled = useReducedMotion();
  const opacity = useSharedValue(reduceMotionEnabled ? 1 : 0);

  useEffect(() => {
    if (!reduceMotionEnabled) {
      opacity.value = withTiming(1, { duration: duration.base });
    }
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));

  return (
    <Animated.View style={[styles.container, animatedStyle, style]}>
      <View style={[styles.iconCircle, { backgroundColor: colors.surfaceMuted }]}>
        <MaterialIcons name={icon} size={36} color={colors.textMuted} />
      </View>
      <Text style={[typography.title, { color: colors.textPrimary, marginTop: spacing.md }]}>
        {title}
      </Text>
      {message ? (
        <Text
          style={[
            typography.body,
            { color: colors.textSecondary, textAlign: 'center', marginTop: 4 },
          ]}
        >
          {message}
        </Text>
      ) : null}
      {actionLabel && onAction ? (
        <AppButton
          title={actionLabel}
          onPress={onAction}
          variant="outline"
          size="sm"
          fullWidth={false}
          style={{ marginTop: spacing.lg }}
        />
      ) : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: spacing.xxl,
    paddingHorizontal: spacing.lg,
  },
  iconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
