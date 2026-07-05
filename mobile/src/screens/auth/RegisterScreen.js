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
  SafeAreaView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import RNPickerSelect from 'react-native-picker-select';
import { AuthContext } from '../../context/AuthContext';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

export default function RegisterScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const pickerStyles = createPickerStyles(colors);
  const { register } = useContext(AuthContext);
  const [agentId, setAgentId] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('AGENT');
  const [department, setDepartment] = useState('SALES');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [faceVerifyOnCheckIn, setFaceVerifyOnCheckIn] = useState(true);
  const [faceVerifyOnCheckOut, setFaceVerifyOnCheckOut] = useState(true);
  const [faceVerifyAnytime, setFaceVerifyAnytime] = useState(true);
  const [faceRegistered, setFaceRegistered] = useState(false);
  const [faceTemplate, setFaceTemplate] = useState('');
  const [faceModalVisible, setFaceModalVisible] = useState(false);

  const handleRegister = async () => {
    if (!agentId.trim()) {
      Alert.alert('Validation Error', 'Agent ID is required.');
      return;
    }
    if (!name.trim()) {
      Alert.alert('Validation Error', 'Name is required.');
      return;
    }
    if (!email.trim() || !email.includes('@')) {
      Alert.alert('Validation Error', 'Please enter a valid email address.');
      return;
    }
    if (!phone.trim()) {
      Alert.alert('Validation Error', 'Phone number is required.');
      return;
    }
    if (!password || password.length < 4) {
      Alert.alert('Validation Error', 'Password must be at least 4 characters long.');
      return;
    }
    if (!role) {
      Alert.alert('Validation Error', 'Please select a role.');
      return;
    }
    if (!password) {
      Alert.alert('Validation Error', 'Password is required.');
      return;
    }
    if (role === 'AGENT' && !faceRegistered) {
      Alert.alert('Face Verification Required', 'Please register your face verification before submitting.');
      return;
    }

    setLoading(true);
    const result = await register(
      agentId.trim().toUpperCase(),
      name.trim(),
      email.trim(),
      phone.trim(),
      password,
      role,
      department,
      faceVerifyOnCheckIn,
      faceVerifyOnCheckOut,
      faceVerifyAnytime,
      faceRegistered,
      faceTemplate
    );
    if (result.success) {
      Alert.alert('Success', 'Registration successful! You can now log in.');
      navigation.navigate('Login');
    } else {
      Alert.alert('Registration Failed', result.error);
      setLoading(false);
    }
  };

  if (loading) {
    return <Loading message="Creating agent account..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <AppCard style={styles.formCard}>
            <Text style={styles.titleText}>Register Agent</Text>
            <Text style={styles.subtitleText}>Enter credentials to onboard onto the platform</Text>

            <Text style={styles.label}>Agent ID</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="badge" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Agent ID (e.g. AGENT001)"
                placeholderTextColor={colors.textMuted}
                value={agentId}
                onChangeText={setAgentId}
                autoCapitalize="characters"
                autoCorrect={false}
              />
            </View>

            <Text style={styles.label}>Full Name</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="person" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Full Name"
                placeholderTextColor={colors.textMuted}
                value={name}
                onChangeText={setName}
              />
            </View>

            <Text style={styles.label}>Email Address</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="email" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Email Address"
                placeholderTextColor={colors.textMuted}
                keyboardType="email-address"
                autoCapitalize="none"
                value={email}
                onChangeText={setEmail}
              />
            </View>

            <Text style={styles.label}>Phone Number</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="phone" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Phone Number"
                placeholderTextColor={colors.textMuted}
                keyboardType="phone-pad"
                value={phone}
                onChangeText={setPhone}
              />
            </View>

            <Text style={styles.label}>Select Role</Text>
            <View style={styles.pickerContainer}>
              <RNPickerSelect
                onValueChange={(value) => setRole(value)}
                value={role}
                placeholder={{}}
                items={[
                  { label: 'Agent', value: 'AGENT' },
                  { label: 'Sales Feed Viewer', value: 'SALES' },
                  { label: 'HR Manager', value: 'HR' },
                  { label: 'Administrator', value: 'ADMIN' },
                ]}
                style={pickerStyles}
              />
            </View>

            <Text style={styles.label}>Select Department</Text>
            <View style={styles.pickerContainer}>
              <RNPickerSelect
                onValueChange={(value) => setDepartment(value)}
                value={department}
                placeholder={{}}
                items={[
                  { label: 'Sales', value: 'SALES' },
                  { label: 'Human Resources', value: 'HR' },
                  { label: 'Operations', value: 'OPERATIONS' },
                  { label: 'Administration', value: 'ADMIN' },
                ]}
                style={pickerStyles}
              />
            </View>

            <Text style={styles.label}>Password</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="lock" size={20} color={colors.textSecondary} style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Create Password"
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

            {role === 'AGENT' && (
              <AppCard style={styles.policyContainer} padding={14}>
                <Text style={styles.policyTitle}>Face Verification Policy Settings</Text>
                <Text style={styles.policyDesc}>Determine when you are required to submit face biometric verification during duty hours:</Text>

                <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyOnCheckIn(!faceVerifyOnCheckIn)}>
                  <MaterialIcons name={faceVerifyOnCheckIn ? "check-box" : "checkbox-blank-outline"} size={22} color={colors.secondary} />
                  <Text style={styles.checkboxLabel}>Verify on Start Duty (Check-In)</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyOnCheckOut(!faceVerifyOnCheckOut)}>
                  <MaterialIcons name={faceVerifyOnCheckOut ? "check-box" : "checkbox-blank-outline"} size={22} color={colors.secondary} />
                  <Text style={styles.checkboxLabel}>Verify on Off Duty (Check-Out)</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyAnytime(!faceVerifyAnytime)}>
                  <MaterialIcons name={faceVerifyAnytime ? "check-box" : "checkbox-blank-outline"} size={22} color={colors.secondary} />
                  <Text style={styles.checkboxLabel}>Verify Anytime in the Day (Duty Checks)</Text>
                </TouchableOpacity>

                <AppButton
                  title={faceRegistered ? 'Face Registered' : 'Register Face Verification'}
                  onPress={() => setFaceModalVisible(true)}
                  variant={faceRegistered ? 'success' : 'danger'}
                  icon={faceRegistered ? 'face' : 'add-a-photo'}
                  size="sm"
                  style={{ marginTop: 12 }}
                />
              </AppCard>
            )}

            <FaceVerificationModal
              visible={faceModalVisible}
              onClose={() => setFaceModalVisible(false)}
              onSuccess={(embeddingBase64) => {
                setFaceRegistered(true);
                setFaceTemplate(embeddingBase64);
                setFaceModalVisible(false);
                Alert.alert('Registration Successful', 'Biometric face points successfully recorded.');
              }}
              agentName={name || 'New Agent'}
            />

            <AppButton
              title="REGISTER"
              onPress={handleRegister}
              variant="secondary"
              icon="person-add"
              iconPosition="right"
              style={{ marginTop: 10 }}
            />
          </AppCard>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  keyboardView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingVertical: 16,
  },
  formCard: {
    marginHorizontal: 20,
  },
  titleText: {
    fontSize: 22,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  subtitleText: {
    fontSize: 13,
    color: colors.textSecondary,
    marginTop: 4,
    marginBottom: 20,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textSecondary,
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    marginBottom: 16,
    paddingHorizontal: 12,
    backgroundColor: colors.inputBackground,
  },
  pickerContainer: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    marginBottom: 16,
    backgroundColor: colors.inputBackground,
    justifyContent: 'center',
  },
  inputIcon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    height: 44,
    color: colors.textPrimary,
    fontSize: 14,
  },
  eyeIcon: {
    padding: 8,
  },
  policyContainer: {
    marginVertical: 12,
  },
  policyTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: colors.textPrimary,
    marginBottom: 4,
  },
  policyDesc: {
    fontSize: 11,
    color: colors.textSecondary,
    lineHeight: 15,
    marginBottom: 10,
  },
  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
  },
  checkboxLabel: {
    fontSize: 12,
    color: colors.textPrimary,
    marginLeft: 8,
  },
});

const createPickerStyles = (colors) => ({
  inputIOS: {
    fontSize: 14,
    paddingVertical: 12,
    paddingHorizontal: 12,
    color: colors.textPrimary,
    paddingRight: 30,
  },
  inputAndroid: {
    fontSize: 14,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: colors.textPrimary,
    paddingRight: 30,
  },
});
