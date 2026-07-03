import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';

export default function AttendanceCard({
  title,
  subtitle,
  checkInTime,
  checkOutTime,
  status,
  distance,
  date,
}) {
  const formatTime = (timeStr) => {
    if (!timeStr) return '--:--';
    try {
      const dateObj = new Date(timeStr);
      return dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return timeStr;
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const dateObj = new Date(dateStr);
      return dateObj.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
    } catch (e) {
      return dateStr;
    }
  };

  // Status styling configurations
  const getStatusStyle = (statusVal) => {
    const s = String(statusVal).toUpperCase();
    if (s === 'IN' || s === 'PRESENT') {
      return { bg: '#E8F5E9', text: '#2E7D32', label: 'ON TIME' };
    } else if (s === 'LATE') {
      return { bg: '#FFF3E0', text: '#EF6C00', label: 'LATE' };
    } else {
      return { bg: '#ECEFF1', text: '#37474F', label: s };
    }
  };

  const statusConfig = getStatusStyle(status);

  return (
    <View style={styles.card}>
      <View style={styles.row}>
        <View style={styles.titleContainer}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          {subtitle ? (
            <Text style={styles.subtitle} numberOfLines={1}>
              {subtitle}
            </Text>
          ) : null}
          {date ? (
            <Text style={styles.dateText}>{formatDate(date)}</Text>
          ) : null}
        </View>
        <View style={[styles.badge, { backgroundColor: statusConfig.bg }]}>
          <Text style={[styles.badgeText, { color: statusConfig.text }]}>
            {statusConfig.label}
          </Text>
        </View>
      </View>

      <View style={styles.divider} />

      <View style={styles.detailsRow}>
        <View style={styles.timeCol}>
          <Text style={styles.timeLabel}>CHECK-IN</Text>
          <View style={styles.timeValueRow}>
            <MaterialIcons name="login" size={14} color="#4CAF50" />
            <Text style={styles.timeText}>{formatTime(checkInTime)}</Text>
          </View>
        </View>

        <View style={styles.timeCol}>
          <Text style={styles.timeLabel}>CHECK-OUT</Text>
          <View style={styles.timeValueRow}>
            <MaterialIcons name="logout" size={14} color="#F44336" />
            <Text style={styles.timeText}>{formatTime(checkOutTime)}</Text>
          </View>
        </View>

        {distance !== undefined && distance !== null ? (
          <View style={styles.distanceCol}>
            <Text style={styles.timeLabel}>DISTANCE</Text>
            <View style={styles.timeValueRow}>
              <MaterialIcons name="location-searching" size={14} color="#2196F3" />
              <Text style={styles.timeText}>
                {distance > 1000 
                  ? `${(distance / 1000).toFixed(1)} km` 
                  : `${Math.round(distance)} m`}
              </Text>
            </View>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginVertical: 6,
    marginHorizontal: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  titleContainer: {
    flex: 1,
    paddingRight: 8,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: '#212121',
  },
  subtitle: {
    fontSize: 13,
    color: '#757575',
    marginTop: 2,
  },
  dateText: {
    fontSize: 12,
    color: '#9E9E9E',
    marginTop: 4,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: 'bold',
  },
  divider: {
    height: 1,
    backgroundColor: '#EEEEEE',
    marginVertical: 12,
  },
  detailsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  timeCol: {
    flex: 1,
  },
  distanceCol: {
    flex: 1,
    alignItems: 'flex-end',
  },
  timeLabel: {
    fontSize: 10,
    color: '#9E9E9E',
    fontWeight: 'bold',
    marginBottom: 4,
  },
  timeValueRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  timeText: {
    fontSize: 13,
    color: '#424242',
    marginLeft: 4,
    fontWeight: '500',
  },
});
