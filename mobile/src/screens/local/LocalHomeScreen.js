import React, { useContext, useEffect, useState } from 'react';
import { Modal, Pressable, SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { AuthContext } from '../../context/AuthContext';
import { useTheme } from '../../theme';
import { STRINGS } from './strings';

/**
 * SALESMAN_LOCAL landing screen. The whole flow is two actions — Enter
 * Sales and Enter Return — so there is deliberately no check-in, duty, stock
 * or face step anywhere in this role's stack.
 *
 * The two actions are big tiles on the screen itself AND in the hamburger
 * menu: the menu was asked for, but hiding the only two things this user
 * ever does behind an icon would be the wrong trade for a low-literacy
 * audience, so nothing here depends on finding it.
 */
export default function LocalHomeScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user, logout } = useContext(AuthContext);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    navigation.setOptions({
      title: 'Dawn Bread',
      headerLeft: () => (
        <TouchableOpacity
          onPress={() => setMenuOpen(true)}
          style={{ marginLeft: 12, padding: 8 }}
          accessibilityRole="button"
          accessibilityLabel="Menu"
        >
          <MaterialIcons name="menu" size={30} color={colors.textOnPrimary} />
        </TouchableOpacity>
      ),
    });
  }, [navigation, colors.textOnPrimary]);

  const go = (mode) => {
    setMenuOpen(false);
    navigation.navigate('LocalNearbyShops', { mode });
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.greeting}>
        <Text style={styles.greetingText}>
          {STRINGS.welcome.en}{user?.name ? `, ${user.name}` : ''}
        </Text>
        <Text style={styles.greetingUrdu}>{STRINGS.welcome.ur}</Text>
      </View>

      <View style={styles.tiles}>
        <BigTile
          styles={styles}
          color={colors.successDark}
          icon="shopping-cart"
          label={STRINGS.enterSales}
          onPress={() => go('SALE')}
        />
        <BigTile
          styles={styles}
          color={colors.warningDark}
          icon="assignment-return"
          label={STRINGS.enterReturn}
          onPress={() => go('RETURN')}
        />
      </View>

      <Modal visible={menuOpen} transparent animationType="fade" onRequestClose={() => setMenuOpen(false)}>
        <Pressable style={styles.overlay} onPress={() => setMenuOpen(false)}>
          <Pressable style={styles.panel} onPress={() => {}}>
            <Text style={styles.panelTitle}>{STRINGS.menu.en} · {STRINGS.menu.ur}</Text>
            <MenuRow styles={styles} icon="shopping-cart" label={STRINGS.enterSales} onPress={() => go('SALE')} />
            <MenuRow styles={styles} icon="assignment-return" label={STRINGS.enterReturn} onPress={() => go('RETURN')} />
            <View style={styles.menuDivider} />
            <MenuRow
              styles={styles}
              icon="logout"
              label={STRINGS.logout}
              onPress={() => { setMenuOpen(false); logout(); }}
            />
            <MenuRow styles={styles} icon="close" label={STRINGS.close} onPress={() => setMenuOpen(false)} muted />
          </Pressable>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}

function BigTile({ styles, color, icon, label, onPress }) {
  return (
    <TouchableOpacity
      activeOpacity={0.85}
      onPress={onPress}
      style={[styles.tile, { backgroundColor: color }]}
      accessibilityRole="button"
      accessibilityLabel={label.en}
    >
      <MaterialIcons name={icon} size={64} color="#FFFFFF" />
      <Text style={styles.tileEnglish}>{label.en}</Text>
      <Text style={styles.tileUrdu}>{label.ur}</Text>
    </TouchableOpacity>
  );
}

function MenuRow({ styles, icon, label, onPress, muted }) {
  return (
    <TouchableOpacity style={styles.menuRow} onPress={onPress} accessibilityRole="button" accessibilityLabel={label.en}>
      <MaterialIcons name={icon} size={30} style={[styles.menuIcon, muted && styles.menuMuted]} />
      <View style={{ flex: 1 }}>
        <Text style={[styles.menuEnglish, muted && styles.menuMuted]}>{label.en}</Text>
        <Text style={[styles.menuUrdu, muted && styles.menuMuted]}>{label.ur}</Text>
      </View>
    </TouchableOpacity>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    greeting: { paddingHorizontal: 20, paddingTop: 20, paddingBottom: 8 },
    greetingText: { fontSize: 22, fontWeight: 'bold', color: colors.textPrimary },
    greetingUrdu: { fontSize: 18, color: colors.textSecondary, marginTop: 2 },
    tiles: { flex: 1, padding: 16, justifyContent: 'center' },
    tile: {
      minHeight: 190,
      borderRadius: 20,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: 20,
      padding: 16,
      elevation: 4,
    },
    tileEnglish: { fontSize: 32, fontWeight: 'bold', color: '#FFFFFF', marginTop: 8 },
    tileUrdu: { fontSize: 26, color: '#FFFFFF', marginTop: 4 },
    overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-start' },
    panel: {
      width: '82%',
      maxWidth: 360,
      height: '100%',
      backgroundColor: colors.surface,
      paddingTop: 48,
      paddingHorizontal: 16,
    },
    panelTitle: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary, marginBottom: 12 },
    menuRow: {
      flexDirection: 'row',
      alignItems: 'center',
      minHeight: 72,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    menuIcon: { color: colors.primary, marginRight: 16 },
    menuEnglish: { fontSize: 22, fontWeight: '600', color: colors.textPrimary },
    menuUrdu: { fontSize: 18, color: colors.textSecondary },
    menuMuted: { color: colors.textMuted },
    menuDivider: { height: 16 },
  });
