import React, { useEffect } from 'react';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withDelay,
  withTiming,
  useReducedMotion,
} from 'react-native-reanimated';
import { duration, stagger, easing } from '../animation';

/**
 * Wraps a list row with a staggered fade+slide-up entrance. Pass the row's
 * index so each item animates slightly after the one before it.
 *
 * Usage: <AnimatedListItem index={index}><YourRow /></AnimatedListItem>
 */
export default function AnimatedListItem({ index = 0, children, style }) {
  const reduceMotionEnabled = useReducedMotion();
  const opacity = useSharedValue(reduceMotionEnabled ? 1 : 0);
  const translateY = useSharedValue(reduceMotionEnabled ? 0 : 12);

  useEffect(() => {
    if (reduceMotionEnabled) return;
    const delay = Math.min(index * stagger.listItemDelay, stagger.listItemMaxDelay);
    opacity.value = withDelay(delay, withTiming(1, { duration: duration.fast, easing: easing.decelerate }));
    translateY.value = withDelay(delay, withTiming(0, { duration: duration.fast, easing: easing.decelerate }));
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  return <Animated.View style={[animatedStyle, style]}>{children}</Animated.View>;
}
