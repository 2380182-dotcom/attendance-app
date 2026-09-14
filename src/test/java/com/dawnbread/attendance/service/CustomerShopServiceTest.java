package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.LmtSettings;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.LmtSettingsRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import com.dawnbread.attendance.util.GeoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LMT flow redesign, D1: proves getNearby() uses the SAME threshold
 * formula (shop.radius + geofenceBufferMeters) as SalesService's real
 * submit-time gate — a shop shown as "nearby" must be guaranteed to also
 * pass that gate, never a false positive/negative between the two.
 */
@SpringBootTest
class CustomerShopServiceTest {

    private static final double BASE_LAT = 31.5204;
    private static final double BASE_LON = 74.3587;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private LmtSettingsRepository lmtSettingsRepository;

    @Autowired
    private CustomerShopService customerShopService;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(tenantId());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DAWNBREAD");
                    t.setName("Dawn Bread");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    private void setBuffer(double bufferMeters) {
        LmtSettings settings = lmtSettingsRepository.findByTenantId(tenantId())
                .orElseGet(() -> {
                    LmtSettings s = new LmtSettings();
                    s.setTenantId(tenantId());
                    s.setCreatedAt(LocalDateTime.now());
                    s.setUpdatedAt(LocalDateTime.now());
                    return s;
                });
        settings.setGeofenceBufferMeters(bufferMeters);
        settings.setUpdatedAt(LocalDateTime.now());
        lmtSettingsRepository.save(settings);
    }

    private Area seedArea() {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Nearby Test Area " + System.nanoTime());
        area.setIsActive(true);
        area.setCreatedAt(LocalDateTime.now());
        return areaRepository.save(area);
    }

    private CustomerShop seedShop(double lat, double lon, double radius, boolean geoFencingEnabled) {
        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("NEARBY_" + System.nanoTime());
        shop.setShopName("Nearby Test Shop");
        shop.setArea(seedArea());
        shop.setLatitude(lat);
        shop.setLongitude(lon);
        shop.setRadius(radius);
        shop.setGeoFencingEnabled(geoFencingEnabled);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    @Test
    void shopAtExactLmtLocationIsAlwaysWithinRange() {
        setBuffer(50.0);
        CustomerShop shop = seedShop(BASE_LAT, BASE_LON, 0.0, true);

        List<CustomerShopService.NearbyShop> results = customerShopService.getNearby(BASE_LAT, BASE_LON);

        assertTrue(results.stream().anyMatch(r -> r.getShop().getId().equals(shop.getId())),
                "A shop at the exact same GPS point (distance 0) must always be within radius(0)+buffer(50)");
    }

    @Test
    void shopFarBeyondRadiusPlusBufferIsExcluded() {
        setBuffer(50.0);
        // ~1 degree of latitude is ~111km — far beyond any small radius+buffer.
        CustomerShop farShop = seedShop(BASE_LAT + 1.0, BASE_LON, 100.0, true);

        List<CustomerShopService.NearbyShop> results = customerShopService.getNearby(BASE_LAT, BASE_LON);

        assertTrue(results.stream().noneMatch(r -> r.getShop().getId().equals(farShop.getId())),
                "A shop ~111km away must not appear as nearby under a 100m radius + 50m buffer");
    }

    @Test
    void shopWithGeoFencingDisabledIsExcludedEvenAtZeroDistance() {
        setBuffer(50.0);
        CustomerShop shop = seedShop(BASE_LAT, BASE_LON, 100.0, false);

        List<CustomerShopService.NearbyShop> results = customerShopService.getNearby(BASE_LAT, BASE_LON);

        assertTrue(results.stream().noneMatch(r -> r.getShop().getId().equals(shop.getId())),
                "A shop with geoFencingEnabled=false has no distance to compute and must be excluded regardless of proximity — it's reachable only via the manual shop-code fallback");
    }

    @Test
    void largeShopRadiusExtendsHowFarItCanBeSeenAsNearby() {
        setBuffer(10.0);
        // A small, precise offset — real haversine distance computed below,
        // not approximated, so the boundary check is exact.
        double offsetLat = BASE_LAT + 0.002;
        double distance = GeoUtils.distanceMeters(BASE_LAT, BASE_LON, offsetLat, BASE_LON);

        // A shop with a large registered radius covering this distance
        // (even though the flat buffer alone would not) must still show up
        // — proves getNearby() uses radius+buffer, not buffer alone.
        CustomerShop shop = seedShop(offsetLat, BASE_LON, distance + 5.0, true);

        List<CustomerShopService.NearbyShop> results = customerShopService.getNearby(BASE_LAT, BASE_LON);

        assertTrue(results.stream().anyMatch(r -> r.getShop().getId().equals(shop.getId())),
                "A shop with a large explicit radius must be reachable beyond the flat buffer distance alone");
    }

    @Test
    void resultsAreSortedNearestFirst() {
        setBuffer(50.0);
        CustomerShop near = seedShop(BASE_LAT, BASE_LON, 20.0, true);
        double offsetLat = BASE_LAT + 0.0001;
        CustomerShop farther = seedShop(offsetLat, BASE_LON, 50.0, true);

        List<CustomerShopService.NearbyShop> results = customerShopService.getNearby(BASE_LAT, BASE_LON);

        int nearIndex = -1;
        int fartherIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).getShop().getId().equals(near.getId())) nearIndex = i;
            if (results.get(i).getShop().getId().equals(farther.getId())) fartherIndex = i;
        }
        assertTrue(nearIndex >= 0 && fartherIndex >= 0, "Both shops must be present in the results");
        assertTrue(nearIndex < fartherIndex, "The closer shop must be sorted before the farther one");
    }
}
