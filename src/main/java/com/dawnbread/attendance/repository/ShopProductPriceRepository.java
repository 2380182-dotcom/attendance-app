package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.ShopProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopProductPriceRepository extends JpaRepository<ShopProductPrice, Long> {

    /** The sale-time lookup: does this shop have its own price for this product? */
    Optional<ShopProductPrice> findByCustomerShopIdAndProductId(Long customerShopId, Long productId);

    /** All of a shop's explicit prices with the product fetched in one query — the admin form and mobile price display. */
    @Query("SELECT p FROM ShopProductPrice p JOIN FETCH p.product WHERE p.customerShop.id = :shopId")
    List<ShopProductPrice> findByCustomerShopIdWithProduct(@Param("shopId") Long shopId);
}
