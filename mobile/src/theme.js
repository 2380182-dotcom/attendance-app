/**
 * Central design system — Dawn Bread brand palette, spacing, and shared constants.
 * Import from here instead of hardcoding hex values in StyleSheet definitions.
 */

export const colors = {
  // Brand palette
  primary: '#00478A',    // navy blue — headers, navigation, primary brand elements
  action: '#ED1C24',     // red — primary action buttons (Check-in, Submit, Export, Generate Report)
  success: '#8DC63F',    // green — active/checked-in/on-time states, success alerts
  warning: '#F7941D',    // orange — pending/missing verification, warning states
  error: '#C62828',      // deeper red — failed/absent/error states (kept distinct from action red)
  background: '#FAF7F2', // warm off-white — screen backgrounds

  // Light tint variants — badge/chip/status-container backgrounds
  primaryLight: '#E3F2FD',
  successLight: '#E8F5E9',
  warningLight: '#FFF3E0',
  errorLight: '#FFEBEE',

  // Neutrals — cover the text/border/surface colors used throughout existing screens
  white: '#FFFFFF',
  surface: '#FFFFFF',
  inputBackground: '#FAFAFA', // form input fields specifically — distinct from screen background
  surfaceMuted: '#ECEFF1',    // muted/secondary button backgrounds (e.g. Cancel/Reset)
  textPrimary: '#212121',
  textSecondary: '#757575',
  textMuted: '#9E9E9E',
  border: '#E0E0E0',
  divider: '#EEEEEE',
  shadow: '#000000',

  // Accent colors — for distinguishing multiple simultaneous stat cards/chart series
  // where reusing a semantic brand color (success/warning/error) would be misleading.
  accent1: '#5E35B1', // purple
  accent2: '#00ACC1', // teal
  accent3: '#1E88E5', // light blue
  accent4: '#E91E63', // pink
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
};

export const radius = {
  card: 12,
  chip: 8,
  pill: 20,
};

export const shadow = {
  card: {
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 3,
    elevation: 2,
  },
};

export default { colors, spacing, radius, shadow };
