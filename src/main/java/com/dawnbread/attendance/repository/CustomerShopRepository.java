package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.CustomerShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Deliberately no findShopsWithinRadius yet — geofence-based shop lookup is
// a mobile/shop-visit concern (a later stage), out of scope for this
// additive-foundation stage.
@Repository
public interface CustomerShopRepository extends JpaRepository<CustomerShop, Long> {

    Optional<CustomerShop> findByShopCode(String shopCode);
    List<CustomerShop> findByShopNameContainingIgnoreCase(String shopName);
    List<CustomerShop> findByIsActiveTrue();
    boolean existsByShopCode(String shopCode);
}
