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
 * Standard failure placeholder — a fetch/submit failed, distinct from EmptyState
 * (no data, not a failure). Always offers a retry action when onRetry is given.
 */
export default function ErrorState({
  title = 'Something went wrong',
  message = 'Please try again.',
  retryLabel = 'Retry',
  onRetry,
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
      <View style={[styles.iconCircle, { backgroundColor: colors.errorLight }]}>
        <MaterialIcons name="error-outline" size={36} color={colors.error} />
      </View>
      <Text style={[typography.title, { color: colors.textPrimary, marginTop: spacing.md }]}>
        {title}
      </Text>
      <Text
        style={[
          typography.body,
          { color: colors.textSecondary, textAlign: 'center', marginTop: 4 },
        ]}
      >
        {message}
      </Text>
      {onRetry ? (
        <AppButton
          title={retryLabel}
          onPress={onRetry}
          variant="primary"
          size="sm"
          fullWidth={false}
          icon="refresh"
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
