import React from 'react';
import { StyleSheet, View } from 'react-native';
import MapView, { Marker, Circle } from 'react-native-maps';

export default function CustomMapView({ activeAgents, marts }) {
  const initialRegion = {
    latitude: 31.5204,
    longitude: 74.3587,
    latitudeDelta: 0.05,
    longitudeDelta: 0.05,
  };

  return (
    <View style={styles.container}>
      <MapView
        style={styles.map}
        initialRegion={initialRegion}
      >
        {marts && marts.map(mart => {
          const lat = parseFloat(mart.latitude);
          const lng = parseFloat(mart.longitude);
          if (isNaN(lat) || isNaN(lng)) return null;
          return (
            <React.Fragment key={`mart-${mart.id}`}>
              <Marker
                coordinate={{ latitude: lat, longitude: lng }}
                title={mart.name}
                description={mart.address}
                pinColor="#E53935"
              />
              <Circle
                center={{ latitude: lat, longitude: lng }}
                radius={parseFloat(mart.radius) || 100}
                fillColor="rgba(229, 57, 53, 0.15)"
                strokeColor="#E53935"
                strokeWidth={2}
              />
            </React.Fragment>
          );
        })}

        {activeAgents && activeAgents.map(agent => {
          const lat = parseFloat(agent.latitude);
          const lng = parseFloat(agent.longitude);
          if (isNaN(lat) || isNaN(lng)) return null;
          return (
            <Marker
              key={`agent-${agent.id}`}
              coordinate={{ latitude: lat, longitude: lng }}
              title={agent.name}
              description={`${agent.martName || 'Mart'} - ${agent.status || 'Active'}`}
              pinColor="#1E88E5"
            />
          );
        })}
      </MapView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
    alignItems: 'center',
  },
  map: {
    ...StyleSheet.absoluteFillObject,
  },
});
