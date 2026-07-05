import React from 'react';
import { Text, StyleSheet } from 'react-native';
import { Pressable } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  useReducedMotion,
} from 'react-native-reanimated';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { useTheme, spacing } from '../theme';
import { spring as springTokens, duration } from '../animation';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/**
 * Floating action button with an elastic press animation and an optional
 * expand-to-label state (e.g. collapse to icon-only on scroll, expand when idle).
 */
export default function AnimatedFAB({
  icon = 'add',
  label,
  expanded = true,
  onPress,
  accessibilityLabel,
  style,
}) {
  const { colors } = useTheme();
  const reduceMotionEnabled = useReducedMotion();
  const scale = useSharedValue(1);
  const widthProgress = useSharedValue(expanded ? 1 : 0);

  React.useEffect(() => {
    widthProgress.value = reduceMotionEnabled
      ? (expanded ? 1 : 0)
      : withTiming(expanded ? 1 : 0, { duration: duration.base });
  }, [expanded]);

  const pressStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const labelStyle = useAnimatedStyle(() => ({
    opacity: widthProgress.value,
    maxWidth: widthProgress.value * 160,
    marginLeft: widthProgress.value * 8,
  }));

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={() => {
        if (!reduceMotionEnabled) scale.value = withSpring(0.92, springTokens.press);
      }}
      onPressOut={() => {
        if (!reduceMotionEnabled) scale.value = withSpring(1, springTokens.press);
      }}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel || label || 'Action'}
      style={[
        pressStyle,
        styles.fab,
        { backgroundColor: colors.primary },
        style,
      ]}
    >
      <MaterialIcons name={icon} size={24} color={colors.textOnPrimary} />
      {label ? (
        <Animated.Text
          numberOfLines={1}
          style={[styles.label, { color: colors.textOnPrimary }, labelStyle]}
        >
          {label}
        </Animated.Text>
      ) : null}
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: 'absolute',
    bottom: spacing.lg,
    right: spacing.lg,
    minWidth: 56,
    height: 56,
    borderRadius: 28,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
  },
  label: {
    fontWeight: '700',
    fontSize: 14,
    overflow: 'hidden',
  },
});
