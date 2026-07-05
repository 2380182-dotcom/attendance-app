/**
 * Central design system — Dawn Bread Pakistan brand guidelines.
 * Import from here instead of hardcoding hex values, font sizes, or spacing in
 * StyleSheet definitions.
 *
 * NOTE: this supersedes an earlier draft palette (navy/red/green built for the
 * Login screen only) — that screen still needs re-touching to match these values.
 */
import { useColorScheme } from 'react-native';

const lightColors = {
  // Brand palette
  primary: '#E30613',    // Dawn Red — primary brand color, top bars, primary CTAs
  secondary: '#0057B8',  // Brand Blue — secondary actions, links, selected states
  accent: '#FDB913',     // Golden Wheat — sparing use: highlights, badges, ratings
  success: '#22C55E',
  warning: '#F59E0B',
  error: '#C62828',      // kept distinct from primary red so "submit" and "failed" never look identical
  background: '#F8F9FA',

  // Text/icon-safe variants of success/warning/accent — the base tones above are
  // calibrated for buttons/large chips and fail WCAG contrast (~2:1, and accent
  // fails as badly as ~1.6:1) as text or icons on top of their own *Light tint.
  // Use these instead whenever success/warning/accent renders as text or an icon
  // on a tinted background (e.g. MetricCard, StatusChip).
  successDark: '#166534',
  warningDark: '#92400E',
  accentDark: '#854D0E',

  // Light tint variants — badge/chip/status-container backgrounds
  primaryLight: '#FCE4E6',
  secondaryLight: '#E1EDFB',
  accentLight: '#FEF3D6',
  successLight: '#E6F9EF',
  warningLight: '#FEF3E2',
  errorLight: '#FBEAEA',

  // Neutrals
  white: '#FFFFFF',
  surface: '#FFFFFF',        // card background — "white with subtle elevation"
  inputBackground: '#F5F6F7',
  surfaceMuted: '#EEF0F2',
  textPrimary: '#1A1D1F',
  textSecondary: '#6B7280',
  textMuted: '#9CA3AF',
  textOnPrimary: '#FFFFFF',
  border: '#E5E7EB',
  divider: '#EDEEF0',
  shadow: '#000000',

  // Accent colors — for distinguishing multiple simultaneous stat cards/chart series
  // where reusing a semantic brand color (success/warning/error) would be misleading.
  chart1: '#5E35B1',
  chart2: '#00ACC1',
  chart3: '#1E88E5',
  chart4: '#E91E63',

  // Light-tint chip background for MetricCard when color="chart1"/"chart2", plus
  // an icon-safe dark variant for chart2 (the base tone fails contrast on its own
  // tint, same issue as success/warning/accent above).
  chart1Light: '#EDE7F6',
  chart2Light: '#E0F7FA',
  chart2Dark: '#00707F',
};

const darkColors = {
  primary: '#FF4D5A',
  secondary: '#4C9AFF',
  accent: '#FDB913',
  success: '#3DDC84',
  warning: '#FBBF24',
  error: '#EF5350',
  background: '#121417',

  // Already high-contrast against successLight/warningLight/accentLight in dark
  // mode (~7-8:1), so these text/icon-safe variants just alias the base tones.
  // Kept as separate keys for parity with lightColors and so call sites don't
  // need a light/dark branch.
  successDark: '#3DDC84',
  warningDark: '#FBBF24',
  accentDark: '#FDB913',

  primaryLight: '#3A1518',
  secondaryLight: '#132A42',
  accentLight: '#3A2E0E',
  successLight: '#0F2E1E',
  warningLight: '#3A2A0E',
  errorLight: '#3A1414',

  white: '#FFFFFF',
  surface: '#1C1F23',
  inputBackground: '#22262B',
  surfaceMuted: '#262B31',
  textPrimary: '#F3F4F6',
  textSecondary: '#A6ADBB',
  textMuted: '#7C8492',
  textOnPrimary: '#FFFFFF',
  border: '#2E333A',
  divider: '#282D33',
  shadow: '#000000',

  chart1: '#9575CD',
  chart2: '#4DD0E1',
  chart3: '#64B5F6',
  chart4: '#F06292',

  // Already high-contrast in dark mode — see lightColors for why these exist.
  chart1Light: '#2A2438',
  chart2Light: '#0F2E33',
  chart2Dark: '#4DD0E1',
};

// Static export for call sites that can't use the hook (e.g. outside components).
// Prefer useTheme() inside components so dark mode is respected automatically.
export const colors = lightColors;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 40,
};

export const radius = {
  card: 20,
  button: 14,
  chip: 12,
  pill: 24,
  input: 12,
};

export const typography = {
  display: { fontSize: 32, fontWeight: '700', lineHeight: 40 },
  h1: { fontSize: 26, fontWeight: '700', lineHeight: 32 },
  h2: { fontSize: 22, fontWeight: '700', lineHeight: 28 },
  h3: { fontSize: 18, fontWeight: '600', lineHeight: 24 },
  title: { fontSize: 16, fontWeight: '600', lineHeight: 22 },
  subtitle: { fontSize: 14, fontWeight: '600', lineHeight: 20 },
  body: { fontSize: 14, fontWeight: '400', lineHeight: 20 },
  bodySmall: { fontSize: 13, fontWeight: '400', lineHeight: 18 },
  label: { fontSize: 12, fontWeight: '700', lineHeight: 16, letterSpacing: 0.4 },
  caption: { fontSize: 11, fontWeight: '500', lineHeight: 14 },
};

export function cardShadow(c = lightColors) {
  return {
    shadowColor: c.shadow,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  };
}

/**
 * Resolves the active palette based on the device's light/dark setting.
 * Use inside components: const { colors: c } = useTheme();
 */
export function useTheme() {
  const scheme = useColorScheme();
  const isDark = scheme === 'dark';
  const activeColors = isDark ? darkColors : lightColors;
  return {
    isDark,
    colors: activeColors,
    spacing,
    radius,
    typography,
    shadow: cardShadow(activeColors),
  };
}

export default { colors, spacing, radius, typography, cardShadow, useTheme };
