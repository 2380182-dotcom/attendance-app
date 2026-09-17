package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.ShopProductDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopProductDiscountRepository extends JpaRepository<ShopProductDiscount, Long> {

    /** The SKU-override lookup at sale time: is there a specific discount for this (shop, product)? */
    Optional<ShopProductDiscount> findByCustomerShopIdAndProductId(Long customerShopId, Long productId);

    /** All overrides for one shop — the admin shop-edit form's list. */
    List<ShopProductDiscount> findByCustomerShopId(Long customerShopId);

    @Modifying
    @Query("DELETE FROM ShopProductDiscount d WHERE d.customerShop.id = :shopId")
    void deleteByCustomerShopId(@Param("shopId") Long shopId);
}
