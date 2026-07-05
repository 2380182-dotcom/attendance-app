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
import AppButton from '../../components/AppButton';
import EmptyState from '../../components/EmptyState';
import { downloadAndShareFile } from '../../utils/downloadAndShareFile';
import { useTheme } from '../../theme';

export default function HistoryScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
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

  const pickerSelectStyles = createPickerStyles(colors);

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
            <MaterialIcons name="date-range" size={18} color={colors.secondary} />
            <Text style={styles.dateSelectorText}>
              Start: {startDate.toLocaleDateString([], { month: 'short', day: 'numeric' })}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setShowEndPicker(true)} style={styles.dateSelector}>
            <MaterialIcons name="date-range" size={18} color={colors.secondary} />
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

        <AppButton
          title="Export to Excel"
          onPress={handleExport}
          variant="secondary"
          size="sm"
          icon="file-download"
          style={{ marginTop: 4 }}
        />
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
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primary]} tintColor={colors.primary} />
        }
        ListEmptyComponent={
          <EmptyState
            icon="event-busy"
            title="No Matching Records"
            message="No attendance records match your filter criteria. Try adjusting the dates or filter settings."
          />
        }
        contentContainerStyle={filteredHistory.length === 0 ? styles.emptyScroll : styles.listScroll}
      />
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  filterCard: {
    backgroundColor: colors.surface,
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    shadowColor: colors.shadow,
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
    color: colors.textSecondary,
    marginBottom: 4,
  },
  pickerWrapper: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    backgroundColor: colors.inputBackground,
  },
  dateSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    padding: 8,
    width: '48%',
    backgroundColor: colors.inputBackground,
    justifyContent: 'center',
  },
  dateSelectorText: {
    fontSize: 13,
    marginLeft: 6,
    color: colors.textPrimary,
  },
  listScroll: {
    paddingVertical: 12,
  },
  emptyScroll: {
    flexGrow: 1,
    justifyContent: 'center',
  },
});

const createPickerStyles = (colors) => ({
  inputIOS: {
    fontSize: 13,
    paddingVertical: 8,
    paddingHorizontal: 8,
    color: colors.textPrimary,
  },
  inputAndroid: {
    fontSize: 13,
    paddingHorizontal: 8,
    paddingVertical: 4,
    color: colors.textPrimary,
  },
});
