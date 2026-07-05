import React, { useContext } from 'react';
import { TouchableOpacity } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { createStackNavigator } from '@react-navigation/stack';
import { AuthContext } from '../context/AuthContext';
import Loading from '../components/Loading';

// Auth Screens
import LoginScreen from '../screens/auth/LoginScreen';
import ServerSettingsScreen from '../screens/auth/ServerSettingsScreen';

// Agent Screens
import AgentDashboardScreen from '../screens/agent/DashboardScreen';
import AgentHistoryScreen from '../screens/agent/HistoryScreen';
import AgentProfileScreen from '../screens/agent/ProfileScreen';
import CheckinScreen from '../screens/agent/CheckinScreen';
import CheckoutScreen from '../screens/agent/CheckoutScreen';
import FaceEnrollmentScreen from '../screens/agent/FaceEnrollmentScreen';
import SalesEntryScreen from '../screens/agent/SalesEntryScreen';

// Sales Screens
import SalesDashboardScreen from '../screens/sales/SalesDashboardScreen';
import SalesReportScreen from '../screens/sales/SalesReportScreen';
import SalesAgentReportScreen from '../screens/sales/SalesAgentReportScreen';

// HR Screens
import HRDashboardScreen from '../screens/hr/HRDashboardScreen';
import HRReportScreen from '../screens/hr/HRReportScreen';
import HRAgentAttendanceReportScreen from '../screens/hr/HRAgentAttendanceReportScreen';

// Admin Screens
import AdminDashboardScreen from '../screens/admin/AdminDashboardScreen';
import AdminMartScreen from '../screens/admin/AdminMartScreen';
import AdminGeoFenceScreen from '../screens/admin/AdminGeoFenceScreen';
import AdminUsersScreen from '../screens/admin/AdminUsersScreen';
import ReportGeneratorScreen from '../screens/reports/ReportGeneratorScreen';

const Stack = createStackNavigator();

export default function AppNavigator() {
  const { isLoading, userToken, user } = useContext(AuthContext);

  if (isLoading) {
    return <Loading fullScreen />;
  }

  const role = user?.role;

  return (
    <Stack.Navigator
      screenOptions={({ navigation }) => ({
        headerStyle: {
          backgroundColor: '#1976D2',
          elevation: 2,
          shadowOpacity: 0.2,
        },
        headerTintColor: '#fff',
        headerTitleStyle: {
          fontWeight: 'bold',
        },
        headerTitleAlign: 'center',
        cardStyle: { backgroundColor: '#f5f5f5' },
        headerRight: () => (
          <TouchableOpacity
            style={{ marginRight: 16, padding: 8 }}
            onPress={() => navigation.navigate('ServerSettings')}
          >
            <MaterialIcons name="settings" size={22} color="#fff" />
          </TouchableOpacity>
        ),
      })}
    >
      {userToken == null ? (
        <>
          <Stack.Screen 
            name="Login" 
            component={LoginScreen} 
            options={{ headerShown: false }} 
          />
        </>
      ) : (
        <>
          {role === 'SALES' && (
            <>
              <Stack.Screen 
                name="SalesDashboard" 
                component={SalesDashboardScreen} 
                options={{ title: 'Sales Dashboard' }} 
              />
              <Stack.Screen
                name="SalesReport"
                component={SalesReportScreen}
                options={{ title: 'Sales Live Feed' }}
              />
              <Stack.Screen
                name="SalesAgentReport"
                component={SalesAgentReportScreen}
                options={{ title: 'Agent Sales CSV' }}
              />
            </>
          )}

          {role === 'HR' && (
            <>
              <Stack.Screen
                name="HRDashboard"
                component={HRDashboardScreen}
                options={{ title: 'HR Roster Dashboard' }}
              />
              <Stack.Screen
                name="HRReport"
                component={HRReportScreen}
                options={{ title: 'HR Roster Reports' }}
              />
              <Stack.Screen
                name="HRAgentAttendanceReport"
                component={HRAgentAttendanceReportScreen}
                options={{ title: 'Agent Attendance CSV' }}
              />
            </>
          )}

          {role === 'ADMIN' && (
            <>
              <Stack.Screen 
                name="AdminDashboard" 
                component={AdminDashboardScreen} 
                options={{ title: 'Admin Console' }} 
              />
              <Stack.Screen 
                name="AdminMart" 
                component={AdminMartScreen} 
                options={{ title: 'Mart Management' }} 
              />
              <Stack.Screen 
                name="AdminGeoFence" 
                component={AdminGeoFenceScreen} 
                options={{ title: 'Geo-Fence Tuning' }} 
              />
              <Stack.Screen 
                name="AdminUsers" 
                component={AdminUsersScreen} 
                options={{ title: 'User Management' }} 
              />
            </>
          )}

          {(role === 'ADMIN' || role === 'HR') && (
            <Stack.Screen 
              name="ReportGenerator" 
              component={ReportGeneratorScreen} 
              options={{ title: 'System Reports' }} 
            />
          )}

          {role !== 'SALES' && role !== 'HR' && role !== 'ADMIN' && (
            <>
              <Stack.Screen 
                name="Dashboard" 
                component={AgentDashboardScreen} 
                options={{ title: 'Agent Dashboard' }} 
              />
              <Stack.Screen 
                name="FaceEnrollment" 
                component={FaceEnrollmentScreen} 
                options={{ title: 'Face Enrollment', headerLeft: null }} 
              />
              <Stack.Screen 
                name="Checkin" 
                component={CheckinScreen} 
                options={{ title: 'Mart Check-In' }} 
              />
              <Stack.Screen 
                name="Checkout" 
                component={CheckoutScreen} 
                options={{ title: 'Mart Check-Out' }} 
              />
              <Stack.Screen 
                name="History" 
                component={AgentHistoryScreen} 
                options={{ title: 'My Attendance History' }} 
              />
              <Stack.Screen 
                name="Profile" 
                component={AgentProfileScreen} 
                options={{ title: 'Agent Profile' }} 
              />
              <Stack.Screen 
                name="SalesEntry" 
                component={SalesEntryScreen} 
                options={{ title: 'Enter Sales - Dawn Bread' }} 
              />
            </>
          )}
        </>
      )}
      <Stack.Screen 
        name="ServerSettings" 
        component={ServerSettingsScreen} 
        options={{ 
          title: 'Server Settings',
          headerRight: null
        }} 
      />
    </Stack.Navigator>
  );
}
