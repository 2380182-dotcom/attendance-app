import React, { useEffect, useRef } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../theme';

/**
 * Big − / number / + control for people who should not have to type:
 * minus on the LEFT, the quantity in the MIDDLE, plus on the RIGHT. A tap
 * changes the number immediately; holding a button repeats it, so 40 breads
 * is not 40 taps. The number is also typeable for large quantities.
 *
 * Controlled: `value` is a whole number, `onChange(next)` is called with the
 * new one, always clamped to [min, max].
 */
const REPEAT_MS = 110;

export default function QuantityStepper({ value, onChange, min = 0, max = 500, color, label }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const plusColor = color || colors.successDark;

  // The hold-to-repeat timer must always step from the LATEST value, not the
  // one captured when the press began.
  const valueRef = useRef(value);
  valueRef.current = value;
  const timerRef = useRef(null);

  const clamp = (n) => Math.max(min, Math.min(max, n));
  const step = (delta) => {
    const next = clamp(valueRef.current + delta);
    if (next !== valueRef.current) {
      valueRef.current = next;
      onChange(next);
    }
  };
  const stopRepeat = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  };
  const startRepeat = (delta) => {
    stopRepeat();
    timerRef.current = setInterval(() => step(delta), REPEAT_MS);
  };
  useEffect(() => stopRepeat, []);

  const atMin = value <= min;
  const atMax = value >= max;

  return (
    <View style={styles.row} accessibilityLabel={label}>
      <TouchableOpacity
        style={[styles.button, { backgroundColor: colors.error }, atMin && styles.disabled]}
        onPress={() => step(-1)}
        onLongPress={() => startRepeat(-1)}
        onPressOut={stopRepeat}
        delayLongPress={350}
        disabled={atMin}
        accessibilityRole="button"
        accessibilityLabel="Decrease"
      >
        <MaterialIcons name="remove" size={36} color="#FFFFFF" />
      </TouchableOpacity>

      <TextInput
        style={styles.number}
        value={String(value)}
        keyboardType="number-pad"
        maxLength={3}
        selectTextOnFocus
        onChangeText={(text) => {
          const digits = text.replace(/[^0-9]/g, '');
          const next = clamp(digits === '' ? 0 : parseInt(digits, 10));
          valueRef.current = next;
          onChange(next);
        }}
        accessibilityLabel={label ? `${label} quantity` : 'Quantity'}
      />

      <TouchableOpacity
        style={[styles.button, { backgroundColor: plusColor }, atMax && styles.disabled]}
        onPress={() => step(1)}
        onLongPress={() => startRepeat(1)}
        onPressOut={stopRepeat}
        delayLongPress={350}
        disabled={atMax}
        accessibilityRole="button"
        accessibilityLabel="Increase"
      >
        <MaterialIcons name="add" size={36} color="#FFFFFF" />
      </TouchableOpacity>
    </View>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    row: { flexDirection: 'row', alignItems: 'center' },
    button: {
      width: 60,
      height: 60,
      borderRadius: 14,
      alignItems: 'center',
      justifyContent: 'center',
    },
    disabled: { opacity: 0.35 },
    number: {
      width: 76,
      height: 60,
      marginHorizontal: 8,
      textAlign: 'center',
      fontSize: 30,
      fontWeight: 'bold',
      color: colors.textPrimary,
      backgroundColor: colors.inputBackground,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
      padding: 0,
    },
  });
