package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CustomerShopCreateDTO;
import com.dawnbread.attendance.dto.ShopProductPricesUpdateDTO;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.ShopProductDiscount;
import com.dawnbread.attendance.entity.ShopProductPrice;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.ShopProductDiscountRepository;
import com.dawnbread.attendance.repository.ShopProductPriceRepository;
import com.dawnbread.attendance.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class CustomerShopService {

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private LmtSettingsService lmtSettingsService;

    @Autowired
    private ShopProductDiscountRepository shopProductDiscountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ShopProductPriceRepository shopProductPriceRepository;

    /** Bad discount data would silently produce wrong revenue at sale time, so reject it at the door. */
    private static void requireValidDiscount(Double discountPercent) {
        if (discountPercent != null && (discountPercent < 0 || discountPercent > 100)) {
            throw new RuntimeException("Discount percent must be between 0 and 100.");
        }
    }

    private Area resolveArea(Long areaId) {
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + areaId));
    }

    /** One active shop plus its computed distance from the LMT's current GPS — see getNearby(). */
    public static class NearbyShop {
        private final CustomerShop shop;
        private final double distanceMeters;

        public NearbyShop(CustomerShop shop, double distanceMeters) {
            this.shop = shop;
            this.distanceMeters = distanceMeters;
        }

        public CustomerShop getShop() { return shop; }
        public double getDistanceMeters() { return distanceMeters; }
    }

    /**
     * Active shops within shop.radius + the admin-configured geofence
     * buffer of the given GPS point — the SAME threshold formula
     * SalesService.submitShopVisit uses for its hard gate, so a shop shown
     * here as "nearby" is guaranteed to also pass that real gate at submit
     * time (never a false "nearby" that then fails to submit). Shops
     * without geofencing configured (geoFencingEnabled=false, or missing
     * lat/lon/radius) have no distance to compute and are excluded here —
     * they remain reachable only via the manual shop-code fallback.
     *
     * Filtering happens in Java over all active shops, not a DB query —
     * consistent with how submitShopVisit already computes distance
     * (Java, not SQL) and fine at this data scale (see
     * CustomerShopRepository's own comment on why no DB-side distance
     * query exists yet).
     */
    public List<NearbyShop> getNearby(double latitude, double longitude) {
        double buffer = lmtSettingsService.getOrCreate().getGeofenceBufferMeters();
        return customerShopRepository.findByIsActiveTrue().stream()
                .filter(shop -> Boolean.TRUE.equals(shop.getGeoFencingEnabled())
                        && shop.getLatitude() != null && shop.getLongitude() != null && shop.getRadius() != null)
                .map(shop -> new NearbyShop(shop, GeoUtils.distanceMeters(latitude, longitude, shop.getLatitude(), shop.getLongitude())))
                .filter(nearby -> nearby.getDistanceMeters() <= nearby.getShop().getRadius() + buffer)
                .sorted(Comparator.comparingDouble(NearbyShop::getDistanceMeters))
                .collect(Collectors.toList());
    }

    public CustomerShop create(CustomerShopCreateDTO dto) {
        requireValidDiscount(dto.getDiscountPercent());
        if (customerShopRepository.existsByShopCode(dto.getShopCode())) {
            throw new RuntimeException("Shop code already exists: " + dto.getShopCode());
        }
        CustomerShop shop = new CustomerShop();
        shop.setShopCode(dto.getShopCode());
        shop.setShopName(dto.getShopName());
        shop.setBranch(dto.getBranch());
        shop.setAddress(dto.getAddress());
        shop.setPhone(dto.getPhone());
        shop.setMobile(dto.getMobile());
        shop.setEmail(dto.getEmail());
        shop.setStrn(dto.getStrn());
        shop.setNtn(dto.getNtn());
        shop.setArea(resolveArea(dto.getAreaId()));
        shop.setLatitude(dto.getLatitude());
        shop.setLongitude(dto.getLongitude());
        shop.setRadius(dto.getRadius());
        if (dto.getGeoFencingEnabled() != null) {
            shop.setGeoFencingEnabled(dto.getGeoFencingEnabled());
        }
        shop.setDiscountPercent(dto.getDiscountPercent());
        shop.setCreatedAt(LocalDateTime.now());
        shop.setIsActive(true);
        return customerShopRepository.save(shop);
    }

    public List<CustomerShop> getActive() {
        return customerShopRepository.findByIsActiveTrue();
    }

    public List<CustomerShop> getAll() {
        return customerShopRepository.findAll();
    }

    public Optional<CustomerShop> getById(Long id) {
        return customerShopRepository.findById(id);
    }

    /** The lookup the mobile "enter shop code" flow will use in a later stage. */
    public Optional<CustomerShop> getByShopCode(String shopCode) {
        return customerShopRepository.findByShopCode(shopCode);
    }

    public List<CustomerShop> searchByName(String shopName) {
        return customerShopRepository.findByShopNameContainingIgnoreCase(shopName);
    }

    public CustomerShop update(Long id, CustomerShopCreateDTO dto) {
        requireValidDiscount(dto.getDiscountPercent());
        CustomerShop shop = customerShopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + id));

        if (dto.getShopCode() != null) {
            Optional<CustomerShop> existing = customerShopRepository.findByShopCode(dto.getShopCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new RuntimeException("Shop code already taken: " + dto.getShopCode());
            }
            shop.setShopCode(dto.getShopCode());
        }
        if (dto.getShopName() != null) {
            shop.setShopName(dto.getShopName());
        }
        if (dto.getBranch() != null) {
            shop.setBranch(dto.getBranch());
        }
        if (dto.getAddress() != null) {
            shop.setAddress(dto.getAddress());
        }
        if (dto.getPhone() != null) {
            shop.setPhone(dto.getPhone());
        }
        if (dto.getMobile() != null) {
            shop.setMobile(dto.getMobile());
        }
        if (dto.getEmail() != null) {
            shop.setEmail(dto.getEmail());
        }
        if (dto.getStrn() != null) {
            shop.setStrn(dto.getStrn());
        }
        if (dto.getNtn() != null) {
            shop.setNtn(dto.getNtn());
        }
        if (dto.getAreaId() != null) {
            shop.setArea(resolveArea(dto.getAreaId()));
        }
        if (dto.getLatitude() != null) {
            shop.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            shop.setLongitude(dto.getLongitude());
        }
        if (dto.getRadius() != null) {
            shop.setRadius(dto.getRadius());
        }
        if (dto.getGeoFencingEnabled() != null) {
            shop.setGeoFencingEnabled(dto.getGeoFencingEnabled());
        }
        if (dto.getDiscountPercent() != null) {
            shop.setDiscountPercent(dto.getDiscountPercent());
        }
        return customerShopRepository.save(shop);
    }

    /**
     * Soft-delete only — matches Mart's rationale exactly: once any sales
     * activity references a shop (a later stage), a hard delete would
     * either fail or destroy historical records.
     */
    public void deactivate(Long id) {
        CustomerShop shop = customerShopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + id));
        shop.setIsActive(false);
        customerShopRepository.save(shop);
    }

    public CustomerShop reactivate(Long id) {
        CustomerShop shop = customerShopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + id));
        shop.setIsActive(true);
        return customerShopRepository.save(shop);
    }

    /** All per-product (SKU) discount overrides for one shop — the admin shop-edit form's list, also read by mobile for the P6 discounted-total preview. */
    public List<ShopProductDiscount> getProductDiscounts(Long shopId) {
        if (!customerShopRepository.existsById(shopId)) {
            throw new RuntimeException("Customer shop not found with id: " + shopId);
        }
        return shopProductDiscountRepository.findByCustomerShopIdWithProduct(shopId);
    }

    /** Creates or replaces the SKU override for (shopId, productId) — one row per pair, per the unique index. */
    public ShopProductDiscount upsertProductDiscount(Long shopId, Long productId, Double discountPercent) {
        requireValidDiscount(discountPercent);
        CustomerShop shop = customerShopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + shopId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        ShopProductDiscount override = shopProductDiscountRepository
                .findByCustomerShopIdAndProductId(shopId, productId)
                .orElseGet(ShopProductDiscount::new);
        override.setCustomerShop(shop);
        override.setProduct(product);
        override.setDiscountPercent(discountPercent);
        return shopProductDiscountRepository.save(override);
    }

    /** Removes a shop's SKU override for one product — that product then falls back to the shop's overall discountPercent. */
    public void removeProductDiscount(Long shopId, Long productId) {
        ShopProductDiscount override = shopProductDiscountRepository
                .findByCustomerShopIdAndProductId(shopId, productId)
                .orElseThrow(() -> new RuntimeException("No discount override found for this shop/product"));
        shopProductDiscountRepository.delete(override);
    }

    /** A shop's explicit per-product prices — read by the admin form and the mobile price display. */
    public List<ShopProductPrice> getProductPrices(Long shopId) {
        if (!customerShopRepository.existsById(shopId)) {
            throw new RuntimeException("Customer shop not found with id: " + shopId);
        }
        return shopProductPriceRepository.findByCustomerShopIdWithProduct(shopId);
    }

    /**
     * Applies a batch of shop prices in one transaction: a non-null price
     * creates or replaces that product's shop price, a null price removes
     * it (back to the global salesman price). Products not listed are left
     * untouched.
     */
    public List<ShopProductPrice> applyProductPrices(Long shopId, List<ShopProductPricesUpdateDTO.Entry> entries) {
        CustomerShop shop = customerShopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + shopId));
        // Validate the whole batch first so a bad row never leaves a half-applied save.
        for (ShopProductPricesUpdateDTO.Entry entry : entries) {
            if (entry.getPrice() != null && entry.getPrice() < 0) {
                throw new RuntimeException("Shop price cannot be negative.");
            }
        }
        for (ShopProductPricesUpdateDTO.Entry entry : entries) {
            var existing = shopProductPriceRepository.findByCustomerShopIdAndProductId(shopId, entry.getProductId());
            if (entry.getPrice() == null) {
                existing.ifPresent(shopProductPriceRepository::delete);
                continue;
            }
            Product product = productRepository.findById(entry.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + entry.getProductId()));
            ShopProductPrice row = existing.orElseGet(ShopProductPrice::new);
            row.setCustomerShop(shop);
            row.setProduct(product);
            row.setPrice(entry.getPrice());
            shopProductPriceRepository.save(row);
        }
        shopProductPriceRepository.flush();
        return shopProductPriceRepository.findByCustomerShopIdWithProduct(shopId);
    }
}
