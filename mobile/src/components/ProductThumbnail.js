import React, { useState } from 'react';
import { View, Image, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

/**
 * Renders a product thumbnail, falling back to a local MaterialIcons glyph (not a
 * remote placeholder image) when there's no image URL or the image fails to load.
 * Product image_url is empty for every product in the catalog right now, and the
 * previous remote placehold.co fallback depended on an external service being
 * reachable — this renders correctly offline and with no data at all.
 */
export default function ProductThumbnail({ uri, size = 32, style }) {
  const [failed, setFailed] = useState(false);
  const dimension = { width: size, height: size, borderRadius: size / 4 };

  if (!uri || failed) {
    return (
      <View style={[styles.fallback, dimension, style]}>
        <MaterialIcons name="bakery-dining" size={size * 0.6} color="#9E9E9E" />
      </View>
    );
  }

  return (
    <Image
      source={{ uri }}
      style={[dimension, style]}
      onError={() => setFailed(true)}
    />
  );
}

const styles = StyleSheet.create({
  fallback: {
    backgroundColor: '#EEEEEE',
    justifyContent: 'center',
    alignItems: 'center',
  },
});
