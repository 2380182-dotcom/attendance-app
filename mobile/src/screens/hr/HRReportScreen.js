import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  FlatList,
  RefreshControl,
  ActivityIndicator,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import NotificationCard from '../../components/NotificationCard';
import Loading from '../../components/Loading';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

const PAGE_SIZE = 20;

export default function HRReportScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [notifications, setNotifications] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const fetchNotifs = useCallback(async () => {
    try {
      const result = await apiService.notifications.getHR(0, PAGE_SIZE);
      setNotifications(result.content);
      setPage(0);
      setHasNext(result.hasNext);
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Unable to retrieve HR warnings logs.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const result = await apiService.notifications.getHR(nextPage, PAGE_SIZE);
      setNotifications((prev) => [...prev, ...result.content]);
      setPage(nextPage);
      setHasNext(result.hasNext);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingMore(false);
    }
  }, [page, hasNext, loadingMore]);

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
        onEndReached={loadMore}
        onEndReachedThreshold={0.4}
        ListFooterComponent={
          loadingMore ? <ActivityIndicator style={styles.footerLoader} color={colors.primary} /> : null
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
  footerLoader: {
    paddingVertical: 16,
  },
});
