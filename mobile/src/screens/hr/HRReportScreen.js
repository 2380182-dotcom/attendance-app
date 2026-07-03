import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  FlatList,
  RefreshControl,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import NotificationCard from '../../components/NotificationCard';
import Loading from '../../components/Loading';

export default function HRReportScreen() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchNotifs = useCallback(async () => {
    try {
      const logs = await apiService.notifications.getHR();
      setNotifications(logs);
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Unable to retrieve HR warnings logs.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchNotifs();
  }, [fetchNotifs]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchNotifs();
  };

  const handleMarkRead = async (id) => {
    try {
      await apiService.notifications.markAsRead(id);
      fetchNotifs();
    } catch (e) {
      Alert.alert('Error', 'Failed to mark notification as read');
    }
  };

  const handleDelete = async (id) => {
    try {
      await apiService.notifications.delete(id);
      fetchNotifs();
    } catch (e) {
      Alert.alert('Error', 'Failed to delete notification');
    }
  };

  if (loading && !refreshing) {
    return <Loading message="Loading history logs..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={notifications}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => (
          <NotificationCard
            notification={item}
            onMarkRead={handleMarkRead}
            onDelete={handleDelete}
          />
        )}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} color="#2196F3" />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <MaterialIcons name="notifications-off" size={48} color="#9E9E9E" />
            <Text style={styles.emptyTitle}>No HR Logs Found</Text>
            <Text style={styles.emptyText}>There are no warnings in the HR notifications system yet.</Text>
          </View>
        }
        contentContainerStyle={styles.listScroll}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  listScroll: {
    padding: 16,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 100,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#424242',
    marginTop: 12,
  },
  emptyText: {
    fontSize: 13,
    color: '#757575',
    textAlign: 'center',
    marginTop: 6,
    paddingHorizontal: 32,
  },
});
