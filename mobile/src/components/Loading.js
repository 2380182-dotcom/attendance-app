import React from 'react';
import { StyleSheet, View, ActivityIndicator, Text } from 'react-native';

export default function Loading({ message = 'Loading...', fullScreen = false }) {
  if (fullScreen) {
    return (
      <View style={styles.fullScreenContainer}>
        <ActivityIndicator size="large" color="#2196F3" />
        {message ? <Text style={styles.messageText}>{message}</Text> : null}
      </View>
    );
  }

  return (
    <View style={styles.inlineContainer}>
      <ActivityIndicator size="small" color="#2196F3" />
      {message ? <Text style={styles.inlineMessageText}>{message}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  fullScreenContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  inlineContainer: {
    padding: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  messageText: {
    marginTop: 12,
    fontSize: 16,
    color: '#666',
    fontWeight: '500',
  },
  inlineMessageText: {
    marginLeft: 8,
    fontSize: 14,
    color: '#666',
  },
});
