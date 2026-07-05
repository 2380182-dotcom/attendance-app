import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  FlatList,
  Alert,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import DateTimePicker from '@react-native-community/datetimepicker';
import api, { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import SearchBar from '../../components/SearchBar';
import { DATE_PRESETS, PRESET_LABELS, computeDateRange } from '../../utils/dateRangePresets';
import { downloadAndShareFile } from '../../utils/downloadAndShareFile';
import { useTheme } from '../../theme';

export default function HRAgentAttendanceReportScreen() {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAgent, setSelectedAgent] = useState(null);

  const [preset, setPreset] = useState('MONTH');
  const [customFrom, setCustomFrom] = useState(new Date());
  const [customTo, setCustomTo] = useState(new Date());
  const [showFromPicker, setShowFromPicker] = useState(false);
  const [showToPicker, setShowToPicker] = useState(false);

  useEffect(() => {
    const fetchAgents = async () => {
      try {
        const response = await api.get('/agents');
        if (response.data && response.data.success) {
          setAgents(response.data.data);
        }
      } catch (e) {
        console.error(e);
        Alert.alert('Error', 'Unable to retrieve agents list.');
      } finally {
        setLoading(false);
      }
    };
    fetchAgents();
  }, []);

  const filteredAgents = searchQuery.trim()
    ? agents.filter((a) => {
        const q = searchQuery.trim().toLowerCase();
        return (
          (a.agentId && a.agentId.toLowerCase().includes(q)) ||
          (a.name && a.name.toLowerCase().includes(q))
        );
      })
    : [];

  const handleSelectAgent = (agent) => {
    setSelectedAgent(agent);
    setSearchQuery('');
  };

  const handleGenerate = async () => {
    if (!selectedAgent) {
      Alert.alert('Select an Agent', 'Search for and select an agent before generating the report.');
      return;
    }
    const { from, to } = computeDateRange(preset, customFrom, customTo);
    if (new Date(from) > new Date(to)) {
      Alert.alert('Invalid Range', '"From" date must be before "To" date.');
      return;
    }
    const path = apiService.reports.getHrAgentAttendanceCsvPath(selectedAgent.id, from, to);
    await downloadAndShareFile(path, `agent_${selectedAgent.agentId}_attendance_${from}_to_${to}.csv`);
  };

  const onFromChange = (event, date) => {
    setShowFromPicker(false);
    if (date) setCustomFrom(date);
  };

  const onToChange = (event, date) => {
    setShowToPicker(false);
    if (date) setCustomTo(date);
  };

  if (loading) {
    return <Loading message="Loading agents..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <AppCard style={styles.card}>
        <Text style={styles.cardTitle}>Agent Attendance CSV Report</Text>
        <Text style={styles.cardDesc}>
          Search an agent, choose a date range, and export their attendance history only —
          check-in/out times, late minutes, face verification, and geofence compliance.
        </Text>

        <View style={styles.divider} />

        <Text style={styles.label}>Search Agent (ID or Name)</Text>
        {selectedAgent ? (
          <View style={styles.selectedAgentRow}>
            <View>
              <Text style={styles.selectedAgentName}>{selectedAgent.name}</Text>
              <Text style={styles.selectedAgentId}>{selectedAgent.agentId}</Text>
            </View>
            <TouchableOpacity onPress={() => setSelectedAgent(null)} style={styles.changeBtn}>
              <Text style={styles.changeBtnText}>Change</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <>
            <SearchBar
              value={searchQuery}
              onChangeText={setSearchQuery}
              placeholder="e.g. RAFAY001 or Rafay"
              autoFocus={false}
            />
            {filteredAgents.length > 0 && (
              <FlatList
                data={filteredAgents}
                keyExtractor={(item) => item.id.toString()}
                style={styles.resultsList}
                renderItem={({ item }) => (
                  <TouchableOpacity style={styles.resultRow} onPress={() => handleSelectAgent(item)}>
                    <MaterialIcons name="person" size={18} color={colors.secondary} />
                    <Text style={styles.resultText}>{item.name} ({item.agentId})</Text>
                  </TouchableOpacity>
                )}
              />
            )}
          </>
        )}

        <Text style={[styles.label, { marginTop: 16 }]}>Date Range</Text>
        <View style={styles.presetRow}>
          {DATE_PRESETS.map((p) => (
            <TouchableOpacity
              key={p}
              style={[styles.presetBtn, preset === p && styles.presetBtnActive]}
              onPress={() => setPreset(p)}
            >
              <Text style={[styles.presetBtnText, preset === p && styles.presetBtnTextActive]}>
                {PRESET_LABELS[p]}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {preset === 'CUSTOM' && (
          <View style={styles.customDateRow}>
            <TouchableOpacity onPress={() => setShowFromPicker(true)} style={styles.dateBtn}>
              <MaterialIcons name="date-range" size={18} color={colors.secondary} />
              <Text style={styles.dateBtnText}>From: {customFrom.toLocaleDateString()}</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => setShowToPicker(true)} style={styles.dateBtn}>
              <MaterialIcons name="date-range" size={18} color={colors.secondary} />
              <Text style={styles.dateBtnText}>To: {customTo.toLocaleDateString()}</Text>
            </TouchableOpacity>
          </View>
        )}

        {showFromPicker && (
          <DateTimePicker value={customFrom} mode="date" display="default" onChange={onFromChange} />
        )}
        {showToPicker && (
          <DateTimePicker value={customTo} mode="date" display="default" onChange={onToChange} />
        )}

        <AppButton
          title="Generate Attendance CSV"
          onPress={handleGenerate}
          variant="success"
          icon="file-download"
          style={{ marginTop: 20 }}
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
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginBottom: 6,
  },
  resultsList: {
    maxHeight: 180,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    marginTop: 6,
  },
  resultRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  resultText: {
    marginLeft: 8,
    fontSize: 13,
    color: colors.textPrimary,
  },
  selectedAgentRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.secondaryLight,
    borderRadius: 8,
    padding: 12,
  },
  selectedAgentName: {
    fontWeight: 'bold',
    fontSize: 14,
    color: colors.secondary,
  },
  selectedAgentId: {
    fontSize: 11,
    color: colors.secondary,
    marginTop: 2,
  },
  changeBtn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: colors.secondary,
    borderRadius: 6,
  },
  changeBtnText: {
    color: colors.textOnPrimary,
    fontSize: 11,
    fontWeight: 'bold',
  },
  presetRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  presetBtn: {
    flex: 1,
    paddingVertical: 8,
    marginHorizontal: 2,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    backgroundColor: colors.inputBackground,
  },
  presetBtnActive: {
    backgroundColor: colors.secondary,
    borderColor: colors.secondary,
  },
  presetBtnText: {
    fontSize: 11,
    fontWeight: 'bold',
    color: colors.textSecondary,
  },
  presetBtnTextActive: {
    color: colors.textOnPrimary,
  },
  customDateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 12,
  },
  dateBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    padding: 10,
    backgroundColor: colors.inputBackground,
    marginHorizontal: 2,
  },
  dateBtnText: {
    fontSize: 12,
    color: colors.textPrimary,
    marginLeft: 6,
  },
});
