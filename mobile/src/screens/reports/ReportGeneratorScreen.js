import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import RNPickerSelect from 'react-native-picker-select';
import DateTimePicker from '@react-native-community/datetimepicker';
import api, { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import { downloadAndShareFile } from '../../utils/downloadAndShareFile';
import { useTheme } from '../../theme';

export default function ReportGeneratorScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const pickerStyles = createPickerStyles(colors);
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
    const dateStr = useDate ? date.toISOString().split('T')[0] : null;
    const agentId = selectedAgentId === 'ALL' ? null : selectedAgentId;
    const path = apiService.reports.getExportPath(
      dateStr,
      agentId,
      parseInt(year),
      parseInt(month)
    );
    await downloadAndShareFile(path, 'attendance_report.xlsx');
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
      <AppCard style={styles.card}>
        <Text style={styles.cardTitle}>Export System Excel Reports</Text>
        <Text style={styles.cardDesc}>Download daily and monthly attendance worksheets, late arrivals list, and geofence logs in one workbook.</Text>

        <View style={styles.divider} />

        <View style={styles.row}>
          <Text style={styles.label}>Use Target Date Filter</Text>
          <TouchableOpacity onPress={() => setUseDate(!useDate)}>
            <MaterialIcons
              name={useDate ? 'check-box' : 'check-box-outline-blank'}
              size={24}
              color={useDate ? colors.secondary : colors.textSecondary}
            />
          </TouchableOpacity>
        </View>

        {useDate && (
          <TouchableOpacity onPress={() => setShowDatePicker(true)} style={styles.dateBtn}>
            <MaterialIcons name="date-range" size={20} color={colors.secondary} />
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

        <AppButton
          title="Export Excel File"
          onPress={handleExport}
          variant="success"
          icon="file-download"
          style={{ marginTop: 10 }}
        />
      </AppCard>
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    padding: 16,
    justifyContent: 'center',
  },
  card: {},
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: colors.textPrimary,
    textAlign: 'center',
  },
  cardDesc: {
    fontSize: 12,
    color: colors.textSecondary,
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 18,
  },
  divider: {
    height: 1,
    backgroundColor: colors.divider,
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
    color: colors.textSecondary,
    marginBottom: 6,
  },
  dateBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    padding: 10,
    backgroundColor: colors.inputBackground,
    marginBottom: 16,
  },
  dateBtnText: {
    fontSize: 14,
    color: colors.textPrimary,
    marginLeft: 8,
  },
  pickerWrapper: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    backgroundColor: colors.inputBackground,
    marginBottom: 16,
  },
});

const createPickerStyles = (colors) => ({
  inputIOS: {
    fontSize: 14,
    paddingVertical: 10,
    paddingHorizontal: 12,
    color: colors.textPrimary,
  },
  inputAndroid: {
    fontSize: 14,
    paddingHorizontal: 12,
    paddingVertical: 6,
    color: colors.textPrimary,
  },
});
