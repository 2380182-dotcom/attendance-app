import React from 'react';
import { View, TextInput, TouchableOpacity, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { useTheme, radius as radiusTokens, spacing, typography } from '../theme';

/**
 * Standard search input — used for agent search (report screens, admin user
 * list) and anywhere else a filter-as-you-type field is needed.
 */
export default function SearchBar({
  value,
  onChangeText,
  placeholder = 'Search...',
  onClear,
  autoFocus = false,
  style,
}) {
  const { colors } = useTheme();

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: colors.inputBackground, borderColor: colors.border, borderRadius: radiusTokens.input },
        style,
      ]}
    >
      <MaterialIcons name="search" size={20} color={colors.textMuted} />
      <TextInput
        style={[styles.input, typography.body, { color: colors.textPrimary }]}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textMuted}
        autoFocus={autoFocus}
        autoCapitalize="none"
        autoCorrect={false}
        accessibilityLabel={placeholder}
        returnKeyType="search"
      />
      {value ? (
        <TouchableOpacity
          onPress={() => (onClear ? onClear() : onChangeText(''))}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          accessibilityRole="button"
          accessibilityLabel="Clear search"
        >
          <MaterialIcons name="close" size={18} color={colors.textMuted} />
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    height: 46,
  },
  input: {
    flex: 1,
    marginLeft: spacing.sm,
    height: '100%',
  },
});
