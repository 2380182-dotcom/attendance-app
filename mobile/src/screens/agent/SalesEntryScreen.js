import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  SafeAreaView,
  ScrollView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import { AuthContext } from '../../context/AuthContext';
import Loading from '../../components/Loading';
import ProductThumbnail from '../../components/ProductThumbnail';
import SearchBar from '../../components/SearchBar';
import AppButton from '../../components/AppButton';
import EmptyState from '../../components/EmptyState';
import { useTheme } from '../../theme';

export default function SalesEntryScreen({ navigation }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const { user } = useContext(AuthContext);
  const [products, setProducts] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [cart, setCart] = useState([]);
  
  // Local quantity inputs for catalog products, keyed by product ID
  const [quantities, setQuantities] = useState({});

  const fetchProducts = useCallback(async () => {
    try {
      const data = await apiService.sales.getProducts();
      setProducts(data);
      // Initialize quantities to 1 for all products
      const qtys = {};
      data.forEach(p => {
        qtys[p.id] = '1';
      });
      setQuantities(qtys);
    } catch (e) {
      console.error(e);
      Alert.alert('Data Error', 'Unable to fetch products list.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  const handleSearch = (text) => {
    setSearchQuery(text);
  };

  const handleQtyChange = (productId, val) => {
    setQuantities(prev => ({
      ...prev,
      [productId]: val
    }));
  };

  const handleAddToCart = (product) => {
    const qtyStr = quantities[product.id] || '1';
    const qty = parseInt(qtyStr);

    if (isNaN(qty) || qty <= 0) {
      Alert.alert('Invalid Quantity', 'Please enter a quantity of 1 or more.');
      return;
    }

    if (qty > 500) {
      Alert.alert('Limit Exceeded', 'Maximum allowed quantity per product is 500.');
      return;
    }

    // Check if product already in cart
    const existingIndex = cart.findIndex(item => item.product.id === product.id);
    if (existingIndex > -1) {
      Alert.alert(
        'Product in Cart',
        `'${product.name}' is already in the cart. You cannot submit duplicate product entries in one request. Use remove to edit.`
      );
      return;
    }

    const cartItem = {
      product,
      quantity: qty,
      totalPrice: product.price * qty
    };

    setCart(prev => [...prev, cartItem]);
    Alert.alert('Added to Cart', `${product.name} (${qty} units) added to your current cart.`);
  };

  const handleRemoveFromCart = (productId) => {
    setCart(prev => prev.filter(item => item.product.id !== productId));
  };

  const calculateCartTotal = () => {
    return cart.reduce((sum, item) => sum + item.totalPrice, 0);
  };

  const calculateTotalUnits = () => {
    return cart.reduce((sum, item) => sum + item.quantity, 0);
  };

  const handleSubmitSales = async () => {
    if (cart.length === 0) {
      Alert.alert('Empty Cart', 'Please add at least one product to the cart before submitting.');
      return;
    }

    Alert.alert(
      'Confirm Submission',
      `Are you sure you want to submit this sales record with ${calculateTotalUnits()} units for a total of PKR ${calculateCartTotal()}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Confirm & Submit',
          onPress: async () => {
            setSubmitting(true);
            try {
              const salesRequest = {
                agentId: user.id,
                location: 'North Outlet', // Default outlet location
                items: cart.map(item => ({
                  productId: item.product.id,
                  quantity: item.quantity
                }))
              };
              
              await apiService.sales.submitSales(salesRequest);
              Alert.alert('Success', 'Sales entry recorded and synced successfully!', [
                { text: 'OK', onPress: () => navigation.goBack() }
              ]);
            } catch (e) {
              console.error(e);
              Alert.alert('Submission Failed', e.message || 'Error occurred while saving sales record.');
            } finally {
              setSubmitting(false);
            }
          }
        }
      ]
    );
  };

  const filteredProducts = products.filter(p => 
    p.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  if (loading) {
    return <Loading message="Loading Dawn Bread products..." fullScreen />;
  }

  if (submitting) {
    return <Loading message="Recording & Syncing sales data..." fullScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <SearchBar
        value={searchQuery}
        onChangeText={handleSearch}
        placeholder="Search products (e.g. Bread, Muffin)..."
        style={styles.searchSection}
      />

      <Text style={styles.sectionHeader}>Available Products</Text>
      <View style={styles.listContainer}>
        <FlatList
          data={filteredProducts}
          keyExtractor={(item) => item.id.toString()}
          renderItem={({ item }) => (
            <View style={styles.productRow}>
              <ProductThumbnail uri={item.thumbnailUrl} size={40} style={styles.productImage} />
              <View style={styles.productInfo}>
                <Text style={styles.productName}>{item.name}</Text>
                <Text style={styles.productPrice}>PKR {item.price}</Text>
              </View>
              <View style={styles.actionRow}>
                <TextInput
                  style={styles.qtyInput}
                  keyboardType="number-pad"
                  value={quantities[item.id] || '1'}
                  onChangeText={(val) => handleQtyChange(item.id, val)}
                  maxLength={3}
                />
                <TouchableOpacity
                  style={styles.addButton}
                  onPress={() => handleAddToCart(item)}
                >
                  <Text style={styles.addButtonText}>+ Add</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
          ListEmptyComponent={
            <EmptyState
              icon="search-off"
              title="No products found"
              message="No products match your search query."
            />
          }
        />
      </View>

      {/* Cart Section */}
      <View style={styles.cartSection}>
        <View style={styles.cartHeader}>
          <Text style={styles.cartTitle}>Current Cart ({cart.length} unique items)</Text>
          <TouchableOpacity onPress={() => setCart([])} disabled={cart.length === 0}>
            <Text style={[styles.clearCartText, cart.length === 0 && { color: colors.textMuted }]}>Clear Cart</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.cartListContainer}>
          <FlatList
            data={cart}
            keyExtractor={(item) => item.product.id.toString()}
            renderItem={({ item }) => (
              <View style={styles.cartItem}>
                <View style={styles.cartItemDetails}>
                  <Text style={styles.cartItemName}>{item.product.name}</Text>
                  <Text style={styles.cartItemSub}>Qty: {item.quantity} x PKR {item.product.price}</Text>
                </View>
                <Text style={styles.cartItemTotal}>PKR {item.totalPrice}</Text>
                <TouchableOpacity
                  onPress={() => handleRemoveFromCart(item.product.id)}
                  style={styles.removeBtn}
                >
                  <MaterialIcons name="cancel" size={20} color={colors.error} />
                </TouchableOpacity>
              </View>
            )}
            ListEmptyComponent={
              <View style={styles.emptyCart}>
                <MaterialIcons name="shopping-cart" size={32} color={colors.textMuted} />
                <Text style={styles.emptyCartText}>Your cart is empty. Add products above.</Text>
              </View>
            }
          />
        </View>

        <View style={styles.summaryContainer}>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryText}>Total Quantity:</Text>
            <Text style={styles.summaryValue}>{calculateTotalUnits()} units</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryText}>Total Amount:</Text>
            <Text style={styles.summaryTotal}>PKR {calculateCartTotal()}</Text>
          </View>
        </View>

        <View style={styles.buttonGroup}>
          <AppButton
            title="Cancel"
            onPress={() => navigation.goBack()}
            variant="ghost"
            style={{ flex: 1, marginRight: 8 }}
          />
          <AppButton
            title="Submit Sales"
            onPress={handleSubmitSales}
            variant="success"
            disabled={cart.length === 0}
            style={{ flex: 1.5, marginLeft: 8 }}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

const createStyles = (colors) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  searchSection: {
    margin: 12,
  },
  sectionHeader: {
    fontSize: 12,
    fontWeight: 'bold',
    color: colors.textSecondary,
    marginLeft: 14,
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.5
  },
  listContainer: {
    flex: 1.2,
    backgroundColor: colors.surface,
    marginHorizontal: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden'
  },
  productRow: {
    flexDirection: 'row',
    padding: 12,
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: colors.divider
  },
  productImage: {
    width: 44,
    height: 44,
    borderRadius: 6,
    backgroundColor: colors.surfaceMuted
  },
  productInfo: {
    flex: 1,
    marginLeft: 12
  },
  productName: {
    fontWeight: 'bold',
    fontSize: 14,
    color: colors.textPrimary
  },
  productPrice: {
    fontSize: 12,
    color: colors.secondary,
    fontWeight: '600',
    marginTop: 2
  },
  actionRow: {
    flexDirection: 'row',
    alignItems: 'center'
  },
  qtyInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    width: 42,
    height: 36,
    textAlign: 'center',
    fontSize: 13,
    color: colors.textPrimary,
    backgroundColor: colors.inputBackground,
    marginRight: 8
  },
  addButton: {
    backgroundColor: colors.secondary,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
  },
  addButtonText: {
    color: colors.textOnPrimary,
    fontWeight: 'bold',
    fontSize: 13
  },
  cartSection: {
    flex: 1,
    backgroundColor: colors.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 16,
    elevation: 8,
    shadowColor: colors.shadow,
    shadowOpacity: 0.15,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: -3 },
    borderWidth: 1,
    borderColor: colors.divider
  },
  cartHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8
  },
  cartTitle: {
    fontWeight: 'bold',
    fontSize: 15,
    color: colors.textPrimary
  },
  clearCartText: {
    color: colors.error,
    fontWeight: '600',
    fontSize: 13
  },
  cartListContainer: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: 8,
    paddingHorizontal: 8,
    backgroundColor: colors.inputBackground
  },
  cartItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider
  },
  cartItemDetails: {
    flex: 1
  },
  cartItemName: {
    fontWeight: '600',
    fontSize: 13,
    color: colors.textPrimary
  },
  cartItemSub: {
    fontSize: 11,
    color: colors.textSecondary,
    marginTop: 2
  },
  cartItemTotal: {
    fontWeight: 'bold',
    fontSize: 13,
    color: colors.textPrimary,
    marginRight: 8
  },
  removeBtn: {
    padding: 4
  },
  emptyCart: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 30
  },
  emptyCartText: {
    color: colors.textMuted,
    fontSize: 12,
    marginTop: 6
  },
  summaryContainer: {
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
    marginTop: 10
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6
  },
  summaryText: {
    fontSize: 13,
    color: colors.textSecondary
  },
  summaryValue: {
    fontWeight: '600',
    fontSize: 13,
    color: colors.textPrimary
  },
  summaryTotal: {
    fontWeight: 'bold',
    fontSize: 16,
    color: colors.secondary
  },
  buttonGroup: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10
  },
});
