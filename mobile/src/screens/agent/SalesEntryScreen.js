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
  Image,
  ScrollView
} from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { apiService } from '../../services/api';
import { AuthContext } from '../../context/AuthContext';
import Loading from '../../components/Loading';

export default function SalesEntryScreen({ navigation }) {
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
      <View style={styles.searchSection}>
        <MaterialIcons name="search" size={20} color="#757575" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search products (e.g. Bread, Muffin)..."
          placeholderTextColor="#9E9E9E"
          value={searchQuery}
          onChangeText={handleSearch}
        />
        {searchQuery !== '' && (
          <TouchableOpacity onPress={() => setSearchQuery('')}>
            <MaterialIcons name="close" size={20} color="#757575" />
          </TouchableOpacity>
        )}
      </View>

      <Text style={styles.sectionHeader}>Available Products</Text>
      <View style={styles.listContainer}>
        <FlatList
          data={filteredProducts}
          keyExtractor={(item) => item.id.toString()}
          renderItem={({ item }) => (
            <View style={styles.productRow}>
              <Image source={{ uri: item.thumbnailUrl }} style={styles.productImage} />
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
            <View style={styles.emptySearch}>
              <MaterialIcons name="search-off" size={40} color="#B0BEC5" />
              <Text style={styles.emptySearchText}>No products match your search query</Text>
            </View>
          }
        />
      </View>

      {/* Cart Section */}
      <View style={styles.cartSection}>
        <View style={styles.cartHeader}>
          <Text style={styles.cartTitle}>Current Cart ({cart.length} unique items)</Text>
          <TouchableOpacity onPress={() => setCart([])} disabled={cart.length === 0}>
            <Text style={[styles.clearCartText, cart.length === 0 && { color: '#B0BEC5' }]}>Clear Cart</Text>
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
                  <MaterialIcons name="cancel" size={20} color="#D32F2F" />
                </TouchableOpacity>
              </View>
            )}
            ListEmptyComponent={
              <View style={styles.emptyCart}>
                <MaterialIcons name="shopping-cart" size={32} color="#CFD8DC" />
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
          <TouchableOpacity 
            style={styles.cancelBtn} 
            onPress={() => navigation.goBack()}
          >
            <Text style={styles.cancelBtnText}>Cancel</Text>
          </TouchableOpacity>
          <TouchableOpacity 
            style={[styles.submitBtn, cart.length === 0 && styles.submitBtnDisabled]} 
            onPress={handleSubmitSales}
            disabled={cart.length === 0}
          >
            <Text style={styles.submitBtnText}>Submit Sales</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  searchSection: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 8,
    margin: 12,
    paddingHorizontal: 12,
    height: 48,
    borderWidth: 1,
    borderColor: '#E0E0E0',
    elevation: 2,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 }
  },
  searchIcon: {
    marginRight: 8
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
    color: '#333'
  },
  sectionHeader: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#555',
    marginLeft: 14,
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.5
  },
  listContainer: {
    flex: 1.2,
    backgroundColor: '#fff',
    marginHorizontal: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#E0E0E0',
    overflow: 'hidden'
  },
  productRow: {
    flexDirection: 'row',
    padding: 12,
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: '#EEEEEE'
  },
  productImage: {
    width: 44,
    height: 44,
    borderRadius: 6,
    backgroundColor: '#EEEEEE'
  },
  productInfo: {
    flex: 1,
    marginLeft: 12
  },
  productName: {
    fontWeight: 'bold',
    fontSize: 14,
    color: '#333'
  },
  productPrice: {
    fontSize: 12,
    color: '#1976D2',
    fontWeight: '600',
    marginTop: 2
  },
  actionRow: {
    flexDirection: 'row',
    alignItems: 'center'
  },
  qtyInput: {
    borderWidth: 1,
    borderColor: '#CFD8DC',
    borderRadius: 6,
    width: 42,
    height: 36,
    textAlign: 'center',
    fontSize: 13,
    color: '#333',
    backgroundColor: '#FAFAFA',
    marginRight: 8
  },
  addButton: {
    backgroundColor: '#1976D2',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
  },
  addButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 13
  },
  emptySearch: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40
  },
  emptySearchText: {
    color: '#757575',
    fontSize: 13,
    marginTop: 8
  },
  cartSection: {
    flex: 1,
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 16,
    elevation: 8,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: -3 },
    borderWidth: 1,
    borderColor: '#EEEEEE'
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
    color: '#333'
  },
  clearCartText: {
    color: '#D32F2F',
    fontWeight: '600',
    fontSize: 13
  },
  cartListContainer: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#EEEEEE',
    borderRadius: 8,
    paddingHorizontal: 8,
    backgroundColor: '#FAFAFA'
  },
  cartItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#EEEEEE'
  },
  cartItemDetails: {
    flex: 1
  },
  cartItemName: {
    fontWeight: '600',
    fontSize: 13,
    color: '#333'
  },
  cartItemSub: {
    fontSize: 11,
    color: '#757575',
    marginTop: 2
  },
  cartItemTotal: {
    fontWeight: 'bold',
    fontSize: 13,
    color: '#333',
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
    color: '#9E9E9E',
    fontSize: 12,
    marginTop: 6
  },
  summaryContainer: {
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: '#EEEEEE',
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
    color: '#555'
  },
  summaryValue: {
    fontWeight: '600',
    fontSize: 13,
    color: '#333'
  },
  summaryTotal: {
    fontWeight: 'bold',
    fontSize: 16,
    color: '#1976D2'
  },
  buttonGroup: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10
  },
  cancelBtn: {
    flex: 1,
    backgroundColor: '#ECEFF1',
    height: 44,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 8
  },
  cancelBtnText: {
    fontWeight: 'bold',
    color: '#607D8B',
    fontSize: 14
  },
  submitBtn: {
    flex: 1.5,
    backgroundColor: '#4CAF50',
    height: 44,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 8
  },
  submitBtnDisabled: {
    backgroundColor: '#A5D6A7'
  },
  submitBtnText: {
    fontWeight: 'bold',
    color: '#fff',
    fontSize: 14
  }
});
