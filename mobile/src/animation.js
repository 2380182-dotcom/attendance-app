/**
 * Shared animation constants — durations, easing curves, and spring configs.
 * Keeps every screen's motion consistent instead of ad hoc numbers per file.
 *
 * Respect the OS "reduce motion" setting: components using these should check
 * useReducedMotion() (from react-native-reanimated) and skip/shorten animations
 * accordingly — see components/AnimatedListItem.js for the reference pattern.
 */
import { Easing } from 'react-native-reanimated';

export const duration = {
  fast: 200,
  base: 300,
  slow: 450,
};

export const easing = {
  standard: Easing.bezier(0.2, 0.0, 0.0, 1.0),
  decelerate: Easing.out(Easing.cubic),
  accelerate: Easing.in(Easing.cubic),
};

// Spring configs tuned for "natural, not bouncy" motion — enterprise app, not a game.
export const spring = {
  gentle: { damping: 18, stiffness: 180, mass: 1 },
  snappy: { damping: 16, stiffness: 220, mass: 0.9 },
  press: { damping: 14, stiffness: 260, mass: 0.7 }, // for elastic button presses
};

export const stagger = {
  listItemDelay: 40, // ms between each item's entrance in a staggered list
  listItemMaxDelay: 400, // cap so long lists don't take forever to finish animating in
};

export default { duration, easing, spring, stagger };
