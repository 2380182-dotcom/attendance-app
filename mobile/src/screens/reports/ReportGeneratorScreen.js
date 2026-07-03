import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  Alert,
  SafeAreaView,
  Linking
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import RNPickerSelect from 'react-native-picker-select';
import DateTimePicker from '@react-native-community/datetimepicker';
import api, { apiService } from '../../services/api';
import Loading from '../../components/Loading';

export default function ReportGeneratorScreen() {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);

  const [selectedAgentId, setSelectedAgentId] = useState('ALL');
  const [date, setDate] = useState(new Date());
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [useDate, setUseDate] = useState(true);

  const [year, setYear] = useState(new Date().getFullYear().toString());
  const [month, setMonth] = useState((new Date().getMonth() + 1).toString());

  useEffect(() => {
    const fetchAgents = async () => {
      try {
        const response = await api.get('/agents');
        if (response.data && response.data.success) {
          setAgents(response.data.data);
        }
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchAgents();
  }, []);

  const handleExport = async () => {
    try {
      const dateStr = useDate ? date.toISOString().split('T')[0] : null;
      const agentId = selectedAgentId === 'ALL' ? null : selectedAgentId;
      const url = apiService.reports.getExportUrl(
        dateStr,
        agentId,
        parseInt(year),
        parseInt(month)
      );
      await Linking.openURL(url);
    } catch (e) {
      Alert.alert('Download Error', 'Could not open the export download link.');
    }
  };

  const onDateChange = (event, selectedDate) => {
    setShowDatePicker(false);
    if (selectedDate) {
      setDate(selectedDate);
    }
  };

  if (loading) {
    return <Loading message="Loading report options..." fullScreen />;
  }

  const agentItems = [
    { label: 'All Agents', value: 'ALL' },
    ...agents.map(a => ({ label: a.name, value: a.id }))
  ];

  const years = [
    { label: '2026', value: '2026' },
    { label: '2025', value: '2025' },
    { label: '2024', value: '2024' }
  ];

  const months = [
    { label: 'January', value: '1' },
    { label: 'February', value: '2' },
    { label: 'March', value: '3' },
    { label: 'April', value: '4' },
    { label: 'May', value: '5' },
    { label: 'June', value: '6' },
    { label: 'July', value: '7' },
    { label: 'August', value: '8' },
    { label: 'September', value: '9' },
    { label: 'October', value: '10' },
    { label: 'November', value: '11' },
    { label: 'December', value: '12' }
  ];

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Export System Excel Reports</Text>
        <Text style={styles.cardDesc}>Download daily and monthly attendance worksheets, late arrivals list, and geofence logs in one workbook.</Text>

        <View style={styles.divider} />

        <View style={styles.row}>
          <Text style={styles.label}>Use Target Date Filter</Text>
          <TouchableOpacity onPress={() => setUseDate(!useDate)}>
            <MaterialIcons
              name={useDate ? 'check-box' : 'check-box-outline-blank'}
              size={24}
              color={useDate ? '#1976D2' : '#757575'}
            />
          </TouchableOpacity>
        </View>

        {useDate && (
          <TouchableOpacity onPress={() => setShowDatePicker(true)} style={styles.dateBtn}>
            <MaterialIcons name="date-range" size={20} color="#1976D2" />
            <Text style={styles.dateBtnText}>Selected: {date.toLocaleDateString()}</Text>
          </TouchableOpacity>
        )}

        {showDatePicker && (
          <DateTimePicker
            value={date}
            mode="date"
            display="default"
            onChange={onDateChange}
          />
        )}

        <Text style={styles.label}>Filter by Agent (Monthly Sheet)</Text>
        <View style={styles.pickerWrapper}>
          <RNPickerSelect
            onValueChange={setSelectedAgentId}
            value={selectedAgentId}
            items={agentItems}
            placeholder={{}}
            style={pickerStyles}
          />
        </View>

        <View style={styles.rowGrid}>
          <View style={styles.halfCol}>
            <Text style={styles.label}>Month</Text>
            <View style={styles.pickerWrapper}>
              <RNPickerSelect
                onValueChange={setMonth}
                value={month}
                items={months}
                placeholder={{}}
                style={pickerStyles}
              />
            </View>
          </View>

          <View style={styles.halfCol}>
            <Text style={styles.label}>Year</Text>
            <View style={styles.pickerWrapper}>
              <RNPickerSelect
                onValueChange={setYear}
                value={year}
                items={years}
                placeholder={{}}
                style={pickerStyles}
              />
            </View>
          </View>
        </View>

        <TouchableOpacity onPress={handleExport} style={styles.exportButton}>
          <MaterialIcons name="file-download" size={22} color="#fff" />
          <Text style={styles.exportButtonText}>Export Excel File</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
    padding: 16,
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
  },
  cardDesc: {
    fontSize: 12,
    color: '#757575',
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 18,
  },
  divider: {
    height: 1,
    backgroundColor: '#EEEEEE',
    marginVertical: 16,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  rowGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  halfCol: {
    width: '48%',
  },
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#666',
    marginBottom: 6,
  },
  dateBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    padding: 10,
    backgroundColor: '#FAFAFA',
    marginBottom: 16,
  },
  dateBtnText: {
    fontSize: 14,
    color: '#333',
    marginLeft: 8,
  },
  pickerWrapper: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    backgroundColor: '#FAFAFA',
    marginBottom: 16,
  },
  exportButton: {
    backgroundColor: '#4CAF50',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    height: 48,
    borderRadius: 8,
    marginTop: 10,
  },
  exportButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 15,
    marginLeft: 8,
  },
});

const pickerStyles = {
  inputIOS: {
    fontSize: 14,
    paddingVertical: 10,
    paddingHorizontal: 12,
    color: '#333',
  },
  inputAndroid: {
    fontSize: 14,
    paddingHorizontal: 12,
    paddingVertical: 6,
    color: '#333',
  },
};
