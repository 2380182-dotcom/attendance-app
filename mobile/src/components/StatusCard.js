import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

export default function StatusCard({ isCheckedIn, currentCheckIn }) {
  const formatDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return dateStr;
    }
  };

  return (
    <View style={[styles.card, isCheckedIn ? styles.activeCard : styles.inactiveCard]}>
      <View style={styles.headerContainer}>
        <View style={[styles.badge, isCheckedIn ? styles.badgeActive : styles.badgeInactive]}>
          <Text style={styles.badgeText}>
            {isCheckedIn ? 'ACTIVE CHECK-IN' : 'CHECKED OUT'}
          </Text>
        </View>
        <MaterialIcons 
          name={isCheckedIn ? 'check-circle' : 'offline-bolt'} 
          size={24} 
          color={isCheckedIn ? '#4CAF50' : '#757575'} 
        />
      </View>

      <Text style={styles.statusTitle}>
        {isCheckedIn ? 'You are currently on duty' : 'You are currently off duty'}
      </Text>

      {isCheckedIn && currentCheckIn ? (
        <View style={styles.detailsContainer}>
          <View style={styles.detailRow}>
            <MaterialIcons name="store" size={18} color="#555" />
            <Text style={styles.detailText}>
              Mart: <Text style={styles.boldText}>{currentCheckIn.martName || 'Unknown Mart'}</Text>
            </Text>
          </View>
          <View style={styles.detailRow}>
            <MaterialIcons name="schedule" size={18} color="#555" />
            <Text style={styles.detailText}>
              Check-in Time: <Text style={styles.boldText}>{formatDate(currentCheckIn.checkInTime)}</Text>
            </Text>
          </View>
          {currentCheckIn.status === 'LATE' && (
            <View style={[styles.detailRow, styles.lateContainer]}>
              <MaterialIcons name="warning" size={18} color="#D32F2F" />
              <Text style={styles.lateText}>Checked in Late</Text>
            </View>
          )}
        </View>
      ) : (
        <Text style={styles.hintText}>
          Please select a nearby mart and check-in to log your attendance.
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    padding: 20,
    backgroundColor: '#fff',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
    marginVertical: 12,
  },
  activeCard: {
    borderLeftWidth: 6,
    borderLeftColor: '#4CAF50',
  },
  inactiveCard: {
    borderLeftWidth: 6,
    borderLeftColor: '#757575',
  },
  headerContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  badge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeActive: {
    backgroundColor: '#E8F5E9',
  },
  badgeInactive: {
    backgroundColor: '#F5F5F5',
  },
  badgeText: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#333',
  },
  statusTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#212121',
    marginBottom: 12,
  },
  detailsContainer: {
    marginTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#EEEEEE',
    paddingTop: 12,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  detailText: {
    marginLeft: 8,
    fontSize: 14,
    color: '#424242',
  },
  boldText: {
    fontWeight: '600',
    color: '#212121',
  },
  hintText: {
    fontSize: 13,
    color: '#757575',
    lineHeight: 18,
  },
  lateContainer: {
    marginTop: 4,
    backgroundColor: '#FFEBEE',
    padding: 6,
    borderRadius: 6,
  },
  lateText: {
    marginLeft: 6,
    fontSize: 12,
    fontWeight: 'bold',
    color: '#D32F2F',
  },
});
