import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export default function StatusBadge({ status }) {
  const getBadgeStyle = (status) => {
    switch (status?.toUpperCase()) {
      case 'IN':
      case 'ENTERED':
      case 'CHECK_IN':
        return { bg: '#E8F5E9', text: '#2E7D32' };
      case 'OUT':
      case 'EXITED':
      case 'AUTO_CHECKOUT':
      case 'CHECK_OUT':
        return { bg: '#FFEBEE', text: '#C62828' };
      case 'LATE':
        return { bg: '#FFF8E1', text: '#F57F17' };
      case 'ABSENT':
        return { bg: '#EFEBE9', text: '#4E342E' };
      default:
        return { bg: '#ECEFF1', text: '#37474F' };
    }
  };

  const stylesColors = getBadgeStyle(status);

  return (
    <View style={[styles.badge, { backgroundColor: stylesColors.bg }]}>
      <Text style={[styles.text, { color: stylesColors.text }]}>{status}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    alignSelf: 'flex-start',
  },
  text: {
    fontSize: 12,
    fontWeight: 'bold',
  },
});
