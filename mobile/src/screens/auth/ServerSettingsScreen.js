import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import axios from 'axios';
import { storage } from '../../utils/storage';
import config from '../../config';

export default function ServerSettingsScreen({ navigation }) {
  const [enteredUrl, setEnteredUrl] = useState('');
  const [testing, setTesting] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState(null);

  useEffect(() => {
    const loadUrl = async () => {
      const saved = await storage.getServerUrl();
      setEnteredUrl(saved || config.API_URL);
    };
    loadUrl();
  }, []);

  const handleSave = async () => {
    let url = enteredUrl.trim();
    if (!url) {
      Alert.alert('Error', 'Server URL cannot be empty.');
      return;
    }
    
    // Normalize trailing slash
    if (url.endsWith('/')) {
      url = url.substring(0, url.length - 1);
    }
    
    // Auto-append /api if it doesn't end with /api
    if (!url.endsWith('/api') && !url.includes('/api/')) {
      url = url + '/api';
    }

    await storage.setServerUrl(url);
    Alert.alert('Settings Saved', `API Base URL updated to:\n${url}`, [
      { text: 'OK', onPress: () => navigation.goBack() }
    ]);
  };

  const handleReset = async () => {
    await storage.setServerUrl(null);
    setEnteredUrl(config.API_URL);
    setConnectionStatus(null);
    Alert.alert('Reset Successful', `Default configuration restored:\n${config.API_URL}`);
  };

  const handleTestConnection = async () => {
    let url = enteredUrl.trim();
    if (!url) {
      Alert.alert('Error', 'Please enter a URL to test.');
      return;
    }

    if (url.endsWith('/')) {
      url = url.substring(0, url.length - 1);
    }
    if (!url.endsWith('/api') && !url.includes('/api/')) {
      url = url + '/api';
    }

    setTesting(true);
    setConnectionStatus(null);

    const startTime = Date.now();
    try {
      // Create independent Axios instance with short timeout
      const testInstance = axios.create({
        baseURL: url,
        timeout: 3000,
        headers: { 'Content-Type': 'application/json' }
      });

      // Attempt to hit the login endpoint which is public
      await testInstance.post('/auth/login', { agentId: '', password: '' });
      
      const latency = Date.now() - startTime;
      setConnectionStatus({
        success: true,
        message: `Connected successfully!\nLatency: ${latency}ms`
      });
    } catch (error) {
      const latency = Date.now() - startTime;
      
      // If we got an error response (like 400 or 401), the server is alive and responded!
      if (error.response) {
        setConnectionStatus({
          success: true,
          message: `Connected! Server responded with HTTP status ${error.response.status} (Latency: ${latency}ms).`
        });
      } else {
        setConnectionStatus({
          success: false,
          message: `Connection failed: ${error.message || 'Request timed out'}`
        });
      }
    } finally {
      setTesting(false);
    }
  };

  const applyPreset = (presetUrl) => {
    setEnteredUrl(presetUrl);
    setConnectionStatus(null);
  };

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <View style={styles.card}>
            <View style={styles.iconContainer}>
              <MaterialIcons name="dns" size={40} color="#1976D2" />
            </View>
            <Text style={styles.title}>Server Connection Settings</Text>
            <Text style={styles.subtitle}>
              Configure the API endpoint target for this application. If you changed networks, update the server's IP address below.
            </Text>

            <Text style={styles.label}>API Base URL</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="link" size={20} color="#757575" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="http://localhost:8080/api"
                placeholderTextColor="#9E9E9E"
                value={enteredUrl}
                onChangeText={setEnteredUrl}
                autoCapitalize="none"
                autoCorrect={false}
              />
            </View>

            {/* Test Connection Button & Indicator */}
            <View style={styles.testSection}>
              <TouchableOpacity
                style={[styles.testBtn, testing && styles.disabledBtn]}
                onPress={handleTestConnection}
                disabled={testing}
              >
                {testing ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <>
                    <MaterialIcons name="network-check" size={20} color="#fff" style={styles.btnIcon} />
                    <Text style={styles.testBtnText}>Test Connection</Text>
                  </>
                )}
              </TouchableOpacity>

              {connectionStatus && (
                <View
                  style={[
                    styles.statusBadge,
                    connectionStatus.success ? styles.successBadge : styles.errorBadge
                  ]}
                >
                  <MaterialIcons
                    name={connectionStatus.success ? 'check-circle' : 'error'}
                    size={20}
                    color={connectionStatus.success ? '#2E7D32' : '#C62828'}
                  />
                  <Text
                    style={[
                      styles.statusText,
                      connectionStatus.success ? styles.successText : styles.errorText
                    ]}
                  >
                    {connectionStatus.message}
                  </Text>
                </View>
              )}
            </View>

            {/* Presets Section */}
            <Text style={styles.sectionTitle}>Quick Presets</Text>
            <View style={styles.presetsContainer}>
              <TouchableOpacity
                style={styles.presetItem}
                onPress={() => applyPreset(config.API_URL)}
              >
                <Text style={styles.presetTitle}>Default Config URL</Text>
                <Text style={styles.presetSubtitle}>From EXPO_PUBLIC_API_URL</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.presetItem}
                onPress={() => applyPreset('https://attendance-app-rn2m.onrender.com/api')}
              >
                <Text style={styles.presetTitle}>Render Production</Text>
                <Text style={styles.presetSubtitle}>attendance-app-rn2m.onrender.com</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.presetItem}
                onPress={() => applyPreset('http://10.0.2.2:8080/api')}
              >
                <Text style={styles.presetTitle}>Android Emulator</Text>
                <Text style={styles.presetSubtitle}>10.0.2.2 (Loopback)</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.presetItem}
                onPress={() => applyPreset('http://localhost:8080/api')}
              >
                <Text style={styles.presetTitle}>Web Localhost</Text>
                <Text style={styles.presetSubtitle}>localhost (Browser/Expo)</Text>
              </TouchableOpacity>
            </View>

            {/* Save / Reset Actions */}
            <View style={styles.actionContainer}>
              <TouchableOpacity style={styles.resetBtn} onPress={handleReset}>
                <Text style={styles.resetBtnText}>Reset Defaults</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.saveBtn} onPress={handleSave}>
                <Text style={styles.saveBtnText}>Save Settings</Text>
              </TouchableOpacity>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  keyboardView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    padding: 16,
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4,
  },
  iconContainer: {
    alignSelf: 'center',
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#E3F2FD',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
  },
  label: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#424242',
    marginBottom: 6,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    backgroundColor: '#FAFAFA',
    paddingHorizontal: 12,
    marginBottom: 16,
  },
  inputIcon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    height: 48,
    fontSize: 15,
    color: '#212121',
  },
  testSection: {
    marginBottom: 24,
  },
  testBtn: {
    flexDirection: 'row',
    backgroundColor: '#607D8B',
    height: 44,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  disabledBtn: {
    backgroundColor: '#B0BEC5',
  },
  btnIcon: {
    marginRight: 6,
  },
  testBtnText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 14,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
  },
  successBadge: {
    backgroundColor: '#E8F5E9',
    borderColor: '#A5D6A7',
  },
  errorBadge: {
    backgroundColor: '#FFEBEE',
    borderColor: '#EF9A9A',
  },
  statusText: {
    flex: 1,
    marginLeft: 8,
    fontSize: 13,
    lineHeight: 18,
  },
  successText: {
    color: '#2E7D32',
  },
  errorText: {
    color: '#C62828',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 12,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  presetsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  presetItem: {
    width: '48%',
    backgroundColor: '#F5F7FA',
    borderWidth: 1,
    borderColor: '#E4E7EB',
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
  },
  presetTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#1976D2',
    marginBottom: 2,
  },
  presetSubtitle: {
    fontSize: 10,
    color: '#757575',
  },
  actionContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  resetBtn: {
    flex: 1,
    height: 48,
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: '#B0BEC5',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 8,
  },
  resetBtnText: {
    color: '#546E7A',
    fontWeight: 'bold',
    fontSize: 15,
  },
  saveBtn: {
    flex: 1.5,
    height: 48,
    borderRadius: 8,
    backgroundColor: '#2196F3',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 8,
  },
  saveBtnText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 15,
  },
});
