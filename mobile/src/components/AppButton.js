import React from 'react';
import { Text, StyleSheet, ActivityIndicator, Pressable } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  useReducedMotion,
} from 'react-native-reanimated';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { useTheme, radius as radiusTokens, spacing } from '../theme';
import { spring as springTokens } from '../animation';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

const SIZES = {
  sm: { height: 38, fontSize: 13, paddingHorizontal: spacing.md, iconSize: 16 },
  md: { height: 48, fontSize: 15, paddingHorizontal: spacing.lg, iconSize: 18 },
  lg: { height: 56, fontSize: 16, paddingHorizontal: spacing.xl, iconSize: 20 },
};

/**
 * Primary button component with an elastic press animation (scales down slightly
 * on press-in, springs back on release). Respects the OS reduce-motion setting.
 *
 * variant: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
 * size: 'sm' | 'md' | 'lg'
 */
export default function AppButton({
  title,
  onPress,
  variant = 'primary',
  size = 'md',
  icon,
  iconPosition = 'left',
  disabled = false,
  loading = false,
  fullWidth = true,
  style,
  textStyle,
  accessibilityLabel,
}) {
  const { colors } = useTheme();
  const reduceMotionEnabled = useReducedMotion();
  const scale = useSharedValue(1);
  const dims = SIZES[size] || SIZES.md;

  const variantStyles = {
    primary: { backgroundColor: colors.primary, textColor: colors.textOnPrimary, borderColor: 'transparent' },
    secondary: { backgroundColor: colors.secondary, textColor: colors.textOnPrimary, borderColor: 'transparent' },
    outline: { backgroundColor: 'transparent', textColor: colors.primary, borderColor: colors.primary },
    ghost: { backgroundColor: colors.surfaceMuted, textColor: colors.textPrimary, borderColor: 'transparent' },
    danger: { backgroundColor: colors.error, textColor: colors.textOnPrimary, borderColor: 'transparent' },
  };
  const v = variantStyles[variant] || variantStyles.primary;

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handlePressIn = () => {
    if (reduceMotionEnabled) return;
    scale.value = withSpring(0.96, springTokens.press);
  };
  const handlePressOut = () => {
    if (reduceMotionEnabled) return;
    scale.value = withSpring(1, springTokens.press);
  };

  const isDisabled = disabled || loading;

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel || title}
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      style={[
        animatedStyle,
        styles.base,
        {
          height: dims.height,
          paddingHorizontal: dims.paddingHorizontal,
          backgroundColor: v.backgroundColor,
          borderColor: v.borderColor,
          borderWidth: variant === 'outline' ? 1.5 : 0,
          borderRadius: radiusTokens.button,
          opacity: isDisabled ? 0.5 : 1,
          alignSelf: fullWidth ? 'stretch' : 'flex-start',
        },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={v.textColor} />
      ) : (
        <>
          {icon && iconPosition === 'left' && (
            <MaterialIcons name={icon} size={dims.iconSize} color={v.textColor} style={styles.iconLeft} />
          )}
          <Text style={[styles.text, { color: v.textColor, fontSize: dims.fontSize }, textStyle]}>
            {title}
          </Text>
          {icon && iconPosition === 'right' && (
            <MaterialIcons name={icon} size={dims.iconSize} color={v.textColor} style={styles.iconRight} />
          )}
        </>
      )}
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  base: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  iconLeft: {
    marginRight: 8,
  },
  iconRight: {
    marginLeft: 8,
  },
});
