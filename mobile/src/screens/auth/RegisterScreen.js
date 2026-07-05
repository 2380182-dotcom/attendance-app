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

export default function RegisterScreen({ navigation }) {
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
          <View style={styles.formCard}>
            <Text style={styles.titleText}>Register Agent</Text>
            <Text style={styles.subtitleText}>Enter credentials to onboard onto the platform</Text>

            <Text style={styles.label}>Agent ID</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="badge" size={20} color="#757575" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Agent ID (e.g. AGENT001)"
                placeholderTextColor="#9E9E9E"
                value={agentId}
                onChangeText={setAgentId}
                autoCapitalize="characters"
                autoCorrect={false}
              />
            </View>

            <Text style={styles.label}>Full Name</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="person" size={20} color="#757575" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Full Name"
                placeholderTextColor="#9E9E9E"
                value={name}
                onChangeText={setName}
              />
            </View>

            <Text style={styles.label}>Email Address</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="email" size={20} color="#757575" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Email Address"
                placeholderTextColor="#9E9E9E"
                keyboardType="email-address"
                autoCapitalize="none"
                value={email}
                onChangeText={setEmail}
              />
            </View>

            <Text style={styles.label}>Phone Number</Text>
            <View style={styles.inputContainer}>
              <MaterialIcons name="phone" size={20} color="#757575" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Enter Phone Number"
                placeholderTextColor="#9E9E9E"
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
              <MaterialIcons name="lock" size={20} color="#757575" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="Create Password"
                placeholderTextColor="#9E9E9E"
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
                  color="#757575"
                />
              </TouchableOpacity>
            </View>

            {role === 'AGENT' && (
              <View style={styles.policyContainer}>
                <Text style={styles.policyTitle}>Face Verification Policy Settings</Text>
                <Text style={styles.policyDesc}>Determine when you are required to submit face biometric verification during duty hours:</Text>
                
                <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyOnCheckIn(!faceVerifyOnCheckIn)}>
                  <MaterialIcons name={faceVerifyOnCheckIn ? "check-box" : "checkbox-blank-outline"} size={22} color="#1976D2" />
                  <Text style={styles.checkboxLabel}>Verify on Start Duty (Check-In)</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyOnCheckOut(!faceVerifyOnCheckOut)}>
                  <MaterialIcons name={faceVerifyOnCheckOut ? "check-box" : "checkbox-blank-outline"} size={22} color="#1976D2" />
                  <Text style={styles.checkboxLabel}>Verify on Off Duty (Check-Out)</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyAnytime(!faceVerifyAnytime)}>
                  <MaterialIcons name={faceVerifyAnytime ? "check-box" : "checkbox-blank-outline"} size={22} color="#1976D2" />
                  <Text style={styles.checkboxLabel}>Verify Anytime in the Day (Duty Checks)</Text>
                </TouchableOpacity>

                <TouchableOpacity 
                  style={[styles.faceRegisterBtn, faceRegistered && styles.faceRegisterBtnSuccess]} 
                  onPress={() => setFaceModalVisible(true)}
                >
                  <MaterialIcons name={faceRegistered ? "face" : "add-a-photo"} size={20} color="#fff" style={{ marginRight: 6 }} />
                  <Text style={styles.faceRegisterBtnText}>
                    {faceRegistered ? "Face Registered" : "Register Face Verification"}
                  </Text>
                </TouchableOpacity>
              </View>
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

            <TouchableOpacity style={styles.registerButton} onPress={handleRegister}>
              <Text style={styles.registerButtonText}>REGISTER</Text>
              <MaterialIcons name="person-add" size={18} color="#fff" style={{ marginLeft: 6 }} />
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
    backgroundColor: '#f5f5f5',
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
    backgroundColor: '#fff',
    marginHorizontal: 20,
    borderRadius: 20,
    paddingHorizontal: 24,
    paddingVertical: 28,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 6,
    elevation: 3,
  },
  titleText: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#212121',
  },
  subtitleText: {
    fontSize: 13,
    color: '#757575',
    marginTop: 4,
    marginBottom: 20,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    color: '#424242',
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 10,
    marginBottom: 16,
    paddingHorizontal: 12,
    backgroundColor: '#FAFAFA',
  },
  pickerContainer: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 10,
    marginBottom: 16,
    backgroundColor: '#FAFAFA',
    justifyContent: 'center',
  },
  inputIcon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    height: 44,
    color: '#212121',
    fontSize: 14,
  },
  eyeIcon: {
    padding: 8,
  },
  registerButton: {
    backgroundColor: '#2196F3',
    height: 48,
    borderRadius: 10,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 10,
  },
  registerButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
  policyContainer: {
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#E0E0E0',
    padding: 14,
    marginVertical: 12,
  },
  policyTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  policyDesc: {
    fontSize: 11,
    color: '#757575',
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
    color: '#424242',
    marginLeft: 8,
  },
  faceRegisterBtn: {
    backgroundColor: '#E53935',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    height: 40,
    borderRadius: 8,
    marginTop: 12,
  },
  faceRegisterBtnSuccess: {
    backgroundColor: '#2E7D32',
  },
  faceRegisterBtnText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 13,
  },
});

const pickerStyles = {
  inputIOS: {
    fontSize: 14,
    paddingVertical: 12,
    paddingHorizontal: 12,
    color: '#212121',
    paddingRight: 30,
  },
  inputAndroid: {
    fontSize: 14,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: '#212121',
    paddingRight: 30,
  },
};
