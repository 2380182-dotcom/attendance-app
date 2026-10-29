package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ProductPricingDTO;
import com.dawnbread.attendance.dto.ProductPricingUpdateDTO;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public List<ProductPricingDTO> getAllForPricing() {
        return productRepository.findAllByOrderByNameAsc().stream()
                .map(ProductService::toPricingDTO)
                .collect(Collectors.toList());
    }

    private static ProductPricingDTO toPricingDTO(Product p) {
        return new ProductPricingDTO(p.getId(), p.getName(), p.getCategory(), p.getUnit(), p.getAgentPrice(),
                p.getSalesmanPrice(), Boolean.TRUE.equals(p.getIsActive()));
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
