import React, { useState, useContext } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  SafeAreaView,
  Modal,
  StatusBar,
  Image
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import Loading from '../../components/Loading';
import { colors } from '../../theme';

export default function LoginScreen({ navigation }) {
  const { login } = useContext(AuthContext);
  const [agentId, setAgentId] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = async () => {
    if (!agentId.trim()) {
      Alert.alert('Validation Error', 'Please enter your Agent ID.');
      return;
    }
    if (!password) {
      Alert.alert('Validation Error', 'Please enter your password.');
      return;
    }

    setLoading(true);
    const result = await login(agentId.trim(), password);
    setLoading(false);

    if (!result.success) {
      // If it's a network error, let the global axios interceptor handle it
      // to avoid showing duplicate/overlapping alert dialogs.
      const isNetError = result.error && (
        result.error.toLowerCase().includes('network error') ||
        result.error.toLowerCase().includes('timeout') ||
        result.error.toLowerCase().includes('econn')
      );
      if (!isNetError) {
        Alert.alert('Login Failed', result.error);
      }
    }
  };

  if (loading) {
    return <Loading message="Authenticating agent..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Settings Trigger Icon */}
          <TouchableOpacity
            style={styles.settingsIconBtn}
            onPress={() => navigation.navigate('ServerSettings')}
          >
            <MaterialIcons name="dns" size={24} color={colors.white} />
          </TouchableOpacity>

          <View style={styles.header}>
            <View style={styles.logoCard}>
              <Image
                source={require('../../../assets/dawn-bread-logo.png')}
                style={styles.logoImage}
                resizeMode="contain"
              />
            </View>
            <Text style={styles.appSubtitle}>Agent Verification Portal</Text>
          </View>

          <View style={styles.formCard}>
            <Text style={styles.welcomeText}>Welcome Back</Text>
            <Text style={styles.instructionText}>Sign in to manage your daily attendance</Text>

            <Text style={styles.label}>Agent ID</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="badge" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Agent ID (e.g. AGENT001)"
                placeholderTextColor={colors.textMuted}
                value={agentId}
                onChangeText={setAgentId}
                autoCapitalize="characters"
                autoCorrect={false}
              />
            </View>

            <Text style={styles.label}>Password</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="lock" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Password"
                placeholderTextColor={colors.textMuted}
                secureTextEntry={!showPassword}
                value={password}
                onChangeText={setPassword}
                autoCapitalize="none"
              />
              <TouchableOpacity
                onPress={() => setShowPassword(!showPassword)}
                style={styles.eyeIcon}
              >
                <MaterialIcons
                  name={showPassword ? 'visibility' : 'visibility-off'}
                  size={20}
                  color={colors.textSecondary}
                />
              </TouchableOpacity>
            </View>

            <TouchableOpacity style={styles.loginButton} onPress={handleLogin}>
              <Text style={styles.loginButtonText}>LOG IN</Text>
              <MaterialIcons name="arrow-forward" size={18} color={colors.white} style={{ marginLeft: 6 }} />
            </TouchableOpacity>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.primary,
  },
  keyboardView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingBottom: 24,
  },
  header: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  logoCard: {
    width: 120,
    height: 120,
    borderRadius: 20,
    backgroundColor: colors.white,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
    padding: 12,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 4,
  },
  logoImage: {
    width: '100%',
    height: '100%',
  },
  appSubtitle: {
    fontSize: 14,
    color: colors.primaryLight,
    marginTop: 4,
  },
  formCard: {
    backgroundColor: colors.white,
    marginHorizontal: 20,
    borderRadius: 20,
    paddingHorizontal: 24,
    paddingVertical: 32,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 12,
    elevation: 8,
  },
  welcomeText: {
    fontSize: 22,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  instructionText: {
    fontSize: 13,
    color: colors.textSecondary,
    marginTop: 4,
    marginBottom: 24,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    marginBottom: 20,
    paddingHorizontal: 12,
    backgroundColor: colors.inputBackground,
  },
  inputIcon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    height: 48,
    color: colors.textPrimary,
    fontSize: 14,
  },
  eyeIcon: {
    padding: 8,
  },
  loginButton: {
    backgroundColor: colors.action,
    height: 50,
    borderRadius: 10,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 8,
    shadowColor: colors.action,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 4,
  },
  loginButtonText: {
    color: colors.white,
    fontSize: 16,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
  registerContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
  },
  noAccountText: {
    fontSize: 14,
    color: colors.textSecondary,
  },
  registerText: {
    fontSize: 14,
    color: colors.primary,
    fontWeight: 'bold',
  },
  settingsIconBtn: {
    position: 'absolute',
    top: Platform.OS === 'android' ? (StatusBar.currentHeight || 0) + 12 : 16,
    right: 16,
    padding: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.15)',
    zIndex: 99,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: colors.white,
    borderRadius: 16,
    padding: 20,
    width: '85%',
    maxWidth: 400,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  modalLabel: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginBottom: 6,
  },
  modalTextInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: colors.textPrimary,
    backgroundColor: colors.inputBackground,
    marginBottom: 20,
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  resetBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: colors.surfaceMuted,
    alignItems: 'center',
    marginRight: 8,
  },
  resetBtnText: {
    fontWeight: 'bold',
    color: colors.textSecondary,
  },
  saveBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: colors.primary,
    alignItems: 'center',
    marginLeft: 8,
  },
  saveBtnText: {
    fontWeight: 'bold',
    color: colors.white,
  },
  closeBtn: {
    paddingVertical: 10,
    alignItems: 'center',
    marginTop: 8,
  },
  closeBtnText: {
    color: colors.textMuted,
    fontWeight: 'bold',
  },
});
