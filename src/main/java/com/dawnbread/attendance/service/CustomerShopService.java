package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CustomerShopCreateDTO;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
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
}
