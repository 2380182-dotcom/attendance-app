import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import StatusBadge from './StatusBadge';

export default function NotificationCard({ notification, onMarkRead, onDelete }) {
  const formattedTime = notification.createdAt
    ? new Date(notification.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : '';

  return (
    <View style={[styles.card, notification.isRead ? styles.read : styles.unread]}>
      <View style={styles.header}>
        <Text style={styles.agentName}>{notification.agentName}</Text>
        <Text style={styles.time}>{formattedTime}</Text>
      </View>
      
      <Text style={styles.message}>{notification.message}</Text>
      
      <View style={styles.footer}>
        <StatusBadge status={notification.type} />
        <View style={styles.actions}>
          {!notification.isRead && (
            <TouchableOpacity onPress={() => onMarkRead(notification.id)} style={styles.actionButton}>
              <Text style={styles.actionText}>Mark Read</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity onPress={() => onDelete(notification.id)} style={[styles.actionButton, styles.deleteButton]}>
            <Text style={[styles.actionText, styles.deleteText]}>Delete</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 12,
    marginVertical: 6,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
    borderLeftWidth: 4,
  },
  unread: {
    borderLeftColor: '#2196F3',
  },
  read: {
    borderLeftColor: '#B0BEC5',
    opacity: 0.8,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  agentName: {
    fontWeight: 'bold',
    fontSize: 14,
    color: '#333',
  },
  time: {
    fontSize: 11,
    color: '#888',
  },
  message: {
    fontSize: 13,
    color: '#555',
    marginBottom: 8,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  actions: {
    flexDirection: 'row',
  },
  actionButton: {
    marginLeft: 12,
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 4,
    backgroundColor: '#E3F2FD',
  },
  deleteButton: {
    backgroundColor: '#FFEBEE',
  },
  actionText: {
    fontSize: 11,
    color: '#1E88E5',
    fontWeight: 'bold',
  },
  deleteText: {
    color: '#D32F2F',
  },
});
