import React, { useContext } from 'react';
import { TouchableOpacity } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { createStackNavigator } from '@react-navigation/stack';
import { AuthContext } from '../context/AuthContext';
import Loading from '../components/Loading';
import { useTheme } from '../theme';

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

// LMT (Salesman) Screens
import LmtHomeScreen from '../screens/lmt/LmtHomeScreen';
import ShopLookupScreen from '../screens/lmt/ShopLookupScreen';
import RecordVisitScreen from '../screens/lmt/RecordVisitScreen';
import MorningStockEntryScreen from '../screens/lmt/MorningStockEntryScreen';

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
  const { colors } = useTheme();

  if (isLoading) {
    return <Loading fullScreen />;
  }

  const role = user?.role;

  return (
    <Stack.Navigator
      screenOptions={({ navigation }) => ({
        headerStyle: {
          backgroundColor: colors.primary,
          elevation: 2,
          shadowOpacity: 0.2,
        },
        headerTintColor: colors.textOnPrimary,
        headerTitleStyle: {
          fontWeight: 'bold',
        },
        headerTitleAlign: 'center',
        cardStyle: { backgroundColor: colors.background },
        headerRight: () => (
          <TouchableOpacity
            style={{ marginRight: 16, padding: 8 }}
            onPress={() => navigation.navigate('ServerSettings')}
          >
            <MaterialIcons name="settings" size={22} color={colors.textOnPrimary} />
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
                options={{ headerShown: false }}
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
                options={{ headerShown: false }}
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
                options={{ headerShown: false }}
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

          {role === 'SALESMAN_LMT' && (
            <>
              {/*
                Sub-stage 3: LmtHome is now the landing screen (first screen
                registered = React Navigation's default initial route),
                replacing ShopLookup from sub-stage 2 — its own "Look Up a
                Shop" button (shown once checked in) leads there instead.
                AgentDashboardScreen is still deliberately excluded (its
                hardcoded "Dawn Bread Sales" button targets 'SalesEntry',
                which this role's stack doesn't register).
              */}
              <Stack.Screen
                name="LmtHome"
                component={LmtHomeScreen}
                options={{ title: 'Dawn Bread — LMT' }}
              />
              <Stack.Screen
                name="ShopLookup"
                component={ShopLookupScreen}
                options={{ title: 'Shop Lookup' }}
              />
              <Stack.Screen
                name="RecordVisit"
                component={RecordVisitScreen}
                options={{ title: 'Record Sale' }}
              />
              <Stack.Screen
                name="MorningStockEntry"
                component={MorningStockEntryScreen}
                options={{ title: 'Today\'s Stock' }}
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
              {/*
                No FaceEnrollment screen here, deliberately — LMTs never
                self-register a face. Only an admin can register one, via
                the existing agent face-registration flow reused in
                AdminUsersScreen (LMT Phase B). CheckinScreen's unregistered
                -face prompt for this role points them to the admin instead
                of offering a self-enroll action.
              */}
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
            </>
          )}

          {role !== 'SALES' && role !== 'HR' && role !== 'ADMIN' && role !== 'SALESMAN_LMT' && (
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
