package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByIsActiveTrue();

    /** Pricing management view — every product (active or not), name-sorted for a stable admin list. */
    List<Product> findAllByOrderByNameAsc();
}
