import React, { useEffect } from 'react';
import { Modal, View, Text, TouchableWithoutFeedback, StyleSheet } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  withSpring,
  useReducedMotion,
} from 'react-native-reanimated';
import AppButton from './AppButton';
import { useTheme, radius as radiusTokens, spacing, typography } from '../theme';
import { duration, spring as springTokens } from '../animation';

/**
 * Reusable dialog — replaces the hand-rolled <Modal> + custom-styled overlay
 * pattern that was duplicated across admin/settings screens. Animates in with
 * a fade + gentle scale-up.
 *
 * actions: [{ label, onPress, variant }] — rendered as a button row, last
 * action right-aligned as the primary action.
 */
export default function AppDialog({ visible, onClose, title, children, actions = [] }) {
  const { colors } = useTheme();
  const reduceMotionEnabled = useReducedMotion();
  const opacity = useSharedValue(0);
  const scale = useSharedValue(0.92);

  useEffect(() => {
    if (visible) {
      opacity.value = reduceMotionEnabled ? 1 : withTiming(1, { duration: duration.fast });
      scale.value = reduceMotionEnabled ? 1 : withSpring(1, springTokens.gentle);
    } else {
      opacity.value = 0;
      scale.value = 0.92;
    }
  }, [visible]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));
  const cardStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ scale: scale.value }],
  }));

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onClose}>
      <TouchableWithoutFeedback onPress={onClose}>
        <Animated.View style={[styles.backdrop, backdropStyle]} />
      </TouchableWithoutFeedback>

      <View style={styles.centerWrapper} pointerEvents="box-none">
        <Animated.View
          style={[
            styles.card,
            cardStyle,
            { backgroundColor: colors.surface, borderRadius: radiusTokens.card },
          ]}
        >
          {title ? (
            <Text style={[typography.h3, { color: colors.textPrimary, marginBottom: spacing.md }]}>
              {title}
            </Text>
          ) : null}

          {children}

          {actions.length > 0 && (
            <View style={styles.actionsRow}>
              {actions.map((a, idx) => (
                <AppButton
                  key={idx}
                  title={a.label}
                  onPress={a.onPress}
                  variant={a.variant || (idx === actions.length - 1 ? 'primary' : 'ghost')}
                  size="sm"
                  fullWidth={false}
                  style={idx > 0 ? { marginLeft: spacing.sm } : undefined}
                />
              ))}
            </View>
          )}
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  centerWrapper: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  card: {
    width: '85%',
    maxWidth: 400,
    padding: spacing.lg,
  },
  actionsRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: spacing.lg,
  },
});
