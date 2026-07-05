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
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

export default function HRReportScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
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
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
        ListEmptyComponent={
          <EmptyState
            icon="notifications-off"
            title="No HR Logs Found"
            message="There are no warnings in the HR notifications system yet."
            style={styles.emptyContainer}
          />
        }
        contentContainerStyle={styles.listScroll}
      />
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  listScroll: {
    padding: 16,
  },
  emptyContainer: {
    paddingTop: 100,
  },
});
