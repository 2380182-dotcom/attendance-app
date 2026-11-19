package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ProductPricingDTO;
import com.dawnbread.attendance.dto.ProductPricingUpdateDTO;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.ProductFamily;
import com.dawnbread.attendance.repository.ProductFamilyRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.security.TenantContext;
import com.dawnbread.attendance.util.ProductOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Role-based pricing (P3): the Sales Dashboard's product-pricing
 * management. Read-only elsewhere in the app — SalesService independently
 * reads Product.agentPrice/salesmanPrice fresh at submission time, never
 * anything cached or client-supplied, so nothing here can be used to
 * smuggle a price into a sale.
 */
@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductFamilyRepository productFamilyRepository;

    /** In the official price list's order (ProductOrder), never by name. */
    public List<ProductPricingDTO> getAllForPricing() {
        return productRepository.findAll().stream()
                .filter(p -> Objects.equals(p.getTenantId(), TenantContext.getTenantId()))
                .sorted(ProductOrder.PRODUCTS)
                .map(ProductService::toPricingDTO)
                .collect(Collectors.toList());
    }

    private static ProductPricingDTO toPricingDTO(Product p) {
        ProductPricingDTO dto = new ProductPricingDTO(p.getId(), p.getName(), p.getCategory(), p.getUnit(), p.getAgentPrice(),
                p.getSalesmanPrice(), Boolean.TRUE.equals(p.getIsActive()));
        dto.setFamilyId(p.getFamilyId());
        dto.setSortOrder(p.getSortOrder());
        dto.setOnPriceList(Boolean.TRUE.equals(p.getOnPriceList()));
        return dto;
    }

    /**
     * A product's place on the official price list: family (null = none),
     * order number (the list's SR No.; null = after every placed product)
     * and whether it is on the list. ADMIN only (checked in ProductController).
     */
    public ProductPricingDTO setPlacement(Long productId, Long familyId, Integer sortOrder, boolean onPriceList) {
        Product product = ownProduct(productId);
        if (familyId != null) ownFamily(familyId);
        if (sortOrder != null && (sortOrder < 1 || sortOrder > 9999)) {
            throw new IllegalArgumentException("The order number must be between 1 and 9999.");
        }
        product.setFamilyId(familyId);
        product.setSortOrder(sortOrder);
        product.setOnPriceList(onPriceList);
        product.setUpdatedAt(LocalDateTime.now());
        return toPricingDTO(productRepository.save(product));
    }

    // ------------------------------------------------------------------ families

    /** The caller's families in button order. Read by every app (filter buttons) and the dashboard. */
    public List<ProductFamily> getFamilies() {
        return productFamilyRepository.findByTenantIdOrderBySortOrderAscIdAsc(TenantContext.getTenantId());
    }

    /** Adds (id null) or changes a family. ADMIN only. Names are unique per company (any case). */
    public ProductFamily saveFamily(Long id, String name, String nameUr, Integer sortOrder) {
        Long tenantId = TenantContext.getTenantId();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 40) {
            throw new IllegalArgumentException("Enter a family name of 1 to 40 characters.");
        }
        String ur = nameUr == null || nameUr.isBlank() ? null : nameUr.trim();
        if (ur != null && ur.length() > 60) {
            throw new IllegalArgumentException("The Urdu name can be at most 60 characters.");
        }
        if (sortOrder == null || sortOrder < 1 || sortOrder > 999) {
            throw new IllegalArgumentException("The button order must be between 1 and 999.");
        }
        ProductFamily family;
        if (id == null) {
            if (productFamilyRepository.existsByTenantIdAndNameIgnoreCase(tenantId, n)) {
                throw new IllegalArgumentException("A family called '" + n + "' already exists.");
            }
            family = new ProductFamily();
            family.setTenantId(tenantId);
            family.setCreatedAt(LocalDateTime.now());
        } else {
            family = ownFamily(id);
            if (productFamilyRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, n, id)) {
                throw new IllegalArgumentException("A family called '" + n + "' already exists.");
            }
            family.setUpdatedAt(LocalDateTime.now());
        }
        family.setName(n);
        family.setNameUr(ur);
        family.setSortOrder(sortOrder);
        return productFamilyRepository.save(family);
    }

    /** Only an empty family can be deleted, so no product ever loses its family by accident. ADMIN only. */
    public void deleteFamily(Long id) {
        ProductFamily family = ownFamily(id);
        long used = productRepository.countByFamilyId(family.getId());
        if (used > 0) {
            throw new IllegalArgumentException("'" + family.getName() + "' still has " + used
                    + (used == 1 ? " product" : " products") + ". Move them to another family first.");
        }
        productFamilyRepository.delete(family);
    }

    /** findById is not tenant-filtered, so the company is checked here. */
    private Product ownProduct(Long id) {
        return productRepository.findById(id)
                .filter(p -> Objects.equals(p.getTenantId(), TenantContext.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + id));
    }

    private ProductFamily ownFamily(Long id) {
        return productFamilyRepository.findById(id)
                .filter(f -> Objects.equals(f.getTenantId(), TenantContext.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Family not found with ID: " + id));
    }

    /**
     * Switch a product on (offered to salesmen and agents) or off (hidden).
     * ADMIN only (checked in ProductController).
     * On needs both main prices entered — 0 counts, "Not set" does not.
     */
    public ProductPricingDTO setActive(Long productId, boolean active) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
        if (active && (product.getAgentPrice() == null || product.getSalesmanPrice() == null)) {
            throw new IllegalArgumentException("Enter both the agent price and the salesman price before switching '"
                    + product.getName() + "' on.");
        }
        product.setIsActive(active);
        return toPricingDTO(productRepository.save(product));
    }

    /**
     * Sales and stock entries only for products that are switched on. A
     * hidden product (e.g. a new one whose prices are not set yet) is never
     * offered by the apps; this refuses it even from an old app or a direct
     * API call.
     */
    public static Product requireActive(Product product) {
        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("'" + product.getName() + "' is not available yet.");
        }
        return product;
    }

    public ProductPricingDTO updatePricing(Long productId, ProductPricingUpdateDTO dto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
        product.setAgentPrice(dto.getAgentPrice());
        product.setSalesmanPrice(dto.getSalesmanPrice());
        return toPricingDTO(productRepository.save(product));
    }
}
