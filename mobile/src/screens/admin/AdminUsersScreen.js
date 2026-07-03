import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Modal,
  Alert,
  SafeAreaView,
  TextInput,
  ScrollView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import RNPickerSelect from 'react-native-picker-select';
import api from '../../services/api';
import Loading from '../../components/Loading';
import FaceVerificationModal from '../../components/FaceVerificationModal';

export default function AdminUsersScreen() {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Edit Modal State
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState(null);
  const [role, setRole] = useState('AGENT');
  const [department, setDepartment] = useState('SALES');

  // Create Modal State
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [newName, setNewName] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newAgentId, setNewAgentId] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newRole, setNewRole] = useState('AGENT');
  const [newDepartment, setNewDepartment] = useState('SALES');

  // New Face Verify States (Edit)
  const [faceVerifyOnCheckIn, setFaceVerifyOnCheckIn] = useState(true);
  const [faceVerifyOnCheckOut, setFaceVerifyOnCheckOut] = useState(true);
  const [faceVerifyAnytime, setFaceVerifyAnytime] = useState(true);
  const [faceRegistered, setFaceRegistered] = useState(false);
  const [faceTemplate, setFaceTemplate] = useState('');
  const [faceModalVisible, setFaceModalVisible] = useState(false);

  // New Face Verify States (Create)
  const [newFaceVerifyOnCheckIn, setNewFaceVerifyOnCheckIn] = useState(true);
  const [newFaceVerifyOnCheckOut, setNewFaceVerifyOnCheckOut] = useState(true);
  const [newFaceVerifyAnytime, setNewFaceVerifyAnytime] = useState(true);
  const [newFaceRegistered, setNewFaceRegistered] = useState(false);
  const [newFaceTemplate, setNewFaceTemplate] = useState('');
  const [newFaceModalVisible, setNewFaceModalVisible] = useState(false);

  const resetCreateForm = () => {
    setNewName('');
    setNewEmail('');
    setNewPhone('');
    setNewAgentId('');
    setNewPassword('');
    setNewRole('AGENT');
    setNewDepartment('SALES');
    setNewFaceVerifyOnCheckIn(true);
    setNewFaceVerifyOnCheckOut(true);
    setNewFaceVerifyAnytime(true);
    setNewFaceRegistered(false);
    setNewFaceTemplate('');
  };

  const fetchAgents = useCallback(async () => {
    try {
      const response = await api.get('/agents');
      if (response.data && response.data.success) {
        setAgents(response.data.data);
      }
    } catch (e) {
      Alert.alert('Error', 'Unable to retrieve agents list.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAgents();
  }, [fetchAgents]);

  const handleOpenEdit = (agent) => {
    setSelectedAgent(agent);
    setRole(agent.role || 'AGENT');
    setDepartment(agent.department || 'SALES');
    setFaceVerifyOnCheckIn(agent.faceVerifyOnCheckIn ?? true);
    setFaceVerifyOnCheckOut(agent.faceVerifyOnCheckOut ?? true);
    setFaceVerifyAnytime(agent.faceVerifyAnytime ?? true);
    setFaceRegistered(agent.faceRegistered ?? false);
    setFaceTemplate(agent.faceTemplate || '');
    setModalVisible(true);
  };

  const handleSave = async () => {
    if (!selectedAgent) return;
    setLoading(true);
    try {
      const response = await api.put(`/agents/${selectedAgent.id}`, {
        role,
        department,
        faceVerifyOnCheckIn,
        faceVerifyOnCheckOut,
        faceVerifyAnytime,
        faceRegistered,
        faceTemplate
      });
      if (response.data && response.data.success) {
        Alert.alert('Success', 'Agent role and department updated.');
        setModalVisible(false);
        fetchAgents();
      }
    } catch (e) {
      Alert.alert('Error', 'Failed to update agent.');
      setLoading(false);
    }
  };

  const handleDeleteUser = (agent) => {
    Alert.alert(
      'Confirm Delete',
      `Are you sure you want to delete user ${agent.name} (${agent.agentId})?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            setLoading(true);
            try {
              const response = await api.delete(`/agents/${agent.id}`);
              if (response.data && response.data.success) {
                Alert.alert('Success', 'User deleted successfully.');
                fetchAgents();
              } else {
                Alert.alert('Error', response.data.message || 'Failed to delete user.');
                setLoading(false);
              }
            } catch (e) {
              console.error(e);
              const errorMsg = e.response?.data?.message || 'Failed to delete user.';
              Alert.alert('Error', errorMsg);
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const handleCreateUser = async () => {
    if (!newName.trim() || !newEmail.trim() || !newAgentId.trim() || !newPassword) {
      Alert.alert('Validation Error', 'Name, Email, Agent ID, and Password are required.');
      return;
    }
    if (newRole === 'AGENT' && !newFaceRegistered) {
      Alert.alert('Face Verification Required', 'Please register the agent\'s face verification before onboarding.');
      return;
    }
    setLoading(true);
    try {
      const response = await api.post('/agents', {
        name: newName.trim(),
        email: newEmail.trim(),
        phone: newPhone.trim(),
        agentId: newAgentId.trim().toUpperCase(),
        password: newPassword,
        role: newRole,
        department: newDepartment,
        faceVerifyOnCheckIn: newFaceVerifyOnCheckIn,
        faceVerifyOnCheckOut: newFaceVerifyOnCheckOut,
        faceVerifyAnytime: newFaceVerifyAnytime,
        faceRegistered: newFaceRegistered,
        faceTemplate: newFaceTemplate
      });
      if (response.data && response.data.success) {
        Alert.alert('Success', 'User account created successfully.');
        setCreateModalVisible(false);
        resetCreateForm();
        fetchAgents();
      } else {
        Alert.alert('Error', response.data.message || 'Failed to create user.');
        setLoading(false);
      }
    } catch (e) {
      const errorMsg = e.response?.data?.message || 'Failed to create user account.';
      Alert.alert('Error', errorMsg);
      setLoading(false);
    }
  };

  if (loading && agents.length === 0) {
    return <Loading message="Loading system users..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={agents}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => (
          <View style={styles.userCard}>
            <View style={styles.userInfo}>
              <Text style={styles.userName}>{item.name}</Text>
              <Text style={styles.userDetail}>ID: {item.agentId} | Phone: {item.phone || 'N/A'}</Text>
              <Text style={styles.userDetail}>Email: {item.email}</Text>
              
              <View style={styles.tagRow}>
                <Text style={styles.roleTag}>{item.role || 'AGENT'}</Text>
                <Text style={styles.deptTag}>{item.department || 'SALES'}</Text>
              </View>
            </View>

            <View style={{ flexDirection: 'column', justifyContent: 'space-between', height: 75, alignItems: 'center' }}>
              <TouchableOpacity style={styles.editBtn} onPress={() => handleOpenEdit(item)}>
                <MaterialIcons name="edit" size={20} color="#1976D2" />
              </TouchableOpacity>
              <TouchableOpacity style={[styles.editBtn, { backgroundColor: '#FFEBEE', marginTop: 8 }]} onPress={() => handleDeleteUser(item)}>
                <MaterialIcons name="delete" size={20} color="#D32F2F" />
              </TouchableOpacity>
            </View>
          </View>
        )}
        contentContainerStyle={styles.list}
      />

      {/* Floating Action Button for Adding Users */}
      <TouchableOpacity
        style={styles.fab}
        onPress={() => {
          resetCreateForm();
          setCreateModalVisible(true);
        }}
      >
        <MaterialIcons name="add" size={24} color="#fff" />
      </TouchableOpacity>

      {/* Create User Modal */}
      <Modal visible={createModalVisible} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 20 }}>
              <Text style={styles.modalTitle}>Onboard New User</Text>

              <Text style={styles.label}>Name *</Text>
              <TextInput
                style={styles.textInput}
                placeholder="Full Name"
                value={newName}
                onChangeText={setNewName}
              />

              <Text style={styles.label}>Email *</Text>
              <TextInput
                style={styles.textInput}
                placeholder="Email Address"
                keyboardType="email-address"
                autoCapitalize="none"
                value={newEmail}
                onChangeText={setNewEmail}
              />

              <Text style={styles.label}>Phone</Text>
              <TextInput
                style={styles.textInput}
                placeholder="Phone Number"
                keyboardType="phone-pad"
                value={newPhone}
                onChangeText={setNewPhone}
              />

              <Text style={styles.label}>Agent ID *</Text>
              <TextInput
                style={styles.textInput}
                placeholder="Agent ID (e.g. AGENT001)"
                autoCapitalize="characters"
                value={newAgentId}
                onChangeText={setNewAgentId}
              />

              <Text style={styles.label}>Password *</Text>
              <TextInput
                style={styles.textInput}
                placeholder="Password"
                secureTextEntry
                value={newPassword}
                onChangeText={setNewPassword}
              />

              <Text style={styles.label}>System Role</Text>
              <View style={styles.pickerWrapper}>
                <RNPickerSelect
                  onValueChange={(val) => setNewRole(val)}
                  value={newRole}
                  placeholder={{}}
                  items={[
                    { label: 'Agent', value: 'AGENT' },
                    { label: 'Sales Feed Viewer', value: 'SALES' },
                    { label: 'HR Manager', value: 'HR' },
                    { label: 'Administrator', value: 'ADMIN' }
                  ]}
                  style={pickerStyles}
                />
              </View>

              <Text style={styles.label}>Department</Text>
              <View style={styles.pickerWrapper}>
                <RNPickerSelect
                  onValueChange={(val) => setNewDepartment(val)}
                  value={newDepartment}
                  placeholder={{}}
                  items={[
                    { label: 'Sales', value: 'SALES' },
                    { label: 'Human Resources', value: 'HR' },
                    { label: 'Operations', value: 'OPERATIONS' },
                    { label: 'Administration', value: 'ADMIN' }
                  ]}
                  style={pickerStyles}
                />
              </View>

              {newRole === 'AGENT' && (
                <View style={styles.policyContainer}>
                  <Text style={styles.policyTitle}>Face Verification Policy Settings</Text>
                  
                  <TouchableOpacity style={styles.checkboxRow} onPress={() => setNewFaceVerifyOnCheckIn(!newFaceVerifyOnCheckIn)}>
                    <MaterialIcons name={newFaceVerifyOnCheckIn ? "check-box" : "checkbox-blank-outline"} size={20} color="#1976D2" />
                    <Text style={styles.checkboxLabel}>Verify on Check-In</Text>
                  </TouchableOpacity>

                  <TouchableOpacity style={styles.checkboxRow} onPress={() => setNewFaceVerifyOnCheckOut(!newFaceVerifyOnCheckOut)}>
                    <MaterialIcons name={newFaceVerifyOnCheckOut ? "check-box" : "checkbox-blank-outline"} size={20} color="#1976D2" />
                    <Text style={styles.checkboxLabel}>Verify on Check-Out</Text>
                  </TouchableOpacity>

                  <TouchableOpacity style={styles.checkboxRow} onPress={() => setNewFaceVerifyAnytime(!newFaceVerifyAnytime)}>
                    <MaterialIcons name={newFaceVerifyAnytime ? "check-box" : "checkbox-blank-outline"} size={20} color="#1976D2" />
                    <Text style={styles.checkboxLabel}>Verify Anytime (Duty Checks)</Text>
                  </TouchableOpacity>

                  <TouchableOpacity 
                    style={[styles.faceRegisterBtn, newFaceRegistered && styles.faceRegisterBtnSuccess]} 
                    onPress={() => setNewFaceModalVisible(true)}
                  >
                    <MaterialIcons name={newFaceRegistered ? "face" : "add-a-photo"} size={18} color="#fff" style={{ marginRight: 6 }} />
                    <Text style={styles.faceRegisterBtnText}>
                      {newFaceRegistered ? "Face Registered ✓" : "Register Agent Face"}
                    </Text>
                  </TouchableOpacity>
                </View>
              )}

              <FaceVerificationModal
                visible={newFaceModalVisible}
                onClose={() => setNewFaceModalVisible(false)}
                onSuccess={(embeddingBase64) => {
                  setNewFaceRegistered(true);
                  setNewFaceTemplate(embeddingBase64);
                  setNewFaceModalVisible(false);
                  Alert.alert('Registration Successful', 'Agent biometric registered.');
                }}
                agentName={newName || 'New Agent'}
              />

              <View style={styles.modalButtons}>
                <TouchableOpacity style={styles.cancelBtn} onPress={() => setCreateModalVisible(false)}>
                  <Text style={styles.cancelBtnText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.saveBtn} onPress={handleCreateUser}>
                  <Text style={styles.saveBtnText}>Create</Text>
                </TouchableOpacity>
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>

      {/* Edit Role/Department Modal */}
      <Modal visible={modalVisible} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <ScrollView showsVerticalScrollIndicator={false}>
              <Text style={styles.modalTitle}>Manage Authorization</Text>
              {selectedAgent && (
                <Text style={styles.agentNameLabel}>User: {selectedAgent.name}</Text>
              )}

              <Text style={styles.label}>System Role</Text>
              <View style={styles.pickerWrapper}>
                <RNPickerSelect
                  onValueChange={(val) => setRole(val)}
                  value={role}
                  placeholder={{}}
                  items={[
                    { label: 'Agent', value: 'AGENT' },
                    { label: 'Sales Feed Viewer', value: 'SALES' },
                    { label: 'HR Manager', value: 'HR' },
                    { label: 'Administrator', value: 'ADMIN' }
                  ]}
                  style={pickerStyles}
                />
              </View>

              <Text style={styles.label}>Department</Text>
              <View style={styles.pickerWrapper}>
                <RNPickerSelect
                  onValueChange={(val) => setDepartment(val)}
                  value={department}
                  placeholder={{}}
                  items={[
                    { label: 'Sales', value: 'SALES' },
                    { label: 'Human Resources', value: 'HR' },
                    { label: 'Operations', value: 'OPERATIONS' },
                    { label: 'Administration', value: 'ADMIN' }
                  ]}
                  style={pickerStyles}
                />
              </View>

              {role === 'AGENT' && (
                <View style={styles.policyContainer}>
                  <Text style={styles.policyTitle}>Face Verification Policy Settings</Text>
                  
                  <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyOnCheckIn(!faceVerifyOnCheckIn)}>
                    <MaterialIcons name={faceVerifyOnCheckIn ? "check-box" : "checkbox-blank-outline"} size={20} color="#1976D2" />
                    <Text style={styles.checkboxLabel}>Verify on Check-In</Text>
                  </TouchableOpacity>

                  <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyOnCheckOut(!faceVerifyOnCheckOut)}>
                    <MaterialIcons name={faceVerifyOnCheckOut ? "check-box" : "checkbox-blank-outline"} size={20} color="#1976D2" />
                    <Text style={styles.checkboxLabel}>Verify on Check-Out</Text>
                  </TouchableOpacity>

                  <TouchableOpacity style={styles.checkboxRow} onPress={() => setFaceVerifyAnytime(!faceVerifyAnytime)}>
                    <MaterialIcons name={faceVerifyAnytime ? "check-box" : "checkbox-blank-outline"} size={20} color="#1976D2" />
                    <Text style={styles.checkboxLabel}>Verify Anytime (Duty Checks)</Text>
                  </TouchableOpacity>

                  <TouchableOpacity 
                    style={[styles.faceRegisterBtn, faceRegistered && styles.faceRegisterBtnSuccess]} 
                    onPress={() => setFaceModalVisible(true)}
                  >
                    <MaterialIcons name={faceRegistered ? "face" : "add-a-photo"} size={18} color="#fff" style={{ marginRight: 6 }} />
                    <Text style={styles.faceRegisterBtnText}>
                      {faceRegistered ? "Face Registered ✓" : "Register Face Verification"}
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
                  Alert.alert('Registration Successful', 'Agent biometric registered.');
                }}
                agentName={selectedAgent ? selectedAgent.name : 'Agent'}
              />

              <View style={styles.modalButtons}>
                <TouchableOpacity style={styles.cancelBtn} onPress={() => setModalVisible(false)}>
                  <Text style={styles.cancelBtnText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.saveBtn} onPress={handleSave}>
                  <Text style={styles.saveBtnText}>Save</Text>
                </TouchableOpacity>
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  list: {
    padding: 16,
    paddingBottom: 88, // Extra padding to not hide behind FAB
  },
  userCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: '#EEEEEE',
    shadowColor: '#000',
    shadowOpacity: 0.03,
    shadowRadius: 2,
    elevation: 1,
  },
  userInfo: {
    flex: 1,
  },
  userName: {
    fontWeight: 'bold',
    fontSize: 15,
    color: '#333',
  },
  userDetail: {
    fontSize: 12,
    color: '#757575',
    marginTop: 4,
  },
  tagRow: {
    flexDirection: 'row',
    marginTop: 8,
  },
  roleTag: {
    fontSize: 10,
    fontWeight: 'bold',
    color: '#1976D2',
    backgroundColor: '#E3F2FD',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
    marginRight: 8,
  },
  deptTag: {
    fontSize: 10,
    fontWeight: 'bold',
    color: '#37474F',
    backgroundColor: '#ECEFF1',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  editBtn: {
    padding: 8,
    borderRadius: 8,
    backgroundColor: '#E3F2FD',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    width: '85%',
    maxWidth: 400,
    maxHeight: '85%',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#333',
    textAlign: 'center',
  },
  agentNameLabel: {
    fontSize: 14,
    color: '#555',
    marginBottom: 16,
    textAlign: 'center',
  },
  label: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#666',
    marginBottom: 4,
    marginTop: 8,
  },
  textInput: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: '#333',
    backgroundColor: '#FAFAFA',
    marginBottom: 8,
  },
  pickerWrapper: {
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    backgroundColor: '#FAFAFA',
    marginBottom: 12,
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 20,
  },
  cancelBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#ECEFF1',
    alignItems: 'center',
    marginRight: 10,
  },
  cancelBtnText: {
    fontWeight: 'bold',
    color: '#607D8B',
  },
  saveBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#1976D2',
    alignItems: 'center',
    marginLeft: 10,
  },
  saveBtnText: {
    fontWeight: 'bold',
    color: '#fff',
  },
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 24,
    backgroundColor: '#1976D2',
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 6,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
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
    fontSize: 13,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 6,
  },
  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 5,
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
    height: 38,
    borderRadius: 8,
    marginTop: 10,
  },
  faceRegisterBtnSuccess: {
    backgroundColor: '#2E7D32',
  },
  faceRegisterBtnText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 12,
  },
  agentNameLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1565C0',
    marginBottom: 10,
    textAlign: 'center',
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
