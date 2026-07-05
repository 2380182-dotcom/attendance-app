import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  SafeAreaView,
  Linking
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import DateTimePicker from '@react-native-community/datetimepicker';
import api, { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import { DATE_PRESETS, PRESET_LABELS, computeDateRange } from '../../utils/dateRangePresets';

export default function HRAgentAttendanceReportScreen() {
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
    try {
      const url = apiService.reports.getHrAgentAttendanceCsvUrl(selectedAgent.id, from, to);
      await Linking.openURL(url);
    } catch (e) {
      Alert.alert('Download Error', 'Could not open the CSV download link.');
    }
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
      <View style={styles.card}>
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
            <TextInput
              style={styles.input}
              placeholder="e.g. RAFAY001 or Rafay"
              value={searchQuery}
              onChangeText={setSearchQuery}
              autoCapitalize="none"
            />
            {filteredAgents.length > 0 && (
              <FlatList
                data={filteredAgents}
                keyExtractor={(item) => item.id.toString()}
                style={styles.resultsList}
                renderItem={({ item }) => (
                  <TouchableOpacity style={styles.resultRow} onPress={() => handleSelectAgent(item)}>
                    <MaterialIcons name="person" size={18} color="#1976D2" />
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
              <MaterialIcons name="date-range" size={18} color="#1976D2" />
              <Text style={styles.dateBtnText}>From: {customFrom.toLocaleDateString()}</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => setShowToPicker(true)} style={styles.dateBtn}>
              <MaterialIcons name="date-range" size={18} color="#1976D2" />
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

        <TouchableOpacity style={styles.exportButton} onPress={handleGenerate}>
          <MaterialIcons name="file-download" size={22} color="#fff" />
          <Text style={styles.exportButtonText}>Generate Attendance CSV</Text>
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
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#666',
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    paddingHorizontal: 12,
    height: 44,
    backgroundColor: '#FAFAFA',
    fontSize: 14,
    color: '#333',
  },
  resultsList: {
    maxHeight: 180,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    borderRadius: 8,
    marginTop: 6,
  },
  resultRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  resultText: {
    marginLeft: 8,
    fontSize: 13,
    color: '#333',
  },
  selectedAgentRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#E3F2FD',
    borderRadius: 8,
    padding: 12,
  },
  selectedAgentName: {
    fontWeight: 'bold',
    fontSize: 14,
    color: '#1565C0',
  },
  selectedAgentId: {
    fontSize: 11,
    color: '#1976D2',
    marginTop: 2,
  },
  changeBtn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: '#1976D2',
    borderRadius: 6,
  },
  changeBtnText: {
    color: '#fff',
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
    borderColor: '#E0E0E0',
    alignItems: 'center',
    backgroundColor: '#FAFAFA',
  },
  presetBtnActive: {
    backgroundColor: '#1976D2',
    borderColor: '#1976D2',
  },
  presetBtnText: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#666',
  },
  presetBtnTextActive: {
    color: '#fff',
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
    borderColor: '#E0E0E0',
    borderRadius: 8,
    padding: 10,
    backgroundColor: '#FAFAFA',
    marginHorizontal: 2,
  },
  dateBtnText: {
    fontSize: 12,
    color: '#333',
    marginLeft: 6,
  },
  exportButton: {
    backgroundColor: '#4CAF50',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    height: 48,
    borderRadius: 8,
    marginTop: 20,
  },
  exportButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 15,
    marginLeft: 8,
  },
});
