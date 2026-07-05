import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  FlatList,
  RefreshControl,
  Alert,
  SafeAreaView,
  TouchableOpacity
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import RNPickerSelect from 'react-native-picker-select';
import DateTimePicker from '@react-native-community/datetimepicker';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import AttendanceCard from '../../components/AttendanceCard';
import Loading from '../../components/Loading';
import { downloadAndShareFile } from '../../utils/downloadAndShareFile';

export default function HistoryScreen() {
  const { user } = useContext(AuthContext);
  const [history, setHistory] = useState([]);
  const [filteredHistory, setFilteredHistory] = useState([]);
  const [marts, setMarts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [selectedMart, setSelectedMart] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [startDate, setStartDate] = useState(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000));
  const [endDate, setEndDate] = useState(new Date());
  
  const [showStartPicker, setShowStartPicker] = useState(false);
  const [showEndPicker, setShowEndPicker] = useState(false);

  const fetchHistoryAndMarts = useCallback(async () => {
    if (!user?.id) return;
    try {
      const [logs, martsList] = await Promise.all([
        apiService.attendance.getHistory(user.id),
        apiService.marts.getAll()
      ]);
      
      const sortedLogs = logs.sort((a, b) => new Date(b.checkInTime) - new Date(a.checkInTime));
      setHistory(sortedLogs);
      setFilteredHistory(sortedLogs);
      setMarts(martsList);
    } catch (e) {
      console.error(e);
      Alert.alert('History Error', 'Unable to retrieve your attendance history.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user]);

  useEffect(() => {
    fetchHistoryAndMarts();
  }, [fetchHistoryAndMarts]);

  useEffect(() => {
    let result = [...history];

    if (selectedMart !== 'ALL') {
      result = result.filter(item => item.martId === selectedMart);
    }

    if (selectedStatus !== 'ALL') {
      result = result.filter(item => item.status?.toUpperCase() === selectedStatus);
    }

    const start = new Date(startDate);
    start.setHours(0, 0, 0, 0);
    const end = new Date(endDate);
    end.setHours(23, 59, 59, 999);

    result = result.filter(item => {
      const checkIn = new Date(item.checkInTime);
      return checkIn >= start && checkIn <= end;
    });

    setFilteredHistory(result);
  }, [selectedMart, selectedStatus, startDate, endDate, history]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchHistoryAndMarts();
  };

  const handleExport = async () => {
    if (!user?.id) return;
    const startStr = startDate.toISOString().split('T')[0];
    const endStr = endDate.toISOString().split('T')[0];
    const path = apiService.reports.getAgentExportPath(user.id, startStr, endStr);
    await downloadAndShareFile(path, 'agent_attendance_history.xlsx');
  };

  const onStartChange = (event, date) => {
    setShowStartPicker(false);
    if (date) setStartDate(date);
  };

  const onEndChange = (event, date) => {
    setShowEndPicker(false);
    if (date) setEndDate(date);
  };

  if (loading && !refreshing) {
    return <Loading message="Loading history logs..." fullScreen />;
  }

  const martItems = [
    { label: 'All Marts', value: 'ALL' },
    ...marts.map(m => ({ label: m.name, value: m.id }))
  ];

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.filterCard}>
        <View style={styles.filterRow}>
          <View style={styles.halfFilter}>
            <Text style={styles.filterLabel}>Mart</Text>
            <View style={styles.pickerWrapper}>
              <RNPickerSelect
                onValueChange={(val) => setSelectedMart(val)}
                value={selectedMart}
                items={martItems}
                placeholder={{}}
                style={pickerSelectStyles}
              />
            </View>
          </View>
          <View style={styles.halfFilter}>
            <Text style={styles.filterLabel}>Status</Text>
            <View style={styles.pickerWrapper}>
              <RNPickerSelect
                onValueChange={(val) => setSelectedStatus(val)}
                value={selectedStatus}
                items={[
                  { label: 'All Statuses', value: 'ALL' },
                  { label: 'Checked In', value: 'IN' },
                  { label: 'Late', value: 'LATE' },
                  { label: 'Auto Checked Out', value: 'AUTO_CHECKOUT' },
                  { label: 'Absent', value: 'ABSENT' }
                ]}
                placeholder={{}}
                style={pickerSelectStyles}
              />
            </View>
          </View>
        </View>

        <View style={styles.filterRow}>
          <TouchableOpacity onPress={() => setShowStartPicker(true)} style={styles.dateSelector}>
            <MaterialIcons name="date-range" size={18} color="#1976D2" />
            <Text style={styles.dateSelectorText}>
              Start: {startDate.toLocaleDateString([], { month: 'short', day: 'numeric' })}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setShowEndPicker(true)} style={styles.dateSelector}>
            <MaterialIcons name="date-range" size={18} color="#1976D2" />
            <Text style={styles.dateSelectorText}>
              End: {endDate.toLocaleDateString([], { month: 'short', day: 'numeric' })}
            </Text>
          </TouchableOpacity>
        </View>

        {showStartPicker && (
          <DateTimePicker
            value={startDate}
            mode="date"
            display="default"
            onChange={onStartChange}
          />
        )}
        {showEndPicker && (
          <DateTimePicker
            value={endDate}
            mode="date"
            display="default"
            onChange={onEndChange}
          />
        )}

        <TouchableOpacity onPress={handleExport} style={styles.exportButton}>
          <MaterialIcons name="file-download" size={18} color="#fff" />
          <Text style={styles.exportButtonText}>Export to Excel</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={filteredHistory}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => (
          <AttendanceCard
            title={item.martName || 'Mart Location'}
            subtitle={item.status === 'LATE' ? 'Late check-in recorded' : 'Checked-in on duty'}
            checkInTime={item.checkInTime}
            checkOutTime={item.checkOutTime}
            status={item.status}
            distance={item.distanceFromMart}
            date={item.checkInTime}
          />
        )}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} color="#2196F3" />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <View style={styles.emptyIconContainer}>
              <MaterialIcons name="event-busy" size={48} color="#9E9E9E" />
            </View>
            <Text style={styles.emptyTitle}>No Matching Records</Text>
            <Text style={styles.emptyText}>
              No attendance records match your filter criteria. Try adjusting the dates or filter settings.
            </Text>
          </View>
        }
        contentContainerStyle={filteredHistory.length === 0 ? styles.emptyScroll : styles.listScroll}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  filterCard: {
    backgroundColor: '#fff',
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
    elevation: 2,
  },
  filterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  halfFilter: {
    width: '48%',
  },
  filterLabel: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#757575',
    marginBottom: 4,
  },
  pickerWrapper: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 6,
    backgroundColor: '#FAFAFA',
  },
  dateSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 6,
    padding: 8,
    width: '48%',
    backgroundColor: '#FAFAFA',
    justifyContent: 'center',
  },
  dateSelectorText: {
    fontSize: 13,
    marginLeft: 6,
    color: '#333',
  },
  exportButton: {
    backgroundColor: '#1976D2',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 10,
    borderRadius: 6,
    marginTop: 4,
  },
  exportButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    marginLeft: 6,
    fontSize: 13,
  },
  listScroll: {
    paddingVertical: 12,
  },
  emptyScroll: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  emptyIconContainer: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#EEEEEE',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#424242',
  },
  emptyText: {
    fontSize: 13,
    color: '#757575',
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 18,
  },
});

const pickerSelectStyles = {
  inputIOS: {
    fontSize: 13,
    paddingVertical: 8,
    paddingHorizontal: 8,
    color: '#333',
  },
  inputAndroid: {
    fontSize: 13,
    paddingHorizontal: 8,
    paddingVertical: 4,
    color: '#333',
  },
};
