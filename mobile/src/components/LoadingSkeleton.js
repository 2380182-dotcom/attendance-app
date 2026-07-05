import React, { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  useReducedMotion,
  Easing,
} from 'react-native-reanimated';
import { useTheme, radius as radiusTokens, spacing } from '../theme';

/**
 * A single shimmering placeholder block. Compose these into skeleton screens
 * (see LoadingSkeleton.Card / .ListItem below for ready-made layouts).
 */
function Block({ width = '100%', height = 16, borderRadius = 6, style }) {
  const { colors } = useTheme();
  const reduceMotionEnabled = useReducedMotion();
  const opacity = useSharedValue(0.5);

  useEffect(() => {
    if (reduceMotionEnabled) return;
    opacity.value = withRepeat(
      withTiming(1, { duration: 700, easing: Easing.inOut(Easing.ease) }),
      -1,
      true
    );
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: reduceMotionEnabled ? 0.7 : opacity.value,
  }));

  return (
    <Animated.View
      style={[
        { width, height, borderRadius, backgroundColor: colors.surfaceMuted },
        animatedStyle,
        style,
      ]}
    />
  );
}

function CardSkeleton() {
  const { colors, shadow } = useTheme();
  return (
    <View style={[styles.card, shadow, { backgroundColor: colors.surface, borderRadius: radiusTokens.card }]}>
      <View style={styles.row}>
        <Block width={44} height={44} borderRadius={22} />
        <View style={{ flex: 1, marginLeft: spacing.md }}>
          <Block width="60%" height={14} style={{ marginBottom: 8 }} />
          <Block width="40%" height={12} />
        </View>
      </View>
      <Block width="100%" height={12} style={{ marginTop: spacing.md }} />
      <Block width="80%" height={12} style={{ marginTop: 8 }} />
    </View>
  );
}

function ListItemSkeleton() {
  const { colors } = useTheme();
  return (
    <View style={[styles.listItem, { borderBottomColor: colors.divider }]}>
      <Block width={40} height={40} borderRadius={20} />
      <View style={{ flex: 1, marginLeft: spacing.md }}>
        <Block width="50%" height={13} style={{ marginBottom: 6 }} />
        <Block width="30%" height={11} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: spacing.lg,
    marginBottom: spacing.md,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  listItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderBottomWidth: 1,
  },
});

Block.Card = CardSkeleton;
Block.ListItem = ListItemSkeleton;

export default Block;
