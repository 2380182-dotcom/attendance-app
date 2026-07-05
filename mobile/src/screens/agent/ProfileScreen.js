import React, { useContext, useEffect, useState } from 'react';
import { StyleSheet, View, Text, TouchableOpacity, Alert, SafeAreaView } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { apiService } from '../../services/api';
import Loading from '../../components/Loading';
import AppCard from '../../components/AppCard';
import AppButton from '../../components/AppButton';
import { useTheme } from '../../theme';

export default function ProfileScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user, logout } = useContext(AuthContext);
  const [totalCheckins, setTotalCheckins] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchProfileData = async () => {
      if (!user?.id) return;
      try {
        const logs = await apiService.attendance.getHistory(user.id);
        setTotalCheckins(logs.length);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchProfileData();
  }, [user]);

  const handleLogout = () => {
    Alert.alert('Confirm Logout', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Log Out', style: 'destructive', onPress: logout }
    ]);
  };

  if (loading) {
    return <Loading message="Loading profile..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <AppCard style={styles.profileCard}>
        <View style={styles.avatarContainer}>
          <MaterialIcons name="account-circle" size={80} color={colors.secondary} />
        </View>
        <Text style={styles.name}>{user?.name}</Text>
        <Text style={styles.roleTag}>{user?.role || 'AGENT'}</Text>

        <View style={styles.divider} />

        <View style={styles.infoRow}>
          <MaterialIcons name="badge" size={20} color={colors.textSecondary} />
          <Text style={styles.infoText}>Agent ID: {user?.agentId}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="business" size={20} color={colors.textSecondary} />
          <Text style={styles.infoText}>Department: {user?.department || 'Sales'}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="email" size={20} color={colors.textSecondary} />
          <Text style={styles.infoText}>Email: {user?.email}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="phone" size={20} color={colors.textSecondary} />
          <Text style={styles.infoText}>Phone: {user?.phone || 'N/A'}</Text>
        </View>

        <View style={styles.infoRow}>
          <MaterialIcons name="event-available" size={20} color={colors.textSecondary} />
          <Text style={styles.infoText}>Total Check-ins: {totalCheckins}</Text>
        </View>

        <AppButton
          title="Log Out"
          onPress={handleLogout}
          variant="danger"
          icon="exit-to-app"
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
    justifyContent: 'center',
    padding: 16,
  },
  profileCard: {
    alignItems: 'center',
  },
  avatarContainer: {
    marginBottom: 12,
  },
  name: {
    fontSize: 22,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  roleTag: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.secondary,
    backgroundColor: colors.secondaryLight,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    marginTop: 6,
  },
  divider: {
    width: '100%',
    height: 1,
    backgroundColor: colors.divider,
    marginVertical: 20,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    marginBottom: 16,
  },
  infoText: {
    fontSize: 14,
    color: colors.textSecondary,
    marginLeft: 12,
  },
});
