import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Pressable } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  useReducedMotion,
} from 'react-native-reanimated';
import { useTheme, radius as radiusTokens, spacing } from '../theme';
import { spring as springTokens } from '../animation';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/**
 * The single card spec used everywhere in the app — white surface (dark-mode aware),
 * 20dp corners, subtle elevation. Pass onPress to get a gentle press-scale animation;
 * omit it for a static card.
 */
export default function AppCard({ children, onPress, style, padding = spacing.lg, accessibilityLabel }) {
  const { colors, shadow } = useTheme();
  const reduceMotionEnabled = useReducedMotion();
  const scale = useSharedValue(1);

  const cardStyle = [
    styles.card,
    shadow,
    { backgroundColor: colors.surface, borderRadius: radiusTokens.card, padding },
    style,
  ];

  if (!onPress) {
    return <View style={cardStyle}>{children}</View>;
  }

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={() => {
        if (!reduceMotionEnabled) scale.value = withSpring(0.98, springTokens.gentle);
      }}
      onPressOut={() => {
        if (!reduceMotionEnabled) scale.value = withSpring(1, springTokens.gentle);
      }}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={[animatedStyle, cardStyle]}
    >
      {children}
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  card: {
    width: '100%',
  },
});
